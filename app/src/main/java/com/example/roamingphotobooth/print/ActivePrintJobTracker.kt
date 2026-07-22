package com.example.roamingphotobooth.print

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class ActivePrintJobTracker(
    private val wsClient: PrintJobWebSocketClient,
    scope: CoroutineScope
) {
    private val activeJobId = MutableStateFlow<String?>(null)

    fun trackJob(jobId: String) {
        activeJobId.value = jobId
    }

    fun clear() {
        activeJobId.value = null
    }

    val status: StateFlow<PrintJobStatus> = combine(
        wsClient.status, activeJobId
    ) { status, jobId ->
        if (jobId == null) return@combine PrintJobStatus.Idle
        val matches = when (status) {
            is PrintJobStatus.Queued -> status.jobId == jobId
            is PrintJobStatus.Printing -> status.jobId == jobId
            is PrintJobStatus.Completed -> status.jobId == jobId
            is PrintJobStatus.Failed -> status.jobId == jobId
            else -> true
        }
        if (matches) status else PrintJobStatus.Idle
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), PrintJobStatus.Idle)
}