package com.example.roamingphotobooth.template

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
 */
@Composable
fun TemplateListScreen(
    templates: List<PhotoTemplate>,
    frameFileManager: FrameFileManager,
    onTemplateSelected: (PhotoTemplate) -> Unit
) {
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
                text = "${templates.size} template tersedia",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        if (templates.isEmpty()) {
            Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                EmptyState()
            }
        } else {
            TemplateCarousel(
                templates = templates,
                frameFileManager = frameFileManager,
                onTemplateSelected = onTemplateSelected
            )
        }
    }
}

/**
 * Carousel horizontal dengan depth animation. Cara kerjanya:
 * 1. BoxWithConstraints buat tahu lebar viewport (dipakai hitung jarak tiap
 *    kartu ke titik tengah layar dalam satuan px).
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
    onTemplateSelected: (PhotoTemplate) -> Unit
) {
    val listState = rememberLazyListState()
    val flingBehavior = rememberSnapFlingBehavior(
        lazyListState = listState,
        snapPosition = SnapPosition.Center
    )

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val viewportWidthPx = with(LocalDensity.current) {
            maxWidth.toPx()
        }
        val cardWidth = 168.dp
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
            // Kotak bingkai foto rasio 4R (4x6 = lebar:tinggi 2:3), frame di-fit
            // (bukan crop) supaya seluruh bingkai kelihatan utuh tanpa terpotong.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF32363F), Color(0xFF2B2E36))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (thumbnail != null) {
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
private fun EmptyState() {
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
                text = "Template akan muncul di sini setelah dibuat.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF9AA0AC)
            )
        }
    }
}