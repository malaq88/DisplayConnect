package com.example.displayconnect.network

import com.example.displayconnect.models.ConnectionState
import com.example.displayconnect.protocol.NavMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * WebSocket client for JSON navigation updates to the ESP32.
 */
class DisplaySocketClient {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .pingInterval(HEARTBEAT_INTERVAL_SEC, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var heartbeatJob: Job? = null
    private val shouldReconnect = AtomicBoolean(false)
    private var currentHost = ""
    private var currentPort = 0

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    fun connect(host: String, port: Int) {
        disconnect(manual = false)
        currentHost = host
        currentPort = port
        shouldReconnect.set(true)
        openSocket()
    }

    fun disconnect(manual: Boolean = true) {
        if (manual) {
            shouldReconnect.set(false)
        }
        reconnectJob?.cancel()
        heartbeatJob?.cancel()
        webSocket?.close(NORMAL_CLOSE_CODE, "Client disconnect")
        webSocket = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    fun sendNavMessage(json: String): Boolean {
        val socket = webSocket ?: return false
        if (_connectionState.value != ConnectionState.CONNECTED) return false
        return socket.send(json)
    }

    fun release() {
        disconnect(manual = true)
        scope.cancel()
        client.dispatcher.executorService.shutdown()
    }

    private fun openSocket() {
        if (currentHost.isBlank()) return
        _connectionState.value = ConnectionState.CONNECTING

        val url = "ws://$currentHost:$currentPort"
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, socketListener)
    }

    private fun scheduleReconnect() {
        if (!shouldReconnect.get()) return
        reconnectJob?.cancel()
        _connectionState.value = ConnectionState.RECONNECTING
        reconnectJob = scope.launch {
            delay(RECONNECT_DELAY_MS)
            if (shouldReconnect.get() && isActive) {
                openSocket()
            }
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive && _connectionState.value == ConnectionState.CONNECTED) {
                delay(HEARTBEAT_INTERVAL_SEC * 1000)
                webSocket?.send(NavMessage.heartbeat())
            }
        }
    }

    private val socketListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            _connectionState.value = ConnectionState.CONNECTED
            startHeartbeat()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            // ESP may reply with "OK"
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, reason)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            heartbeatJob?.cancel()
            if (shouldReconnect.get()) {
                scheduleReconnect()
            } else {
                _connectionState.value = ConnectionState.DISCONNECTED
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            heartbeatJob?.cancel()
            _connectionState.value = ConnectionState.ERROR
            if (shouldReconnect.get()) {
                scheduleReconnect()
            }
        }
    }

    companion object {
        private const val HEARTBEAT_INTERVAL_SEC = 15L
        private const val RECONNECT_DELAY_MS = 3000L
        private const val NORMAL_CLOSE_CODE = 1000
    }
}
