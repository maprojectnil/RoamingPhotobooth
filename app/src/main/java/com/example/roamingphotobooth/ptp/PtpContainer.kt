package com.example.roamingphotobooth.ptp

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Merepresentasikan 1 paket PTP (header + payload).
 * Semua komunikasi dengan kamera dibungkus dalam struktur ini.
 */
class PtpContainer {

    var containerType: Int = 0
        private set
    var code: Int = 0
        private set
    var transactionId: Long = 0
        private set
    var payload: ByteArray = ByteArray(0)
        private set

    companion object {
        const val HEADER_SIZE = 12 // 4 (length) + 2 (type) + 2 (code) + 4 (transaction id)

        fun createDataPacket(opCode: Int, transactionId: Long, payload: ByteArray): ByteArray {
            val totalLength = HEADER_SIZE + payload.size
            val buffer = ByteBuffer.allocate(totalLength).order(ByteOrder.LITTLE_ENDIAN)
            buffer.putInt(totalLength)
            buffer.putShort(PtpContainerType.DATA.toShort())
            buffer.putShort(opCode.toShort())
            buffer.putInt(transactionId.toInt())
            buffer.put(payload)
            return buffer.array()
        }

        /**
         * Bikin container untuk COMMAND (perintah yang kita kirim ke kamera)
         */
        fun createCommand(opCode: Int, transactionId: Long, params: IntArray = IntArray(0)): ByteArray {
            val payloadSize = params.size * 4
            val totalLength = HEADER_SIZE + payloadSize

            val buffer = ByteBuffer.allocate(totalLength).order(ByteOrder.LITTLE_ENDIAN)
            buffer.putInt(totalLength)
            buffer.putShort(PtpContainerType.COMMAND.toShort())
            buffer.putShort(opCode.toShort())
            buffer.putInt(transactionId.toInt())
            for (param in params) {
                buffer.putInt(param)
            }
            return buffer.array()
        }

        /**
         * Parse byte array mentah (hasil terima dari USB) jadi PtpContainer yang mudah dibaca
         */
        fun parse(data: ByteArray, length: Int): PtpContainer {
            val buffer = ByteBuffer.wrap(data, 0, length).order(ByteOrder.LITTLE_ENDIAN)
            val container = PtpContainer()

            buffer.int // skip: total length (sudah kita tahu dari parameter `length`)
            container.containerType = buffer.short.toInt() and 0xFFFF
            container.code = buffer.short.toInt() and 0xFFFF
            container.transactionId = buffer.int.toLong() and 0xFFFFFFFFL

            val payloadSize = length - HEADER_SIZE
            if (payloadSize > 0) {
                container.payload = ByteArray(payloadSize)
                buffer.get(container.payload, 0, payloadSize)
            }

            return container
        }
    }

    fun isResponseOk(): Boolean {
        return containerType == PtpContainerType.RESPONSE && code == PtpResponseCode.OK
    }
}