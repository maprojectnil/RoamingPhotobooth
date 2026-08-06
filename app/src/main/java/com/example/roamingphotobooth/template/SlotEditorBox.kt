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
import kotlin.math.min

private const val TAG = "TemplateEditor"

/** Ukuran minimum slot (rasio thd bingkai) — dipakai buat clamp geser & resize. */
private const val MIN_SIZE_RATIO = 0.05f
private const val MIN_HALF_SIZE_RATIO = MIN_SIZE_RATIO / 2f

/**
 * Kotak editor untuk 1 slot foto di atas preview bingkai: bisa digeser (drag di badan
 * kotak), diresize (drag di pegangan kanan-bawah — pakai metode **Scale from Center**,
 * jadi titik tengah slot diam & ukuran membesar/mengecil merata ke segala arah),
 * diduplikasi (ikon salin — dipakai saat 1 foto yang sama mau dipasang di beberapa
 * posisi bingkai), dan dihapus (ikon X).
 *
 * **Smart Snap**: selama digeser/diresize, tepi & titik tengah slot otomatis "nyantol"
 * (snap) ke garis Center, Thirds, Quarters, atau Edges bingkai kalau jaraknya masih di
 * dalam threshold (lihat [SmartSnap]). Jenis snap mana yang aktif diatur lewat
 * [snapSettings] (toggle-nya ada di panel kontrol). Setiap kali ada garis yang kena
 * snap, [onSnapGuidesChanged] dipanggil supaya workspace bisa gambar garis panduannya;
 * begitu drag selesai, dipanggil lagi dengan [SnapGuides.NONE] buat hapus garisnya.
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
    snapSettings: SmartSnapSettings,
    onSlotChanged: (PhotoSlot) -> Unit,
    onDuplicateClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onSnapGuidesChanged: (SnapGuides) -> Unit = {}
) {
    val density = LocalDensity.current
    val currentSlot = rememberUpdatedState(slot) // <- selalu pegang slot terbaru
    val currentSnapSettings = rememberUpdatedState(snapSettings) // <- selalu pegang toggle snap terbaru
    val snapThresholdPx = with(density) { SmartSnap.THRESHOLD_DP.dp.toPx() }

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
                    onDragEnd = {
                        Log.d(TAG, "Drag END slot=${slot.id}")
                        onSnapGuidesChanged(SnapGuides.NONE)
                    },
                    onDragCancel = { onSnapGuidesChanged(SnapGuides.NONE) }
                ) { change, dragAmount ->
                    change.consume()
                    val dxRatio = dragAmount.x / containerWidthPx
                    val dyRatio = dragAmount.y / containerHeightPx
                    val latest = currentSlot.value

                    // Posisi mentah (sebelum snap) hasil drag, sudah di-clamp ke dalam bingkai.
                    val rawX = (currentXRatio + dxRatio).coerceIn(0f, 1f - latest.widthRatio)
                    val rawY = (currentYRatio + dyRatio).coerceIn(0f, 1f - latest.heightRatio)

                    // Smart Snap: coba tarik tepi kiri/tengah/kanan (X) & atas/tengah/bawah (Y)
                    // slot ke garis Center/Thirds/Quarters/Edges terdekat sesuai toggle aktif.
                    val targets = SmartSnap.buildTargets(currentSnapSettings.value)
                    val (snappedX, hitX) = SmartSnap.snapPosition(
                        rawX, latest.widthRatio, containerWidthPx, snapThresholdPx, targets
                    )
                    val (snappedY, hitY) = SmartSnap.snapPosition(
                        rawY, latest.heightRatio, containerHeightPx, snapThresholdPx, targets
                    )

                    currentXRatio = snappedX.coerceIn(0f, 1f - latest.widthRatio)
                    currentYRatio = snappedY.coerceIn(0f, 1f - latest.heightRatio)

                    onSnapGuidesChanged(
                        SnapGuides(
                            vertical = hitX?.let { mapOf(it.ratio to it.type) } ?: emptyMap(),
                            horizontal = hitY?.let { mapOf(it.ratio to it.type) } ?: emptyMap()
                        )
                    )

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

        // Pegangan resize (pojok kanan-bawah) — Scale from Center: geser pegangan ini
        // membesarkan/mengecilkan slot ke SEGALA arah sekaligus dari titik tengahnya,
        // bukan cuma dari pojok kanan-bawah seperti resize biasa.
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(22.dp)
                .clip(RoundedCornerShape(topStart = 8.dp))
                .background(accentColor)
                .pointerInput(slot.id) {
                    // Lacak setengah-lebar & setengah-tinggi (jarak pusat -> tepi), karena
                    // scale-from-center pada dasarnya cuma punya 1 derajat kebebasan per
                    // sumbu: seberapa jauh tepi dari pusat yang TETAP diam.
                    var halfWidthRatio = slot.widthRatio / 2f
                    var halfHeightRatio = slot.heightRatio / 2f

                    // Titik pusat DIHITUNG SEKALI di awal gesture (bukan diturunkan ulang dari
                    // state ter-recompose tiap event) — sesuai definisi "scale from center":
                    // pusatnya adalah invarian selama 1 gesture resize berlangsung, jadi jangan
                    // sampai keikut goyang oleh state yang mungkin belum sempat ke-recompose.
                    val centerX = slot.xRatio + slot.widthRatio / 2f
                    val centerY = slot.yRatio + slot.heightRatio / 2f
                    val maxHalfW = min(centerX, 1f - centerX)
                    val maxHalfH = min(centerY, 1f - centerY)

                    detectDragGestures(
                        onDragEnd = { onSnapGuidesChanged(SnapGuides.NONE) },
                        onDragCancel = { onSnapGuidesChanged(SnapGuides.NONE) }
                    ) { change, dragAmount ->
                        change.consume()
                        val latest = currentSlot.value

                        // Pegangan ada di pojok kanan-bawah = titik (centerX+halfW, centerY+halfH).
                        // Geser pegangan ke kanan/bawah sejauh dx/dy -> setengah-ukuran nambah
                        // sejauh dx/dy juga (bukan 2x), tapi karena tepi SATU-nya di sisi
                        // berlawanan ikut gerak simetris, total lebar/tinggi tetap berubah 2x dx/dy.
                        val rawHalfW = (halfWidthRatio + dragAmount.x / containerWidthPx)
                            .coerceIn(MIN_HALF_SIZE_RATIO, maxHalfW)
                        val rawHalfH = (halfHeightRatio + dragAmount.y / containerHeightPx)
                            .coerceIn(MIN_HALF_SIZE_RATIO, maxHalfH)

                        // Smart Snap: coba tarik tepi kanan (X) & tepi bawah (Y) — yang otomatis
                        // berarti tepi kiri/atas di sisi berlawanan ikut ke garis simetrisnya,
                        // karena pusatnya diam — ke garis Center/Thirds/Quarters/Edges terdekat.
                        val targets = SmartSnap.buildTargets(currentSnapSettings.value)
                        val (snappedHalfW, hitX) = SmartSnap.snapHalfExtent(
                            centerX, rawHalfW, containerWidthPx, snapThresholdPx, targets
                        )
                        val (snappedHalfH, hitY) = SmartSnap.snapHalfExtent(
                            centerY, rawHalfH, containerHeightPx, snapThresholdPx, targets
                        )

                        halfWidthRatio = snappedHalfW.coerceIn(MIN_HALF_SIZE_RATIO, maxHalfW)
                        halfHeightRatio = snappedHalfH.coerceIn(MIN_HALF_SIZE_RATIO, maxHalfH)

                        onSnapGuidesChanged(
                            SnapGuides(
                                vertical = hitX?.let { mapOf(it.ratio to it.type) } ?: emptyMap(),
                                horizontal = hitY?.let { mapOf(it.ratio to it.type) } ?: emptyMap()
                            )
                        )

                        val newWidth = halfWidthRatio * 2f
                        val newHeight = halfHeightRatio * 2f
                        val newX = centerX - halfWidthRatio
                        val newY = centerY - halfHeightRatio

                        Log.d(
                            TAG,
                            "Resize(ScaleFromCenter) slot=${slot.id} newW=$newWidth newH=$newHeight " +
                                    "newX=$newX newY=$newY"
                        )
                        onSlotChanged(
                            latest.copy(
                                xRatio = newX,
                                yRatio = newY,
                                widthRatio = newWidth,
                                heightRatio = newHeight
                            )
                        )
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