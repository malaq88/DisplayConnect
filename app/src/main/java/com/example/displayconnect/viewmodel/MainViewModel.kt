package com.example.displayconnect.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.displayconnect.DisplayConnectApp
import com.example.displayconnect.R
import com.example.displayconnect.models.MainUiState
import com.example.displayconnect.utils.TransmissionHub
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel da tela principal: conexão, transmissão e estatísticas.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as DisplayConnectApp
    private val socketClient = app.socketClient
    private val settingsRepository = app.settingsRepository

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                settingsRepository.settings,
                socketClient.connectionState,
                TransmissionHub.isTransmitting,
                TransmissionHub.stats
            ) { settings, connection, transmitting, stats ->
                MainUiState(
                    espIp = settings.espIp,
                    espPort = settings.espPort.toString(),
                    connectionState = connection,
                    isTransmitting = transmitting,
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
            app.captureManager.updateSettings(settingsRepository.settings.first())
            socketClient.connect(state.espIp, port)
        }
    }

    fun disconnect() {
        socketClient.disconnect()
        stopCaptureService()
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun stopCaptureService() {
        com.example.displayconnect.capture.CaptureForegroundService.stop(getApplication())
    }
}
