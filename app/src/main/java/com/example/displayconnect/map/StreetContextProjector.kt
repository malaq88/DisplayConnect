package com.example.displayconnect.map

import com.example.displayconnect.routing.LatLon
import kotlin.math.hypot
import kotlin.math.min

/**
 * Projects nearby OSM street ways to screen line segments for the CYD map background.
 */
object StreetContextProjector {

    const val MAX_SEGMENTS = 56
    private const val MIN_SEGMENT_PX = 3

    fun projectSegments(
        centerLat: Double,
        centerLon: Double,
        ways: List<List<LatLon>>,
        scaleMeters: Double,
        maxSegments: Int = MAX_SEGMENTS
    ): List<IntArray> {
        if (ways.isEmpty()) return emptyList()

        val candidates = mutableListOf<Pair<IntArray, Double>>()
        for (way in ways) {
            for (i in 0 until way.lastIndex) {
                val p0 = MapProjector.projectPoint(
                    centerLat, centerLon, way[i].lat, way[i].lon, scaleMeters
                )
                val p1 = MapProjector.projectPoint(
                    centerLat, centerLon, way[i + 1].lat, way[i + 1].lon, scaleMeters
                )
                if (!segmentVisible(p0, p1)) continue
                val dx = (p1.first - p0.first).toDouble()
                val dy = (p1.second - p0.second).toDouble()
                if (hypot(dx, dy) < MIN_SEGMENT_PX) continue

                val midX = (p0.first + p1.first) / 2.0
                val midY = (p0.second + p1.second) / 2.0
                val distFromCenter = hypot(
                    midX - MapProjector.MAP_WIDTH / 2.0,
                    midY - MapProjector.MAP_HEIGHT / 2.0
                )
                candidates.add(
                    intArrayOf(p0.first, p0.second, p1.first, p1.second) to distFromCenter
                )
            }
        }

        return candidates
            .sortedBy { it.second }
            .take(maxSegments.coerceAtMost(MAX_SEGMENTS))
            .map { it.first }
    }

    private fun segmentVisible(p0: Pair<Int, Int>, p1: Pair<Int, Int>): Boolean {
        val margin = 8
        val maxX = MapProjector.MAP_WIDTH + margin
        val maxY = MapProjector.MAP_HEIGHT + margin
        fun inBounds(p: Pair<Int, Int>) =
            p.first in -margin..maxX && p.second in -margin..maxY
        if (inBounds(p0) || inBounds(p1)) return true
        val cx = MapProjector.MAP_WIDTH / 2
        val cy = MapProjector.MAP_HEIGHT / 2
        val d0 = hypot((p0.first - cx).toDouble(), (p0.second - cy).toDouble())
        val d1 = hypot((p1.first - cx).toDouble(), (p1.second - cy).toDouble())
        return min(d0, d1) < maxOf(MapProjector.MAP_WIDTH, MapProjector.MAP_HEIGHT) * 0.75
    }
}
