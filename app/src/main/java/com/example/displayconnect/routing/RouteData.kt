package com.example.displayconnect.routing

data class LatLon(val lat: Double, val lon: Double)

data class RouteStep(
    val instruction: String,
    val street: String,
    val distanceM: Int,
    val endLocation: LatLon
)

data class RouteData(
    val coordinates: List<LatLon>,
    val steps: List<RouteStep>,
    val distanceM: Double = 0.0,
    val durationS: Double = 0.0,
    val segmentDistancesM: List<Double> = emptyList(),
    val segmentDurationsS: List<Double> = emptyList()
)
