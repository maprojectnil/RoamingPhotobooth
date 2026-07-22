package com.roamingphotobooth.print

import com.example.roamingphotobooth.print.PrintServerRepository
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Menyuntikkan host & port hasil mDNS discovery ke request print yang sudah ada,
 * TANPA mengubah cara request itu dibuat (endpoint path, multipart body, dsb tetap sama).
 *
 * Cara pakai: tambahkan interceptor ini ke OkHttpClient yang sudah dipakai untuk
 * mengirim print job. Base URL lama yang di-hardcode (mis. http://192.168.x.x:port/)
 * cukup diganti jadi placeholder host, contoh: http://printserver.local/,
 * lalu interceptor ini yang mengganti host:port-nya saat runtime.
 */
class PrintServerHostInterceptor(
    private val repository: PrintServerRepository
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val serverInfo = repository.server.value
            ?: return chain.proceed(original) // fallback: biarkan request asli jalan apa adanya

        val newUrl: HttpUrl = original.url.newBuilder()
            .host(serverInfo.host)
            .port(serverInfo.port)
            .build()

        val newRequest = original.newBuilder().url(newUrl).build()
        return chain.proceed(newRequest)
    }
}