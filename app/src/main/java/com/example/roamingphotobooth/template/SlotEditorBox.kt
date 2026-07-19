package com.example.roamingphotobooth.template

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import android.util.Log

private const val TAG = "TemplateEditor"

@Composable
fun SlotEditorBox(
    slot: PhotoSlot,
    containerWidthPx: Float,
    containerHeightPx: Float,
    onSlotChanged: (PhotoSlot) -> Unit,
    onDeleteClick: () -> Unit
) {
    val density = LocalDensity.current
    val currentSlot = rememberUpdatedState(slot) // <- selalu pegang slot terbaru

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
            .border(2.dp, Color.Yellow)
            .background(Color.Yellow.copy(alpha = 0.2f))
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
        Text(
            text = "${slot.order}",
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopStart)
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(4.dp)
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(24.dp)
                .background(Color.Yellow)
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

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(24.dp)
                .background(Color.Red)
                .pointerInput(slot.id) {
                    detectTapGestures {
                        Log.d(TAG, "Delete slot=${slot.id}")
                        onDeleteClick()
                    }
                }
        ) {
            Text("X", color = Color.White, modifier = Modifier.align(Alignment.Center))
        }
    }
}