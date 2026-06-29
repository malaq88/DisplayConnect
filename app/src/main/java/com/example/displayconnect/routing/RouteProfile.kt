package com.example.displayconnect.routing

enum class RouteProfile(val osrmProfile: String, val storageKey: String) {
    CAR("driving", "car"),
    MOTORCYCLE("driving", "motorcycle"),
    BIKE("cycling", "bike"),
    WALKING("walking", "walking");

    companion object {
        fun fromStorage(value: String?): RouteProfile =
            entries.find { it.storageKey == value } ?: CAR
    }
}
