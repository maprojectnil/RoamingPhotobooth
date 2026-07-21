package com.example.roamingphotobooth.template

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
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
        return buildComposite(frameBitmap, showEmptySlotPlaceholders = false)
    }

    /**
     * Sama seperti [buildFinalImage], tapi BOLEH dipanggil kapan pun — walau slot belum
     * penuh. Dipakai untuk preview live di layar sambil sesi foto masih berjalan.
     *
     * [showEmptySlotPlaceholders] mengontrol tampilan slot yang BELUM ada fotonya:
     * - `false` (default, dipakai mode MOBILE): slot kosong dibiarkan transparan
     *   seperti behavior lama — biasanya tertutup area frame PNG di atasnya.
     * - `true` (dipakai mode STAND): slot kosong digambar sebagai kotak berwarna
     *   bernomor (nomor = urutan foto, warna sama = slot duplikat/foto yang sama)
     *   supaya operator langsung tahu status tiap slot dari layar preview kiri.
     */
    fun buildPreviewImage(frameBitmap: Bitmap, showEmptySlotPlaceholders: Boolean = false): Bitmap {
        return buildComposite(frameBitmap, showEmptySlotPlaceholders)
    }

    /**
     * Layer compositing inti: gambar foto-foto yang SUDAH ADA (partial atau lengkap)
     * di posisi slotnya masing-masing, lalu tumpuk frame PNG di atasnya.
     */
    private fun buildComposite(frameBitmap: Bitmap, showEmptySlotPlaceholders: Boolean): Bitmap {
        val resultWidth = template.frameWidthPx
        val resultHeight = template.frameHeightPx

        val resultBitmap = Bitmap.createBitmap(resultWidth, resultHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(resultBitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        // Peta nomor urut -> rank warna, sekali per composite (bukan per-slot) supaya
        // slot dgn `order` sama (duplikat) selalu dapat warna yang identik. Cuma
        // dibutuhkan kalau placeholder-nya memang mau ditampilkan.
        val rankMap = if (showEmptySlotPlaceholders) {
            SlotColorPalette.buildRankMap(template.slots.map { it.order })
        } else {
            emptyMap()
        }

        // Layer 1: gambar tiap foto yang sudah ter-capture di posisi slotnya (bawah).
        // Kalau [showEmptySlotPlaceholders] true (mode STAND), slot yang BELUM ada
        // fotonya digambar sebagai kotak placeholder berwarna dengan nomor urutnya
        // di tengah. Kalau false (mode MOBILE, behavior lama), slot kosong dilewati
        // sama sekali alias tetap transparan.
        for (slot in template.slots) {
            val photo = capturedPhotos[slot.order]
            if (photo == null && !showEmptySlotPlaceholders) continue

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
                if (photo != null) {
                    drawPhotoCoverFit(canvas, photo, destRect, paint)
                } else {
                    drawEmptySlotPlaceholder(canvas, slot, destRect, rankMap)
                }
                canvas.restore()
            } else {
                if (photo != null) {
                    drawPhotoCoverFit(canvas, photo, destRect, paint)
                } else {
                    drawEmptySlotPlaceholder(canvas, slot, destRect, rankMap)
                }
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
     * Gambar 1 kotak placeholder untuk slot yang BELUM ada fotonya: kotak rounded
     * berwarna solid (warna dari [SlotColorPalette], konsisten per nomor urut lewat
     * [rankMap]) dengan garis tepi sedikit lebih gelap, dan nomor urut slot besar
     * di tengah. Ukuran teks & radius sudut menyesuaikan ukuran slot supaya tetap
     * proporsional baik untuk slot kecil maupun besar.
     */
    private fun drawEmptySlotPlaceholder(
        canvas: Canvas,
        slot: PhotoSlot,
        destRect: Rect,
        rankMap: Map<Int, Int>
    ) {
        val width = destRect.width().toFloat()
        val height = destRect.height().toFloat()
        if (width <= 0f || height <= 0f) return

        val baseColor = SlotColorPalette.colorForOrder(slot.order, rankMap)
        val shortSide = minOf(width, height)
        val cornerRadius = shortSide * 0.08f

        // Isi kotak
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = baseColor
        }
        val rectF = RectF(destRect)
        canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, fillPaint)

        // Garis tepi sedikit lebih gelap, biar kotaknya tidak flat & tetap kelihatan
        // rapi walau warnanya terang (mis. kuning).
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = shortSide * 0.02f
            color = SlotColorPalette.darken(baseColor)
        }
        canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, borderPaint)

        // Nomor urut, di tengah kotak, dengan bayangan tipis supaya kontras di warna apa pun.
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
            textSize = shortSide * 0.4f
            setShadowLayer(shortSide * 0.03f, 0f, shortSide * 0.015f, Color.argb(110, 0, 0, 0))
        }
        val textY = destRect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(slot.order.toString(), destRect.centerX().toFloat(), textY, textPaint)
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