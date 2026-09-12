package com.example.displayconnect.routing

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Searches only on explicit submission, with caching and a global request interval. */
class NominatimGeocoder(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS).build(),
    private val nominatimUrl: String = "https://nominatim.openstreetmap.org/search",
    private val photonUrl: String = "https://photon.komoot.io/api/"
) {
    suspend fun search(query: String, city: String = "", limit: Int = 10): Result<List<PlaceSearchResult>> =
        withContext(Dispatchers.IO) {
            try {
                require(query.trim().length >= 3) { "Query too short" }
                val fullQuery = AddressQuery.withCity(query, city)
                val maxResults = limit.coerceIn(1, 15)
                val key = "$nominatimUrl|$photonUrl|$fullQuery|$maxResults".lowercase()
                val results = mutex.withLock {
                    cache[key]?.let { return@withLock it }
                    val number = AddressQuery.requestedHouseNumber(query)
                    var failure: Exception? = null
                    suspend fun attempt(block: suspend () -> List<PlaceSearchResult>): List<PlaceSearchResult> =
                        try { block() } catch (e: CancellationException) { throw e }
                        catch (e: Exception) { failure = e; emptyList() }
                    val primary = attempt { nominatim(fullQuery, maxResults, number) }
                    val extra = if (primary.size < maxResults) attempt {
                        photon(fullQuery, maxResults, number)
                    } else emptyList()
                    var merged = (primary + extra).distinctBy {
                        Triple(it.displayName.substringBefore(',').lowercase(),
                            Math.round(it.lat * 10000), Math.round(it.lon * 10000))
                    }
                    val relaxed = AddressQuery.withCity(AddressQuery.withoutHouseNumber(query), city)
                    if (merged.isEmpty() && number != null && relaxed != fullQuery) {
                        merged = attempt { nominatim(relaxed, maxResults, number) }
                            .map { it.copy(houseNumberUnconfirmed = true) }
                    }
                    if (merged.isEmpty()) {
                        if (failure != null) throw IllegalStateException("Search services unavailable", failure)
                        error("No places found")
                    }
                    merged.take(maxResults).also {
                        cache[key] = it
                        if (cache.size > 50) cache.remove(cache.keys.first())
                    }
                }
                Result.success(results)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { Result.failure(e) }
        }

    private suspend fun nominatim(query: String, limit: Int, number: String?): List<PlaceSearchResult> {
        val elapsed = (System.nanoTime() - lastRequestNs) / 1_000_000
        if (elapsed < 1100) delay(1100 - elapsed)
        currentCoroutineContext().ensureActive()
        lastRequestNs = System.nanoTime()
        val url = nominatimUrl.toHttpUrl().newBuilder()
            .addQueryParameter("q", query).addQueryParameter("format", "jsonv2")
            .addQueryParameter("limit", limit.toString()).addQueryParameter("addressdetails", "1")
            .addQueryParameter("accept-language", "pt-BR,pt,en").build()
        val array = JSONArray(request(url))
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val house = item.optJSONObject("address")?.optString("house_number", "").orEmpty()
                val lat = item.optString("lat").toDoubleOrNull() ?: continue
                val lon = item.optString("lon").toDoubleOrNull() ?: continue
                if (lat !in -90.0..90.0 || lon !in -180.0..180.0) continue
                add(PlaceSearchResult(item.getString("display_name"), lat, lon,
                    number != null && !house.equals(number, true)))
            }
        }
    }

    private suspend fun photon(query: String, limit: Int, number: String?): List<PlaceSearchResult> {
        val url = photonUrl.toHttpUrl().newBuilder()
            .addQueryParameter("q", query).addQueryParameter("limit", limit.toString()).build()
        val features = JSONObject(request(url)).getJSONArray("features")
        return buildList {
            for (i in 0 until features.length()) {
                val feature = features.getJSONObject(i)
                val coordinates = feature.optJSONObject("geometry")?.optJSONArray("coordinates") ?: continue
                if (coordinates.length() < 2) continue
                val props = feature.getJSONObject("properties")
                val house = props.optString("housenumber", "")
                val parts = listOf("name", "street", "housenumber", "district", "city", "state", "postcode", "country")
                    .map { props.optString(it, "") }.filter { it.isNotBlank() }.distinct()
                if (parts.isEmpty()) continue
                val lat = coordinates.getDouble(1)
                val lon = coordinates.getDouble(0)
                if (lat !in -90.0..90.0 || lon !in -180.0..180.0) continue
                add(PlaceSearchResult(parts.joinToString(", "), lat, lon,
                    number != null && !house.equals(number, true), "OpenStreetMap / Photon"))
            }
        }
    }

    private suspend fun request(url: HttpUrl): String {
        currentCoroutineContext().ensureActive()
        val request = Request.Builder().url(url)
            .header("User-Agent", "DisplayConnect-CYD/2.1 (personal Android navigation)")
            .header("Accept", "application/json").build()
        val body = client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Geocoding HTTP " + response.code }
            response.body?.string() ?: error("Empty geocoding response")
        }
        currentCoroutineContext().ensureActive()
        return body
    }

    companion object {
        private val mutex = Mutex()
        private var lastRequestNs = 0L
        private val cache = LinkedHashMap<String, List<PlaceSearchResult>>()
    }
}
