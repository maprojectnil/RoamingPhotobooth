package com.example.roamingphotobooth.print

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.net.InetAddress

/**
 * Auto discovery Photobooth Print Server via mDNS/NSD di jaringan Wi-Fi lokal.
 * Service type harus sama persis dengan MdnsService.ServiceType di server C#
 * ("_photobooth._tcp"), + trailing dot yang wajib untuk NsdManager di Android.
 */
class PrintServerDiscovery(private val context: Context) {

    companion object {
        private const val TAG = "PrintServerDiscovery"
        const val SERVICE_TYPE = "_photobooth._tcp."
    }

    private val nsdManager: NsdManager by lazy {
        context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    }

    private val wifiManager: WifiManager by lazy {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    fun discover(): Flow<PrintServerInfo> = callbackFlow {
        val multicastLock = wifiManager.createMulticastLock("photobooth_mdns_lock").apply {
            setReferenceCounted(true)
            acquire()
        }

        val resolveQueue = ArrayDeque<NsdServiceInfo>()
        var resolveInProgress = false

        fun resolveNext() {
            if (resolveInProgress || resolveQueue.isEmpty()) return
            resolveInProgress = true
            val next = resolveQueue.removeFirst()
            nsdManager.resolveService(next, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.w(TAG, "Resolve gagal untuk ${serviceInfo.serviceName}, error=$errorCode")
                    resolveInProgress = false
                    resolveNext()
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    val host: InetAddress? = serviceInfo.host
                    if (host != null) {
                        trySend(
                            PrintServerInfo(
                                serviceName = serviceInfo.serviceName,
                                host = host.hostAddress ?: host.hostName,
                                port = serviceInfo.port
                            )
                        )
                    }
                    resolveInProgress = false
                    resolveNext()
                }
            })
        }

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                Log.d(TAG, "mDNS discovery dimulai untuk $serviceType")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service ditemukan: ${serviceInfo.serviceName}")
                resolveQueue.addLast(serviceInfo)
                resolveNext()
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service hilang: ${serviceInfo.serviceName}")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "mDNS discovery dihentikan")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Gagal memulai discovery, error=$errorCode")
                close(IllegalStateException("NSD start discovery failed: $errorCode"))
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Gagal menghentikan discovery, error=$errorCode")
            }
        }

        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)

        awaitClose {
            runCatching { nsdManager.stopServiceDiscovery(discoveryListener) }
            runCatching { if (multicastLock.isHeld) multicastLock.release() }
        }
    }
}