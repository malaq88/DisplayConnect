package com.example.displayconnect.routing

import kotlin.math.*

data class TripProgress(
    val remainingDistanceM: Int? = null,
    val remainingDurationS: Int? = null,
    val offRoute: Boolean = false,
    val matchedPosition: LatLon? = null,
    val distanceFromRouteM: Double? = null,
    val routeBearing: Float? = null
)

/** Projects GPS onto the route; remaining distance follows its bends, not a straight line. */
class RouteProgressCalculator(private val route: RouteData) {
    private val points = route.coordinates
    private val geometry = points.zipWithNext { a, b ->
        val y = Math.toRadians(b.lat - a.lat) * EARTH_RADIUS
        val x = Math.toRadians(b.lon - a.lon) * EARTH_RADIUS * cos(Math.toRadians((a.lat + b.lat) / 2))
        hypot(x, y)
    }
    private val distances = weights(route.segmentDistancesM, route.distanceM, geometry)
    private val durations = weights(route.segmentDurationsS, route.durationS, distances)
    private val distanceSuffix = suffix(distances)
    private val durationSuffix = suffix(durations)
    private var lastSegment: Int? = null

    fun update(location: LatLon): TripProgress {
        if (points.size < 2 || !location.lat.isFinite() || !location.lon.isFinite()) return TripProgress()
        val cosLat = cos(Math.toRadians(location.lat))
        fun match(range: IntRange): Match {
            var best = Match(range.first, 0.0, Double.POSITIVE_INFINITY)
            for (i in range) {
                val a = points[i]
                val b = points[i + 1]
                val ax = Math.toRadians(a.lon - location.lon) * EARTH_RADIUS * cosLat
                val ay = Math.toRadians(a.lat - location.lat) * EARTH_RADIUS
                val dx = Math.toRadians(b.lon - a.lon) * EARTH_RADIUS * cosLat
                val dy = Math.toRadians(b.lat - a.lat) * EARTH_RADIUS
                val lengthSq = dx * dx + dy * dy
                val fraction = if (lengthSq > 0) (-(ax * dx + ay * dy) / lengthSq).coerceIn(0.0, 1.0) else 0.0
                val distance = hypot(ax + fraction * dx, ay + fraction * dy)
                if (distance < best.distance - 0.01) best = Match(i, fraction, distance)
            }
            return best
        }
        val all = 0 until points.lastIndex
        val previous = lastSegment
        var found = match(if (previous == null) all else max(0, previous - 3)..min(points.lastIndex - 1, previous + 40))
        if (found.distance > 80 && previous != null) found = match(all)
        // Do not present a made-up ETA when GPS is far from this route.
        if (found.distance > 80) return TripProgress(offRoute = true)
        lastSegment = found.index
        val i = found.index
        val remainingM = distanceSuffix[i + 1] + distances[i] * (1 - found.fraction)
        val remainingS = durationSuffix[i + 1] + durations[i] * (1 - found.fraction)
        val a = points[i]; val b = points[i + 1]
        val matched = LatLon(a.lat + (b.lat - a.lat) * found.fraction, a.lon + (b.lon - a.lon) * found.fraction)
        val direction = ((Math.toDegrees(atan2((b.lon - a.lon) * cosLat, b.lat - a.lat)) + 360) % 360).toFloat()
        return TripProgress(
            ceil(remainingM).toInt().coerceAtLeast(0),
            if (route.durationS > 0) ceil(remainingS).toInt().coerceAtLeast(0) else null,
            matchedPosition = matched, distanceFromRouteM = found.distance, routeBearing = direction
        )
    }

    private fun weights(annotations: List<Double>, total: Double, fallback: List<Double>): List<Double> {
        val usable = annotations.size == geometry.size && annotations.all { it.isFinite() && it >= 0 } && annotations.sum() > 0
        val values = if (usable) annotations else fallback
        val sum = values.sum()
        val target = if (total.isFinite() && total > 0) total else sum
        return values.map { if (sum > 0) it * target / sum else 0.0 }
    }

    private fun suffix(values: List<Double>): DoubleArray {
        val result = DoubleArray(values.size + 1)
        for (i in values.indices.reversed()) result[i] = result[i + 1] + values[i]
        return result
    }

    private data class Match(val index: Int, val fraction: Double, val distance: Double)
    private companion object { const val EARTH_RADIUS = 6_371_000.0 }
}
