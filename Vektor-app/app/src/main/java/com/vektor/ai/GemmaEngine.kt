package com.vektor.ai

import android.content.Context
import com.cactus.CactusTokenCallback
import com.cactus.cactusComplete
import com.cactus.cactusDestroy
import com.cactus.cactusGetLastError
import com.cactus.cactusInit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GemmaEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val systemPromptBuilder: SystemPromptBuilder
) {
    private var modelHandle: Long = 0L

    private val modelPath: String
        get() {
            // 1. Gemma 4 E2B INT4 — extracted from Cactus-Compute zip
            val gemma4Dir = java.io.File(context.filesDir, "models/gemma-4-e2b-it")
            if (gemma4Dir.exists() && java.io.File(gemma4Dir, ".download_complete").exists()) {
                return gemma4Dir.absolutePath
            }

            // 2. Gemma 3 270M — downloaded small model
            val gemma3File = java.io.File(context.filesDir, "models/gemma-3-270m-it/model.gguf")
            if (gemma3File.exists()) {
                return gemma3File.absolutePath
            }

            // 3. Stub mode (no real model)
            return gemma4Dir.absolutePath
        }

    suspend fun load(onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        if (modelHandle != 0L) return@withContext
        modelHandle = cactusInit(modelPath, null, false)
        if (modelHandle == 0L) throw RuntimeException(cactusGetLastError())
        onProgress(1.0f)
    }

    fun isLoaded(): Boolean = modelHandle != 0L

    suspend fun chat(userMessage: String): Flow<String> {
        // Ensure model is loaded (stub returns handle=1 when path is non-blank)
        if (modelHandle == 0L) {
            try {
                load {}
            } catch (_: Exception) {
                // Stub mode: cactusInit returns 1L for non-blank path, so this
                // only throws when the real .so is present but init fails.
            }
        }

        val systemPrompt = systemPromptBuilder.buildSync()
        val messages = buildString {
            append("[")
            append("""{"role":"system","content":"${systemPrompt.escapeJson()}"}""")
            append(",")
            append("""{"role":"user","content":"${userMessage.escapeJson()}"}""")
            append("]")
        }
        val options = """{"max_tokens":512,"temperature":0.7}"""
        val handle = modelHandle

        return callbackFlow {
            withContext(Dispatchers.IO) {
                cactusComplete(
                    handle, messages, options, null,
                    object : CactusTokenCallback {
                        override fun onToken(token: String, tokenId: Int) {
                            trySend(token)
                        }
                    }
                )
            }
            close()
            awaitClose()
        }.flowOn(Dispatchers.IO)
    }

    suspend fun complete(prompt: String, maxTokens: Int = 200): String = withContext(Dispatchers.IO) {
        if (modelHandle == 0L) {
            try { load {} } catch (_: Exception) { }
        }
        val messages = """[{"role":"user","content":"${prompt.escapeJson()}"}]"""
        val options = """{"max_tokens":$maxTokens,"temperature":0.3}"""
        val resultJson = cactusComplete(modelHandle, messages, options, null, null)
        val result = JSONObject(resultJson)
        if (!result.getBoolean("success")) throw RuntimeException(result.optString("error"))
        result.getString("response")
    }

    fun destroy() {
        if (modelHandle != 0L) cactusDestroy(modelHandle)
        modelHandle = 0L
    }

    private fun String.escapeJson() = replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
}
