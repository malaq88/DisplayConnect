package com.example.displayconnect.navigation

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class LocationUpdate(
    val lat: Double,
    val lon: Double,
    val bearing: Float,
    val speedMps: Float,
    val accuracyM: Float = Float.POSITIVE_INFINITY,
    val elapsedMs: Long = 0
)

class LocationTracker(context: Context) {

    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    fun locationFlow(intervalMs: Long): Flow<LocationUpdate> = callbackFlow {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs)
            .setMaxUpdateAgeMillis(0)
            .setMaxUpdateDelayMillis(0)
            .setWaitForAccurateLocation(true)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.locations.maxByOrNull { it.elapsedRealtimeNanos } ?: return
                trySend(
                    LocationUpdate(
                        lat = location.latitude,
                        lon = location.longitude,
                        bearing = if (location.hasBearing()) location.bearing else Float.NaN,
                        speedMps = if (location.hasSpeed()) location.speed else 0f,
                        accuracyM = if (location.hasAccuracy()) location.accuracy else Float.POSITIVE_INFINITY,
                        elapsedMs = location.elapsedRealtimeNanos / 1_000_000
                    )
                )
            }
        }

        fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
            .addOnFailureListener { close(it) }
        awaitClose { fusedClient.removeLocationUpdates(callback) }
    }
}
