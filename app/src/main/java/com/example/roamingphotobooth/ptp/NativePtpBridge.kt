package com.example.roamingphotobooth.ptp

object NativePtpBridge {
    init {
        System.loadLibrary("ptpbridge")
    }

    external fun testConnection(): String
    external fun openDeviceWithFd(fd: Int): Boolean
    external fun claimInterface(): Boolean
    external fun bulkWrite(data: ByteArray): Int
    external fun bulkRead(buffer: ByteArray, maxLen: Int): Int
    external fun closeDevice()
}