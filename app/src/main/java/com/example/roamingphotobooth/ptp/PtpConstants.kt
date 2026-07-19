package com.example.roamingphotobooth.ptp

/**
 * Konstanta kode operasi, response, dan event PTP standar.
 * Referensi: PTP Standard (ISO 15740) + riset dari DslrDashboard/libgphoto2.
 */
object PtpOpCode {
    const val GET_DEVICE_INFO: Int = 0x1001
    const val OPEN_SESSION: Int = 0x1002
    const val CLOSE_SESSION: Int = 0x1003
    const val GET_STORAGE_IDS: Int = 0x1004
    const val GET_DEVICE_PROP_DESC: Int = 0x1014
    const val GET_DEVICE_PROP_VALUE: Int = 0x1015
    const val GET_OBJECT_INFO: Int = 0x1008
    const val GET_OBJECT: Int = 0x1009
    const val CANON_EOS_SET_REMOTE_MODE: Int = 0x9114
    const val CANON_EOS_SET_EVENT_MODE: Int = 0x9115
    const val CANON_EOS_GET_EVENT: Int = 0x9116
    const val CANON_EOS_SET_DEVICE_PROP_VALUE_EX: Int = 0x9110
    const val CANON_EOS_PCHDD_CAPACITY: Int = 0x911A
    const val GET_OBJECT_HANDLES: Int = 0x1007
    const val CANON_EOS_INITIATE_VIEWFINDER: Int = 0x9151
    const val CANON_EOS_TERMINATE_VIEWFINDER: Int = 0x9152
    const val CANON_EOS_GET_VIEWFINDER_DATA: Int = 0x9153
    const val CANON_EOS_REMOTE_RELEASE: Int = 0x910F

    const val CANON_EOS_REMOTE_RELEASE_ON: Int = 0x9128
    const val CANON_EOS_REMOTE_RELEASE_OFF: Int = 0x9129

}

object PtpEventCode {
    const val OBJECT_ADDED: Int = 0x4002 // generik, tidak dipakai untuk Canon
    const val DEVICE_PROP_CHANGED: Int = 0x4006

    // Kode event khusus Canon EOS (muncul DI DALAM payload GetEvent, bukan container terpisah)
    const val CANON_PROP_VALUE_CHANGED: Int = 0xc189
    const val CANON_OBJECT_ADDED_EX: Int = 0xc181
    const val CANON_REQUEST_OBJECT_TRANSFER: Int = 0xc186
}

object PtpResponseCode {
    const val OK: Int = 0x2001
    const val SESSION_ALREADY_OPEN: Int = 0x201E
    const val DEVICE_BUSY: Int = 0x2019
    const val CANON_EOS_SET_DEVICE_PROP_VALUE_EX: Int = 0x9110
}

object PtpPropCode {
    const val CANON_CAPTURE_DESTINATION: Int = 0xd11c
    const val CANON_EVF_OUTPUT_DEVICE: Int = 0xD1B0
    const val CANON_CONTINUOUS_AF_MODE: Int = 0xD1C9
}

/** Tipe container/paket data PTP (bagian header setiap paket USB) */
object PtpContainerType {
    const val COMMAND: Int = 1
    const val DATA: Int = 2
    const val RESPONSE: Int = 3
    const val EVENT: Int = 4
}