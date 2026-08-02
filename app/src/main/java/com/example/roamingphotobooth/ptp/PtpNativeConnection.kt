package com.example.roamingphotobooth.ptp

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.util.Log

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
 */
class PtpNativeConnection(
    private val usbManager: UsbManager,
    private val device: UsbDevice
) {
    companion object {
        private const val TAG = "PtpNativeConnection"
    }

    private var usbConnection: UsbDeviceConnection? = null
    private var ptpInterface: UsbInterface? = null
    private var epIn: UsbEndpoint? = null
    private var epOut: UsbEndpoint? = null

    private var usingNativeFallback = false
    private var nativeInterfaceClaimed = false
    private var libusbOpened = false

    fun open(): Boolean {
        val connection = usbManager.openDevice(device)
        if (connection == null) {
            Log.e(TAG, "Gagal membuka UsbDeviceConnection")
            return false
        }
        usbConnection = connection

        // Cari interface still-image (PTP) & endpoint bulk IN/OUT secara dinamis
        // dari descriptor -- dibutuhkan untuk jalur fallback native, dan sekalian
        // dipakai untuk validasi (bukan hardcode nomor interface/endpoint).
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

        val fd = connection.fileDescriptor
        Log.d(TAG, "File descriptor: $fd")

        val wrapOk = NativePtpBridge.openDeviceWithFd(fd)
        val claimOk = wrapOk && NativePtpBridge.claimInterface()

        if (wrapOk && claimOk) {
            libusbOpened = true
            Log.i(TAG, "Koneksi native PTP (libusb) berhasil dibuka.")
            return true
        }

        // libusb gagal total dari awal (bukan cuma timeout transfer) -- langsung
        // pakai jalur native tanpa buang waktu mencoba transfer via libusb dulu.
        Log.w(TAG, "libusb gagal dibuka (wrap=$wrapOk claim=$claimOk), langsung pakai fallback native.")
        return switchToNativeFallback()
    }

    private fun switchToNativeFallback(): Boolean {
        if (usingNativeFallback) return true

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

        // Sama seperti libusb_clear_halt() di jalur libusb: bersihkan kemungkinan
        // status halt/stall yang tertinggal di endpoint dari transfer sebelumnya
        // yang timeout. Android tidak punya API clearHalt() publik, jadi ini
        // dikirim manual sebagai control transfer CLEAR_FEATURE(ENDPOINT_HALT).
        clearHaltNative(epOut)
        clearHaltNative(epIn)

        Log.i(TAG, "Fallback native aktif untuk sisa sesi ini.")
        return true
    }

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
        return connection.bulkTransfer(ep, data, data.size, 5000)
    }

    fun read(buffer: ByteArray, timeoutMs: Int = 5000): Int {
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