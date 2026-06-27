package com.example.displayconnect.models

/**
 * Estado da conexão WebSocket com a ESP32.
 */
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    ERROR
}
