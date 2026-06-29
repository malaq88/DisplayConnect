package com.example.displayconnect.utils

import com.example.displayconnect.models.TransmissionStats

class StatsTracker {

    private var updateCount = 0
    private var bytesSent = 0L
    private var windowStartMs = System.currentTimeMillis()
    private var lastUpdatesPerSecond = 0f
    private var lastBytesPerSecond = 0L
    private var totalUpdates = 0L

    fun onNavUpdate(byteCount: Int) {
        updateCount++
        totalUpdates++
        bytesSent += byteCount
        maybeRollWindow()
    }

    fun currentStats(resolution: String): TransmissionStats {
        maybeRollWindow(force = false)
        return TransmissionStats(
            updatesPerSecond = lastUpdatesPerSecond,
            bytesPerSecond = lastBytesPerSecond,
            resolution = resolution,
            totalUpdates = totalUpdates
        )
    }

    fun reset() {
        updateCount = 0
        bytesSent = 0L
        totalUpdates = 0L
        windowStartMs = System.currentTimeMillis()
        lastUpdatesPerSecond = 0f
        lastBytesPerSecond = 0L
    }

    private fun maybeRollWindow(force: Boolean = true) {
        val now = System.currentTimeMillis()
        val elapsed = now - windowStartMs
        if (!force && elapsed < WINDOW_MS) return

        if (elapsed > 0) {
            lastUpdatesPerSecond = updateCount * 1000f / elapsed
            lastBytesPerSecond = bytesSent * 1000L / elapsed
        }
        updateCount = 0
        bytesSent = 0L
        windowStartMs = now
    }

    companion object {
        private const val WINDOW_MS = 1000L
    }
}
