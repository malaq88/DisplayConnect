package com.example.displayconnect.models

data class MainUiState(
    val espIp: String = "192.168.4.1",
    val espPort: String = "81",
    val destLat: String = "",
    val destLon: String = "",
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val isNavigating: Boolean = false,
    val stats: TransmissionStats = TransmissionStats(),
    val errorMessage: String? = null
)
