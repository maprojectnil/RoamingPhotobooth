package com.example.roamingphotobooth.ptp

import android.content.Context
import android.hardware.usb.UsbDevice
import android.util.Log

/**
 * Menyimpan catatan device USB (kamera) mana yang pernah terbukti gagal
 * lewat jalur libusb (wrap_sys_device + bulk_transfer) di kombinasi
 * device+host ini -- lihat catatan panjang di [PtpNativeConnection].
 *
 * Kenapa ini perlu: dari observasi lapangan, begitu libusb sempat mengirim
 * satu command ke device tertentu (walau balasannya lalu timeout), device
 * itu tampak "macet"/wedged untuk sisa sesi USB fisik itu -- tidak bisa
 * dipulihkan lagi lewat fd baru atau koneksi baru di tengah sesi yang sama
 * (clear_halt di kedua sisi pun tidak menolong). Jadi begitu satu device
 * tercatat pernah gagal, sesi-sesi PTP berikutnya untuk device yang sama
 * TIDAK akan mencoba libusb sama sekali -- langsung pakai jalur
 * UsbDeviceConnection.bulkTransfer() bawaan Android dari awal, meniru
 * kondisi bersih yang terbukti jalan di NativeUsbBulkTest.
 *
 * Catatan penting: flag ini menandai "device ini historically bermasalah
 * dengan libusb di host ini", BUKAN "device sedang wedged sekarang". Device
 * yang sedang wedged biasanya pulih lagi setelah dicabut-colok ulang, tapi
 * itu tidak berarti libusb jadi aman dipakai lagi untuk device tsb -- jadi
 * flag ini sengaja tidak auto-clear saat device detach/attach ulang. Kalau
 * suatu saat perlu dites ulang manual (misal setelah update ROM/driver),
 * panggil [clearAll].
 */
class UsbQuirksStorage(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Kunci per device: pakai serial number kalau tersedia (paling presisi --
     * membedakan 2 kamera dengan model persis sama), fallback ke
     * vendorId:productId kalau serial tidak bisa dibaca (tidak semua device
     * expose serial number, dan membaca serial butuh izin USB yang di titik
     * pemanggilan ini seharusnya sudah granted).
     */
    fun keyFor(device: UsbDevice): String {
        val serial = try {
            device.serialNumber
        } catch (e: SecurityException) {
            Log.w(TAG, "Tidak bisa baca serial number (izin belum lengkap): ${e.message}")
            null
        }
        return if (!serial.isNullOrBlank()) {
            "${device.vendorId}:${device.productId}:$serial"
        } else {
            "${device.vendorId}:${device.productId}"
        }
    }

    fun isLibusbUnreliable(deviceKey: String): Boolean {
        return prefs.getBoolean(KEY_PREFIX + deviceKey, false)
    }

    fun markLibusbUnreliable(deviceKey: String) {
        if (isLibusbUnreliable(deviceKey)) return // sudah tercatat, tidak perlu tulis ulang
        Log.w(TAG, "Menandai device '$deviceKey' bermasalah dengan libusb -- sesi berikutnya langsung pakai native fallback.")
        prefs.edit().putBoolean(KEY_PREFIX + deviceKey, true).apply()
    }

    /** Reset semua catatan quirk -- sediakan sebagai tombol manual di Settings kalau dibutuhkan. */
    fun clearAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val TAG = "UsbQuirksStorage"
        private const val PREFS_NAME = "usb_quirks"
        private const val KEY_PREFIX = "libusb_unreliable_"
    }
}