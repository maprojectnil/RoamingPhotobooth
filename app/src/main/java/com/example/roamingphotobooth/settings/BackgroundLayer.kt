package com.example.roamingphotobooth.settings

import android.graphics.BitmapFactory
import android.net.Uri
import android.view.ViewGroup
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/**
 * Menampilkan [background] sebagai layar penuh: gambar diam (Image dengan
 * ContentScale.Crop) atau video yang di-loop otomatis tanpa suara
 * (ExoPlayer + PlayerView dibungkus AndroidView, RESIZE_MODE_ZOOM). Kedua
 * mode sama-sama "fit to screen" dengan cara mengisi penuh layar & memotong
 * kelebihan (bukan letterbox), sesuai gaya background pada umumnya.
 *
 * Kalau [background].path null (belum diatur user), tidak menggambar apa-apa
 * — pemanggil (HomeScreen) tetap punya warna latar default di baliknya.
 */
@Composable
fun BackgroundLayer(
    background: BackgroundSetting,
    modifier: Modifier = Modifier
) {
    val path = background.path ?: return

    if (background.isVideo) {
        VideoBackground(path = path, modifier = modifier)
    } else {
        ImageBackground(path = path, modifier = modifier)
    }
}

/**
 * <-- BARU: menggambar file PNG (dari [path]) DI ATAS background lain (live
 * view atau [BackgroundLayer]) — dipakai HomeScreen untuk overlay opsional
 * (mis. logo event/bingkai dekoratif) di atas background Home, lihat
 * AppearanceSettings.homeOverlayImagePath & AppearanceScreen "Overlay PNG".
 *
 * Pakai [ContentScale.Fit] (BUKAN Crop seperti [BackgroundLayer]) supaya
 * seluruh gambar overlay selalu utuh terlihat & tidak terpotong, karena
 * overlay biasanya logo/elemen dekoratif yang bentuknya penting dijaga
 * (beda dengan background utama yang memang didesain full-bleed).
 *
 * Kalau [path] null (belum diatur user), tidak menggambar apa-apa.
 */
@Composable
fun OverlayPngLayer(
    path: String?,
    modifier: Modifier = Modifier
) {
    if (path == null) return

    val bitmap = remember(path) {
        try {
            BitmapFactory.decodeFile(path)
        } catch (e: Exception) {
            null
        }
    } ?: return

    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier.fillMaxSize()
    )
}

@Composable
private fun ImageBackground(path: String, modifier: Modifier) {
    val bitmap = remember(path) {
        try {
            BitmapFactory.decodeFile(path)
        } catch (e: Exception) {
            null
        }
    } ?: return

    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.fillMaxSize()
    )
}

@Composable
private fun VideoBackground(path: String, modifier: Modifier) {
    val context = LocalContext.current

    val exoPlayer = remember(path) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(java.io.File(path))))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            playWhenReady = true
            prepare()
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = {
            PlayerView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                player = exoPlayer
            }
        }
    )
}
