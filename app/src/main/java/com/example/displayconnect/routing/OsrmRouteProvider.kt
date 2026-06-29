package com.example.displayconnect.routing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class OsrmRouteProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
) {

    suspend fun fetchRoute(origin: LatLon, destination: LatLon): Result<RouteData> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = buildString {
                    append(BASE_URL)
                    append(origin.lon).append(',').append(origin.lat)
                    append(';')
                    append(destination.lon).append(',').append(destination.lat)
                    append("?overview=full&geometries=geojson&steps=true")
                }
                val request = Request.Builder().url(url).get().build()
                val body = client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        error("OSRM HTTP ${response.code}")
                    }
                    response.body?.string() ?: error("Empty OSRM response")
                }
                parseResponse(body)
            }
        }

    private fun parseResponse(json: String): RouteData {
        val root = JSONObject(json)
        val routes = root.getJSONArray("routes")
        if (routes.length() == 0) {
            error("No route found")
        }
        val route = routes.getJSONObject(0)
        val geometry = route.getJSONObject("geometry")
        val coordinates = geometry.getJSONArray("coordinates")
        val points = buildList {
            for (i in 0 until coordinates.length()) {
                val coord = coordinates.getJSONArray(i)
                add(LatLon(lat = coord.getDouble(1), lon = coord.getDouble(0)))
            }
        }

        val steps = mutableListOf<RouteStep>()
        val legs = route.getJSONArray("legs")
        for (l in 0 until legs.length()) {
            val legSteps = legs.getJSONObject(l).getJSONArray("steps")
            for (s in 0 until legSteps.length()) {
                val step = legSteps.getJSONObject(s)
                val maneuver = step.getJSONObject("maneuver")
                val location = maneuver.getJSONArray("location")
                val modifier = maneuver.optString("modifier", "")
                val type = maneuver.optString("type", "continue")
                val instruction = formatInstruction(type, modifier)
                steps.add(
                    RouteStep(
                        instruction = instruction,
                        street = step.optString("name", ""),
                        distanceM = step.optDouble("distance", 0.0).toInt(),
                        endLocation = LatLon(
                            lat = location.getDouble(1),
                            lon = location.getDouble(0)
                        )
                    )
                )
            }
        }

        return RouteData(coordinates = points, steps = steps)
    }

    private fun formatInstruction(type: String, modifier: String): String = when {
        type == "arrive" -> "Arrive at destination"
        type == "depart" -> "Start route"
        modifier == "right" -> "Turn right"
        modifier == "left" -> "Turn left"
        modifier == "slight right" -> "Slight right"
        modifier == "slight left" -> "Slight left"
        modifier == "sharp right" -> "Sharp right"
        modifier == "sharp left" -> "Sharp left"
        modifier == "straight" -> "Continue straight"
        modifier.isNotBlank() -> modifier.replaceFirstChar { it.uppercase() }
        else -> type.replaceFirstChar { it.uppercase() }
    }

    companion object {
        private const val BASE_URL = "https://router.project-osrm.org/route/v1/driving/"
    }
}
