package com.example.displayconnect.utils

import com.example.displayconnect.models.TransmissionStats

/**
 * Calcula FPS e taxa de transmissão com base em janelas deslizantes.
 */
class StatsTracker {

    private var frameCount = 0
    private var skippedCount = 0
    private var bytesSent = 0L
    private var windowStartMs = System.currentTimeMillis()
    private var lastFps = 0f
    private var lastBytesPerSecond = 0L

    fun onFrameSent(byteCount: Int) {
        frameCount++
        bytesSent += byteCount
        maybeRollWindow()
    }

    fun onFrameSkipped() {
        skippedCount++
        maybeRollWindow()
    }

    fun currentStats(resolution: String): TransmissionStats {
        maybeRollWindow(force = false)
        return TransmissionStats(
            fps = lastFps,
            bytesPerSecond = lastBytesPerSecond,
            resolution = resolution,
            framesSent = frameCount.toLong(),
            framesSkipped = skippedCount.toLong()
        )
    }

    fun reset() {
        frameCount = 0
        skippedCount = 0
        bytesSent = 0L
        windowStartMs = System.currentTimeMillis()
        lastFps = 0f
        lastBytesPerSecond = 0L
    }

    private fun maybeRollWindow(force: Boolean = true) {
        val now = System.currentTimeMillis()
        val elapsed = now - windowStartMs
        if (!force && elapsed < WINDOW_MS) return

        if (elapsed > 0) {
            lastFps = frameCount * 1000f / elapsed
            lastBytesPerSecond = bytesSent * 1000L / elapsed
        }
        frameCount = 0
        skippedCount = 0
        bytesSent = 0L
        windowStartMs = now
    }

    companion object {
        private const val WINDOW_MS = 1000L
    }
}
