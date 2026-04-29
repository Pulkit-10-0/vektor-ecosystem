package com.vektor.ui.screens.gemma

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vektor.ai.GeminiEngine
import com.vektor.ai.GemmaEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ModelState { UNLOADED, LOADING, READY, ERROR }

@HiltViewModel
class GemmaChatViewModel @Inject constructor(
    private val gemmaEngine: GemmaEngine,
    private val geminiEngine: GeminiEngine
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _modelState = MutableStateFlow(ModelState.UNLOADED)
    val modelState: StateFlow<ModelState> = _modelState.asStateFlow()

    // Track which engine is powering the chat
    private val _poweredBy = MutableStateFlow("Gemma")
    val poweredBy: StateFlow<String> = _poweredBy.asStateFlow()

    init {
        loadModel()
    }

    private fun loadModel() {
        // Feature 6: don't reload if already loaded
        if (gemmaEngine.isLoaded()) {
            _modelState.value = ModelState.READY
            _poweredBy.value = "Gemma"
            return
        }

        viewModelScope.launch {
            _modelState.value = ModelState.LOADING
            try {
                gemmaEngine.load {}
                _modelState.value = ModelState.READY
                _poweredBy.value = "Gemma"
            } catch (e: Exception) {
                if (gemmaEngine.isLoaded()) {
                    _modelState.value = ModelState.READY
                    _poweredBy.value = "Gemma"
                } else {
                    // Gemma not available — fall back to Gemini
                    _modelState.value = ModelState.READY
                    _poweredBy.value = "Gemini"
                }
            }
        }
    }

    fun sendMessage(text: String) {
        _messages.value = _messages.value + ChatMessage("user", text)
        viewModelScope.launch {
            var assistant = ""
            var usedGemini = false

            // Try Gemma first; fall back to Gemini if not loaded or throws
            val useGemini = !gemmaEngine.isLoaded()

            if (!useGemini) {
                try {
                    gemmaEngine.chat(text).collect { token ->
                        assistant += token
                    }
                } catch (_: Exception) {
                    assistant = ""
                }
            }

            // Fall back to Gemini if Gemma returned blank or wasn't loaded
            if (assistant.isBlank()) {
                try {
                    geminiEngine.chat(text).collect { token ->
                        assistant += token
                    }
                    usedGemini = true
                } catch (_: Exception) {
                    assistant = ""
                }
            }

            if (assistant.isBlank()) {
                assistant = "I am ready to help. Please share emergency details."
            }

            _poweredBy.value = if (usedGemini) "Gemini" else "Gemma"
            _messages.value = _messages.value + ChatMessage("assistant", assistant.trim())
        }
    }
}
