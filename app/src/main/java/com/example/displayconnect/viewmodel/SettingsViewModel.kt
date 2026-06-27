package com.example.displayconnect.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.displayconnect.DisplayConnectApp
import com.example.displayconnect.models.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * ViewModel da tela de configurações.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as DisplayConnectApp
    private val settingsRepository = app.settingsRepository

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { _settings.value = it }
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            settingsRepository.updateSettings(transform)
            val updated = settingsRepository.settings.first()
            app.captureManager.updateSettings(updated)
        }
    }
}
