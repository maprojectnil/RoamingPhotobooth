package com.example.roamingphotobooth.template

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Image as ImageIcon
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.zIndex
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Layar daftar template — HANYA untuk menelusuri & memilih template yang sudah ada
 * (tidak ada aksi hapus atau buat-baru di layar ini). Tidak ada logika edit
 * slot/bingkai di sini sama sekali; begitu template dipilih, kontrol berpindah ke
 * TemplateEditorScreen lewat callback yang disediakan activity pemanggil.
 *
 * Palet warna & bentuk kartu sengaja disamakan dengan TemplateEditorScreen supaya
 * transisi antar layar terasa satu keluarga visual (dark surface #262A33 di atas
 * gradient #16181D → #1F2229, aksen cyan #4DD0E1, rounded corner besar).
 *
 * Daftar template ditampilkan sebagai carousel kartu HORIZONTAL bergaya
 * "infinite depth carousel": kartu yang paling dekat ke tengah viewport tampil
 * paling besar & paling depan (zIndex tinggi), kartu di kiri/kanannya mengecil
 * (scale) dan makin transparan makin jauh dari tengah — efeknya dihitung ulang
 * tiap frame scroll (bukan cuma saat snap), jadi terasa depth-nya pas digeser.
 *
 * Karena bingkai bisa berupa 2 orientasi (landscape & potrait), di atas carousel
 * ada filter pill "Semua / Landscape / Potrait" supaya user bisa fokus ke satu
 * jenis orientasi tanpa harus scroll bolak-balik lewat semua template.
 */

/** Filter orientasi yang bisa dipilih user di atas carousel. */
private enum class OrientationFilter(val label: String) {
    ALL("Semua"),
    LANDSCAPE("Landscape"),
    PORTRAIT("Potrait")
}

/**
 * Orientasi bingkai ditentukan dari metadata ukuran asli PNG bingkai
 * (frameWidthPx vs frameHeightPx) yang sudah tersimpan di PhotoTemplate —
 * TIDAK perlu load bitmap dulu, jadi murah dipanggil untuk semua item saat
 * filter dievaluasi. Bingkai persegi (width == height) dianggap Potrait.
 */
private val PhotoTemplate.isLandscapeFrame: Boolean
    get() = frameWidthPx > frameHeightPx

/**
 * Rata-rata aspect ratio (width/height) dari satu set template, dipakai
 * TemplateCarousel buat memutuskan seberapa besar jatah lebar kartu.
 * Dihitung dari metadata frameWidthPx/frameHeightPx (murah, tidak perlu
 * load bitmap) — cukup akurat untuk keputusan ukuran kartu secara umum,
 * meskipun render tiap kartu tetap pakai aspect ratio dari bitmap asli
 * masing-masing (lihat CarouselCard).
 */
private fun averageAspectRatio(templates: List<PhotoTemplate>): Float {
    if (templates.isEmpty()) return 2f / 3f
    val sum = templates.sumOf { it.frameWidthPx.toDouble() / it.frameHeightPx.toDouble() }
    return (sum / templates.size).toFloat()
}

@Composable
fun TemplateListScreen(
    templates: List<PhotoTemplate>,
    frameFileManager: FrameFileManager,
    onTemplateSelected: (PhotoTemplate) -> Unit
) {
    var selectedFilter by remember { mutableStateOf(OrientationFilter.ALL) }

    val filteredTemplates = remember(templates, selectedFilter) {
        when (selectedFilter) {
            OrientationFilter.ALL -> templates
            OrientationFilter.LANDSCAPE -> templates.filter { it.isLandscapeFrame }
            OrientationFilter.PORTRAIT -> templates.filter { !it.isLandscapeFrame }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF16181D), Color(0xFF1F2229))
                )
            )
            .padding(vertical = 20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 20.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.CollectionsBookmark,
                contentDescription = null,
                tint = Color(0xFF4DD0E1)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${filteredTemplates.size} template tersedia",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }

        if (templates.isNotEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            OrientationFilterRow(
                selected = selectedFilter,
                onSelect = { selectedFilter = it },
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        if (filteredTemplates.isEmpty()) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    hasAnyTemplate = templates.isNotEmpty(),
                    filter = selectedFilter
                )
            }
        } else {
            TemplateCarousel(
                templates = filteredTemplates,
                frameFileManager = frameFileManager,
                onTemplateSelected = onTemplateSelected,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Baris pill filter orientasi. Sengaja dibuat custom (bukan pakai FilterChip
 * bawaan Material3) supaya warnanya bisa persis mengikuti palet gelap +
 * aksen cyan yang dipakai di seluruh layar ini, bukan warna default tema.
 */
@Composable
private fun OrientationFilterRow(
    selected: OrientationFilter,
    onSelect: (OrientationFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OrientationFilter.values().forEach { filter ->
            val isSelected = filter == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (isSelected) Color(0xFF4DD0E1) else Color(0xFF262A33))
                    .clickable { onSelect(filter) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = filter.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) Color(0xFF16181D) else Color(0xFFAEB4C0)
                )
            }
        }
    }
}

/**
 * Carousel horizontal dengan depth animation. Cara kerjanya:
 * 1. BoxWithConstraints buat tahu lebar viewport (dipakai hitung jarak tiap
 *    kartu ke titik tengah layar dalam satuan px, DAN buat hitung lebar kartu
 *    yang proporsional ke lebar layar — lihat `cardWidth` di bawah).
 * 2. LazyRow diberi contentPadding kiri-kanan sebesar setengah lebar viewport
 *    dikurangi setengah lebar kartu, supaya kartu PERTAMA dan TERAKHIR pun bisa
 *    berhenti tepat di tengah (bukan mepet ke pinggir layar).
 * 3. rememberSnapFlingBehavior bikin scroll selalu "snap" ke satu kartu penuh
 *    di tengah setelah jari dilepas — kayak carousel pada umumnya.
 * 4. Tiap item baca posisi globalnya sendiri lewat onGloballyPositioned, hitung
 *    jarak pusatnya ke pusat viewport, lalu convert jarak itu jadi scale,
 *    alpha, translationY dikit (biar ada kesan "depth" 3D), dan zIndex lewat
 *    graphicsLayer — dihitung ulang tiap kali posisi berubah (tiap frame
 *    scroll), jadi transisinya menyatu sama gesture geser, bukan animasi
 *    terpisah yang baru jalan setelah snap selesai.
 */
@Composable
private fun TemplateCarousel(
    templates: List<PhotoTemplate>,
    frameFileManager: FrameFileManager,
    onTemplateSelected: (PhotoTemplate) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val flingBehavior = rememberSnapFlingBehavior(
        lazyListState = listState,
        snapPosition = SnapPosition.Center
    )

    // fillMaxSize (bukan cuma fillMaxWidth seperti sebelumnya) supaya
    // BoxWithConstraints ini juga tahu TINGGI ruang yang benar-benar
    // tersedia (dari `modifier.weight(1f)` yang dikasih pemanggil) —
    // itu kunci biar kartu tidak pernah kepotong secara vertikal lagi.
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val viewportWidthPx = with(LocalDensity.current) {
            maxWidth.toPx()
        }

        // Lebar kartu dibuat PROPORSIONAL ke lebar layar (bukan angka tetap
        // 168.dp seperti sebelumnya). Karena bingkai landscape jauh lebih
        // pendek dari potrait untuk lebar yang sama (tinggi kartu = cardWidth
        // / aspectRatio), pakai lebar yang sama utk keduanya bikin kartu
        // landscape kelihatan kecil walau lebarnya sama besar. Jadi jatah
        // lebar dihitung beda tergantung orientasi rata-rata set yang lagi
        // ditampilkan (ditentukan dari metadata frameWidthPx/frameHeightPx,
        // bukan tebakan):
        // - Set landscape (rata-rata width > height): jatah lebar sebagian
        //   besar viewport (65%).
        // - Set potrait/campuran: tetap seperti sebelumnya (58% viewport,
        //   190dp-260dp).
        val avgAspectRatio = remember(templates) { averageAspectRatio(templates) }
        val isLandscapeSet = avgAspectRatio > 1.05f
        val widthBasedCardWidth = if (isLandscapeSet) {
            (maxWidth * 0.65f).coerceAtLeast(260.dp)
        } else {
            (maxWidth * 0.58f).coerceIn(190.dp, 260.dp)
        }

        // BATAS DARI TINGGI: dari `maxHeight` yang tersedia, sisihkan ruang
        // buat bagian non-gambar di dalam kartu (padding atas-bawah Column
        // 14dp*2, spacer 10dp+2dp, baris judul + baris jumlah slot) sebelum
        // sisanya dipakai buat gambar bingkai. Angka 100dp sengaja dilebihkan
        // dikit dari perkiraan itu (~76dp) sebagai buffer aman supaya teks
        // nama template & jumlah slot foto DIJAMIN tidak pernah ikut kepotong
        // walau ukuran font/densitas layar beda-beda antar device.
        //
        // Dipakai rasio PALING RENDAH (bingkai paling "tinggi" relatif ke
        // lebarnya) di antara seluruh template yang sedang tampil — bukan
        // rata-rata — supaya template manapun yang paling butuh tinggi tetap
        // dijamin muat, bukan cuma yang rata-rata.
        val nonImageChromeHeight = 100.dp
        val availableImageHeight = (maxHeight - nonImageChromeHeight).coerceAtLeast(80.dp)
        val minAspectRatio = remember(templates) {
            templates.minOfOrNull { it.frameWidthPx.toFloat() / it.frameHeightPx.toFloat() }
                ?: avgAspectRatio
        }
        val heightBasedCardWidth = availableImageHeight * minAspectRatio

        // Lebar kartu FINAL = yang lebih kecil di antara batas lebar & batas
        // tinggi — jadi kartu dijamin selalu muat penuh di kedua arah
        // sekaligus, tidak akan pernah kepotong di sisi manapun, apapun
        // orientasi/ukuran layarnya.
        val cardWidth = if (widthBasedCardWidth < heightBasedCardWidth) {
            widthBasedCardWidth
        } else {
            heightBasedCardWidth
        }.coerceAtLeast(160.dp)

        val sidePadding = max((maxWidth - cardWidth) / 2, 0.dp)

        LazyRow(
            state = listState,
            flingBehavior = flingBehavior,
            contentPadding = PaddingValues(horizontal = sidePadding),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            items(templates, key = { it.id }) { template ->
                CarouselCard(
                    template = template,
                    frameFileManager = frameFileManager,
                    cardWidth = cardWidth,
                    viewportWidthPx = viewportWidthPx,
                    onClick = { onTemplateSelected(template) }
                )
            }
        }
    }
}

@Composable
private fun CarouselCard(
    template: PhotoTemplate,
    frameFileManager: FrameFileManager,
    cardWidth: Dp,
    viewportWidthPx: Float,
    onClick: () -> Unit
) {
    val thumbnail = remember(template.framePngPath) {
        frameFileManager.loadBitmap(template.framePngPath)
    }

    // distanceFraction: 0f = pas di tengah viewport, 1f = sejauh setengah layar,
    // bisa lebih dari 1f kalau kartu ada di luar layar sepenuhnya.
    var distanceFraction by remember { mutableStateOf(0f) }

    Card(
        onClick = onClick,
        modifier = Modifier
            .width(cardWidth)
            .wrapContentHeight()
            .onGloballyPositioned { coords ->
                val cardCenterX = coords.boundsInParent().let { it.left + it.right } / 2f
                val viewportCenterX = viewportWidthPx / 2f
                val halfViewport = max(viewportCenterX, 1f)
                distanceFraction = min(abs(cardCenterX - viewportCenterX) / halfViewport, 1.5f)
            }
            .graphicsLayer {
                // Kartu di tengah: scale 1f, alpha 1f, tidak ada offset.
                // Makin jauh dari tengah: mengecil sampai minimum 0.72f, makin
                // transparan sampai minimum 0.35f, dan sedikit turun (translationY)
                // supaya terkesan "mundur ke belakang" (depth), bukan cuma pipih.
                val scale = 1f - (distanceFraction * 0.28f).coerceIn(0f, 0.28f)
                val alpha = 1f - (distanceFraction * 0.65f).coerceIn(0f, 0.65f)
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
                translationY = distanceFraction * 22f
            }
            .zIndex(1f - distanceFraction),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF262A33)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .width(cardWidth)
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Kotak pembungkus thumbnail SEKARANG ikutin rasio ASLI bitmap bingkai
            // (bukan dipaksa 2:3 lagi) — jadi ContentScale.Fit SELALU mengisi PENUH
            // kotak tanpa letterbox sama sekali, apapun bentuk bingkainya (potret,
            // landscape, dll). Konsekuensinya tinggi kartu di carousel jadi bisa
            // beda-beda antar template (bukan seragam persis), tapi ini trade-off
            // yang jauh lebih AMAN daripada replikasi manual rumus letterbox
            // ContentScale.Fit — pendekatan itu gampang meleset dikit (pembulatan,
            // dsb) dan bikin kotak nomor slot salah ukuran/posisi total, bahkan
            // bisa sampai tidak kelihatan sama sekali.
            //
            // Rasio dihitung dari dimensi BITMAP YANG BENERAN DI-LOAD
            // (thumbnail.width/height) — bukan dari metadata template.frameWidthPx/
            // frameHeightPx — supaya persis sama dengan yang Compose pakai buat
            // nge-render Image-nya (ContentScale apapun selalu pakai intrinsic size
            // bitmap, bukan metadata manapun).
            val frameAspectRatio = remember(thumbnail) {
                if (thumbnail != null && thumbnail.height > 0) {
                    thumbnail.width.toFloat() / thumbnail.height.toFloat()
                } else {
                    2f / 3f
                }
            }
            var thumbnailWidthPx by remember(template.id) { mutableStateOf(0f) }
            var thumbnailHeightPx by remember(template.id) { mutableStateOf(0f) }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(frameAspectRatio)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF32363F), Color(0xFF2B2E36))
                        )
                    )
                    .onSizeChanged { size ->
                        thumbnailWidthPx = size.width.toFloat()
                        thumbnailHeightPx = size.height.toFloat()
                    },
                contentAlignment = Alignment.Center
            ) {
                if (thumbnail != null) {
                    // Layer BAWAH: kotak nomor slot. Karena kotak pembungkus di atas
                    // udah pas rasio bingkainya, containerWidthPx/HeightPx di sini =
                    // ukuran gambar yang BENERAN tampil (tidak ada letterbox yang perlu
                    // dikompensasi lagi). Digambar SEBELUM (di belakang) bingkai supaya
                    // cuma kelihatan lewat "lubang" transparan slot di PNG bingkai,
                    // PERSIS seperti hasil akhir foto nanti (lihat
                    // TemplateSessionManager.buildComposite, urutan layer-nya sama:
                    // slot/foto dulu, baru bingkai di atasnya).
                    TemplateSlotOverlay(
                        slots = template.slots,
                        containerWidthPx = thumbnailWidthPx,
                        containerHeightPx = thumbnailHeightPx,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Layer ATAS: bingkai PNG asli — bagian yang opaque otomatis
                    // nutupin kotak nomor di baliknya, cuma bagian transparan
                    // (lubang slot) yang nampilin warna+nomornya.
                    Image(
                        bitmap = thumbnail.asImageBitmap(),
                        contentDescription = "Thumbnail ${template.name}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.ImageIcon,
                        contentDescription = null,
                        tint = Color(0xFF7E8592)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = template.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFE4E6EA),
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${template.slotCount} slot foto",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF9AA0AC)
            )
        }
    }
}

@Composable
private fun EmptyState(
    hasAnyTemplate: Boolean,
    filter: OrientationFilter
) {
    val message = when {
        !hasAnyTemplate -> "Template akan muncul di sini setelah dibuat."
        filter == OrientationFilter.LANDSCAPE -> "Belum ada template dengan orientasi Landscape."
        filter == OrientationFilter.PORTRAIT -> "Belum ada template dengan orientasi Potrait."
        else -> "Template akan muncul di sini setelah dibuat."
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF262A33)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.ImageIcon,
                contentDescription = null,
                tint = Color(0xFF4DD0E1)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Belum ada template",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF9AA0AC)
            )
        }
    }
}