#include <jni.h>
#include <android/log.h>
#include <unistd.h>
#include "libusb.h"

#define LOG_TAG "PtpBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static libusb_context *g_ctx = nullptr;
static libusb_device_handle *g_handle = nullptr;

// Endpoint yang sudah kita ketahui dari deteksi Kotlin sebelumnya
#define EP_WRITE 0x02
#define EP_READ  0x81
#define EP_INTERRUPT 0x83
#define USB_TIMEOUT_MS 5000

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_roamingphotobooth_ptp_NativePtpBridge_testConnection(JNIEnv *env, jobject) {
    LOGI("Native bridge berhasil dipanggil dari Kotlin!");
    return env->NewStringUTF("Halo dari C++!");
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_roamingphotobooth_ptp_NativePtpBridge_openDeviceWithFd(JNIEnv *env, jobject, jint fd) {
    int r = libusb_set_option(nullptr, LIBUSB_OPTION_NO_DEVICE_DISCOVERY, nullptr);
    if (r != LIBUSB_SUCCESS) {
        LOGE("libusb_set_option gagal: %d", r);
        return JNI_FALSE;
    }

    r = libusb_init(&g_ctx);
    if (r < 0) {
        LOGE("libusb_init gagal: %d", r);
        return JNI_FALSE;
    }

    r = libusb_wrap_sys_device(g_ctx, (intptr_t) fd, &g_handle);
    if (r < 0 || g_handle == nullptr) {
        LOGE("libusb_wrap_sys_device gagal: %d", r);
        return JNI_FALSE;
    }

    LOGI("libusb_wrap_sys_device BERHASIL! Device handle siap dipakai.");
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_roamingphotobooth_ptp_NativePtpBridge_claimInterface(JNIEnv *env, jobject) {
    if (g_handle == nullptr) {
        LOGE("claimInterface: device handle belum siap (openDeviceWithFd belum dipanggil?)");
        return JNI_FALSE;
    }

    // Beberapa ROM (mis. MIUI/HyperOS di chipset MediaTek) masih menyelesaikan
    // handshake internal terhadap device tepat setelah izin USB baru diberikan,
    // walau secara API sudah "granted". Kalau kita langsung claim + langsung
    // transfer, transfer pertama bisa silent-timeout meski claim_interface
    // sendiri sukses. Kasih jeda kecil dulu sebelum mencoba claim.
    usleep(300000); // 300ms

    const int MAX_CLAIM_ATTEMPTS = 3;
    int r = 0;
    for (int attempt = 1; attempt <= MAX_CLAIM_ATTEMPTS; attempt++) {
        r = libusb_claim_interface(g_handle, 0);
        if (r == 0) {
            break;
        }

        LOGE("libusb_claim_interface gagal (percobaan %d/%d): %d (%s)",
             attempt, MAX_CLAIM_ATTEMPTS, r, libusb_error_name(r));

        if (attempt < MAX_CLAIM_ATTEMPTS) {
            usleep(400000); // 400ms sebelum retry
        }
    }

    if (r < 0) {
        LOGE("libusb_claim_interface GAGAL TOTAL setelah %d percobaan", MAX_CLAIM_ATTEMPTS);
        return JNI_FALSE;
    }

    LOGI("libusb_claim_interface BERHASIL!");

    // Reset kemungkinan state "halt/stall" yang tersisa di endpoint dari
    // sesi sebelumnya. claim_interface TIDAK menyentuh state ini sama
    // sekali, dan beberapa host USB controller (terutama di chipset
    // non-Qualcomm) bisa meninggalkan endpoint dalam kondisi halted
    // walau device sudah di-enumerate ulang. Kalau ini terjadi, semua
    // bulk transfer akan timeout dari percobaan pertama -- persis gejala
    // yang kita lihat di beberapa device MediaTek.
    int rClearRead = libusb_clear_halt(g_handle, EP_READ);
    int rClearWrite = libusb_clear_halt(g_handle, EP_WRITE);
    int rClearIntr = libusb_clear_halt(g_handle, EP_INTERRUPT);
    LOGI("clear_halt EP_READ=%d(%s) EP_WRITE=%d(%s) EP_INTERRUPT=%d(%s)",
         rClearRead, libusb_error_name(rClearRead),
         rClearWrite, libusb_error_name(rClearWrite),
         rClearIntr, libusb_error_name(rClearIntr));

    // Jeda tambahan kecil setelah claim sukses, sebelum command PTP pertama
    // dikirim dari sisi Kotlin. Ini murah (300ms) dan hanya terjadi sekali
    // per sesi, tapi meredam race condition di atas pada device yang rawan.
    usleep(300000);

    return JNI_TRUE;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_roamingphotobooth_ptp_NativePtpBridge_bulkWrite(JNIEnv *env, jobject, jbyteArray data) {
    if (g_handle == nullptr) return -1;

    jsize len = env->GetArrayLength(data);
    jbyte *buffer = env->GetByteArrayElements(data, nullptr);

    int transferred = 0;
    int r = libusb_bulk_transfer(g_handle, EP_WRITE, (unsigned char *) buffer, len, &transferred, USB_TIMEOUT_MS);

    env->ReleaseByteArrayElements(data, buffer, JNI_ABORT);

    if (r < 0) {
        LOGE("bulkWrite gagal: %d (%s)", r, libusb_error_name(r));
        return -1;
    }

    return transferred;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_roamingphotobooth_ptp_NativePtpBridge_bulkRead(JNIEnv *env, jobject, jbyteArray buffer, jint maxLen) {
    if (g_handle == nullptr) return -1;

    unsigned char *nativeBuffer = new unsigned char[maxLen];
    int transferred = 0;

    int r = libusb_bulk_transfer(g_handle, EP_READ, nativeBuffer, maxLen, &transferred, USB_TIMEOUT_MS);

    if (r < 0) {
        LOGE("bulkRead gagal: %d (%s)", r, libusb_error_name(r));
        delete[] nativeBuffer;
        return -1;
    }

    env->SetByteArrayRegion(buffer, 0, transferred, (jbyte *) nativeBuffer);
    delete[] nativeBuffer;

    return transferred;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_roamingphotobooth_ptp_NativePtpBridge_closeDevice(JNIEnv *env, jobject) {
    if (g_handle != nullptr) {
        libusb_release_interface(g_handle, 0);
        libusb_close(g_handle);
        g_handle = nullptr;
    }
    if (g_ctx != nullptr) {
        libusb_exit(g_ctx);
        g_ctx = nullptr;
    }
    LOGI("Device closed.");
}