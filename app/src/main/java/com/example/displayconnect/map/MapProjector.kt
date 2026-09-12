package com.example.displayconnect.map

import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * Projects lat/lon coordinates to screen pixels for the CYD map area.
 */
object MapProjector {

    const val MAP_WIDTH = 240
    const val MAP_HEIGHT = 232

    private const val METERS_PER_DEG_LAT = 111_320.0

    fun projectRoute(
        centerLat: Double,
        centerLon: Double,
        routePoints: List<Pair<Double, Double>>,
        scaleMeters: Double
    ): List<Pair<Int, Int>> {
        val runs = ScreenGeometry.visibleRuns(routePoints.map { (lat, lon) ->
            projectUnclipped(centerLat, centerLon, lat, lon, scaleMeters)
        })
        val result = mutableListOf<Pair<Int, Int>>()
        for (run in runs) {
            val simplified = ScreenGeometry.simplify(run)
            val remaining = 64 - result.size - if (result.isEmpty()) 0 else 1
            if (remaining < 2) break
            if (result.isNotEmpty()) result.add(-1 to -1) // Off-screen gap; never draw a shortcut.
            result.addAll(simplified.take(remaining).map { it.x.toInt() to it.y.toInt() })
        }
        return result
    }

    fun projectPoint(
        centerLat: Double,
        centerLon: Double,
        lat: Double,
        lon: Double,
        scaleMeters: Double
    ): Pair<Int, Int> {
        val point = projectUnclipped(centerLat, centerLon, lat, lon, scaleMeters)
        return point.x.roundToInt().coerceIn(0, MAP_WIDTH - 1) to
            point.y.roundToInt().coerceIn(0, MAP_HEIGHT - 1)
    }

    fun projectUnclipped(
        centerLat: Double, centerLon: Double, lat: Double, lon: Double, scaleMeters: Double
    ): ScreenPoint {
        val metersPerDegLon = METERS_PER_DEG_LAT * cos(Math.toRadians(centerLat))
        val dxMeters = (lon - centerLon) * metersPerDegLon
        val dyMeters = (lat - centerLat) * METERS_PER_DEG_LAT

        val halfW = scaleMeters.coerceAtLeast(50.0)
        // Preserve vertical range and use equal pixel scale on both axes.
        // The landscape display shows more context horizontally, without stretching roads.
        val pixelsPerMeter = MAP_HEIGHT / (2.0 * halfW)
        return ScreenPoint(MAP_WIDTH / 2.0 + dxMeters * pixelsPerMeter,
            MAP_HEIGHT / 2.0 - dyMeters * pixelsPerMeter)
    }

    fun simplifyRoute(
        points: List<Pair<Double, Double>>,
        maxPoints: Int = 64
    ): List<Pair<Double, Double>> {
        if (points.size <= maxPoints) return points
        val step = points.size.toDouble() / maxPoints
        return (0 until maxPoints).map { i ->
            points[(i * step).toInt().coerceAtMost(points.lastIndex)]
        }
    }
}
