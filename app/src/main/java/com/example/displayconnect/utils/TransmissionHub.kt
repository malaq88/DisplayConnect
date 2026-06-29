package com.example.displayconnect.utils

import com.example.displayconnect.models.TransmissionStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object TransmissionHub {

    private val _isNavigating = MutableStateFlow(false)
    val isNavigating: StateFlow<Boolean> = _isNavigating.asStateFlow()

    private val _stats = MutableStateFlow(TransmissionStats())
    val stats: StateFlow<TransmissionStats> = _stats.asStateFlow()

    fun setNavigating(active: Boolean) {
        _isNavigating.value = active
        if (!active) {
            _stats.value = TransmissionStats()
        }
    }

    fun updateStats(stats: TransmissionStats) {
        _stats.value = stats
    }
}
