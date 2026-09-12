package com.example.displayconnect.navigation

import com.example.displayconnect.routing.LatLon
import kotlin.math.*

data class GpsSample(val position: LatLon, val accuracyM: Float, val speedMps: Float,
    val bearing: Float, val elapsedMs: Long)
data class GpsStatus(val accuracyM: Float? = null, val lastAcceptedMs: Long = 0, val weak: Boolean = true)

/** Reject stale/batched, imprecise and isolated physically implausible fixes; allow verified reacquisition. */
class GpsFilter(private val maxSpeedMps: Double) {
    private var last: GpsSample? = null
    private var candidate: GpsSample? = null
    private var candidateCount = 0

    fun accept(sample: GpsSample, nowMs: Long): Boolean {
        if (sample.position.lat !in -90.0..90.0 || sample.position.lon !in -180.0..180.0 ||
            !sample.accuracyM.isFinite() || sample.accuracyM !in 0f..40f ||
            nowMs - sample.elapsedMs !in 0..5000) return false
        val previous = last
        if (previous != null) {
            val dt = (sample.elapsedMs - previous.elapsedMs) / 1000.0
            if (dt <= 0) return false
            val distance = distanceM(previous.position, sample.position)
            val plausible = maxSpeedMps * dt + previous.accuracyM + sample.accuracyM + 5
            if (distance > plausible) {
                val suspect = candidate
                candidateCount = if (suspect != null && sample.elapsedMs > suspect.elapsedMs &&
                    distanceM(suspect.position, sample.position) <
                    maxSpeedMps * ((sample.elapsedMs - suspect.elapsedMs) / 1000.0) + 10) candidateCount + 1 else 1
                candidate = sample
                if (candidateCount < 3) return false
            }
        }
        candidate = null; candidateCount = 0; last = sample
        return true
    }

    companion object {
        fun distanceM(a: LatLon, b: LatLon): Double = hypot((b.lat - a.lat) * 111320,
            (b.lon - a.lon) * 111320 * cos(Math.toRadians((a.lat + b.lat) / 2)))

        fun displayPosition(raw: LatLon, matched: LatLon?, routeDistanceM: Double?, accuracyM: Float,
            speedMps: Float, bearing: Float, routeBearing: Float?): LatLon {
            if (matched == null || routeDistanceM == null || accuracyM > 40) return raw
            val threshold = max(8.0, accuracyM.toDouble()).coerceAtMost(20.0)
            if (routeDistanceM > threshold) return raw
            if (speedMps > 2 && routeBearing != null && bearing.isFinite()) {
                val difference = abs((bearing - routeBearing + 540) % 360 - 180)
                if (difference > 60) return raw
            }
            return matched
        }
    }
}
