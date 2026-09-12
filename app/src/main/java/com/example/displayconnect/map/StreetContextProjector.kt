package com.example.displayconnect.map

import com.example.displayconnect.routing.LatLon
import kotlin.math.*

object StreetContextProjector {
    // Must match NAV_MAX_STREET_SEGMENTS in the CYD firmware.
    const val MAX_SEGMENTS = 128

    fun projectSegments(
        centerLat: Double, centerLon: Double, ways: List<List<LatLon>>,
        scaleMeters: Double, maxSegments: Int = MAX_SEGMENTS
    ): List<IntArray> {
        val budget = maxSegments.coerceIn(0, MAX_SEGMENTS)
        if (budget == 0) return emptyList()
        val visible = ways.mapNotNull { way ->
            ScreenGeometry.visibleRuns(way.map {
                MapProjector.projectUnclipped(centerLat, centerLon, it.lat, it.lon, scaleMeters)
            }).takeIf { it.isNotEmpty() }
        }
        // First simplify all visible streets equally, keeping distant roads as well as central roads.
        var simplified = visible.map { runs -> runs.map { ScreenGeometry.simplify(it) } }
        for (tolerance in listOf(2.0, 3.0)) {
            if (simplified.sumOf { runs -> runs.sumOf { it.size - 1 } } <= budget) break
            simplified = visible.map { runs -> runs.map { ScreenGeometry.simplify(it, tolerance) } }
        }
        // In very dense areas, distribute complete ways over a 6x3 screen grid.
        val groups = simplified.groupBy { runs ->
            val points = runs.flatten()
            val x = (points.minOf { it.x } + points.maxOf { it.x }) / 2
            val y = (points.minOf { it.y } + points.maxOf { it.y }) / 2
            (x / (MapProjector.MAP_WIDTH / 6.0)).toInt().coerceIn(0, 5) +
                6 * (y / (MapProjector.MAP_HEIGHT / 3.0)).toInt().coerceIn(0, 2)
        }.toSortedMap().values.map { java.util.ArrayDeque(it) }
        val result = mutableListOf<IntArray>()
        while (groups.any { it.isNotEmpty() }) {
            for (group in groups) {
                if (group.isEmpty()) continue
                val runs = group.removeFirst()
                if (runs.sumOf { it.size - 1 } > budget - result.size) continue
                for (run in runs) for ((a, b) in run.zipWithNext()) {
                    result.add(intArrayOf(a.x.toInt(), a.y.toInt(), b.x.toInt(), b.y.toInt()))
                }
                if (result.size == budget) return result
            }
        }
        return result
    }
}
