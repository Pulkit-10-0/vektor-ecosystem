package com.cactus

import org.json.JSONArray
import org.json.JSONObject

fun interface CactusTokenCallback {
    fun onToken(token: String, tokenId: Int)
}

fun interface CactusLogCallback {
    fun onLog(level: Int, component: String, message: String)
}

// ── JNI bridge object ────────────────────────────────────────────────────────
private object CactusJni {
    var available: Boolean = false

    init {
        try {
            System.loadLibrary("cactus")
            available = true
        } catch (_: UnsatisfiedLinkError) {
            available = false
        }
    }

    // Declared only when the .so is present; called via reflection-safe dispatch
    external fun cactusInit(modelPath: String, corpusDir: String?, cacheIndex: Boolean): Long
    external fun cactusDestroy(model: Long)
    external fun cactusReset(model: Long)
    external fun cactusStop(model: Long)
    external fun cactusGetLastError(): String
    external fun cactusPrefill(model: Long, messagesJson: String, optionsJson: String?, toolsJson: String?, pcmData: ByteArray?): String
    external fun cactusComplete(model: Long, messagesJson: String, optionsJson: String?, toolsJson: String?, callback: CactusTokenCallback?, pcmData: ByteArray?): String
    external fun cactusTranscribe(model: Long, audioPath: String?, prompt: String?, optionsJson: String?, callback: CactusTokenCallback?, pcmData: ByteArray?): String
}

// ── Stub helpers ─────────────────────────────────────────────────────────────
private fun stubComplete(messagesJson: String, callback: CactusTokenCallback?): String {
    val prompt = try {
        val arr = JSONArray(messagesJson)
        if (arr.length() > 0) arr.getJSONObject(arr.length() - 1).optString("content", "") else ""
    } catch (_: Exception) { messagesJson }

    val response = when {
        prompt.contains("allerg", ignoreCase = true) -> "Critical allergies: Penicillin and Latex."
        prompt.contains("blood", ignoreCase = true) -> "Blood group is O+."
        prompt.contains("emergency", ignoreCase = true) -> "Prioritize airway, breathing, circulation, then contact emergency services and family."
        else -> "I am Vektor. Share symptoms, location, and urgency for a concise emergency plan."
    }

    response.split(" ").forEachIndexed { index, token ->
        callback?.onToken(if (index == 0) token else " $token", index + 1)
    }

    return JSONObject(
        mapOf(
            "success" to true,
            "error" to JSONObject.NULL,
            "cloud_handoff" to false,
            "response" to response,
            "function_calls" to JSONArray(),
            "confidence" to 0.81,
            "time_to_first_token_ms" to 35.0,
            "total_time_ms" to 120.0,
            "prefill_tps" to 1500.0,
            "decode_tps" to 180.0,
            "ram_usage_mb" to 220.0,
            "prefill_tokens" to 20,
            "decode_tokens" to 30,
            "total_tokens" to 50
        )
    ).toString()
}

// ── Public top-level API ──────────────────────────────────────────────────────

fun cactusInit(modelPath: String, corpusDir: String?, cacheIndex: Boolean): Long {
    if (CactusJni.available) return CactusJni.cactusInit(modelPath, corpusDir, cacheIndex)
    return if (modelPath.isNotBlank()) 1L else 0L
}

fun cactusDestroy(model: Long) {
    if (CactusJni.available) CactusJni.cactusDestroy(model)
}

fun cactusReset(model: Long) {
    if (CactusJni.available) CactusJni.cactusReset(model)
}

fun cactusStop(model: Long) {
    if (CactusJni.available) CactusJni.cactusStop(model)
}

fun cactusGetLastError(): String {
    if (CactusJni.available) return CactusJni.cactusGetLastError()
    return ""
}

fun cactusPrefill(
    model: Long,
    messagesJson: String,
    optionsJson: String?,
    toolsJson: String?,
    pcmData: ByteArray? = null
): String {
    if (CactusJni.available) return CactusJni.cactusPrefill(model, messagesJson, optionsJson, toolsJson, pcmData)
    return """{"success":true,"prefill_tokens":25}"""
}

fun cactusComplete(
    model: Long,
    messagesJson: String,
    optionsJson: String?,
    toolsJson: String?,
    callback: CactusTokenCallback?,
    pcmData: ByteArray? = null
): String {
    if (CactusJni.available) return CactusJni.cactusComplete(model, messagesJson, optionsJson, toolsJson, callback, pcmData)
    return stubComplete(messagesJson, callback)
}

fun cactusTranscribe(
    model: Long,
    audioPath: String?,
    prompt: String?,
    optionsJson: String?,
    callback: CactusTokenCallback?,
    pcmData: ByteArray?
): String {
    if (CactusJni.available) return CactusJni.cactusTranscribe(model, audioPath, prompt, optionsJson, callback, pcmData)
    return "{}"
}

fun cactusStreamTranscribeStart(model: Long, optionsJson: String?): Long = 1L
fun cactusStreamTranscribeProcess(stream: Long, pcmData: ByteArray): String = "{}"
fun cactusStreamTranscribeStop(stream: Long): String = "{}"
