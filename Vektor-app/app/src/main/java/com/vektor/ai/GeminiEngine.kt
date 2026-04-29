package com.vektor.ai

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val systemPromptBuilder: SystemPromptBuilder
) {
    private val GEMINI_API_KEY = "AIzaSyCNXm2nstez1iS0jTEF-J7EKH-9kguqAts"
    private val BASE_URL =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Calls the Gemini REST API and returns a Flow<String> of word tokens
     * (simulated streaming by splitting the response into words).
     */
    fun chat(userMessage: String): Flow<String> = flow {
        val responseText = withContext(Dispatchers.IO) {
            callGeminiApi(userMessage)
        }
        // Simulate streaming by emitting word by word
        val words = responseText.split(" ")
        for ((index, word) in words.withIndex()) {
            emit(if (index == words.lastIndex) word else "$word ")
            delay(30L)
        }
    }.flowOn(Dispatchers.IO)

    private fun callGeminiApi(userMessage: String): String {
        val systemPrompt = systemPromptBuilder.buildSync()

        val requestBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", userMessage)
                        })
                    })
                })
            })
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", systemPrompt)
                    })
                })
            })
        }

        val request = Request.Builder()
            .url("$BASE_URL?key=$GEMINI_API_KEY")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("Gemini API error: ${response.code}")
            }
            val body = response.body?.string() ?: throw RuntimeException("Empty response from Gemini")
            val json = JSONObject(body)
            return json
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
        }
    }
}
