package com.example.displayconnect.utils

import com.example.displayconnect.models.TransmissionStats
import com.example.displayconnect.routing.TripProgress
import com.example.displayconnect.offline.OfflineMapState
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object TransmissionHub {
    private val _gps = MutableStateFlow(com.example.displayconnect.navigation.GpsStatus())
    val gps = _gps.asStateFlow()
    fun updateGps(state: com.example.displayconnect.navigation.GpsStatus) { _gps.value = state }
    private val _offlineMap = MutableStateFlow(OfflineMapState())
    val offlineMap: StateFlow<OfflineMapState> = _offlineMap.asStateFlow()
    fun updateOfflineMap(state: OfflineMapState) {
        _offlineMap.update { state.copy(outsideCoverage = it.outsideCoverage) }
    }
    fun updateMapCoverage(complete: Boolean) { _offlineMap.update { it.copy(outsideCoverage = !complete) } }
    private val _navigationError = MutableStateFlow<String?>(null)
    val navigationError: StateFlow<String?> = _navigationError.asStateFlow()
    fun navigationFailed(message: String) { _navigationError.value = message }
    fun clearNavigationError() { _navigationError.value = null }
    private val _tripProgress = MutableStateFlow(TripProgress())
    val tripProgress: StateFlow<TripProgress> = _tripProgress.asStateFlow()

    fun updateTripProgress(progress: TripProgress) { _tripProgress.value = progress }

    private val _isNavigating = MutableStateFlow(false)
    val isNavigating: StateFlow<Boolean> = _isNavigating.asStateFlow()

    private val _stats = MutableStateFlow(TransmissionStats())
    val stats: StateFlow<TransmissionStats> = _stats.asStateFlow()

    fun setNavigating(active: Boolean) {
        _offlineMap.value = OfflineMapState()
        _gps.value = com.example.displayconnect.navigation.GpsStatus()
        _tripProgress.value = TripProgress()
        _isNavigating.value = active
        if (!active) {
            _stats.value = TransmissionStats()
        }
    }

    fun updateStats(stats: TransmissionStats) {
        _stats.value = stats
    }
}
