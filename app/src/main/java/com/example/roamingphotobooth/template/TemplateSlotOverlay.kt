package com.example.roamingphotobooth.template

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Overlay READ-ONLY yang menggambar tiap slot foto sebagai kotak berwarna dengan
 * nomor urutnya di tengah — dipakai di thumbnail Template List, supaya user bisa
 * langsung lihat berapa banyak FOTO BERBEDA yang bakal diambil (jumlah nomor unik)
 * dan slot mana saja yang bakal diisi 1 foto yang sama (nomor & warna sama = slot
 * duplikat), tanpa harus buka editor dulu.
 *
 * BEDA dari [SlotEditorBox]: di sini tidak ada drag/resize/duplicate/delete sama
 * sekali — cuma visual. Warna nomor dijamin SAMA persis dengan yang dipakai di
 * preview live booth (Stand & Mobile) karena keduanya ambil dari [SlotColorPalette]
 * yang sama.
 *
 * [containerWidthPx]/[containerHeightPx] harus ukuran AKTUAL dari Box pembungkus
 * (biasanya diukur lewat `onSizeChanged` / `onGloballyPositioned` di composable
 * pemanggil), supaya posisi rasio slot pas menimpa gambar bingkai di baliknya.
 */
@Composable
fun TemplateSlotOverlay(
    slots: List<PhotoSlot>,
    containerWidthPx: Float,
    containerHeightPx: Float,
    modifier: Modifier = Modifier
) {
    if (slots.isEmpty() || containerWidthPx <= 0f || containerHeightPx <= 0f) return

    val density = LocalDensity.current
    val rankMap = remember(slots) { SlotColorPalette.buildRankMap(slots.map { it.order }) }

    Box(modifier = modifier) {
        slots.forEach { slot ->
            val accentColor = remember(slot.order, rankMap) {
                Color(SlotColorPalette.colorForOrder(slot.order, rankMap))
            }

            val xPx = slot.xRatio * containerWidthPx
            val yPx = slot.yRatio * containerHeightPx
            val widthPx = slot.widthRatio * containerWidthPx
            val heightPx = slot.heightRatio * containerHeightPx
            val shortSidePx = minOf(widthPx, heightPx)

            Box(
                modifier = Modifier
                    .offset(
                        x = with(density) { xPx.toDp() },
                        y = with(density) { yPx.toDp() }
                    )
                    .size(
                        width = with(density) { widthPx.toDp() },
                        height = with(density) { heightPx.toDp() }
                    )
                    .rotate(slot.rotationDegrees)
                    .clip(RoundedCornerShape(with(density) { (shortSidePx * 0.08f).toDp() }))
                    .background(accentColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${slot.order}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleLarge.copy(
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.35f),
                            blurRadius = 6f
                        )
                    )
                )
            }
        }
    }
}
