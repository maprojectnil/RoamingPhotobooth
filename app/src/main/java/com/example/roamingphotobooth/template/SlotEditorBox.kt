package com.example.roamingphotobooth.template

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import android.util.Log

private const val TAG = "TemplateEditor"

/**
 * Kotak editor untuk 1 slot foto di atas preview bingkai: bisa digeser (drag di badan
 * kotak), diresize (drag di pegangan kanan-bawah), diduplikasi (ikon salin — dipakai
 * saat 1 foto yang sama mau dipasang di beberapa posisi bingkai), dan dihapus (ikon X).
 *
 * [isShared] = true kalau ada slot LAIN dengan `order` yang sama (hasil duplikat) —
 * dikasih warna beda supaya user ngeh slot-slot itu bakal keisi 1 foto yang sama.
 */
@Composable
fun SlotEditorBox(
    slot: PhotoSlot,
    containerWidthPx: Float,
    containerHeightPx: Float,
    isShared: Boolean,
    onSlotChanged: (PhotoSlot) -> Unit,
    onDuplicateClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val density = LocalDensity.current
    val currentSlot = rememberUpdatedState(slot) // <- selalu pegang slot terbaru

    val accentColor = if (isShared) Color(0xFFFF7A59) else Color(0xFF4DD0E1)

    val xPx = slot.xRatio * containerWidthPx
    val yPx = slot.yRatio * containerHeightPx
    val widthPx = slot.widthRatio * containerWidthPx
    val heightPx = slot.heightRatio * containerHeightPx

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
            .clip(RoundedCornerShape(10.dp))
            .background(accentColor.copy(alpha = 0.22f))
            .border(2.dp, accentColor, RoundedCornerShape(10.dp))
            .pointerInput(slot.id) {
                var currentXRatio = slot.xRatio
                var currentYRatio = slot.yRatio

                detectDragGestures(
                    onDragStart = { Log.d(TAG, "Drag START slot=${slot.id}") },
                    onDragEnd = { Log.d(TAG, "Drag END slot=${slot.id}") }
                ) { change, dragAmount ->
                    change.consume()
                    val dxRatio = dragAmount.x / containerWidthPx
                    val dyRatio = dragAmount.y / containerHeightPx
                    val latest = currentSlot.value
                    currentXRatio = (currentXRatio + dxRatio).coerceIn(0f, 1f - latest.widthRatio)
                    currentYRatio = (currentYRatio + dyRatio).coerceIn(0f, 1f - latest.heightRatio)
                    Log.d(TAG, "Drag MOVE slot=${slot.id} newX=$currentXRatio newY=$currentYRatio")
                    // .copy() dari objek TERBARU, hanya timpa x & y
                    onSlotChanged(latest.copy(xRatio = currentXRatio, yRatio = currentYRatio))
                }
            }
    ) {
        // Badge urutan slot (di pojok kiri-atas)
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(4.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(accentColor)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = "${slot.order}",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium
            )
        }

        // Ikon pindah kecil di tengah, sekadar penanda visual area bisa di-drag
        Icon(
            imageVector = Icons.Filled.OpenWith,
            contentDescription = null,
            tint = accentColor.copy(alpha = 0.55f),
            modifier = Modifier.align(Alignment.Center).size(18.dp)
        )

        // Tombol duplikat (pojok kiri-bawah) — bikin slot baru dengan foto yang sama
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(4.dp)
                .size(26.dp)
                .clip(CircleShape)
                .background(Color(0xFF2E7D32))
                .pointerInput(slot.id) {
                    detectTapGestures {
                        Log.d(TAG, "Duplicate slot=${slot.id}")
                        onDuplicateClick()
                    }
                }
        ) {
            Icon(
                imageVector = Icons.Filled.ContentCopy,
                contentDescription = "Duplikat slot",
                tint = Color.White,
                modifier = Modifier.align(Alignment.Center).size(14.dp)
            )
        }

        // Pegangan resize (pojok kanan-bawah)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(22.dp)
                .clip(RoundedCornerShape(topStart = 8.dp))
                .background(accentColor)
                .pointerInput(slot.id) {
                    var currentWidthRatio = slot.widthRatio
                    var currentHeightRatio = slot.heightRatio

                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val dwRatio = dragAmount.x / containerWidthPx
                        val dhRatio = dragAmount.y / containerHeightPx
                        val latest = currentSlot.value
                        currentWidthRatio = (currentWidthRatio + dwRatio).coerceIn(0.05f, 1f - latest.xRatio)
                        currentHeightRatio = (currentHeightRatio + dhRatio).coerceIn(0.05f, 1f - latest.yRatio)
                        Log.d(TAG, "Resize slot=${slot.id} newW=$currentWidthRatio newH=$currentHeightRatio")
                        // .copy() dari objek TERBARU, hanya timpa width & height
                        onSlotChanged(latest.copy(widthRatio = currentWidthRatio, heightRatio = currentHeightRatio))
                    }
                }
        )

        // Tombol hapus (pojok kanan-atas)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(Color(0xFFD32F2F))
                .pointerInput(slot.id) {
                    detectTapGestures {
                        Log.d(TAG, "Delete slot=${slot.id}")
                        onDeleteClick()
                    }
                }
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Hapus slot",
                tint = Color.White,
                modifier = Modifier.align(Alignment.Center).size(14.dp)
            )
        }
    }
}
