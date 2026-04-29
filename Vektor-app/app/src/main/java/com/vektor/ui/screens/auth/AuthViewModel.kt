package com.vektor.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vektor.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun login(username: String, password: String, onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            val result = authRepository.login(username.trim(), password)
            result.onSuccess {
                _error.value = null
                onSuccess(it)
            }.onFailure {
                _error.value = it.message ?: "Login failed"
            }
        }
    }

    fun signup(username: String, password: String, onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            val result = authRepository.signup(username.trim(), password)
            result.onSuccess {
                _error.value = null
                onSuccess(it)
            }.onFailure {
                _error.value = it.message ?: "Signup failed"
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
