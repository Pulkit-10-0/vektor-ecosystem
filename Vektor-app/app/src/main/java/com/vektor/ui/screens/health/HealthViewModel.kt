package com.vektor.ui.screens.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vektor.data.local.prefs.ProfileDataStore
import com.vektor.domain.model.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

@HiltViewModel
class HealthViewModel @Inject constructor(
    private val profileStore: ProfileDataStore
) : ViewModel() {

    private val _profile = MutableStateFlow<UserProfile?>(null)
    val profile: StateFlow<UserProfile?> = _profile.asStateFlow()

    init {
        loadProfile()
    }

    private fun loadProfile() {
        viewModelScope.launch(Dispatchers.IO) {
            val json = profileStore.getProfileSync() ?: return@launch
            try {
                _profile.value = Json { ignoreUnknownKeys = true }.decodeFromString(json)
            } catch (_: Exception) { }
        }
    }
}
