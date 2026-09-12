package com.example.displayconnect.offline

import com.example.displayconnect.routing.*
import kotlinx.coroutines.*
import java.io.File

class OfflineMapRepository(root: File, private val source: StreetSource, private val pauseMs: Long = 1200) {
    private val disk = OfflineDiskStore(root)

    suspend fun findRoute(origin: LatLon, destination: LatLon, profile: RouteProfile): RouteData? =
        withContext(Dispatchers.IO) { disk.findRoute(origin, destination, profile) }

    suspend fun saveRoute(route: RouteData, destination: LatLon, profile: RouteProfile) =
        withContext(Dispatchers.IO) { disk.saveRoute(route, destination, profile) }

    suspend fun prepare(route: RouteData, scale: Double, onProgress: suspend (OfflineMapState) -> Unit) =
        withContext(Dispatchers.IO) {
            var state = OfflineMapState(downloading = true, radiusM = RouteCoverage.radiusMeters(scale).toInt())
            onProgress(state)
            try {
                val tiles = RouteCoverage.plan(route.coordinates, scale)
                disk.trim(tiles.toSet())
                val missing = tiles.filter { currentCoroutineContext().ensureActive(); disk.load(it) == null }
                state = state.copy(total = tiles.size, downloaded = tiles.size - missing.size)
                onProgress(state)
                for ((index, batch) in missing.chunked(4).withIndex()) {
                    currentCoroutineContext().ensureActive()
                    if (index > 0) delay(pauseMs)
                    val ways = source.fetch(batch.map { it.bounds() })
                    currentCoroutineContext().ensureActive()
                    for (tile in batch) {
                        disk.save(tile, ways.filter { it.bounds.intersects(tile.bounds()) })
                        state = state.copy(downloaded = state.downloaded + 1)
                        onProgress(state)
                    }
                }
                state = state.copy(downloading = false)
                onProgress(state)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                onProgress(state.copy(downloading = false,
                    error = e.message ?: "Falha ao baixar as ruas. Confira a internet e tente novamente."))
            }
        }

    /** No network here. Only read saved tiles needed to cover this complete viewport. */
    suspend fun visibleWays(center: LatLon, scale: Double): Pair<List<List<LatLon>>, Boolean> =
        withContext(Dispatchers.IO) {
            val tiles = RouteCoverage.tiles(RouteCoverage.around(center, RouteCoverage.radiusMeters(scale)))
            val ways = linkedMapOf<Long, StreetWay>()
            var complete = true
            for (tile in tiles) {
                currentCoroutineContext().ensureActive()
                val cached = disk.load(tile)
                if (cached == null) complete = false else cached.forEach { ways[it.id] = it }
            }
            ways.values.map { it.points } to complete
        }
}
