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
     * Helper: decode ByteArray (dari hasil download PTP, atau hasil re-encode JPEG
     * di EsCameraSession) jadi Bitmap.
     *
     * Catatan: frame live view TIDAK lagi lewat sini sejak migrasi ke library
     * es-ptp-camera — library-nya sudah memberikan Bitmap langsung yang sudah
     * di-decode (lihat EsCameraSession.onLiveViewData / LiveViewData.bitmap),
     * jadi tidak ada lagi ByteArray mentah untuk di-decode di jalur live view.
     */
    fun decodeBitmap(bytes: ByteArray): Bitmap? {
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
}