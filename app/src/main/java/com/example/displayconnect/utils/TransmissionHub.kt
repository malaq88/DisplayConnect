package com.example.displayconnect.utils

import com.example.displayconnect.models.TransmissionStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Hub singleton para compartilhar estado entre o Foreground Service e os ViewModels.
 */
object TransmissionHub {

    private val _isTransmitting = MutableStateFlow(false)
    val isTransmitting: StateFlow<Boolean> = _isTransmitting.asStateFlow()

    private val _stats = MutableStateFlow(TransmissionStats())
    val stats: StateFlow<TransmissionStats> = _stats.asStateFlow()

    fun setTransmitting(active: Boolean) {
        _isTransmitting.value = active
        if (!active) {
            _stats.value = TransmissionStats()
        }
    }

    fun updateStats(stats: TransmissionStats) {
        _stats.value = stats
    }
}
