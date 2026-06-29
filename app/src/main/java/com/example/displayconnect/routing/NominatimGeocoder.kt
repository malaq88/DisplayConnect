package com.example.displayconnect.routing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

/**
 * Forward geocoding via OpenStreetMap Nominatim (no API key).
 * Use sparingly — max 1 request per user action (see OSM usage policy).
 */
class NominatimGeocoder(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
) {

    suspend fun search(query: String, limit: Int = 5): Result<List<PlaceSearchResult>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val trimmed = query.trim()
                if (trimmed.length < 3) {
                    error("Query too short")
                }

                val url = BASE_URL.toHttpUrl().newBuilder()
                    .addQueryParameter("q", trimmed)
                    .addQueryParameter("format", "json")
                    .addQueryParameter("limit", limit.coerceIn(1, 10).toString())
                    .addQueryParameter("addressdetails", "0")
                    .build()

                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .get()
                    .build()

                val body = client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        error("Geocoding HTTP ${response.code}")
                    }
                    response.body?.string() ?: error("Empty geocoding response")
                }

                parseResults(body)
            }
        }

    private fun parseResults(json: String): List<PlaceSearchResult> {
        val array = JSONArray(json)
        if (array.length() == 0) {
            error("No places found")
        }
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(
                    PlaceSearchResult(
                        displayName = item.getString("display_name"),
                        lat = item.getString("lat").toDouble(),
                        lon = item.getString("lon").toDouble()
                    )
                )
            }
        }
    }

    companion object {
        private const val BASE_URL = "https://nominatim.openstreetmap.org/search"
        private const val USER_AGENT = "DisplayConnect/2.0 (Android navigation app)"
    }
}
