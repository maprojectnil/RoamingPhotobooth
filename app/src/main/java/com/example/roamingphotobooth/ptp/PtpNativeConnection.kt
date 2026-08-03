package com.example.roamingphotobooth.ptp

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

/**
 * Koneksi PTP hybrid: libusb (native) sebagai jalur utama, dengan fallback
 * otomatis ke UsbDeviceConnection.bulkTransfer() bawaan Android kalau libusb
 * gagal.
 *
 * Kenapa hybrid: di sebagian besar device libusb bekerja normal (lebih cepat,
 * tidak ada overhead JNI marshalling per-transfer besar). Tapi di beberapa
 * host (terbukti pada Redmi Pad 2 / chipset MediaTek tertentu) libusb_bulk_transfer
 * konsisten timeout walau claim_interface & clear_halt sukses, sementara
 * UsbDeviceConnection.bulkTransfer() native terbukti bekerja normal di device
 * yang sama, kamera yang sama. Root cause diduga ada di layer libusb/usbfs
 * saat menerima fd yang di-wrap lintas proses dari system_server, bukan hal
 * yang bisa kita perbaiki dari sisi aplikasi.
 *
 * Strategi: pakai libusb dulu. Begitu satu operasi (write ATAU read) gagal,
 * langsung pindah permanen ke jalur native untuk sisa sesi ini -- tidak
 * mencoba bolak-balik ke libusb lagi di sesi yang sama, supaya tidak
 * berulang kali kena timeout 5 detik per percobaan.
 *
 * Selain itu, kegagalan dicatat lewat [UsbQuirksStorage] per device (lihat
 * catatan di file itu untuk alasan lengkapnya): begitu satu device pernah
 * gagal dengan libusb, sesi PTP berikutnya untuk device yang sama TIDAK
 * akan mencoba libusb sama sekali -- langsung ke native fallback dari awal.
 * Ini penting karena dari observasi, device yang sekali gagal lewat libusb
 * cenderung "macet" untuk sisa sesi USB fisik itu; percobaan ulang libusb
 * di sesi baru cuma buang waktu (timeout lagi) sebelum akhirnya jatuh ke
 * fallback juga.
 */
class PtpNativeConnection(
    private val usbManager: UsbManager,
    private val device: UsbDevice,
    private val quirksStorage: UsbQuirksStorage
) {
    companion object {
        private const val TAG = "PtpNativeConnection"

        // Satu thread khusus, TUNGGAL, untuk SELURUH siklus hidup koneksi USB --
        // dari open()/claimInterface() sampai semua write()/read() berikutnya di
        // PtpSessionManager. Alasan: dari observasi berulang, transfer pertama
        // SELALU gagal instan (5-19ms, bukan timeout asli ~5 detik) tepat setelah
        // pindah dari thread yang meng-claim interface (main thread) ke thread
        // coroutine IO yang berbeda (Dispatchers.IO itu thread pool elastis --
        // BEDA thread OS tiap kali dipanggil walau sama-sama "Dispatchers.IO").
        // Satu-satunya jalur yang konsisten bersih (NativeUsbBulkTest) menjalankan
        // claim+write+read di SATU thread yang sama, tanpa pindah sama sekali.
        // Host USB (MediaTek/MIUI) yang sudah terbukti rewel di banyak titik lain
        // (butuh settling delay, dst.) membuat ini kandidat kuat -- pakai satu
        // thread khusus supaya claim & transfer PASTI di thread OS yang identik.
        // PtpSessionManager WAJIB memakai dispatcher yang sama ini untuk scope-nya.
        val usbDispatcher: CoroutineDispatcher =
            Executors.newSingleThreadExecutor { r -> Thread(r, "PtpUsbIO") }.asCoroutineDispatcher()
    }

    private var usbConnection: UsbDeviceConnection? = null
    private var ptpInterface: UsbInterface? = null
    private var epIn: UsbEndpoint? = null
    private var epOut: UsbEndpoint? = null

    private var usingNativeFallback = false
    private var nativeInterfaceClaimed = false
    private var libusbOpened = false

    // Diisi di open(), dipakai untuk tahu kunci device ini & apakah device
    // ini sudah tercatat bermasalah dengan libusb dari sesi sebelumnya.
    private var deviceKey: String = ""
    private var knownLibusbUnreliable = false

    fun open(): Boolean {
        // Cari interface still-image (PTP) & endpoint bulk IN/OUT dari descriptor
        // device -- ini TIDAK butuh koneksi terbuka sama sekali (UsbDevice.getInterface
        // /getEndpoint murni baca descriptor). Sengaja dipindah ke sini, sebelum
        // openDevice() dipanggil, supaya kita bisa menentukan strategi (libusb vs
        // native langsung) TANPA perlu buka-lalu-tutup koneksi cuma untuk baca
        // descriptor -- lihat catatan panjang di [openNativeDirect].
        var foundInterface: UsbInterface? = null
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            if (intf.interfaceClass == UsbConstants.USB_CLASS_STILL_IMAGE) {
                foundInterface = intf
                break
            }
        }
        if (foundInterface == null) {
            Log.w(TAG, "Tidak ada interface STILL_IMAGE, fallback ke interface 0")
            foundInterface = device.getInterface(0)
        }
        ptpInterface = foundInterface

        for (e in 0 until foundInterface.endpointCount) {
            val ep = foundInterface.getEndpoint(e)
            if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                if (ep.direction == UsbConstants.USB_DIR_IN) epIn = ep
                if (ep.direction == UsbConstants.USB_DIR_OUT) epOut = ep
            }
        }
        if (epIn == null || epOut == null) {
            Log.e(TAG, "Endpoint bulk IN/OUT tidak ketemu di interface PTP")
            return false
        }

        deviceKey = quirksStorage.keyFor(device)
        knownLibusbUnreliable = quirksStorage.isLibusbUnreliable(deviceKey)

        if (knownLibusbUnreliable) {
            // Device ini sudah pernah tercatat gagal dengan libusb di sesi
            // sebelumnya -- jangan coba libusb sama sekali, dan JANGAN buka
            // koneksi cuma untuk ditutup lagi (versi sebelumnya buka koneksi
            // di sini lalu switchToNativeFallback() menutupnya & buka ulang --
            // buang waktu + potensi race condition tambahan di USB host yang
            // sudah rewel ini). Langsung satu koneksi bersih lewat
            // openNativeDirect(), meniru persis NativeUsbBulkTest.
            Log.w(TAG, "Device '$deviceKey' tercatat bermasalah dengan libusb, langsung native (single clean open) tanpa mencoba libusb sama sekali.")
            return openNativeDirect()
        }

        val connection = usbManager.openDevice(device)
        if (connection == null) {
            Log.e(TAG, "Gagal membuka UsbDeviceConnection")
            return false
        }
        usbConnection = connection

        val fd = connection.fileDescriptor
        Log.d(TAG, "File descriptor: $fd")

        val wrapOk = NativePtpBridge.openDeviceWithFd(fd)
        val claimOk = wrapOk && NativePtpBridge.claimInterface()

        if (wrapOk && claimOk) {
            libusbOpened = true
            Log.i(TAG, "Koneksi native PTP (libusb) berhasil dibuka.")
            return true
        }

        // libusb gagal total dari awal (bukan cuma timeout transfer) -- di sini
        // fd SUDAH disentuh libusb (openDeviceWithFd/claimInterface sempat
        // dipanggil), jadi switchToNativeFallback() yang buka koneksi baru
        // (bukan reuse fd ini) tetap diperlukan -- lihat catatan di fungsi itu.
        Log.w(TAG, "libusb gagal dibuka (wrap=$wrapOk claim=$claimOk), langsung pakai fallback native.")
        return switchToNativeFallback()
    }

    /**
     * Jalur native "bersih": SATU kali openDevice() + claimInterface(), tanpa
     * buka-tutup ganda, dipakai khusus untuk device yang sudah tercatat
     * bermasalah dengan libusb ([knownLibusbUnreliable]) -- di kasus ini fd
     * belum pernah disentuh libusb sama sekali jadi tidak ada alasan untuk
     * buka-tutup-buka ulang seperti di [switchToNativeFallback].
     *
     * Termasuk jeda settling 300ms sebelum claim, MENIRU jalur libusb (lihat
     * usleep(300000) sebelum libusb_claim_interface() di ptp_bridge.cpp) --
     * komentar di sana menyebutkan beberapa ROM (MIUI/HyperOS di MediaTek)
     * masih menyelesaikan handshake internal terhadap device tepat setelah
     * izin USB diberikan, dan jalur native ini belum pernah punya jeda
     * setara sebelumnya.
     */
    private fun openNativeDirect(): Boolean {
        val connection = usbManager.openDevice(device)
        if (connection == null) {
            Log.e(TAG, "Native langsung GAGAL: tidak bisa buka UsbDeviceConnection.")
            return false
        }
        usbConnection = connection

        val intf = ptpInterface ?: return false

        Thread.sleep(300)

        val claimed = connection.claimInterface(intf, true)
        if (!claimed) {
            Log.e(TAG, "Native langsung GAGAL: claimInterface gagal.")
            return false
        }

        nativeInterfaceClaimed = true
        usingNativeFallback = true
        Log.i(TAG, "Native langsung aktif (satu koneksi bersih, dengan settling delay, tanpa clear_halt preemptif).")
        return true
    }

    private fun switchToNativeFallback(): Boolean {
        if (usingNativeFallback) return true

        // Catat quirk ini SEKALI per kejadian baru (bukan saat kita memang
        // sudah tahu dari awal dan sengaja skip libusb -- itu bukan info
        // baru, deviceKey mungkin juga belum terisi kalau dipanggil dari
        // luar open(), makanya dicek null-safe di bawah).
        if (!knownLibusbUnreliable && deviceKey.isNotEmpty()) {
            quirksStorage.markLibusbUnreliable(deviceKey)
            knownLibusbUnreliable = true
        }

        Log.w(TAG, "Beralih ke fallback native (UsbDeviceConnection.bulkTransfer), meninggalkan libusb untuk sesi ini.")

        // Lepas handle libusb supaya tidak berebut fd dengan jalur native.
        if (libusbOpened) {
            NativePtpBridge.closeDevice()
            libusbOpened = false
        }

        // PENTING: jangan pakai ulang UsbDeviceConnection/fd lama yang sudah
        // disentuh libusb. Terbukti dari isolasi sebelumnya (NativeUsbBulkTest)
        // bahwa transfer native cuma jalan mulus di fd yang BENAR-BENAR baru --
        // fd lama tampaknya menyisakan state URB macet di kernel akibat
        // transfer libusb yang timeout, walau clear_halt sudah dipanggil dan
        // sukses di kedua sisi. Jadi tutup total koneksi lama, buka baru.
        usbConnection?.close()
        val freshConnection = usbManager.openDevice(device)
        if (freshConnection == null) {
            Log.e(TAG, "Fallback native GAGAL: tidak bisa buka UsbDeviceConnection baru.")
            return false
        }
        usbConnection = freshConnection

        val connection = freshConnection
        val intf = ptpInterface ?: return false

        val claimed = connection.claimInterface(intf, true)
        if (!claimed) {
            Log.e(TAG, "Fallback native GAGAL: claimInterface juga gagal.")
            return false
        }

        nativeInterfaceClaimed = true
        usingNativeFallback = true

        // PENTING -- perubahan dari versi sebelumnya: TIDAK lagi mengirim
        // clear_halt (CLEAR_FEATURE ENDPOINT_HALT) preemptif di sini.
        //
        // Dari observasi terbaru: NativeUsbBulkTest (yang TIDAK pernah
        // mengirim clear_halt sama sekali) konsisten sukses instan setiap
        // kali dites di device fisik fresh. Sebaliknya, DUA jalur yang sama-
        // sama mengirim clear_halt sebelum transfer pertama -- baik libusb
        // (lihat clear_halt di ptp_bridge.cpp::claimInterface) maupun jalur
        // native ini versi sebelumnya -- SELALU gagal di transfer pertama
        // (baca timeout atau gagal instan). Endpoint yang baru saja di-claim
        // dari device yang belum pernah ditransfer sama sekali semestinya
        // tidak dalam kondisi halted, jadi clear_halt di titik ini bukan
        // "membersihkan state lama" seperti dugaan awal -- kemungkinan besar
        // JUSTRU clear_halt itu sendiri yang membuat device bingung/wedge di
        // kombinasi host MediaTek/MIUI + kamera Canon ini.
        //
        // Jadi sekarang kita meniru persis kondisi NativeUsbBulkTest yang
        // terbukti bersih: claim interface, langsung transfer, tanpa
        // clear_halt di awal. clearHaltNative() masih disediakan di bawah
        // kalau suatu saat perlu dipakai SECARA REAKTIF (misal device benar-
        // benar terbukti stall di tengah sesi yang sedang berjalan) -- bukan
        // dipanggil otomatis di setiap open.

        Log.i(TAG, "Fallback native aktif untuk sisa sesi ini (tanpa clear_halt preemptif).")
        return true
    }

    /**
     * Kirim CLEAR_FEATURE(ENDPOINT_HALT) manual ke satu endpoint. Android
     * tidak punya API clearHalt() publik seperti libusb, jadi ini dikirim
     * sebagai control transfer manual.
     *
     * SENGAJA TIDAK dipanggil otomatis di [switchToNativeFallback] lagi --
     * lihat komentar di sana. Simpan fungsi ini untuk pemakaian reaktif kalau
     * nanti terbukti perlu (misal endpoint benar-benar stall di tengah sesi).
     */
    private fun clearHaltNative(endpoint: UsbEndpoint?) {
        val connection = usbConnection ?: return
        val ep = endpoint ?: return

        val bmRequestType = 0x02 // host-to-device | standard | recipient=endpoint
        val bRequest = 0x01      // CLEAR_FEATURE
        val wValue = 0x00        // ENDPOINT_HALT
        val wIndex = ep.address

        val result = connection.controlTransfer(bmRequestType, bRequest, wValue, wIndex, null, 0, 5000)
        Log.i(TAG, "clearHaltNative endpoint=0x${ep.address.toString(16)} hasil=$result")
    }

    fun write(data: ByteArray): Int {
        if (!usingNativeFallback) {
            val result = NativePtpBridge.bulkWrite(data)
            if (result >= 0) return result

            Log.w(TAG, "libusb bulkWrite gagal (r=$result), pindah ke fallback native.")
            if (!switchToNativeFallback()) return result
            // lanjut ke bawah, coba lagi lewat native supaya percobaan ini
            // tidak langsung dianggap gagal total oleh pemanggil
        }

        val connection = usbConnection ?: return -1
        val ep = epOut ?: return -1
        // Timeout 10000ms (bukan 5000ms) -- meniru qDslrDashboard (referensi
        // implementasi PTP-over-USB Android yang terbukti stabil di device
        // serupa), yang konsisten memakai 10 detik untuk bulkTransfer biasa.
        return connection.bulkTransfer(ep, data, data.size, 10000)
    }

    fun read(buffer: ByteArray, timeoutMs: Int = 10000): Int {
        if (!usingNativeFallback) {
            val result = NativePtpBridge.bulkRead(buffer, buffer.size)
            if (result >= 0) return result

            Log.w(TAG, "libusb bulkRead gagal (r=$result), pindah ke fallback native.")
            if (!switchToNativeFallback()) return result
        }

        val connection = usbConnection ?: return -1
        val ep = epIn ?: return -1
        return connection.bulkTransfer(ep, buffer, buffer.size, timeoutMs)
    }

    /**
     * Sama seperti [read], tapi menulis ke offset tertentu di buffer yang
     * sudah dialokasikan sebelumnya, dan sengaja hanya minta [length] byte
     * (bukan buffer.size) -- dipakai [PtpSessionManager] untuk membaca
     * SISA data sebuah paket PTP (setelah panjang totalnya diketahui dari
     * header) langsung ke posisi yang tepat di buffer akhir, tanpa perlu
     * buffer perantara. Meniru pola readPtpPacketEP() di qDslrDashboard.
     *
     * Catatan: jalur libusb tidak punya overload dengan offset, jadi untuk
     * device yang masih lewat libusb ini baca ke buffer sementara lalu
     * disalin -- tidak masalah karena device yang justru butuh pola baca
     * bertahap ini (yang rewel) semuanya sudah lewat native fallback murni.
     */
    fun readInto(buffer: ByteArray, offset: Int, length: Int, timeoutMs: Int = 10000): Int {
        if (!usingNativeFallback) {
            val temp = ByteArray(length)
            val result = NativePtpBridge.bulkRead(temp, length)
            if (result >= 0) {
                System.arraycopy(temp, 0, buffer, offset, result)
                return result
            }
            Log.w(TAG, "libusb bulkRead (readInto) gagal (r=$result), pindah ke fallback native.")
            if (!switchToNativeFallback()) return result
        }

        val connection = usbConnection ?: return -1
        val ep = epIn ?: return -1
        return connection.bulkTransfer(ep, buffer, offset, length, timeoutMs)
    }

    fun readInterrupt(buffer: ByteArray, timeoutMs: Int = 100): Int {
        // Untuk sekarang belum diimplementasikan di kedua jalur (libusb & native),
        // Canon EOS kita pakai GetEvent polling jadi ini tidak kritikal dulu
        return -1
    }

    fun close() {
        if (libusbOpened) {
            NativePtpBridge.closeDevice()
            libusbOpened = false
        }
        if (nativeInterfaceClaimed) {
            ptpInterface?.let { usbConnection?.releaseInterface(it) }
            nativeInterfaceClaimed = false
        }
        usbConnection?.close()
        usbConnection = null
    }
}