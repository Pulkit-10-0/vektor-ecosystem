package com.vektor.ui.screens.emergency

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vektor.emergency.EmergencyDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CountdownViewModel @Inject constructor(
    private val emergencyDispatcher: EmergencyDispatcher
) : ViewModel() {

    private val _countdownValue = MutableStateFlow(10)
    val countdownValue: StateFlow<Int> = _countdownValue.asStateFlow()

    private val _sosDispatched = MutableStateFlow(false)
    val sosDispatched: StateFlow<Boolean> = _sosDispatched.asStateFlow()

    private var countdownJob: Job? = null

    fun startCountdown() {
        if (countdownJob?.isActive == true) return
        _countdownValue.value = 10
        _sosDispatched.value = false
        countdownJob = viewModelScope.launch {
            for (i in 10 downTo 1) {
                _countdownValue.value = i
                delay(1000L)
            }
            _countdownValue.value = 0
            dispatchSos()
        }
    }

    fun cancelCountdown() {
        countdownJob?.cancel()
        countdownJob = null
    }

    private fun dispatchSos() {
        viewModelScope.launch {
            try {
                // Use 0.0 as placeholder coords; real implementation would use FusedLocationProvider
                emergencyDispatcher.dispatch(0.0, 0.0)
            } catch (_: Exception) { }
            _sosDispatched.value = true
        }
    }
}
