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
    val streetSegments: List<IntArray> = emptyList(),
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
        if (streetSegments.isNotEmpty()) {
            val streets = JSONArray()
            streetSegments.forEach { seg ->
                if (seg.size >= 4) {
                    streets.put(JSONArray().put(seg[0]).put(seg[1]).put(seg[2]).put(seg[3]))
                }
            }
            if (streets.length() > 0) {
                json.put("streets", streets)
            }
        }
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
