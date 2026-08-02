package com.example.roamingphotobooth.status

import java.util.UUID

/**
 * Generate identitas foto (slug) secara LOKAL, tanpa perlu koneksi internet
 * dan tanpa perlu nunggu upload Drive selesai. Slug ini dipakai untuk:
 *  - path dokumen Firestore: photos/{slug}
 *  - path landing page: https://<project>.web.app/p/{slug}
 * QR bisa langsung dibuat dari slug ini begitu foto selesai di-merge.
 */
object SlugGenerator {
    fun newSlug(): String = UUID.randomUUID().toString()

    fun landingUrl(baseUrl: String, slug: String): String {
        val trimmed = baseUrl.trimEnd('/')
        return "$trimmed/p/$slug"
    }
}
