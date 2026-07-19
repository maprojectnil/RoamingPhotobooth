package com.example.roamingphotobooth.ptp

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import android.os.Parcelable
import androidx.core.content.ContextCompat

/**
 * Mengelola deteksi kamera USB dan permintaan izin akses.
 */
class PtpDeviceManager(private val context: Context) {

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var onDeviceReady: ((UsbDevice) -> Unit)? = null
    private var onDeviceDetached: ((UsbDevice) -> Unit)? = null

    companion object {
        private const val TAG = "PtpDeviceManager"
        private const val ACTION_USB_PERMISSION = "com.example.roamingphotobooth.USB_PERMISSION"
        private const val CANON_VENDOR_ID = 1193 // 0x04A9
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            try {
                when (intent.action) {
                    ACTION_USB_PERMISSION -> {
                        synchronized(this) {
                            val device: UsbDevice? = intent.getParcelableExtraCompat(UsbManager.EXTRA_DEVICE)
                            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)

                            if (granted && device != null) {
                                Log.i(TAG, "Izin USB diberikan untuk device: ${device.deviceName}")
                                onDeviceReady?.invoke(device)
                            } else {
                                Log.w(TAG, "Izin USB DITOLAK oleh user")
                            }
                        }
                    }
                    UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                        val device: UsbDevice? = intent.getParcelableExtraCompat(UsbManager.EXTRA_DEVICE)
                        if (device != null && device.vendorId == CANON_VENDOR_ID) {
                            Log.i(TAG, "Device tersambung (broadcast): ${device.deviceName}")
                            requestPermissionIfNeeded(device)
                        }
                    }
                    UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                        val device: UsbDevice? = intent.getParcelableExtraCompat(UsbManager.EXTRA_DEVICE)
                        if (device != null && device.vendorId == CANON_VENDOR_ID) {
                            Log.i(TAG, "Device terputus: ${device.deviceName}")
                            onDeviceDetached?.invoke(device)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error di onReceive: ${e.message}", e)
            }
        }
    }

    private var isListening = false

    /**
     * Panggil ini saat aplikasi mulai (misal di onCreate Activity),
     * untuk mendaftarkan listener USB attach/permission.
     */
    fun startListening(onReady: (UsbDevice) -> Unit, onDetached: ((UsbDevice) -> Unit)? = null) {
        onDeviceReady = onReady
        onDeviceDetached = onDetached

        if (!isListening) {
            val filter = IntentFilter().apply {
                addAction(ACTION_USB_PERMISSION)
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Kita gunakan RECEIVER_EXPORTED karena USB_DEVICE_DETACHED adalah system broadcast
                context.registerReceiver(usbReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                ContextCompat.registerReceiver(
                    context,
                    usbReceiver,
                    filter,
                    ContextCompat.RECEIVER_EXPORTED
                )
            }
            isListening = true
        }

        // Cek juga: mungkin kamera sudah tersambung SEBELUM aplikasi dibuka
        checkAlreadyConnectedDevice()
    }

    /**
     * Cek device yang sudah tersambung duluan (misal: kamera sudah nyala & tersambung,
     * baru aplikasi kita dibuka setelahnya)
     */
    private fun checkAlreadyConnectedDevice() {
        val deviceList = usbManager.deviceList
        for (device in deviceList.values) {
            if (device.vendorId == CANON_VENDOR_ID) {
                requestPermissionIfNeeded(device)
            }
        }
    }

    private fun requestPermissionIfNeeded(device: UsbDevice) {
        if (device.vendorId != CANON_VENDOR_ID) {
            return // Bukan kamera Canon, abaikan
        }

        if (usbManager.hasPermission(device)) {
            // Sudah punya izin sebelumnya, langsung lanjut
            onDeviceReady?.invoke(device)
            return
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }
        val intent = Intent(ACTION_USB_PERMISSION).apply {
            setPackage(context.packageName)
        }
        val permissionIntent = PendingIntent.getBroadcast(
            context, 0, intent, flags
        )
        usbManager.requestPermission(device, permissionIntent)
    }

    fun stopListening() {
        if (isListening) {
            try {
                context.unregisterReceiver(usbReceiver)
            } catch (e: IllegalArgumentException) {
                // Receiver sudah tidak terdaftar, aman diabaikan
            }
            isListening = false
        }
    }
}

/**
 * Helper supaya getParcelableExtra tidak deprecated di Android versi baru
 */
private inline fun <reified T : Parcelable> Intent.getParcelableExtraCompat(key: String): T? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(key, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(key) as? T
    }
}