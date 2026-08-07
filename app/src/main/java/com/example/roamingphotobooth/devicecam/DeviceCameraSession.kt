package com.example.roamingphotobooth.devicecam

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.roamingphotobooth.camera.CameraBackend
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Sumber kamera untuk **Developer Mode** (lihat DeveloperModeButton di HomeScreen) —
 * dipakai supaya booth bisa dites/dipakai tanpa kamera eksternal (DSLR/mirrorless
 * via PTP-USB, lihat [com.example.roamingphotobooth.ptp.EsCameraSession]) tersambung,
 * cukup pakai kamera DEPAN perangkat Android itu sendiri.
 *
 * Implementasi [CameraBackend] yang sama dipakai MainActivity untuk wiring callback
 * live view / hasil capture, apapun sumbernya — lihat catatan di [CameraBackend].
 *
 * Live view: [ImageAnalysis] (bukan Preview + PreviewView) dipakai supaya frame-nya
 * bisa dikonversi ke [Bitmap] lalu diteruskan lewat [onLiveViewFrame] — app ini
 * sudah render live view manual pakai Compose Image(bitmap=...), sama seperti alur
 * PTP, jadi tidak perlu SurfaceView/PreviewView terpisah.
 *
 * Capture: [ImageCapture] menghasilkan JPEG langsung dari CameraX. Kamera depan
 * hasil captured-nya perlu diputar manual sesuai [ImageProxy.getImageInfo]'s
 * rotationDegrees supaya orientasinya tegak (sama seperti yang dilihat user di
 * live view) sebelum di-encode ulang & diteruskan ke [onNewPhotoCaptured].
 */
class DeviceCameraSession(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) : CameraBackend {

    companion object {
        private const val TAG = "DeviceCameraSession"
        private const val CAPTURE_JPEG_QUALITY = 92
        private const val LIVE_VIEW_JPEG_QUALITY = 85
    }

    override var onSessionReady: (() -> Unit)? = null
    override var onSessionError: ((String) -> Unit)? = null
    override var onLiveViewFrame: ((Bitmap) -> Unit)? = null
    override var onNewPhotoCaptured: ((ByteArray) -> Unit)? = null
    override var onCaptureFailed: (() -> Unit)? = null
    override var onDeviceDetached: (() -> Unit)? = null

    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var boundCamera: Camera? = null
    private var isShutDown = false

    /** Mulai kamera depan & bind use case (live view + capture) ke [lifecycleOwner]. */
    fun start() {
        isShutDown = false
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            if (isShutDown) return@addListener
            try {
                val provider = providerFuture.get()
                cameraProvider = provider
                bindUseCases(provider)
            } catch (e: Exception) {
                Log.e(TAG, "Gagal memulai kamera depan: ${e.message}", e)
                onSessionError?.invoke("Kamera depan gagal dimulai: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindUseCases(provider: ProcessCameraProvider) {
        val analysis = ImageAnalysis.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        analysis.setAnalyzer(cameraExecutor) { imageProxy -> processLiveViewFrame(imageProxy) }

        val capture = ImageCapture.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        provider.unbindAll()
        try {
            boundCamera = provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_FRONT_CAMERA,
                analysis,
                capture
            )
            imageCapture = capture
            onSessionReady?.invoke()
        } catch (e: Exception) {
            Log.e(TAG, "Gagal bind kamera depan: ${e.message}", e)
            onSessionError?.invoke("Kamera depan tidak tersedia di perangkat ini: ${e.message}")
        }
    }

    private fun processLiveViewFrame(imageProxy: ImageProxy) {
        try {
            val bitmap = imageProxy.toBitmapViaYuv(LIVE_VIEW_JPEG_QUALITY)
            if (bitmap != null) onLiveViewFrame?.invoke(bitmap)
        } catch (e: Exception) {
            Log.w(TAG, "Gagal proses frame live view: ${e.message}")
        } finally {
            imageProxy.close()
        }
    }

    /** Kirim command jepret ke kamera depan. Return false kalau kamera belum siap. */
    override fun capturePhoto(): Boolean {
        val capture = imageCapture
        if (capture == null) {
            Log.w(TAG, "capturePhoto() dipanggil tapi kamera depan belum siap")
            return false
        }
        capture.takePicture(cameraExecutor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val bytes = try {
                    imageProxyToUprightJpeg(image)
                } catch (e: Exception) {
                    Log.e(TAG, "Gagal proses hasil capture: ${e.message}", e)
                    null
                } finally {
                    image.close()
                }
                if (bytes != null) onNewPhotoCaptured?.invoke(bytes) else onCaptureFailed?.invoke()
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e(TAG, "Capture kamera depan gagal: ${exception.message}", exception)
                onCaptureFailed?.invoke()
            }
        })
        return true
    }

    /** Decode JPEG mentah dari ImageCapture, putar tegak sesuai rotationDegrees, encode ulang. */
    private fun imageProxyToUprightJpeg(image: ImageProxy): ByteArray? {
        val buffer = image.planes[0].buffer
        val rawBytes = ByteArray(buffer.remaining())
        buffer.get(rawBytes)

        val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size) ?: return null
        val upright = rotateBitmap(decoded, image.imageInfo.rotationDegrees)

        return ByteArrayOutputStream().use { stream ->
            upright.compress(Bitmap.CompressFormat.JPEG, CAPTURE_JPEG_QUALITY, stream)
            stream.toByteArray()
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /** Lepas binding kamera (dipanggil saat Developer Mode dimatikan atau ganti sumber). */
    override fun release() {
        try {
            cameraProvider?.unbindAll()
        } catch (e: Exception) {
            Log.w(TAG, "Gagal unbind kamera depan: ${e.message}")
        }
        cameraProvider = null
        imageCapture = null
        boundCamera = null
    }

    /** Panggil sekali dari onDestroy/saat sesi Developer Mode benar-benar selesai. */
    fun shutdown() {
        isShutDown = true
        release()
        cameraExecutor.shutdown()
    }
}

/**
 * Konversi frame [ImageAnalysis] (YUV_420_888) ke [Bitmap] lewat NV21 -> JPEG -> decode.
 * Cukup untuk live view (bukan hasil capture final, yang dipakai [ImageCapture] JPEG
 * langsung) -- pola konversi umum dipakai untuk CameraX ImageAnalysis.
 */
private fun ImageProxy.toBitmapViaYuv(jpegQuality: Int): Bitmap? {
    if (planes.size < 3) return null
    val yBuffer = planes[0].buffer
    val uBuffer = planes[1].buffer
    val vBuffer = planes[2].buffer
    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()

    val nv21 = ByteArray(ySize + uSize + vSize)
    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)

    val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, width, height), jpegQuality, out)
    val bytes = out.toByteArray()
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null

    val degrees = imageInfo.rotationDegrees
    if (degrees == 0) return decoded
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
}
