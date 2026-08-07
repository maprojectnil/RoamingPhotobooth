package com.example.roamingphotobooth.camera

import android.graphics.Bitmap

/**
 * Kontrak umum sumber kamera yang dipakai MainActivity, supaya alur wiring
 * callback + capturePhoto() SAMA persis baik saat sumbernya kamera eksternal
 * (EsCameraSession, PTP over USB) maupun kamera depan perangkat (DeviceCameraSession,
 * dipakai saat Developer Mode aktif — lihat DeveloperModeButton di HomeScreen).
 *
 * MainActivity cukup pegang 1 referensi bertipe [CameraBackend] (`activeCameraBackend`)
 * dan tidak perlu tahu implementasi konkretnya lagi setelah wiring awal — switch
 * antara dua sumber ini cukup ganti isi referensi tsb (lihat
 * MainActivity.enableDeveloperMode()/disableDeveloperMode()).
 */
interface CameraBackend {

    /** Kamera siap dipakai — live view otomatis mulai setelah ini. */
    var onSessionReady: (() -> Unit)?

    /** Error sumber kamera (permission ditolak, device error, dll). */
    var onSessionError: ((String) -> Unit)?

    /** 1 frame live view baru siap ditampilkan. */
    var onLiveViewFrame: ((Bitmap) -> Unit)?

    /** Foto baru selesai diambil, di-encode sebagai JPEG bytes. */
    var onNewPhotoCaptured: ((ByteArray) -> Unit)?

    /** capturePhoto() gagal dikirim, atau hasilnya gagal/korup. */
    var onCaptureFailed: (() -> Unit)?

    /** Kamera (fisik) terputus/hilang. Tidak relevan untuk kamera depan perangkat. */
    var onDeviceDetached: (() -> Unit)?

    /** Kirim command jepret. Return false kalau kamera belum siap. */
    fun capturePhoto(): Boolean

    /** Lepas sumber kamera & bersihkan resource terkait. */
    fun release()
}
