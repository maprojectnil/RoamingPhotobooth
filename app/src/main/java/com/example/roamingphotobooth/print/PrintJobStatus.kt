package com.example.roamingphotobooth.print

sealed class PrintJobStatus {
    object Idle : PrintJobStatus()
    object ConnectingToServer : PrintJobStatus()
    object Disconnected : PrintJobStatus()

    data class Queued(val jobId: String, val fileName: String, val copies: Int) : PrintJobStatus()
    data class Printing(val jobId: String, val fileName: String, val copies: Int) : PrintJobStatus()
    data class Completed(val jobId: String, val fileName: String, val copies: Int) : PrintJobStatus()
    data class Failed(val jobId: String, val fileName: String, val errorMessage: String?) : PrintJobStatus()
}