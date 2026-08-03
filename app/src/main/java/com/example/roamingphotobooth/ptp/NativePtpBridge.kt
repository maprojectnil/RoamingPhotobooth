package com.example.roamingphotobooth.ptp

object NativePtpBridge {
    init {
        System.loadLibrary("ptpbridge")
    }

    external fun testConnection(): String
    external fun openDeviceWithFd(fd: Int): Boolean
    external fun claimInterface(): Boolean
    external fun bulkWrite(data: ByteArray): Int
    // timeoutMs punya default 5000 (nilai lama, hardcoded di sisi native
    // sebelum ini) supaya pemanggil lain yang belum di-update tetap kompatibel.
    external fun bulkRead(buffer: ByteArray, maxLen: Int, timeoutMs: Int = 5000): Int
    external fun closeDevice()
}