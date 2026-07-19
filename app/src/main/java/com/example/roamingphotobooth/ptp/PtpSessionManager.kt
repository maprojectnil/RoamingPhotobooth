package com.example.roamingphotobooth.ptp

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PtpSessionManager(private val usbConnection: PtpNativeConnection) {

    private var transactionId: Long = 1
    private var sessionOpen = false
    private val knownObjectIds = mutableSetOf<Int>()

    private var eventListenerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    var onNewPhotoCaptured: ((ByteArray) -> Unit)? = null
    var onSessionReady: (() -> Unit)? = null
    var onSessionError: ((String) -> Unit)? = null

    companion object {
        private const val TAG = "PtpSessionManager"
        private const val READ_BUFFER_SIZE = 1024 * 1024
    }

    fun startSession() {
        scope.launch {
            try {
                // URUTAN WAJIB: GetDeviceInfo dulu, sebelum command apapun lainnya
                val deviceInfoOk = sendCommandAndWait(PtpOpCode.GET_DEVICE_INFO)
                if (!deviceInfoOk) {
                    onSessionError?.invoke("Gagal GetDeviceInfo")
                    return@launch
                }

                val openOk = sendCommandAndWait(PtpOpCode.OPEN_SESSION, intArrayOf(transactionId.toInt()))
                if (!openOk) {
                    onSessionError?.invoke("Gagal OpenSession")
                    return@launch
                }

                // Wajib untuk Canon EOS, supaya kamera keluar dari mode locked
                val remoteModeOk = sendCommandAndWait(PtpOpCode.CANON_EOS_SET_REMOTE_MODE, intArrayOf(1))
                Log.d(TAG, "SetRemoteMode hasil: $remoteModeOk")

                val eventModeOk = sendCommandAndWait(PtpOpCode.CANON_EOS_SET_EVENT_MODE, intArrayOf(1))
                Log.d(TAG, "SetEventMode hasil: $eventModeOk")

                knownObjectIds.addAll(getAllObjectIdsRecursive(-1))
                Log.d(TAG, "Object awal terdeteksi: ${knownObjectIds.size} total (rekursif semua level)")

                sessionOpen = true
                Log.i(TAG, "Sesi PTP berhasil dibuka")
                onSessionReady?.invoke()

                startPollingLoop()

            } catch (e: Exception) {
                Log.e(TAG, "Error saat startSession: ${e.message}")
                onSessionError?.invoke(e.message ?: "Unknown error")
            }
        }
    }

    private var lastObjectCheckTime = 0L
    private val OBJECT_CHECK_INTERVAL_MS = 3000L // cek foto baru tiap 3 detik saja

    private fun startPollingLoop() {
        eventListenerJob = scope.launch {
            var loopCount = 0
            while (sessionOpen) {
                try {
                    loopCount++

                    if (liveViewRequested && !liveViewSetupDone) {
                        val captureDestOk = setCanonDeviceProperty(PtpPropCode.CANON_CAPTURE_DESTINATION, 2)
                        Log.d(TAG, "SetCaptureDestination(SD) hasil: $captureDestOk")

                        // EVFOutputDevice adalah BITMASK: bit0(1)=layar kamera(TFT), bit1(2)=PC/host.
                        // Sebelumnya dipakai 3 (TFT+PC, DUA-DUANYA nyala) — itu bikin layar kamera
                        // ikut nyala DAN prosesor kamera kerja lebih berat buat render 2 output
                        // sekaligus (kemungkinan besar penyebab live view patah-patah). Sekarang
                        // PC-only (2) — layar kamera tetap mati, live view cuma dikirim ke app.
                        val setOutputOk = setCanonDeviceProperty(PtpPropCode.CANON_EVF_OUTPUT_DEVICE, 2)
                        Log.d(TAG, "SetEVFOutputDevice(PC only) hasil: $setOutputOk")

                        // Continuous AF: pakai property resmi ContinousAFMode, BUKAN half-press
                        // manual (yang cuma AF sekali). Ini bikin kamera terus nyari fokus sendiri
                        // selama live view aktif, tanpa perlu simulasi setengah-tekan tombol fisik.
                        val continuousAfOk = setCanonDeviceProperty(PtpPropCode.CANON_CONTINUOUS_AF_MODE, 1)
                        Log.d(TAG, "SetContinuousAF(on) hasil: $continuousAfOk")

                        liveViewSetupDone = true
                        liveViewActive = true
                    }

                    // Live view: SELALU jalan tiap iterasi (prioritas utama, cepat)
                    if (liveViewActive) {
                        pollViewFinderData()
                    }

                    // Capture foto (dari tombol shutter software): dieksekusi DI SINI juga,
                    // di dalam loop yang sama dengan live view polling — supaya command
                    // USB-nya SERIAL/gantian, tidak nabrak/interleave dengan pollViewFinderData
                    // yang jalan tiap 50ms. Kalau capturePhoto() jalan sendiri di coroutine
                    // terpisah, dua-duanya baca/tulis ke koneksi USB yang sama secara
                    // bersamaan dan responsenya bisa kacau — ini kemungkinan besar penyebab
                    // shutter "nggak jepret" sebelumnya.
                    if (captureRequested) {
                        captureRequested = false
                        performCapturePress()
                    }

                    // Cek foto baru: cuma tiap OBJECT_CHECK_INTERVAL_MS, TIDAK setiap iterasi
                    val now = System.currentTimeMillis()
                    if (now - lastObjectCheckTime >= OBJECT_CHECK_INTERVAL_MS) {
                        pollGetEvent()
                        pollNewObjects()
                        lastObjectCheckTime = now
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Exception di polling loop: ${e.message}", e)
                }
                delay(if (liveViewActive) 50 else 1000) // live view: cepat; kalau belum aktif, tetap lambat
            }
            Log.w(TAG, "Polling loop BERHENTI (sessionOpen = $sessionOpen)")
        }
    }

    private fun getCanonDeviceProperty(propCode: Int): Int? {
        val payload = sendCommandAndGetData(PtpOpCode.GET_DEVICE_PROP_VALUE, intArrayOf(propCode)) ?: return null
        if (payload.size < 4) return null
        val buffer = ByteBuffer.wrap(payload, 0, 4).order(ByteOrder.LITTLE_ENDIAN)
        return buffer.int
    }

    private fun pollGetEvent() {
        val nextTxId = ++transactionId
        val commandPacket = PtpContainer.createCommand(PtpOpCode.CANON_EOS_GET_EVENT, nextTxId)

        val written = usbConnection.write(commandPacket)
        if (written < 0) return

        val buffer = ByteArray(READ_BUFFER_SIZE)
        val bytesRead = usbConnection.read(buffer)
        if (bytesRead <= 0) return

        val responseBuffer = ByteArray(64)
        usbConnection.read(responseBuffer)
    }

    private fun setCanonDeviceProperty(propCode: Int, value: Int): Boolean {
        val nextTxId = ++transactionId

        val commandPacket = PtpContainer.createCommand(PtpOpCode.CANON_EOS_SET_DEVICE_PROP_VALUE_EX, nextTxId)
        val written = usbConnection.write(commandPacket)
        if (written < 0) return false

        val dataPayload = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(12)
            putInt(propCode)
            putInt(value)
        }.array()
        val dataPacket = PtpContainer.createDataPacket(PtpOpCode.CANON_EOS_SET_DEVICE_PROP_VALUE_EX, nextTxId, dataPayload)
        val dataWritten = usbConnection.write(dataPacket)
        if (dataWritten < 0) return false

        val responseBuffer = ByteArray(64)
        val respBytesRead = usbConnection.read(responseBuffer)
        if (respBytesRead <= 0) return false

        val responseContainer = PtpContainer.parse(responseBuffer, respBytesRead)
        Log.d(TAG, "SetDeviceProperty response code: 0x${Integer.toHexString(responseContainer.code)}")
        return responseContainer.isResponseOk()
    }

    private fun pollNewObjects() {
        val allFoundIds = getAllObjectIdsRecursive(-1).toSet()

        Log.d(TAG, "pollNewObjects: total ditemukan ${allFoundIds.size} object, known sebelumnya ${knownObjectIds.size}")

        for (id in allFoundIds) {
            if (id !in knownObjectIds) {
                Log.i(TAG, "🎯 Foto baru terdeteksi via polling! Object ID: 0x${Integer.toHexString(id)}")
                knownObjectIds.add(id)
                downloadObject(id)
            }
        }
    }

    var onLiveViewFrame: ((ByteArray) -> Unit)? = null
    private var liveViewActive = false

    private var liveViewRequested = false
    private var liveViewSetupDone = false

    fun startLiveView() {
        liveViewRequested = true
    }

    fun stopLiveView() {
        liveViewActive = false
        sendCommandAndWait(PtpOpCode.CANON_EOS_TERMINATE_VIEWFINDER)
    }

    /**
     * Trigger jepret foto lewat software (dipanggil dari tombol shutter di layar,
     * dipakai di mode Stand). Cuma nyalain flag di sini — command PTP yang beneran
     * dieksekusi DI DALAM polling loop (lihat startPollingLoop -> performCapturePress),
     * supaya SERIAL/gantian sama command USB lain (live view dll), bukan nyerobot
     * jalur USB sendiri di coroutine terpisah (yang bisa bikin komunikasi USB kacau).
     */
    @Volatile
    private var captureRequested = false

    fun capturePhoto() {
        captureRequested = true
    }

    /**
     * Eksekusi beneran command capture. Dipanggil dari dalam loop polling
     * (startPollingLoop), JANGAN dipanggil langsung dari luar supaya urutan
     * command USB tetap serial.
     *
     * Param (3, 0) di RemoteReleaseOn ini BUKAN tebakan — ini persis sama
     * dengan yang dipakai `camera_trigger_canon_eos_capture()` di libgphoto2
     * (library open-source yang dipakai gphoto2, dukungan Canon EOS paling
     * matang & udah terbukti kerja di banyak model EOS/EOS M lewat ribuan
     * laporan pengguna). Param ini disebut "Full-Press" / "Immediate press" —
     * setara nekan tombol shutter fisik sampai penuh dalam 1 langkah.
     *
     * Durasi tahan antara ON dan OFF SENGAJA dipersingkat (100ms, sebelumnya
     * 300ms). Kalau kamera di mode drive CONTINUOUS/BURST, menahan tombol
     * kepencet 300ms bisa kejepret 2+ frame sekaligus (mirip nekan &amp; nahan
     * tombol fisik pas mode burst). 100ms jauh lebih kecil dari jeda antar-frame
     * kebanyakan kamera EOS di mode burst, jadi harusnya cuma 1 foto per tap.
     *
     * Kalau ternyata TETAP nggak jepret di kamera kamu: cek Logcat, filter tag
     * "PtpSessionManager", cari baris "performCapturePress()". Kalau responsenya
     * "Device Busy (0x2019)" biasanya kamera masih nganggep tombol lagi
     * ditekan dari sebelumnya — coba tambah delay lebih lama sebelum capture,
     * atau kasih tau saya hasil Logcat-nya biar saya bantu lacak lebih jauh.
     *
     * Catatan lain: kalau MASIH kejepret 2 foto meski udah dipersingkat,
     * kemungkinan besar drive mode fisik di kamera emang lagi di-set ke
     * Continuous/Burst — coba ganti manual ke Single Shot di kamera.
     */
    private suspend fun performCapturePress() {
        Log.i(TAG, "performCapturePress(): mengirim full-press shutter release")
        val pressOk = sendCommandAndWait(PtpOpCode.CANON_EOS_REMOTE_RELEASE_ON, intArrayOf(3, 0))
        Log.i(TAG, "performCapturePress(): RemoteReleaseOn(3,0) hasil = $pressOk")
        delay(100)
        val releaseOk = sendCommandAndWait(PtpOpCode.CANON_EOS_REMOTE_RELEASE_OFF, intArrayOf(3))
        Log.i(TAG, "performCapturePress(): RemoteReleaseOff(3) hasil = $releaseOk")

        if (!pressOk) {
            Log.w(TAG, "performCapturePress(): RemoteReleaseOn GAGAL (response bukan OK) — kemungkinan Device Busy, cek Logcat detail di atas")
        }
    }

    private fun pollViewFinderData() {
        val payload = sendCommandAndGetData(PtpOpCode.CANON_EOS_GET_VIEWFINDER_DATA, intArrayOf(0x00200000, 0x0, 0x0))

        if (payload == null || payload.isEmpty()) {
            return
        }

        // Cari posisi marker JPEG (0xFF 0xD8) di dalam payload
        val jpegStart = findJpegStart(payload)
        if (jpegStart == -1) {
            Log.w(TAG, "pollViewFinderData: JPEG marker tidak ditemukan di payload ${payload.size} byte")
            return
        }

        val jpegBytes = payload.copyOfRange(jpegStart, payload.size)
        onLiveViewFrame?.invoke(jpegBytes)
    }

    private fun findJpegStart(data: ByteArray): Int {
        for (i in 0 until data.size - 1) {
            if (data[i] == 0xFF.toByte() && data[i + 1] == 0xD8.toByte()) {
                return i
            }
        }
        return -1
    }

    private fun getObjectHandlesInParent(parentId: Int): List<Int> {
        val payload = sendCommandAndGetData(PtpOpCode.GET_OBJECT_HANDLES, intArrayOf(-1, 0, parentId))
        if (payload == null) {
            Log.w(TAG, "getObjectHandlesInParent($parentId): payload null")
            return emptyList()
        }
        val result = parseObjectHandles(payload)
        Log.d(TAG, "getObjectHandlesInParent($parentId): ${result.size} item -> ${result.joinToString { "0x${Integer.toHexString(it)}" }}")
        return result
    }

    private val knownFolderIds = mutableSetOf<Int>()

    // Objek yang SUDAH diklasifikasi BUKAN folder (lewat isFolder() minimal sekali).
    // Tanpa cache ini, getAllObjectIdsRecursive() manggil isFolder() -> 1 round-trip
    // USB (GetObjectInfo, 0x1008) PER OBJECT, SETIAP kali pollNewObjects() jalan
    // (tiap OBJECT_CHECK_INTERVAL_MS = 3 detik) — walau objeknya sama persis dengan
    // yang dicek 3 detik sebelumnya. Untuk ~100+ objek di kartu SD, itu ~2 detik
    // full nge-block loop polling (termasuk pollViewFinderData()), yang kelihatan
    // sebagai live view freeze berkala. Terbukti dari logcat: burst live view lancar
    // ~1 detik, lalu diam total ~2 detik pas 100+ command 0x1008 numpuk beruntun,
    // berulang tiap 3 detik. Dengan cache ini, tiap object ID cuma di-isFolder()-check
    // SEKALI seumur sesi (baik itu folder maupun bukan) — bukan diulang tiap poll.
    private val knownNonFolderIds = mutableSetOf<Int>()

    private fun getAllObjectIdsRecursive(parentId: Int, depth: Int = 0): List<Int> {
        if (depth > 5) return emptyList()

        val children = getObjectHandlesInParent(parentId)
        val result = mutableListOf<Int>()
        result.addAll(children)

        for (childId in children) {
            if (childId in knownFolderIds) {
                // Sudah tahu ini folder dari poll sebelumnya, langsung masuk tanpa cek ulang
                result.addAll(getAllObjectIdsRecursive(childId, depth + 1))
            } else if (childId in knownNonFolderIds) {
                // Sudah tahu ini BUKAN folder dari poll sebelumnya — skip isFolder()
                // sepenuhnya, tidak ada round-trip USB baru buat object ini.
                continue
            } else {
                // Object ID BARU (belum pernah diklasifikasi sama sekali) — baru di
                // sini isFolder() (1 round-trip USB) boleh dipanggil.
                if (isFolder(childId)) {
                    knownFolderIds.add(childId)
                    result.addAll(getAllObjectIdsRecursive(childId, depth + 1))
                } else {
                    knownNonFolderIds.add(childId)
                }
            }
        }

        return result
    }

    private fun isFolder(objectId: Int): Boolean {
        val payload = sendCommandAndGetData(PtpOpCode.GET_OBJECT_INFO, intArrayOf(objectId)) ?: return false
        if (payload.size < 6) return false

        // Struktur ObjectInfo (PTP standar): 4 byte StorageID, lalu 2 byte ObjectFormat
        val buffer = ByteBuffer.wrap(payload, 4, 2).order(ByteOrder.LITTLE_ENDIAN)
        val format = buffer.short.toInt() and 0xFFFF

        return format == 0x3001 // 0x3001 = Association (folder), sesuai standar PTP
    }

    private fun parseObjectHandles(payload: ByteArray): List<Int> {
        if (payload.size < 4) return emptyList()
        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        val count = buffer.int
        val handles = mutableListOf<Int>()
        for (i in 0 until count) {
            if (buffer.remaining() >= 4) {
                handles.add(buffer.int)
            }
        }
        return handles
    }

    private fun sendCommandAndGetData(opCode: Int, params: IntArray = IntArray(0)): ByteArray? {
        val txId = ++transactionId
        val commandPacket = PtpContainer.createCommand(opCode, txId, params)

        val written = usbConnection.write(commandPacket)
        if (written < 0) {
            Log.e(TAG, "sendCommandAndGetData: write gagal untuk 0x${Integer.toHexString(opCode)}")
            return null
        }

        val buffer = ByteArray(READ_BUFFER_SIZE)
        val bytesRead = usbConnection.read(buffer)
        if (bytesRead <= 0) {
            Log.e(TAG, "sendCommandAndGetData: read gagal (return $bytesRead) untuk 0x${Integer.toHexString(opCode)}")
            return null
        }

        val container = PtpContainer.parse(buffer, bytesRead)

        // LOG SELALU, supaya kita tahu persis apa yang dibalas kamera
        Log.d(TAG, "sendCommandAndGetData(0x${Integer.toHexString(opCode)}): container type=${container.containerType}, code=0x${Integer.toHexString(container.code)}, payloadSize=${container.payload.size}")

        if (container.containerType != PtpContainerType.DATA) {
            Log.w(TAG, "sendCommandAndGetData(0x${Integer.toHexString(opCode)}): BUKAN DATA container! Kemungkinan RESPONSE error code 0x${Integer.toHexString(container.code)}")
            return null
        }

        val payload = container.payload

        val responseBuffer = ByteArray(64)
        usbConnection.read(responseBuffer)

        return payload
    }

    private fun downloadObject(objectId: Int) {
        val nextTxId = ++transactionId
        val commandPacket = PtpContainer.createCommand(
            PtpOpCode.GET_OBJECT,
            nextTxId,
            intArrayOf(objectId)
        )

        val written = usbConnection.write(commandPacket)
        if (written < 0) {
            Log.e(TAG, "Gagal kirim command GetObject")
            return
        }

        val outputStream = ByteArrayOutputStream()
        val readBuffer = ByteArray(READ_BUFFER_SIZE)
        var totalExpectedSize = -1

        var isFirstChunk = true
        while (true) {
            val bytesRead = usbConnection.read(readBuffer)
            if (bytesRead <= 0) break

            if (isFirstChunk) {
                val lengthBuffer = ByteBuffer.wrap(readBuffer, 0, 4).order(ByteOrder.LITTLE_ENDIAN)
                totalExpectedSize = lengthBuffer.int - PtpContainer.HEADER_SIZE
                outputStream.write(readBuffer, PtpContainer.HEADER_SIZE, bytesRead - PtpContainer.HEADER_SIZE)
                isFirstChunk = false
            } else {
                outputStream.write(readBuffer, 0, bytesRead)
            }

            if (totalExpectedSize in 1..outputStream.size()) break
        }

        val photoBytes = outputStream.toByteArray()
        Log.i(TAG, "Foto berhasil di-download, ukuran: ${photoBytes.size} byte")
        onNewPhotoCaptured?.invoke(photoBytes)

        val responseBuffer = ByteArray(64)
        usbConnection.read(responseBuffer)
    }

    private fun sendCommandAndWait(opCode: Int, params: IntArray = IntArray(0), maxRetries: Int = 3): Boolean {
        repeat(maxRetries) { attempt ->
            val txId = transactionId
            val commandPacket = PtpContainer.createCommand(opCode, txId, params)

            Log.d(TAG, "Mengirim command 0x${Integer.toHexString(opCode)} (percobaan ${attempt + 1}/$maxRetries)")

            val written = usbConnection.write(commandPacket)
            if (written < 0) {
                Log.w(TAG, "write() gagal (percobaan ${attempt + 1}), retry setelah delay...")
                Thread.sleep(400)
                return@repeat
            }

            val buffer = ByteArray(READ_BUFFER_SIZE)
            val bytesRead = usbConnection.read(buffer)
            if (bytesRead <= 0) {
                Log.w(TAG, "read() gagal (percobaan ${attempt + 1}), retry setelah delay...")
                Thread.sleep(400)
                return@repeat
            }

            val container = PtpContainer.parse(buffer, bytesRead)
            Log.d(TAG, "Container diterima - type: ${container.containerType}, code: 0x${Integer.toHexString(container.code)}")

            if (container.containerType == PtpContainerType.RESPONSE) {
                val ok = container.isResponseOk()
                Log.d(TAG, "Langsung RESPONSE, isResponseOk = $ok")
                return ok
            }

            val responseBuffer = ByteArray(64)
            val respBytesRead = usbConnection.read(responseBuffer)
            if (respBytesRead <= 0) {
                Log.w(TAG, "read() kedua gagal (percobaan ${attempt + 1}), retry setelah delay...")
                Thread.sleep(400)
                return@repeat
            }

            val responseContainer = PtpContainer.parse(responseBuffer, respBytesRead)
            val ok = responseContainer.isResponseOk()
            Log.d(TAG, "isResponseOk = $ok")
            return ok
        }

        Log.e(TAG, "sendCommandAndWait: GAGAL TOTAL setelah $maxRetries percobaan untuk 0x${Integer.toHexString(opCode)}")
        return false
    }

    fun closeSession() {
        if (liveViewActive) {
            sendCommandAndWait(PtpOpCode.CANON_EOS_REMOTE_RELEASE_OFF, intArrayOf(1))
        }
        sessionOpen = false
        eventListenerJob?.cancel()
        usbConnection.close()
    }
}