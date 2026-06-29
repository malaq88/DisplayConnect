package com.example.displayconnect.models

data class TransmissionStats(
    val updatesPerSecond: Float = 0f,
    val bytesPerSecond: Long = 0L,
    val resolution: String = "240×232",
    val totalUpdates: Long = 0L
)
