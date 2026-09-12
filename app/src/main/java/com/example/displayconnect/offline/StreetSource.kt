package com.example.displayconnect.offline

import com.example.displayconnect.routing.LatLon

data class StreetWay(val id: Long, val points: List<LatLon>) {
    val bounds: GeoBounds = GeoBounds(points.minOf { it.lat }, points.minOf { it.lon },
        points.maxOf { it.lat }, points.maxOf { it.lon })
}

fun interface StreetSource {
    suspend fun fetch(bounds: List<GeoBounds>): List<StreetWay>
}

data class OfflineMapState(
    val downloaded: Int = 0,
    val total: Int = 0,
    val downloading: Boolean = false,
    val error: String? = null,
    val radiusM: Int = 0,
    val outsideCoverage: Boolean = false
) {
    val ready: Boolean get() = total > 0 && downloaded == total && error == null && !downloading
}
