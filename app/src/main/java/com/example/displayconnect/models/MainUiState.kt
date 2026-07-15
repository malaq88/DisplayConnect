package com.example.displayconnect.models

import com.example.displayconnect.network.BleDeviceItem
import com.example.displayconnect.routing.PlaceSearchResult
import com.example.displayconnect.routing.RouteProfile

data class MainUiState(
    val bleDeviceAddress: String = "",
    val bleDeviceName: String = "",
    val scannedDevices: List<BleDeviceItem> = emptyList(),
    val isScanning: Boolean = false,
    val destQuery: String = "",
    val destLabel: String = "",
    val destLat: String = "",
    val destLon: String = "",
    val routeProfile: RouteProfile = RouteProfile.CAR,
    val searchResults: List<PlaceSearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val isNavigating: Boolean = false,
    val stats: TransmissionStats = TransmissionStats(),
    val errorMessage: String? = null
)
