package com.example.roamingphotobooth.ptp

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log

/**
 * Pengganti PtpUsbConnection lama — sekarang pakai libusb (native) di baliknya,
 * bukan UsbDeviceConnection.bulkTransfer() Kotlin murni yang terbukti tidak stabil.
 */
class PtpNativeConnection(
    private val usbManager: UsbManager,
    private val device: UsbDevice
) {
    companion object {
        private const val TAG = "PtpNativeConnection"
    }

    fun open(): Boolean {
        val connection = usbManager.openDevice(device)
        if (connection == null) {
            Log.e(TAG, "Gagal membuka UsbDeviceConnection")
            return false
        }

        val fd = connection.fileDescriptor
        Log.d(TAG, "File descriptor: $fd")

        val wrapOk = NativePtpBridge.openDeviceWithFd(fd)
        if (!wrapOk) {
            Log.e(TAG, "libusb gagal wrap file descriptor")
            return false
        }

        val claimOk = NativePtpBridge.claimInterface()
        if (!claimOk) {
            Log.e(TAG, "libusb gagal claim interface")
            return false
        }

        Log.i(TAG, "Koneksi native PTP berhasil dibuka.")
        return true
    }

    fun write(data: ByteArray): Int {
        return NativePtpBridge.bulkWrite(data)
    }

    fun read(buffer: ByteArray, timeoutMs: Int = 5000): Int {
        return NativePtpBridge.bulkRead(buffer, buffer.size)
    }

    fun readInterrupt(buffer: ByteArray, timeoutMs: Int = 100): Int {
        // Untuk sekarang belum diimplementasikan di native bridge,
        // Canon EOS kita pakai GetEvent polling jadi ini tidak kritikal dulu
        return -1
    }

    fun close() {
        NativePtpBridge.closeDevice()
    }
}