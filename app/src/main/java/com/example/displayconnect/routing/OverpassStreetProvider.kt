package com.example.displayconnect.routing

import com.example.displayconnect.offline.GeoBounds
import com.example.displayconnect.offline.StreetSource
import com.example.displayconnect.offline.StreetWay
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Small sequential batches, full geometries, no partial response accepted as offline coverage. */
class OverpassStreetProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(65, TimeUnit.SECONDS)
        .callTimeout(75, TimeUnit.SECONDS).build()
) : StreetSource {
    override suspend fun fetch(bounds: List<GeoBounds>): List<StreetWay> = withContext(Dispatchers.IO) {
        try {
            require(bounds.size in 1..4)
            val selectors = bounds.joinToString("\n") {
                "way[highway][highway!~\"^(proposed|construction|abandoned|razed)$\"](" +
                    it.south + "," + it.west + "," + it.north + "," + it.east + ");"
            }
            val query = "[out:json][timeout:45][maxsize:67108864];(" + selectors + ");out geom;"
            val request = Request.Builder().url("https://overpass-api.de/api/interpreter")
                .header("User-Agent", "DisplayConnect-CYD/2.1 (personal route offline map)")
                .post(FormBody.Builder().add("data", query).build()).build()
            val json = requestBody(request)
            currentCoroutineContext().ensureActive()
            val root = JSONObject(json)
            // Overpass may return HTTP 200 with partial results plus a timeout/size remark.
            check(root.optString("remark").isBlank()) { "Servidor de mapas ocupado. Tente novamente para concluir as áreas restantes." }
            val elements = root.getJSONArray("elements")
            var pointCount = 0
            val ways = buildList {
                for (i in 0 until elements.length()) {
                    currentCoroutineContext().ensureActive()
                    val element = elements.getJSONObject(i)
                    if (element.optString("type") != "way") continue
                    val geometry = element.optJSONArray("geometry") ?: continue
                    require(geometry.length() >= 2) { "Geometria de rua incompleta" }
                    pointCount += geometry.length()
                    check(pointCount <= 300000) { "Área com dados demais. Tente uma escala menor." }
                    val points = List(geometry.length()) { g ->
                        val point = geometry.getJSONObject(g)
                        LatLon(point.getDouble("lat"), point.getDouble("lon")).also {
                            require(it.lat in -90.0..90.0 && it.lon in -180.0..180.0)
                        }
                    }
                    add(StreetWay(element.getLong("id"), points))
                }
            }
            ways
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { throw e }
    }

    private suspend fun requestBody(request: Request): String = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.use {
                        check(it.isSuccessful) { "Falha no servidor de mapas (HTTP " + it.code + "). Aguarde e tente novamente." }
                        val stream = it.body?.byteStream() ?: error("Resposta vazia do mapa")
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = stream.read(buffer)
                            if (count < 0) break
                            check(output.size() + count <= 20 * 1024 * 1024) { "Resposta de mapa muito grande" }
                            output.write(buffer, 0, count)
                        }
                        output.toString("UTF-8")
                    }
                    if (continuation.isActive) continuation.resume(body)
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
            }
        })
    }
}
