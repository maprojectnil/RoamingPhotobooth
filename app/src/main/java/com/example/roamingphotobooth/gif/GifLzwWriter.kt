package com.example.roamingphotobooth.gif

import java.io.ByteArrayOutputStream

/**
 * Encoder LZW varian GIF (kode ukuran variabel 9..12 bit, clear code & end code,
 * output dipecah jadi sub-block maksimal 255 byte) -- dipanggil [GifEncoder] untuk
 * meng-compress data index warna tiap frame. Implementasi berdiri sendiri mengikuti
 * spesifikasi GIF89a (Table Based Image Data), tidak bergantung library luar.
 */
internal object GifLzwWriter {

    private const val MAX_CODE_BITS = 12
    private const val MAX_CODE = (1 shl MAX_CODE_BITS) - 1 // 4095

    /**
     * @param indices index warna (0..255) per piksel, urut baris demi baris.
     * @param colorBits jumlah bit warna (8 untuk palet 256 warna).
     */
    fun encode(indices: ByteArray, colorBits: Int, out: ByteArrayOutputStream) {
        val minCodeSize = if (colorBits < 2) 2 else colorBits
        out.write(minCodeSize) // byte pertama blok data gambar: LZW Minimum Code Size

        val clearCode = 1 shl minCodeSize
        val endCode = clearCode + 1

        var nextCode = endCode + 1
        var codeSize = minCodeSize + 1
        var dict = HashMap<Int, Int>()

        fun dictKey(prefix: Int, symbol: Int) = (prefix shl 8) or symbol

        fun resetDictionary() {
            dict = HashMap()
            nextCode = endCode + 1
            codeSize = minCodeSize + 1
        }

        val bitWriter = GifBitSubBlockWriter(out)
        bitWriter.writeCode(clearCode, codeSize)

        if (indices.isEmpty()) {
            bitWriter.writeCode(endCode, codeSize)
            bitWriter.flush()
            out.write(0) // block terminator
            return
        }

        var prefix = indices[0].toInt() and 0xFF
        for (i in 1 until indices.size) {
            val symbol = indices[i].toInt() and 0xFF
            val key = dictKey(prefix, symbol)
            val existing = dict[key]
            if (existing != null) {
                prefix = existing
                continue
            }

            bitWriter.writeCode(prefix, codeSize)

            if (nextCode <= MAX_CODE) {
                // <-- FIX: kode yang BARU SAJA di-assign (SEBELUM nextCode++) dipakai
                // buat ngecek apa saatnya nambah codeSize -- BUKAN nextCode SESUDAH
                // di-increment. Ini penting banget & gampang salah (bug klasik LZW-GIF):
                // DECODER hanya bisa nambah 1 entri dictionary-nya SETELAH baca kode
                // BERIKUTNYA (dia butuh tahu simbol pertama dari kode berikutnya buat
                // melengkapi entry baru) -- makanya ukuran dictionary di sisi decoder
                // SELALU TELAT 1 LANGKAH dibanding di sisi encoder pada titik manapun di
                // stream. Kalau encoder naikkan codeSize PERSIS begitu dictionary-nya
                // sendiri penuh (nextCode SESUDAH increment == 2^codeSize), decoder yang
                // masih telat 1 entri itu belum siap baca kode berikutnya pakai bit lebih
                // banyak -> bitstream jadi tidak singkron & GIF-nya rusak/tidak bisa
                // dibuka (sudah dites manual lewat referensi Python + PIL/ImageMagick,
                // sebelum fix ini persis gagal dengan gejala itu). Solusinya: TUNDA
                // kenaikan codeSize 1 kode lagi -- cek pakai kode yang BARU di-assign
                // (assignedCode, sebelum nextCode++), bukan nextCode yang sudah nambah.
                val assignedCode = nextCode
                dict[key] = assignedCode
                nextCode++
                if (assignedCode >= (1 shl codeSize) && codeSize < MAX_CODE_BITS) {
                    codeSize++
                }
            } else {
                // Dictionary penuh (sudah mencapai 4096 entri) -> kirim clear code,
                // dictionary & code size di-reset ke kondisi awal.
                bitWriter.writeCode(clearCode, codeSize)
                resetDictionary()
            }
            prefix = symbol
        }

        bitWriter.writeCode(prefix, codeSize)
        bitWriter.writeCode(endCode, codeSize)
        bitWriter.flush()
        out.write(0) // block terminator blok data gambar
    }
}

/**
 * Tulis kode-kode LZW sebagai aliran bit LSB-first (sesuai spec GIF), lalu kemas
 * jadi sub-block data maksimal 255 byte (tiap sub-block diawali 1 byte panjang).
 */
private class GifBitSubBlockWriter(private val out: ByteArrayOutputStream) {
    private var bitBuffer = 0
    private var bitCount = 0
    private val pendingBytes = ArrayList<Byte>(256)

    fun writeCode(code: Int, codeSize: Int) {
        bitBuffer = bitBuffer or (code shl bitCount)
        bitCount += codeSize
        while (bitCount >= 8) {
            pendingBytes.add((bitBuffer and 0xFF).toByte())
            bitBuffer = bitBuffer ushr 8
            bitCount -= 8
            if (pendingBytes.size >= 255) {
                flushSubBlock(255)
            }
        }
    }

    fun flush() {
        if (bitCount > 0) {
            pendingBytes.add((bitBuffer and 0xFF).toByte())
            bitBuffer = 0
            bitCount = 0
        }
        while (pendingBytes.isNotEmpty()) {
            flushSubBlock(minOf(255, pendingBytes.size))
        }
    }

    private fun flushSubBlock(count: Int) {
        out.write(count)
        for (i in 0 until count) out.write(pendingBytes[i].toInt())
        // Buang [count] byte pertama yang sudah ditulis.
        for (i in 0 until count) pendingBytes.removeAt(0)
    }
}
