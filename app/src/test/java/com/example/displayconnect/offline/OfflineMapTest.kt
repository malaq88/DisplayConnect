package com.example.displayconnect.offline

import com.example.displayconnect.routing.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class OfflineMapTest {
    @get:Rule val temp = TemporaryFolder()
    private val points = listOf(LatLon(-23.55, -46.65), LatLon(-23.55, -46.55))
    private fun route() = RouteData(points, emptyList(), 10000.0, 1000.0)

    @Test fun coverageIncludesCornersAlongLongSegmentsAndTurns() {
        val route = points + LatLon(-23.50, -46.55)
        val planned = RouteCoverage.plan(route, 400.0).toSet()
        assertTrue(RouteCoverage.radiusMeters(400.0) > 600)
        for ((a, b) in route.zipWithNext()) for (i in 0..100) {
            val t = i / 100.0
            val center = LatLon(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)
            assertTrue(planned.containsAll(RouteCoverage.tiles(RouteCoverage.around(center, RouteCoverage.radiusMeters(400.0)))))
        }
    }

    @Test fun largerScaleIncreasesCoverageAndRepeatedRouteDoesNotDuplicateTiles() {
        val small = RouteCoverage.plan(points, 150.0)
        val big = RouteCoverage.plan(points, 800.0)
        assertTrue(big.size >= small.size)
        assertEquals(small.toSet(), RouteCoverage.plan(points + points.reversed(), 150.0).toSet())
        assertEquals(big.size, big.distinct().size)
    }

    @Test fun downloadPersistsAndSecondInstanceNeedsNoNetworkEvenAtFarEnd() = runBlocking {
        val folder = temp.newFolder()
        var calls = 0
        val source = StreetSource { bounds ->
            calls++
            bounds.mapIndexed { index, b ->
                StreetWay(calls * 100L + index, listOf(LatLon(b.south, b.west), LatLon(b.north, b.east)))
            }
        }
        val first = OfflineMapRepository(folder, source, 0)
        var progress = OfflineMapState()
        first.prepare(route(), 400.0) { progress = it }
        assertTrue(progress.toString(), progress.ready)
        val disconnected = OfflineMapRepository(folder, StreetSource { error("Network must not be used") }, 0)
        disconnected.prepare(route(), 400.0) { progress = it }
        assertTrue(progress.toString(), progress.ready)
        val (ways, complete) = disconnected.visibleWays(points.last(), 400.0)
        assertTrue(complete)
        assertTrue(ways.isNotEmpty())
    }

    @Test fun partialDownloadIsNotReadyAndRetryFetchesOnlyMissingAreas() = runBlocking {
        val folder = temp.newFolder()
        var calls = 0
        var state = OfflineMapState()
        OfflineMapRepository(folder, StreetSource {
            calls++
            if (calls == 1) emptyList() else throw Exception("Sem internet")
        }, 0).prepare(route(), 400.0) { state = it }
        assertFalse(state.ready)
        assertEquals(state.toString(), 4, state.downloaded)
        var requested = 0
        OfflineMapRepository(folder, StreetSource { requested += it.size; emptyList() }, 0)
            .prepare(route(), 400.0) { state = it }
        assertTrue(state.ready)
        assertEquals(state.total - 4, requested)
    }

    @Test fun cancelledDownloadNeverClaimsCompletion() = runBlocking {
        val repository = OfflineMapRepository(temp.newFolder(), StreetSource { throw CancellationException() }, 0)
        var state = OfflineMapState()
        try { repository.prepare(route(), 400.0) { state = it }; fail("Expected cancellation") }
        catch (_: CancellationException) { assertFalse(state.ready) }
    }

    @Test fun corruptTileIsMissingAndCanBeReplaced() {
        val folder = temp.newFolder()
        val tile = RouteCoverage.plan(points, 400.0).first()
        OfflineDiskStore(folder).save(tile, emptyList())
        File(folder, tile.key + ".gz").writeBytes(byteArrayOf(1, 2, 3))
        val reloaded = OfflineDiskStore(folder)
        assertNull(reloaded.load(tile))
        reloaded.save(tile, emptyList())
        assertEquals(emptyList<StreetWay>(), OfflineDiskStore(folder).load(tile))
    }

    @Test fun savedRouteResumesOfflineButNotForADifferentProfileOrDestination() {
        val folder = temp.newFolder()
        OfflineDiskStore(folder).saveRoute(route(), points.last(), RouteProfile.BIKE)
        val reloaded = OfflineDiskStore(folder)
        assertEquals(route(), reloaded.findRoute(points.first(), points.last(), RouteProfile.BIKE))
        assertNull(reloaded.findRoute(points.first(), points.last(), RouteProfile.CAR))
        assertNull(reloaded.findRoute(points.first(), LatLon(-22.0, -46.0), RouteProfile.BIKE))
        assertNull(reloaded.findRoute(LatLon(-22.0, -46.0), points.last(), RouteProfile.BIKE))
    }

    @Test fun bikeAndFootUseTheirOwnRoutingGraphs() {
        assertTrue(RouteProfile.BIKE.routeBaseUrl.contains("/routed-bike/"))
        assertTrue(RouteProfile.WALKING.routeBaseUrl.contains("/routed-foot/"))
        assertTrue(RouteProfile.CAR.routeBaseUrl.contains("/routed-car/"))
        assertNotEquals(RouteProfile.CAR.routeBaseUrl, RouteProfile.BIKE.routeBaseUrl)
    }
}
