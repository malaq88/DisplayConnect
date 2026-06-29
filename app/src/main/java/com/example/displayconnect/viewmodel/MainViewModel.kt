package com.example.displayconnect.viewmodel

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.displayconnect.DisplayConnectApp
import com.example.displayconnect.R
import com.example.displayconnect.maps.MapsBrowserActivity
import com.example.displayconnect.models.MainUiState
import com.example.displayconnect.navigation.NavigationForegroundService
import com.example.displayconnect.utils.TransmissionHub
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as DisplayConnectApp
    private val socketClient = app.socketClient
    private val settingsRepository = app.settingsRepository

    private var pendingMapsBrowser = false

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                settingsRepository.settings,
                socketClient.connectionState,
                TransmissionHub.isNavigating,
                TransmissionHub.stats
            ) { settings, connection, navigating, stats ->
                MainUiState(
                    espIp = settings.espIp,
                    espPort = settings.espPort.toString(),
                    destLat = settings.destLat,
                    destLon = settings.destLon,
                    connectionState = connection,
                    isNavigating = navigating,
                    stats = stats.copy(resolution = settings.resolutionLabel)
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun updateIp(ip: String) {
        _uiState.update { it.copy(espIp = ip) }
    }

    fun updatePort(port: String) {
        _uiState.update { it.copy(espPort = port) }
    }

    fun updateDestLat(value: String) {
        _uiState.update { it.copy(destLat = value) }
        persistDestination(value, _uiState.value.destLon)
    }

    fun updateDestLon(value: String) {
        _uiState.update { it.copy(destLon = value) }
        persistDestination(_uiState.value.destLat, value)
    }

    private fun persistDestination(lat: String, lon: String) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(destLat = lat, destLon = lon) }
        }
    }

    fun connect() {
        val state = _uiState.value
        val port = state.espPort.toIntOrNull()
        if (state.espIp.isBlank() || port == null || port !in 1..65535) {
            _uiState.update {
                it.copy(errorMessage = getApplication<DisplayConnectApp>().getString(R.string.error_invalid_ip_port))
            }
            return
        }

        viewModelScope.launch {
            settingsRepository.updateEspConnection(state.espIp, port)
            socketClient.connect(state.espIp, port)
        }
    }

    fun disconnect() {
        stopNavigation()
        socketClient.disconnect()
    }

    fun startNavigation(onNeedLocationPermission: () -> Unit) {
        val state = _uiState.value
        if (state.connectionState != com.example.displayconnect.models.ConnectionState.CONNECTED) {
            _uiState.update { it.copy(errorMessage = getApplication<DisplayConnectApp>().getString(R.string.error_not_connected)) }
            return
        }

        val lat = state.destLat.toDoubleOrNull()
        val lon = state.destLon.toDoubleOrNull()
        if (lat == null || lon == null || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
            _uiState.update { it.copy(errorMessage = getApplication<DisplayConnectApp>().getString(R.string.error_invalid_destination)) }
            return
        }

        if (hasLocationPermission()) {
            NavigationForegroundService.start(getApplication(), lat, lon)
        } else {
            onNeedLocationPermission()
        }
    }

    fun startMapsBrowserNavigation(context: Context, onNeedLocationPermission: () -> Unit) {
        val state = _uiState.value
        if (state.connectionState != com.example.displayconnect.models.ConnectionState.CONNECTED) {
            _uiState.update { it.copy(errorMessage = getApplication<DisplayConnectApp>().getString(R.string.error_not_connected)) }
            return
        }

        val lat = state.destLat.toDoubleOrNull()
        val lon = state.destLon.toDoubleOrNull()
        if (lat == null || lon == null || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
            _uiState.update { it.copy(errorMessage = getApplication<DisplayConnectApp>().getString(R.string.error_invalid_destination)) }
            return
        }

        if (hasLocationPermission()) {
            launchMapsBrowser(context, lat, lon)
        } else {
            pendingMapsBrowser = true
            onNeedLocationPermission()
        }
    }

    fun onLocationPermissionGranted() {
        val state = _uiState.value
        val lat = state.destLat.toDoubleOrNull() ?: return
        val lon = state.destLon.toDoubleOrNull() ?: return
        if (pendingMapsBrowser) {
            pendingMapsBrowser = false
            launchMapsBrowser(getApplication(), lat, lon)
        } else {
            NavigationForegroundService.start(getApplication(), lat, lon)
        }
    }

    private fun launchMapsBrowser(context: Context, lat: Double, lon: Double) {
        NavigationForegroundService.start(context, lat, lon)
        val intent = Intent(context, MapsBrowserActivity::class.java).apply {
            putExtra(MapsBrowserActivity.EXTRA_DEST_LAT, lat)
            putExtra(MapsBrowserActivity.EXTRA_DEST_LON, lon)
            if (context !is android.app.Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(intent)
    }

    private fun hasLocationPermission(): Boolean {
        val app = getApplication<Application>()
        return ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun stopNavigation() {
        pendingMapsBrowser = false
        NavigationForegroundService.stop(getApplication())
        TransmissionHub.setNavigating(false)
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
