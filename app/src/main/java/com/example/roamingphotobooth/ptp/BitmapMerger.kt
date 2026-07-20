package com.example.roamingphotobooth.ptp

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint

object BitmapMerger {

    /**
     * Flip horizontal (efek cermin) sebuah bitmap. Dipakai di titik paling awal —
     * begitu foto dari kamera selesai di-decode, SEBELUM masuk ke slot template /
     * di-merge / disimpan — supaya efek mirror konsisten ke semua turunannya
     * (preview kiri, layar review, hasil akhir yang disimpan & di-upload ke Drive).
     *
     * Bitmap sumber di-recycle otomatis kalau bukan bitmap yang sama dengan hasil
     * (aman dipanggil langsung setelah decode, sebelum bitmap sumber dipakai lagi
     * di tempat lain).
     */
    fun mirrorHorizontal(source: Bitmap): Bitmap {
        val matrix = Matrix().apply { preScale(-1f, 1f) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    /**
     * Menggabungkan foto asli dari kamera dengan frame overlay PNG.
     */
    fun mergeBitmap(cameraPhoto: Bitmap, frameOverlay: Bitmap): Bitmap {
        val resultWidth = cameraPhoto.width
        val resultHeight = cameraPhoto.height

        val resultBitmap = Bitmap.createBitmap(
            resultWidth,
            resultHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(resultBitmap)

        canvas.drawBitmap(cameraPhoto, 0f, 0f, null)

        val scaledFrame = if (frameOverlay.width != resultWidth || frameOverlay.height != resultHeight) {
            Bitmap.createScaledBitmap(frameOverlay, resultWidth, resultHeight, true)
        } else {
            frameOverlay
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(scaledFrame, 0f, 0f, paint)

        if (scaledFrame != frameOverlay) scaledFrame.recycle()

        return resultBitmap
    }

    /**
     * Helper: decode ByteArray (dari hasil download PTP) jadi Bitmap
     */
    fun decodeBitmap(bytes: ByteArray): Bitmap? {
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    // Dipakai khusus buat decodeLiveViewFrame() di bawah. RGB_565 = 2 byte/pixel
    // (vs ARGB_8888 default = 4 byte/pixel) — live view kamera nggak butuh alpha
    // channel, jadi ini aman. Instance-nya dipakai ulang (bukan bikin Options baru
    // tiap panggilan) supaya tidak nambah alokasi kecil-kecil di jalur yang sudah
    // dipanggil ~20x/detik.
    private val liveViewDecodeOptions = BitmapFactory.Options().apply {
        inPreferredConfig = Bitmap.Config.RGB_565
    }

    /**
     * Decode frame JPEG live view. TERPISAH dari [decodeBitmap] biasa karena live view
     * di-decode ~20x/detik (tiap 50ms, lihat PtpSessionManager.pollViewFinderData) —
     * kalau pakai ARGB_8888 default (4 byte/pixel) itu ~48MB/detik alokasi yang
     * langsung dibuang lagi, bikin GC jalan terus-terusan dan live view kelihatan
     * lancar sebentar (~2 detik, sebelum young-gen heap penuh) lalu patah-patah
     * berulang selama sesi (GC pause tiap heap penuh lagi). RGB_565 motong alokasi
     * itu jadi separuh, cukup buat nurunin frekuensi GC secara signifikan tanpa
     * ngubah kualitas foto hasil akhir (yang tetap lewat decodeBitmap() biasa).
     */
    fun decodeLiveViewFrame(bytes: ByteArray): Bitmap? {
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, liveViewDecodeOptions)
    }
}