package com.example.roamingphotobooth.ptp

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.util.Log

/**
 * Test isolasi: kirim command PTP GetDeviceInfo (0x1001) memakai
 * UsbDeviceConnection.bulkTransfer() BAWAAN ANDROID, TANPA libusb sama sekali.
 *
 * Tujuannya cuma satu: membuktikan apakah bulk transfer ke kamera memang
 * bisa jalan di device ini kalau tidak lewat libusb/wrap_sys_device.
 *
 * Ini KODE SEKALI PAKAI untuk debugging -- bukan pengganti PtpSessionManager.
 * Setelah selesai isolasi, file ini boleh dihapus.
 */
object NativeUsbBulkTest {

    private const val TAG = "NativeBulkTest"
    private const val TIMEOUT_MS = 5000

    /**
     * Panggil ini dengan UsbDevice yang sama persis dengan yang biasa
     * diserahkan ke PtpNativeConnection (mis. dari callback onDeviceReady
     * di PtpDeviceManager.startListening).
     */
    fun run(context: Context, device: UsbDevice) {
        Log.i(TAG, "=== Mulai test bulk transfer native (tanpa libusb) ===")

        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

        if (!usbManager.hasPermission(device)) {
            Log.e(TAG, "Tidak ada izin USB untuk device ini. Jalankan setelah permission granted.")
            return
        }

        // 1. Cari interface still-image (PTP) -- jangan asumsikan interface 0,
        //    cari berdasarkan class supaya valid untuk device manapun.
        var targetInterface: UsbInterface? = null
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            Log.i(TAG, "Interface #$i: class=${intf.interfaceClass} subclass=${intf.interfaceSubclass} endpointCount=${intf.endpointCount}")
            if (intf.interfaceClass == UsbConstants.USB_CLASS_STILL_IMAGE) {
                targetInterface = intf
            }
        }
        if (targetInterface == null) {
            Log.w(TAG, "Tidak ada interface dengan class STILL_IMAGE, fallback ke interface 0")
            targetInterface = device.getInterface(0)
        }

        // 2. Cari endpoint bulk IN dan bulk OUT dari interface itu (bukan hardcode 0x81/0x02)
        var epIn: UsbEndpoint? = null
        var epOut: UsbEndpoint? = null
        for (e in 0 until targetInterface.endpointCount) {
            val ep = targetInterface.getEndpoint(e)
            Log.i(
                TAG,
                "  Endpoint #$e: address=0x${ep.address.toString(16)} type=${ep.type} direction=${if (ep.direction == UsbConstants.USB_DIR_IN) "IN" else "OUT"}"
            )
            if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                if (ep.direction == UsbConstants.USB_DIR_IN) epIn = ep
                if (ep.direction == UsbConstants.USB_DIR_OUT) epOut = ep
            }
        }

        if (epIn == null || epOut == null) {
            Log.e(TAG, "Endpoint bulk IN/OUT tidak ketemu. epIn=$epIn epOut=$epOut")
            return
        }

        // 3. Buka koneksi & claim interface lewat jalur Android native
        val connection: UsbDeviceConnection? = usbManager.openDevice(device)
        if (connection == null) {
            Log.e(TAG, "usbManager.openDevice() gagal, null")
            return
        }

        val claimed = connection.claimInterface(targetInterface, true)
        Log.i(TAG, "claimInterface (native Android) hasil: $claimed")
        if (!claimed) {
            Log.e(TAG, "Gagal claim interface, batal.")
            connection.close()
            return
        }

        try {
            // 4. Susun command GetDeviceInfo (0x1001), transaction ID bebas (pakai 1)
            val commandPacket = PtpContainer.createCommand(0x1001, 1)
            Log.i(TAG, "Mengirim command 0x1001 via bulkTransfer native, ukuran=${commandPacket.size} byte")

            val writeResult = connection.bulkTransfer(epOut, commandPacket, commandPacket.size, TIMEOUT_MS)
            Log.i(TAG, "bulkTransfer WRITE hasil: $writeResult (negatif = gagal/timeout)")

            if (writeResult < 0) {
                Log.e(TAG, "Write gagal. STOP -- ini berarti masalahnya BUKAN di libusb, tapi lebih dalam (device/host).")
                return
            }

            // 5. Baca balasan (response block dari device)
            val readBuffer = ByteArray(512)
            val readResult = connection.bulkTransfer(epIn, readBuffer, readBuffer.size, TIMEOUT_MS)
            Log.i(TAG, "bulkTransfer READ hasil: $readResult (negatif = gagal/timeout)")

            if (readResult > 0) {
                val container = PtpContainer.parse(readBuffer, readResult)
                Log.i(
                    TAG,
                    "SUKSES! Container diterima - type=${container.code} responseOk=${container.isResponseOk()}"
                )
                Log.i(TAG, "=== KESIMPULAN: bulk transfer native BERHASIL. Masalahnya spesifik di jalur libusb. ===")
            } else {
                Log.e(TAG, "=== KESIMPULAN: bulk transfer native JUGA gagal. Masalahnya bukan di libusb, tapi lebih dalam (device/OS/host controller). ===")
            }
        } finally {
            connection.releaseInterface(targetInterface)
            connection.close()
        }
    }
}