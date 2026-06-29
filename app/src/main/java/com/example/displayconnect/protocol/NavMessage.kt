package com.example.displayconnect.protocol

import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON navigation message sent to the ESP32 (protocol v2).
 */
data class NavMessage(
    val lat: Double,
    val lon: Double,
    val bearing: Float,
    val instruction: String,
    val distanceM: Int,
    val street: String,
    val routeScreenPoints: List<Pair<Int, Int>>,
    val userScreenPoint: Pair<Int, Int>?,
    val html: String? = null
) {
    fun toJson(): String {
        val route = JSONArray()
        routeScreenPoints.forEach { (x, y) ->
            route.put(JSONArray().put(x).put(y))
        }
        val json = JSONObject()
            .put("type", "nav")
            .put("lat", lat)
            .put("lon", lon)
            .put("bearing", bearing.toDouble())
            .put("instruction", instruction)
            .put("distance_m", distanceM)
            .put("street", street)
            .put("route", route)
        userScreenPoint?.let { (x, y) ->
            json.put("user_x", x).put("user_y", y)
        }
        if (!html.isNullOrBlank()) {
            json.put("html", html.take(480))
        }
        return json.toString()
    }

    companion object {
        fun heartbeat(): String = """{"type":"heartbeat"}"""
    }
}
