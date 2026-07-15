package com.example.displayconnect.viewmodel

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.displayconnect.DisplayConnectApp
import com.example.displayconnect.R
import com.example.displayconnect.maps.MapsBrowserActivity
import com.example.displayconnect.models.MainUiState
import com.example.displayconnect.navigation.NavigationForegroundService
import com.example.displayconnect.network.BleDeviceItem
import com.example.displayconnect.routing.NominatimGeocoder
import com.example.displayconnect.routing.PlaceSearchResult
import com.example.displayconnect.routing.RouteProfile
import com.example.displayconnect.utils.TransmissionHub
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as DisplayConnectApp
    private val navClient = app.navClient
    private val settingsRepository = app.settingsRepository
    private val geocoder = NominatimGeocoder()

    private var pendingMapsBrowser = false
    private var pendingBleAction: (() -> Unit)? = null

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                settingsRepository.settings,
                navClient.connectionState,
                TransmissionHub.isNavigating,
                TransmissionHub.stats
            ) { settings, connection, navigating, stats ->
                MainUiState(
                    bleDeviceAddress = settings.bleDeviceAddress,
                    bleDeviceName = settings.bleDeviceName,
                    destQuery = settings.destQuery,
                    destLabel = settings.destLabel,
                    destLat = settings.destLat,
                    destLon = settings.destLon,
                    routeProfile = settings.routeProfile,
                    connectionState = connection,
                    isNavigating = navigating,
                    stats = stats.copy(resolution = settings.resolutionLabel)
                )
            }.collect { fromSettings ->
                _uiState.update { current ->
                    fromSettings.copy(
                        scannedDevices = current.scannedDevices,
                        isScanning = current.isScanning,
                        searchResults = current.searchResults,
                        isSearching = current.isSearching
                    )
                }
            }
        }

        viewModelScope.launch {
            navClient.scannedDevices.collect { devices ->
                _uiState.update { it.copy(scannedDevices = devices) }
            }
        }

        viewModelScope.launch {
            navClient.isScanning.collect { scanning ->
                _uiState.update { it.copy(isScanning = scanning) }
            }
        }
    }

    fun updateDestQuery(value: String) {
        _uiState.update { it.copy(destQuery = value) }
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(destQuery = value) }
        }
    }

    fun updateDestLat(value: String) {
        _uiState.update { it.copy(destLat = value) }
        persistDestinationCoords(value, _uiState.value.destLon)
    }

    fun updateDestLon(value: String) {
        _uiState.update { it.copy(destLon = value) }
        persistDestinationCoords(_uiState.value.destLat, value)
    }

    fun updateRouteProfile(profile: RouteProfile) {
        _uiState.update { it.copy(routeProfile = profile) }
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(routeProfile = profile) }
        }
    }

    fun searchDestination() {
        val query = _uiState.value.destQuery.trim()
        if (query.length < 3) {
            _uiState.update {
                it.copy(errorMessage = getApplication<DisplayConnectApp>().getString(R.string.error_search_too_short))
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, searchResults = emptyList()) }
            geocoder.search(query)
                .onSuccess { results ->
                    _uiState.update { it.copy(searchResults = results, isSearching = false) }
                }
                .onFailure { error ->
                    val appCtx = getApplication<DisplayConnectApp>()
                    val message = when (error.message) {
                        "No places found" -> appCtx.getString(R.string.error_place_not_found)
                        "Query too short" -> appCtx.getString(R.string.error_search_too_short)
                        else -> error.message ?: appCtx.getString(R.string.error_search_failed)
                    }
                    _uiState.update {
                        it.copy(
                            isSearching = false,
                            searchResults = emptyList(),
                            errorMessage = message
                        )
                    }
                }
        }
    }

    fun selectSearchResult(result: PlaceSearchResult) {
        val lat = result.lat.toString()
        val lon = result.lon.toString()
        val shortLabel = result.displayName.lineSequence().first().take(80)

        _uiState.update {
            it.copy(
                destLabel = shortLabel,
                destLat = lat,
                destLon = lon,
                searchResults = emptyList()
            )
        }

        viewModelScope.launch {
            settingsRepository.updateSettings {
                it.copy(
                    destQuery = _uiState.value.destQuery,
                    destLabel = shortLabel,
                    destLat = lat,
                    destLon = lon
                )
            }
        }
    }

    private fun persistDestinationCoords(lat: String, lon: String) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(destLat = lat, destLon = lon) }
        }
    }

    fun startBleScan(onNeedBluetoothPermission: () -> Unit) {
        runWithBluetoothPermission(onNeedBluetoothPermission) {
            if (!navClient.isBluetoothEnabled()) {
                _uiState.update {
                    it.copy(errorMessage = getApplication<DisplayConnectApp>().getString(R.string.error_bluetooth_disabled))
                }
                return@runWithBluetoothPermission
            }
            navClient.startScan()
        }
    }

    fun stopBleScan() {
        navClient.stopScan()
    }

    fun connectToDevice(device: BleDeviceItem, onNeedBluetoothPermission: () -> Unit) {
        runWithBluetoothPermission(onNeedBluetoothPermission) {
            viewModelScope.launch {
                settingsRepository.updateBleDevice(device.address, device.name)
                _uiState.update {
                    it.copy(bleDeviceAddress = device.address, bleDeviceName = device.name)
                }
                navClient.stopScan()
                navClient.connect(device.address, device.name)
            }
        }
    }

    fun connectSavedDevice(onNeedBluetoothPermission: () -> Unit) {
        val address = _uiState.value.bleDeviceAddress
        if (address.isBlank()) {
            _uiState.update {
                it.copy(errorMessage = getApplication<DisplayConnectApp>().getString(R.string.error_no_ble_device))
            }
            return
        }
        runWithBluetoothPermission(onNeedBluetoothPermission) {
            if (!navClient.isBluetoothEnabled()) {
                _uiState.update {
                    it.copy(errorMessage = getApplication<DisplayConnectApp>().getString(R.string.error_bluetooth_disabled))
                }
                return@runWithBluetoothPermission
            }
            navClient.connect(address, _uiState.value.bleDeviceName)
        }
    }

    fun disconnect() {
        stopNavigation()
        navClient.disconnect()
    }

    fun onBluetoothPermissionGranted() {
        pendingBleAction?.invoke()
        pendingBleAction = null
    }

    private fun runWithBluetoothPermission(onNeedPermission: () -> Unit, action: () -> Unit) {
        if (hasBluetoothPermissions()) {
            action()
        } else {
            pendingBleAction = action
            onNeedPermission()
        }
    }

    fun startNavigation(onNeedLocationPermission: () -> Unit) {
        if (!ensureConnectedAndDestination()) return
        val (lat, lon) = resolveDestination() ?: return

        if (hasLocationPermission()) {
            NavigationForegroundService.start(getApplication(), lat, lon)
        } else {
            onNeedLocationPermission()
        }
    }

    fun startMapsBrowserNavigation(context: Context, onNeedLocationPermission: () -> Unit) {
        if (!ensureConnectedAndDestination()) return
        val (lat, lon) = resolveDestination() ?: return

        if (hasLocationPermission()) {
            launchMapsBrowser(context, lat, lon)
        } else {
            pendingMapsBrowser = true
            onNeedLocationPermission()
        }
    }

    fun onLocationPermissionGranted() {
        val coords = resolveDestination() ?: return
        val (lat, lon) = coords
        if (pendingMapsBrowser) {
            pendingMapsBrowser = false
            launchMapsBrowser(getApplication(), lat, lon)
        } else {
            NavigationForegroundService.start(getApplication(), lat, lon)
        }
    }

    private fun ensureConnectedAndDestination(): Boolean {
        val state = _uiState.value
        if (state.connectionState != com.example.displayconnect.models.ConnectionState.CONNECTED) {
            _uiState.update { it.copy(errorMessage = getApplication<DisplayConnectApp>().getString(R.string.error_not_connected)) }
            return false
        }
        if (resolveDestination() == null) {
            _uiState.update { it.copy(errorMessage = getApplication<DisplayConnectApp>().getString(R.string.error_invalid_destination)) }
            return false
        }
        return true
    }

    private fun resolveDestination(): Pair<Double, Double>? {
        val state = _uiState.value
        val lat = state.destLat.toDoubleOrNull()
        val lon = state.destLon.toDoubleOrNull()
        if (lat == null || lon == null || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
            return null
        }
        return lat to lon
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
        val appCtx = getApplication<Application>()
        return ContextCompat.checkSelfPermission(appCtx, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(appCtx, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun hasBluetoothPermissions(): Boolean {
        val appCtx = getApplication<Application>()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(appCtx, Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(appCtx, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            hasLocationPermission()
        }
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
