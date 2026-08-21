package com.example.roamingphotobooth.gallery

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Layar Galeri: daftar SEMUA sesi foto yang sudah pernah selesai (lihat
 * [GallerySessionEntry] / [GalleryRepository]), ditampilkan sebagai grid
 * thumbnail terbaru dulu. Tap 1 entri -> [onEntryClick], biasanya dipakai
 * MainActivity untuk buka layar hasil (FinalResultScreen, mode
 * [com.example.roamingphotobooth.ui.FinalResultMode.GALLERY_RECALL]) supaya
 * user bisa "recall" print ulang & tampilkan lagi QR sesi lama itu, tanpa
 * perlu upload ulang atau jepret ulang apapun.
 *
 * Entri masuk sini murni dari riwayat LOKAL (SharedPreferences di HP ini) --
 * bukan sinkron dari Drive/Firestore -- jadi hanya berisi sesi yang memang
 * pernah selesai di perangkat yang sama.
 */
@Composable
fun GalleryScreen(
    entries: List<GallerySessionEntry>,
    onEntryClick: (GallerySessionEntry) -> Unit,
    onBackClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (entries.isEmpty()) {
            Text(
                text = "📭 Belum ada sesi foto tersimpan.\nHasil sesi yang selesai akan muncul di sini.",
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp)
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 120.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    top = 72.dp,
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 16.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(entries, key = { it.slug }) { entry ->
                    GalleryThumbnailCard(entry = entry, onClick = { onEntryClick(entry) })
                }
            }
        }

        // Header: judul + tombol back, ditumpuk di atas grid (biar grid bisa
        // scroll di baliknya tanpa header ikut kepotong).
        Surface(
            color = Color.Black.copy(alpha = 0.75f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Text(text = "←", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                }
                Text(
                    text = "Galeri",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(vertical = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun GalleryThumbnailCard(entry: GallerySessionEntry, onClick: () -> Unit) {
    val context = LocalContext.current
    val thumbnail by rememberGalleryThumbnail(context, entry.mediaUri)

    Surface(
        color = Color(0xFF1A1D24),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        androidx.compose.foundation.layout.Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .background(Color(0xFF262A33)),
                contentAlignment = Alignment.Center
            ) {
                val bmp = thumbnail
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Thumbnail ${entry.fileName}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
            Text(
                text = formatTimestamp(entry.createdAtMillis),
                style = MaterialTheme.typography.labelSmall,
                color = Color.LightGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}

/**
 * Decode thumbnail (di-downsample, BUKAN resolusi penuh -- itu cuma dipakai
 * saat entri benar-benar dibuka lewat [onEntryClick]) dari [mediaUriString]
 * secara async di background thread, supaya scroll grid tetap mulus walau
 * ada puluhan/ratusan entri sekaligus.
 */
@Composable
private fun rememberGalleryThumbnail(context: Context, mediaUriString: String) =
    produceState<Bitmap?>(initialValue = null, key1 = mediaUriString) {
        value = withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(mediaUriString)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val options = BitmapFactory.Options().apply { inSampleSize = 4 }
                    BitmapFactory.decodeStream(input, null, options)
                }
            } catch (e: Exception) {
                null
            }
        }
    }

private fun formatTimestamp(millis: Long): String {
    // Bukan @Composable -> tidak pakai remember{} di sini (SimpleDateFormat murah
    // dibuat per-panggilan, tidak perlu di-memoize lewat state Compose).
    val fmt = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))
    return fmt.format(Date(millis))
}
