package com.example.displayconnect.models

import com.example.displayconnect.routing.RouteProfile

data class AppSettings(
    val espIp: String = "192.168.4.1",
    val espPort: Int = 81,
    val navUpdateHz: Int = 2,
    val mapScaleMeters: Double = 400.0,
    val routeProfile: RouteProfile = RouteProfile.CAR,
    val destQuery: String = "",
    val destLabel: String = "",
    val destLat: String = "",
    val destLon: String = ""
) {
    val resolutionLabel: String get() = "240×232 map"
}
