package com.example.displayconnect.navigation

import com.example.displayconnect.map.MapProjector
import com.example.displayconnect.maps.MapsHtmlHolder
import com.example.displayconnect.models.AppSettings
import com.example.displayconnect.network.DisplaySocketClient
import com.example.displayconnect.protocol.NavMessage
import com.example.displayconnect.routing.LatLon
import com.example.displayconnect.routing.OsrmRouteProvider
import com.example.displayconnect.routing.RouteData
import com.example.displayconnect.routing.RouteStep
import com.example.displayconnect.utils.StatsTracker
import com.example.displayconnect.utils.TransmissionHub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class NavigationEngine(
    private val scope: CoroutineScope,
    private val locationTracker: LocationTracker,
    private val routeProvider: OsrmRouteProvider,
    private val socketClient: DisplaySocketClient,
    private val settingsProvider: suspend () -> AppSettings
) {

    private var route: RouteData? = null
    private var destination: LatLon? = null
    private var locationJob: Job? = null
    private val statsTracker = StatsTracker()

    fun startNavigation(destLat: Double, destLon: Double) {
        destination = LatLon(destLat, destLon)
        TransmissionHub.setNavigating(true)
        statsTracker.reset()

        locationJob?.cancel()
        locationJob = scope.launch {
            val settings = settingsProvider()
            val intervalMs = (1000L / settings.navUpdateHz.coerceIn(1, 5))

            val originUpdate = locationTracker.locationFlow(intervalMs).first()
            val dest = destination ?: return@launch

            routeProvider.fetchRoute(
                LatLon(originUpdate.lat, originUpdate.lon),
                dest
            ).onSuccess { route = it }
                .onFailure { TransmissionHub.setNavigating(false) }

            locationTracker.locationFlow(intervalMs).collect { update ->
                publishUpdate(update, settings)
            }
        }
    }

    fun stopNavigation() {
        locationJob?.cancel()
        locationJob = null
        route = null
        destination = null
        TransmissionHub.setNavigating(false)
        statsTracker.reset()
    }

    private suspend fun publishUpdate(update: LocationUpdate, settings: AppSettings) {
        val currentRoute = route ?: return
        if (socketClient.connectionState.value != com.example.displayconnect.models.ConnectionState.CONNECTED) {
            return
        }

        val step = findCurrentStep(update, currentRoute.steps)
        val distanceM = step?.let {
            haversineMeters(update.lat, update.lon, it.endLocation.lat, it.endLocation.lon).toInt()
        } ?: 0
        val simplified = MapProjector.simplifyRoute(
            currentRoute.coordinates.map { it.lat to it.lon }
        )
        val screenRoute = MapProjector.projectRoute(
            centerLat = update.lat,
            centerLon = update.lon,
            routePoints = simplified,
            scaleMeters = settings.mapScaleMeters
        )
        val userPoint = MapProjector.projectPoint(
            centerLat = update.lat,
            centerLon = update.lon,
            lat = update.lat,
            lon = update.lon,
            scaleMeters = settings.mapScaleMeters
        )

        val bearing = if (update.bearing > 0f) {
            update.bearing
        } else {
            computeBearingFromRoute(update, simplified)
        }

        val message = NavMessage(
            lat = update.lat,
            lon = update.lon,
            bearing = bearing,
            instruction = step?.instruction ?: "Continue",
            distanceM = distanceM,
            street = step?.street ?: "",
            routeScreenPoints = screenRoute,
            userScreenPoint = userPoint,
            html = MapsHtmlHolder.get()
        )

        val json = message.toJson()
        if (socketClient.sendNavMessage(json)) {
            statsTracker.onNavUpdate(json.toByteArray().size)
            TransmissionHub.updateStats(
                statsTracker.currentStats(settings.resolutionLabel)
            )
        }
    }

    private fun findCurrentStep(update: LocationUpdate, steps: List<RouteStep>): RouteStep? {
        if (steps.isEmpty()) return null
        var closest = steps.first()
        var minDist = Double.MAX_VALUE
        for (step in steps) {
            val d = haversineMeters(update.lat, update.lon, step.endLocation.lat, step.endLocation.lon)
            if (d < minDist) {
                minDist = d
                closest = step
            }
        }
        return closest
    }

    private fun computeBearingFromRoute(
        update: LocationUpdate,
        routePoints: List<Pair<Double, Double>>
    ): Float {
        if (routePoints.size < 2) return update.bearing
        var bestIndex = 0
        var minDist = Double.MAX_VALUE
        routePoints.forEachIndexed { index, (lat, lon) ->
            val d = haversineMeters(update.lat, update.lon, lat, lon)
            if (d < minDist) {
                minDist = d
                bestIndex = index
            }
        }
        val nextIndex = (bestIndex + 1).coerceAtMost(routePoints.lastIndex)
        val (lat1, lon1) = routePoints[bestIndex]
        val (lat2, lon2) = routePoints[nextIndex]
        return bearingDegrees(lat1, lon1, lat2, lon2)
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val dLon = Math.toRadians(lon2 - lon1)
        val y = sin(dLon) * cos(Math.toRadians(lat2))
        val x = cos(Math.toRadians(lat1)) * sin(Math.toRadians(lat2)) -
            sin(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * cos(dLon)
        return ((Math.toDegrees(atan2(y, x)) + 360) % 360).toFloat()
    }
}
