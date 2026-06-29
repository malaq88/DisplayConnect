package com.example.displayconnect.models

import com.example.displayconnect.routing.PlaceSearchResult
import com.example.displayconnect.routing.RouteProfile

data class MainUiState(
    val espIp: String = "192.168.4.1",
    val espPort: String = "81",
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
