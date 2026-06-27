package com.example.displayconnect.models

/**
 * Estatísticas em tempo real da transmissão de quadros.
 */
data class TransmissionStats(
    val fps: Float = 0f,
    val bytesPerSecond: Long = 0L,
    val resolution: String = "240×320",
    val framesSent: Long = 0L,
    val framesSkipped: Long = 0L
)
