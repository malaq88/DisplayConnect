package com.example.displayconnect.routing

enum class RouteProfile(val osrmProfile: String, val storageKey: String) {
    CAR("driving", "car"),
    MOTORCYCLE("driving", "motorcycle"),
    BIKE("cycling", "bike"),
    WALKING("walking", "walking");

    // OSRM profiles are built into each server's graph. The /v1/{profile} label alone does not switch it.
    val routeBaseUrl: String get() = when (this) {
        BIKE -> "https://routing.openstreetmap.de/routed-bike/route/v1/cycling/"
        WALKING -> "https://routing.openstreetmap.de/routed-foot/route/v1/walking/"
        CAR, MOTORCYCLE -> "https://routing.openstreetmap.de/routed-car/route/v1/driving/"
    }

    companion object {
        fun fromStorage(value: String?): RouteProfile =
            entries.find { it.storageKey == value } ?: CAR
    }
}
