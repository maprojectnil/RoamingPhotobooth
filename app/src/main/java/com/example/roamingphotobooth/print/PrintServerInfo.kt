package com.example.roamingphotobooth.print

data class PrintServerInfo(
    val serviceName: String,
    val host: String,
    val port: Int
) {
    val httpBaseUrl: String get() = "http://$host:$port/"
    val wsUrl: String get() = "ws://$host:$port/ws/status"
}