package com.example.roamingphotobooth.gif

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

/**
 * Encoder GIF89a animasi yang berdiri sendiri (tanpa dependency pihak ketiga) --
 * dipakai [PhotoGifBuilder] untuk membuat animasi slideshow dari foto-foto MENTAH
 * (tanpa frame) 1 sesi. Pakai 1 Global Color Table (256 warna, lihat
 * [GifColorPalette]) yang sama untuk semua frame + kompresi LZW per frame (lihat
 * [GifLzwWriter]).
 */
internal object GifEncoder {

    /**
     * @param frames daftar bitmap ARGB, SEMUA harus berukuran (lebar x tinggi) SAMA
     *   PERSIS -- 1 kanvas GIF cuma boleh 1 ukuran (lihat PhotoGifBuilder.centerCropScale).
     * @param frameDelayCentiseconds jeda antar frame, satuan 1/100 detik (spec GIF).
     * @param loop true = animasi diulang terus-menerus (NETSCAPE2.0 loop count 0).
     */
    fun encode(
        frames: List<Bitmap>,
        frameDelayCentiseconds: Int,
        loop: Boolean = true
    ): ByteArray {
        require(frames.isNotEmpty()) { "frames tidak boleh kosong" }
        val width = frames[0].width
        val height = frames[0].height

        val out = ByteArrayOutputStream()
        out.write("GIF89a".toByteArray(Charsets.US_ASCII))
        writeShortLE(out, width)
        writeShortLE(out, height)
        // Packed field Logical Screen Descriptor: global color table flag=1,
        // color resolution=7 (8 bit/kanal), sort flag=0, size global color table=7
        // (-> 2^(7+1) = 256 warna).
        out.write(0xF7)
        out.write(0) // background color index
        out.write(0) // pixel aspect ratio (tidak dipakai)
        out.write(GifColorPalette.globalColorTable())

        if (loop) {
            writeNetscapeLoopExtension(out)
        }

        for (frame in frames) {
            require(frame.width == width && frame.height == height) {
                "Semua frame GIF harus berukuran sama"
            }
            writeGraphicControlExtension(out, frameDelayCentiseconds)
            writeImageDescriptor(out, width, height)
            GifLzwWriter.encode(GifColorPalette.mapToIndices(frame), colorBits = 8, out = out)
        }

        out.write(0x3B) // GIF Trailer
        return out.toByteArray()
    }

    private fun writeNetscapeLoopExtension(out: ByteArrayOutputStream) {
        out.write(0x21) // Extension Introducer
        out.write(0xFF) // Application Extension Label
        out.write(11) // block size
        out.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
        out.write(3) // ukuran sub-block data
        out.write(1) // sub-block id: loop count berikut
        writeShortLE(out, 0) // 0 = ulang tanpa batas
        out.write(0) // block terminator
    }

    private fun writeGraphicControlExtension(out: ByteArrayOutputStream, delayCentiseconds: Int) {
        out.write(0x21) // Extension Introducer
        out.write(0xF9) // Graphic Control Label
        out.write(4) // block size
        out.write(0x00) // disposal method: tidak ditentukan, tanpa warna transparan
        writeShortLE(out, delayCentiseconds)
        out.write(0) // transparent color index (tidak dipakai)
        out.write(0) // block terminator
    }

    private fun writeImageDescriptor(out: ByteArrayOutputStream, width: Int, height: Int) {
        out.write(0x2C) // Image Separator
        writeShortLE(out, 0) // left
        writeShortLE(out, 0) // top
        writeShortLE(out, width)
        writeShortLE(out, height)
        out.write(0x00) // tanpa local color table, tanpa interlace
    }

    private fun writeShortLE(out: ByteArrayOutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value ushr 8) and 0xFF)
    }
}
