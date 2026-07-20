package com.example.roamingphotobooth.template

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import com.example.roamingphotobooth.ptp.BitmapMerger

private const val TAG = "TemplateSession"

/**
 * Mengelola 1 sesi pengambilan foto berdasarkan template:
 * menyimpan progress foto per slot, dan menggabungkan semuanya jadi 1 gambar akhir.
 */
class TemplateSessionManager(private val template: PhotoTemplate) {

    private val capturedPhotos = mutableMapOf<Int, Bitmap>() // key: slot order (1, 2, 3, ...)

    val totalSlots: Int get() = template.slots.map { it.order }.distinct().size
    val filledSlots: Int get() = capturedPhotos.size
    val isComplete: Boolean get() = filledSlots >= totalSlots

    /**
     * Slot mana yang harus diisi SELANJUTNYA (urutan 1, 2, 3, ...).
     * Return null kalau semua slot sudah terisi.
     */
    fun nextSlotOrder(): Int? {
        val filled = capturedPhotos.keys
        return template.slots
            .map { it.order }
            .sorted()
            .firstOrNull { it !in filled }
    }

    /**
     * Panggil ini setiap kali foto baru berhasil di-capture.
     * Otomatis masuk ke slot berikutnya yang masih kosong.
     */
    fun addPhoto(photoBytes: ByteArray): Boolean {
        val slotOrder = nextSlotOrder() ?: run {
            Log.w(TAG, "addPhoto dipanggil tapi semua slot sudah penuh")
            return false
        }

        val decoded = BitmapFactory.decodeByteArray(photoBytes, 0, photoBytes.size)
        if (decoded == null) {
            Log.e(TAG, "Gagal decode foto jadi Bitmap")
            return false
        }

        // Mirror horizontal (efek cermin) di sini, di titik paling awal — supaya
        // preview kiri, layar review, dan hasil akhir yang disimpan/di-upload semua
        // otomatis konsisten pakai foto yang sudah ke-flip ini.
        val bitmap = BitmapMerger.mirrorHorizontal(decoded)
        decoded.recycle()

        capturedPhotos[slotOrder] = bitmap
        Log.i(TAG, "Foto masuk ke slot $slotOrder. Progress: $filledSlots/$totalSlots")
        return true
    }

    fun reset() {
        capturedPhotos.values.forEach { it.recycle() }
        capturedPhotos.clear()
    }

    /**
     * Gabungkan semua foto ke posisi slot masing-masing, ditumpuk DI BAWAH frame PNG.
     * Hasil akhir berukuran sama dengan frame asli (frameWidthPx x frameHeightPx).
     * Mensyaratkan semua slot sudah terisi (dipakai untuk hasil akhir yang disimpan).
     */
    fun buildFinalImage(frameBitmap: Bitmap): Bitmap? {
        if (!isComplete) {
            Log.w(TAG, "buildFinalImage dipanggil padahal belum semua slot terisi ($filledSlots/$totalSlots)")
            return null
        }
        return buildComposite(frameBitmap)
    }

    /**
     * Sama seperti [buildFinalImage], tapi BOLEH dipanggil kapan pun — walau slot belum
     * penuh. Slot yang belum ada fotonya akan dibiarkan kosong/transparan (biasanya
     * tertutup oleh area frame PNG di atasnya). Dipakai untuk preview live di layar
     * sambil sesi foto masih berjalan.
     */
    fun buildPreviewImage(frameBitmap: Bitmap): Bitmap {
        return buildComposite(frameBitmap)
    }

    /**
     * Layer compositing inti: gambar foto-foto yang SUDAH ADA (partial atau lengkap)
     * di posisi slotnya masing-masing, lalu tumpuk frame PNG di atasnya.
     */
    private fun buildComposite(frameBitmap: Bitmap): Bitmap {
        val resultWidth = template.frameWidthPx
        val resultHeight = template.frameHeightPx

        val resultBitmap = Bitmap.createBitmap(resultWidth, resultHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(resultBitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        // Layer 1: gambar tiap foto yang sudah ter-capture di posisi slotnya (bawah)
        for (slot in template.slots) {
            val photo = capturedPhotos[slot.order] ?: continue

            val destRect = Rect(
                (slot.xRatio * resultWidth).toInt(),
                (slot.yRatio * resultHeight).toInt(),
                ((slot.xRatio + slot.widthRatio) * resultWidth).toInt(),
                ((slot.yRatio + slot.heightRatio) * resultHeight).toInt()
            )

            if (slot.rotationDegrees != 0f) {
                canvas.save()
                canvas.rotate(
                    slot.rotationDegrees,
                    destRect.centerX().toFloat(),
                    destRect.centerY().toFloat()
                )
                drawPhotoCoverFit(canvas, photo, destRect, paint)
                canvas.restore()
            } else {
                drawPhotoCoverFit(canvas, photo, destRect, paint)
            }
        }

        // Layer 2: gambar frame PNG di atasnya (supaya "lubang" transparan menutupi sambungan foto)
        val scaledFrame = if (frameBitmap.width != resultWidth || frameBitmap.height != resultHeight) {
            Bitmap.createScaledBitmap(frameBitmap, resultWidth, resultHeight, true)
        } else {
            frameBitmap
        }
        canvas.drawBitmap(scaledFrame, 0f, 0f, paint)
        if (scaledFrame != frameBitmap) scaledFrame.recycle()

        return resultBitmap
    }

    /**
     * Gambar foto ke dalam [destRect] dengan mode "fit ke arah terjauh" (cover):
     * foto di-skala pakai faktor skala TERBESAR di antara skala lebar dan skala tinggi
     * (arah yang butuh pembesaran paling jauh), supaya slot selalu terisi PENUH tanpa
     * ada bagian kosong — tanpa distorsi karena skala X dan Y selalu sama.
     * Kelebihan di sisi yang satunya lagi (bisa lebar, bisa tinggi, tergantung rasio foto
     * vs rasio slot) di-crop secara merata dari tengah — mirip CSS `object-fit: cover`.
     */
    private fun drawPhotoCoverFit(canvas: Canvas, photo: Bitmap, destRect: Rect, paint: Paint) {
        val destWidth = destRect.width().toFloat()
        val destHeight = destRect.height().toFloat()
        if (destWidth <= 0f || destHeight <= 0f || photo.width <= 0 || photo.height <= 0) return

        val scaleX = destWidth / photo.width.toFloat()
        val scaleY = destHeight / photo.height.toFloat()
        val scale = maxOf(scaleX, scaleY) // arah terjauh: skala terbesar supaya slot selalu penuh

        val scaledWidth = photo.width * scale
        val scaledHeight = photo.height * scale

        val drawLeft = destRect.left + (destWidth - scaledWidth) / 2f
        val drawTop = destRect.top + (destHeight - scaledHeight) / 2f
        val drawRect = RectF(drawLeft, drawTop, drawLeft + scaledWidth, drawTop + scaledHeight)

        canvas.save()
        canvas.clipRect(destRect)
        canvas.drawBitmap(photo, null, drawRect, paint)
        canvas.restore()
    }
}