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

    /**
     * Hasil addPhoto() — dipecah jadi 3 kondisi supaya caller bisa kasih pesan yang
     * BENAR ke user. Sebelumnya cuma Boolean, jadi "semua slot penuh" (normal) dan
     * "foto korup gagal di-decode" (biasanya gara-gara glitch transfer USB) sama-sama
     * ditampilkan sebagai "Semua slot sudah terisi" — user tidak pernah tahu foto
     * itu sebenarnya dibuang diam-diam dan slot-nya masih kosong, jadi tidak retake.
     */
    enum class AddPhotoResult { ADDED, ALL_SLOTS_FULL, DECODE_FAILED }

    private val capturedPhotos = mutableMapOf<Int, Bitmap>() // key: slot order (1, 2, 3, ...)

    val totalSlots: Int get() = template.slots.map { it.order }.distinct().size
    val filledSlots: Int get() = capturedPhotos.size
    val isComplete: Boolean get() = filledSlots >= totalSlots

    // <-- BARU: dipakai fitur Retake mode MOBILE (lihat MobileBoothScreen +
    // MainActivity.mobileRetakeLastPhoto). Mobile tidak punya layar review
    // seperti Stand (foto langsung ke-commit ke slot begitu jepret) — jadi
    // "retake" di sini berarti "buang foto slot TERAKHIR yang sudah masuk,
    // biar user bisa jepret ulang buat slot itu". True kalau ada minimal 1
    // slot yang bisa dibatalkan.
    val canRetakeLastPhoto: Boolean get() = capturedPhotos.isNotEmpty()

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
     *
     * [mirror] mengikuti setting "Mirror Camera" (default dari Settings > Session,
     * bisa di-override per-sesi lewat toggle Mirror di Session Preview — lihat
     * MainActivity.sessionMirrorEnabled). Default `true` di sini supaya caller lama
     * yang belum diupdate tetap dapat behavior asli (mirror selalu aktif).
     */
    fun addPhoto(photoBytes: ByteArray, mirror: Boolean = true): AddPhotoResult {
        val slotOrder = nextSlotOrder() ?: run {
            Log.w(TAG, "addPhoto dipanggil tapi semua slot sudah penuh")
            return AddPhotoResult.ALL_SLOTS_FULL
        }

        val decoded = BitmapFactory.decodeByteArray(photoBytes, 0, photoBytes.size)
        if (decoded == null) {
            // Biasanya gara-gara data foto korup akibat glitch transfer USB (lihat
            // EsCameraSession.encodeCaptureToJpeg) -- slot ini TETAP kosong (tidak
            // ke-assign ke capturedPhotos), jadi nextSlotOrder() bakal balik ke slot
            // yang sama lagi di jepretan berikutnya. Caller WAJIB kasih tahu user
            // buat retake, bukan nampilin pesan "semua slot sudah terisi".
            Log.e(TAG, "Gagal decode foto jadi Bitmap (slot $slotOrder tetap kosong)")
            return AddPhotoResult.DECODE_FAILED
        }

        // Mirror horizontal (efek cermin) di sini, di titik paling awal — supaya
        // preview kiri, layar review, dan hasil akhir yang disimpan/di-upload semua
        // otomatis konsisten pakai foto yang sudah ke-flip ini. Cuma dilakukan kalau
        // [mirror] true (setting Mirror Camera aktif untuk sesi ini) — kalau false,
        // foto dipakai apa adanya tanpa di-flip.
        val bitmap = if (mirror) {
            val mirrored = BitmapMerger.mirrorHorizontal(decoded)
            decoded.recycle()
            mirrored
        } else {
            decoded
        }

        capturedPhotos[slotOrder] = bitmap
        Log.i(TAG, "Foto masuk ke slot $slotOrder. Progress: $filledSlots/$totalSlots")
        return AddPhotoResult.ADDED
    }

    /**
     * <-- BARU: snapshot foto MENTAH (belum di-merge/di-frame) per slot, urut
     * berdasar nomor slot. Dipakai MainActivity untuk ikut upload semua foto
     * mentah ini ke folder sesi di Drive (saat setting "Folder Terpisah per Sesi"
     * ON) -- supaya folder sesi isinya bukan cuma 1 foto hasil merge, tapi juga
     * foto asli tiap slot yang bisa dipilih ulang/dicetak terpisah oleh tamu.
     * Bitmap yang dikembalikan MASIH dipegang oleh [capturedPhotos] (tidak
     * di-recycle di sini) -- caller cuma boleh BACA (encode ke JPEG dsb), jangan
     * di-recycle sendiri, karena masih dipakai composite berikutnya sampai
     * [reset] dipanggil.
     */
    fun capturedPhotosSnapshot(): List<Pair<Int, Bitmap>> =
        capturedPhotos.toSortedMap().map { (order, bitmap) -> order to bitmap }

    fun reset() {
        capturedPhotos.values.forEach { it.recycle() }
        capturedPhotos.clear()
    }

    /**
     * <-- BARU: buang foto di slot dengan `order` PALING BESAR yang sudah terisi
     * (== slot yang paling terakhir di-capture, karena alur pengisian selalu
     * berurutan lewat [nextSlotOrder]). Dipakai untuk fitur Retake mode MOBILE:
     * begitu dipanggil, [nextSlotOrder] otomatis balik nunjuk ke slot itu lagi,
     * jadi jepretan fisik berikutnya masuk ke slot yang sama (retake), bukan
     * lanjut ke slot baru.
     *
     * Return `true` kalau ada foto yang dibuang, `false` kalau belum ada foto
     * sama sekali (tidak ada yang bisa di-retake).
     */
    fun removeLastPhoto(): Boolean {
        val lastOrder = capturedPhotos.keys.maxOrNull() ?: return false
        capturedPhotos.remove(lastOrder)?.recycle()
        Log.i(TAG, "Foto slot $lastOrder dibuang (retake). Progress: $filledSlots/$totalSlots")
        return true
    }

    /**
     * <-- BARU: buang foto pada slot SPESIFIK [order] -- beda dari [removeLastPhoto]
     * yang cuma bisa buang slot TERAKHIR. Dipakai fitur "Retake slot sebelumnya":
     * user pilih salah satu foto yang SUDAH ke-capture (lewat dialog pilih slot di
     * layar capture, lihat StandBoothScreen.RetakeSlotDialog) buat diulang, tidak
     * harus yang paling baru.
     *
     * Begitu dipanggil, [nextSlotOrder] otomatis balik nunjuk ke slot kosong dengan
     * NOMOR TERKECIL -- yang biasanya (tapi tidak selalu) berarti slot [order] itu
     * sendiri. CATATAN: kalau kebetulan ada slot lain dengan nomor LEBIH KECIL dari
     * [order] yang juga masih kosong (mis. sesi belum selesai & user retake slot 3
     * padahal slot 1 belum sempat kefoto), jepretan berikutnya akan diarahkan ke
     * slot 1 dulu, BUKAN ke slot 3 yang baru di-retake -- ini konsisten dengan alur
     * pengisian normal (selalu urut dari nomor terkecil), bukan bug.
     *
     * Return `true` kalau memang ada foto yang dibuang di slot itu, `false` kalau
     * slot [order] belum ada fotonya sama sekali (tidak ada yang bisa di-retake).
     */
    fun removePhotoAt(order: Int): Boolean {
        val removed = capturedPhotos.remove(order) ?: return false
        removed.recycle()
        Log.i(TAG, "Foto slot $order dibuang (retake per-slot). Progress: $filledSlots/$totalSlots")
        return true
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
     *
     * [liveViewBitmap]: <-- BARU. Kalau diisi (non-null), frame live view kamera
     * SAAT INI digambar ke dalam slot KOSONG BERIKUTNYA (== [nextSlotOrder]) --
     * di-crop & diskalakan PERSIS seperti foto hasil capture nanti (cover-fit,
     * lihat [drawPhotoCoverFit]/[drawLiveViewCoverFit]), jadi operator/tamu bisa
     * lihat framing yang akan ke-capture SEBELUM shutter ditekan, bukan cuma kotak
     * placeholder nomor. Slot lain yang masih kosong tetap pakai placeholder biasa
     * (kalau [showEmptySlotPlaceholders] true) -- cuma slot TUJUAN berikutnya yang
     * diganti live view. Kalau semua slot sudah penuh ([nextSlotOrder] null) atau
     * [liveViewBitmap] null, behavior sama seperti sebelumnya (tidak ada perubahan).
     *
     * [mirrorLiveView]: ikutin setting Mirror Camera sesi berjalan (lihat
     * MainActivity.sessionMirrorEnabled) supaya arah live view yang ditampilkan di
     * slot ini KONSISTEN dengan live view besar di kanan (yang di-mirror lewat
     * Modifier.scale di StandBoothScreen) dan dengan foto hasil capture beneran
     * (yang di-mirror di [addPhoto]) -- tanpa ini, preview slot akan kelihatan
     * "kebalik" dibanding apa yang user lihat & apa yang akan tersimpan.
     */
    fun buildPreviewImage(
        frameBitmap: Bitmap,
        showEmptySlotPlaceholders: Boolean = false,
        liveViewBitmap: Bitmap? = null,
        mirrorLiveView: Boolean = false
    ): Bitmap {
        return buildComposite(frameBitmap, showEmptySlotPlaceholders, liveViewBitmap, mirrorLiveView)
    }

    /**
     * Layer compositing inti: gambar foto-foto yang SUDAH ADA (partial atau lengkap)
     * di posisi slotnya masing-masing -- ATAU live view kamera di slot tujuan
     * berikutnya kalau [liveViewBitmap] diisi (lihat [buildPreviewImage]) -- lalu
     * tumpuk frame PNG di atasnya.
     */
    private fun buildComposite(
        frameBitmap: Bitmap,
        showEmptySlotPlaceholders: Boolean,
        liveViewBitmap: Bitmap? = null,
        mirrorLiveView: Boolean = false
    ): Bitmap {
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

        // Slot KOSONG berikutnya yang bakal diisi jepretan selanjutnya -- cuma dipakai
        // kalau ada live view untuk ditampilkan di situ (lihat param [liveViewBitmap]
        // di [buildPreviewImage]). null kalau tidak ada live view ATAU semua slot
        // sudah penuh (behavior lama, tidak ada slot yang diganti live view).
        val liveTargetSlotOrder = if (liveViewBitmap != null) nextSlotOrder() else null

        // Layer 1: gambar tiap foto yang sudah ter-capture di posisi slotnya (bawah).
        // Kalau [showEmptySlotPlaceholders] true (mode STAND), slot yang BELUM ada
        // fotonya digambar sebagai kotak placeholder berwarna dengan nomor urutnya
        // di tengah. Kalau false (mode MOBILE, behavior lama), slot kosong dilewati
        // sama sekali alias tetap transparan. PENGECUALIAN: slot TUJUAN berikutnya
        // ([liveTargetSlotOrder]) SELALU digambar kalau live view tersedia, terlepas
        // dari [showEmptySlotPlaceholders] -- live view di slot tujuan itu sendiri
        // yang jadi indikator "slot ini yang akan diisi", jadi tetap relevan di
        // kedua mode.
        for (slot in template.slots) {
            val photo = capturedPhotos[slot.order]
            val isLiveTargetSlot = photo == null && slot.order == liveTargetSlotOrder
            if (photo == null && !isLiveTargetSlot && !showEmptySlotPlaceholders) continue

            val destRect = Rect(
                (slot.xRatio * resultWidth).toInt(),
                (slot.yRatio * resultHeight).toInt(),
                ((slot.xRatio + slot.widthRatio) * resultWidth).toInt(),
                ((slot.yRatio + slot.heightRatio) * resultHeight).toInt()
            )

            fun drawSlotContent() {
                when {
                    photo != null -> drawPhotoCoverFit(canvas, photo, destRect, paint)
                    isLiveTargetSlot -> drawLiveViewCoverFit(
                        canvas, liveViewBitmap!!, destRect, paint, mirrorLiveView
                    )
                    else -> drawEmptySlotPlaceholder(canvas, slot, destRect, rankMap)
                }
            }

            if (slot.rotationDegrees != 0f) {
                canvas.save()
                canvas.rotate(
                    slot.rotationDegrees,
                    destRect.centerX().toFloat(),
                    destRect.centerY().toFloat()
                )
                drawSlotContent()
                canvas.restore()
            } else {
                drawSlotContent()
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

    /**
     * <-- BARU. Sama persis seperti [drawPhotoCoverFit] (cover-fit: skala terbesar
     * dari skala lebar/tinggi, crop merata dari tengah di sisi yang kelebihan) --
     * dipakai KHUSUS untuk gambar frame live view kamera ke slot tujuan berikutnya
     * (lihat [buildComposite]/[buildPreviewImage]), supaya crop yang kelihatan di
     * preview PERSIS sama dengan crop yang bakal dipakai di foto hasil akhir nanti.
     * Bedanya cuma dukungan [mirror]: kalau true, gambar di-flip horizontal
     * DI SEKITAR TITIK TENGAH [destRect] (bukan titik tengah bitmap live view),
     * supaya crop cover-fit-nya tetap benar sebelum di-flip.
     */
    private fun drawLiveViewCoverFit(
        canvas: Canvas,
        liveFrame: Bitmap,
        destRect: Rect,
        paint: Paint,
        mirror: Boolean
    ) {
        val destWidth = destRect.width().toFloat()
        val destHeight = destRect.height().toFloat()
        if (destWidth <= 0f || destHeight <= 0f || liveFrame.width <= 0 || liveFrame.height <= 0) return

        val scaleX = destWidth / liveFrame.width.toFloat()
        val scaleY = destHeight / liveFrame.height.toFloat()
        val scale = maxOf(scaleX, scaleY)

        val scaledWidth = liveFrame.width * scale
        val scaledHeight = liveFrame.height * scale

        val drawLeft = destRect.left + (destWidth - scaledWidth) / 2f
        val drawTop = destRect.top + (destHeight - scaledHeight) / 2f
        val drawRect = RectF(drawLeft, drawTop, drawLeft + scaledWidth, drawTop + scaledHeight)

        canvas.save()
        canvas.clipRect(destRect)
        if (mirror) {
            canvas.scale(-1f, 1f, destRect.centerX().toFloat(), destRect.centerY().toFloat())
        }
        canvas.drawBitmap(liveFrame, null, drawRect, paint)
        canvas.restore()
    }
}