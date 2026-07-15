package com.example.displayconnect.models

import com.example.displayconnect.routing.RouteProfile

data class AppSettings(
    val bleDeviceAddress: String = "",
    val bleDeviceName: String = "",
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
