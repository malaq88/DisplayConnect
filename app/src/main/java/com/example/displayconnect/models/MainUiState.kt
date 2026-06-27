package com.example.displayconnect.models

/**
 * Estado agregado da tela principal.
 */
data class MainUiState(
    val espIp: String = "192.168.4.1",
    val espPort: String = "81",
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val isTransmitting: Boolean = false,
    val stats: TransmissionStats = TransmissionStats(),
    val errorMessage: String? = null
)
