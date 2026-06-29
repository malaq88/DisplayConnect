package com.example.displayconnect.routing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.cos

/**
 * Fetches nearby road geometry from OpenStreetMap via Overpass (no API key).
 */
class OverpassStreetProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()
) {

    suspend fun fetchStreetWays(
        centerLat: Double,
        centerLon: Double,
        radiusMeters: Double,
        sampleRatio: Double = STREET_SAMPLE_RATIO
    ): Result<List<List<LatLon>>> = withContext(Dispatchers.IO) {
        runCatching {
            val latDelta = radiusMeters / METERS_PER_DEG_LAT
            val lonDelta = radiusMeters / (METERS_PER_DEG_LAT * cos(Math.toRadians(centerLat)))
            val south = centerLat - latDelta
            val north = centerLat + latDelta
            val west = centerLon - lonDelta
            val east = centerLon + lonDelta

            val query = buildQuery(south, west, north, east)
            val request = Request.Builder()
                .url(OVERPASS_URL)
                .header("User-Agent", USER_AGENT)
                .post(query.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
                .build()

            val body = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Overpass HTTP ${response.code}")
                }
                response.body?.string() ?: error("Empty Overpass response")
            }

            sampleWays(parseWays(body), sampleRatio)
        }
    }

    private fun buildQuery(south: Double, west: Double, north: Double, east: Double): String {
        val bbox = "$south,$west,$north,$east"
        val overpass = """
            [out:json][timeout:12];
            (
              way["highway"~"^(motorway|trunk|primary|secondary|tertiary|residential|unclassified|living_street|service)$"]($bbox);
            );
            out geom;
        """.trimIndent()
        return "data=${java.net.URLEncoder.encode(overpass, Charsets.UTF_8.name())}"
    }

    private fun parseWays(json: String): List<StreetWay> {
        val elements = JSONObject(json).getJSONArray("elements")
        return buildList {
            for (i in 0 until elements.length()) {
                val element = elements.getJSONObject(i)
                if (element.optString("type") != "way") continue
                val geometry = element.optJSONArray("geometry") ?: continue
                if (geometry.length() < 2) continue
                val points = buildList {
                    for (g in 0 until geometry.length()) {
                        val node = geometry.getJSONObject(g)
                        add(LatLon(node.getDouble("lat"), node.getDouble("lon")))
                    }
                }
                if (points.size >= 2) {
                    val tags = element.optJSONObject("tags")
                    val highway = tags?.optString("highway", "unclassified") ?: "unclassified"
                    add(StreetWay(highway, points))
                }
            }
        }
    }

    private fun sampleWays(ways: List<StreetWay>, ratio: Double): List<List<LatLon>> {
        if (ways.isEmpty()) return emptyList()
        val clampedRatio = ratio.coerceIn(0.2, 1.0)
        val (major, minor) = ways.partition { it.highway in MAJOR_HIGHWAYS }
        val minorKeep = if (clampedRatio >= 0.99) {
            minor
        } else {
            val step = (1.0 / clampedRatio).toInt().coerceAtLeast(2)
            minor.filterIndexed { index, _ -> index % step == 0 }
        }
        return (major + minorKeep).map { it.points }
    }

    private data class StreetWay(val highway: String, val points: List<LatLon>)

    companion object {
        private const val OVERPASS_URL = "https://overpass-api.de/api/interpreter"
        private const val USER_AGENT = "DisplayConnect/2.0 (Android navigation app)"
        private const val METERS_PER_DEG_LAT = 111_320.0
        const val STREET_SAMPLE_RATIO = 0.55

        private val MAJOR_HIGHWAYS = setOf(
            "motorway", "trunk", "primary", "secondary", "tertiary"
        )
    }
}
