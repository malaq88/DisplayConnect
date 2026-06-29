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
        return routePoints.map { (lat, lon) ->
            projectPoint(centerLat, centerLon, lat, lon, scaleMeters)
        }
    }

    fun projectPoint(
        centerLat: Double,
        centerLon: Double,
        lat: Double,
        lon: Double,
        scaleMeters: Double
    ): Pair<Int, Int> {
        val metersPerDegLon = METERS_PER_DEG_LAT * cos(Math.toRadians(centerLat))
        val dxMeters = (lon - centerLon) * metersPerDegLon
        val dyMeters = (lat - centerLat) * METERS_PER_DEG_LAT

        val halfW = scaleMeters.coerceAtLeast(50.0)
        val x = (MAP_WIDTH / 2.0 + (dxMeters / halfW) * (MAP_WIDTH / 2.0)).roundToInt()
        val y = (MAP_HEIGHT / 2.0 - (dyMeters / halfW) * (MAP_HEIGHT / 2.0)).roundToInt()
        return x.coerceIn(0, MAP_WIDTH - 1) to y.coerceIn(0, MAP_HEIGHT - 1)
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
