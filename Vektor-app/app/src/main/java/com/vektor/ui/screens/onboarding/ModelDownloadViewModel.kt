package com.vektor.ui.screens.onboarding

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
import javax.inject.Inject

enum class DownloadState { CHECKING, NOT_FOUND, DOWNLOADING, EXTRACTING, DONE, ERROR }

@HiltViewModel
class ModelDownloadViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(DownloadState.CHECKING)
    val state: StateFlow<DownloadState> = _state.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _statusText = MutableStateFlow("Checking for AI model…")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    private val _bundledModelReady = MutableStateFlow(false)
    val bundledModelReady: StateFlow<Boolean> = _bundledModelReady.asStateFlow()

    // Gemma 4 E2B INT4 — Cactus-Compute weights (Android, ~4.4 GB)
    private val MODEL_DIR_NAME = "gemma-4-e2b-it"
    private val modelDir get() = File(context.filesDir, "models/$MODEL_DIR_NAME")
    private val sentinelFile get() = File(modelDir, ".download_complete")

    // Gemma 3 270M — small model
    private val SMALL_MODEL_DIR_NAME = "gemma-3-270m-it"
    private val smallModelDir get() = File(context.filesDir, "models/$SMALL_MODEL_DIR_NAME")
    private val smallModelFile get() = File(smallModelDir, "model.gguf")

    private val HF_TOKEN = "hf_WMGihedWWSDlSqllpmpNEarttsNjFNOLJ"
    private val MODEL_URL =
        "https://huggingface.co/Cactus-Compute/gemma-4-E2B-it/resolve/main/weights/gemma-4-e2b-it-int4.zip"
    private val SMALL_MODEL_URL =
        "https://huggingface.co/Cactus-Compute/gemma-3-270m-it/resolve/main/gemma-3-270m-it-INT4.gguf"

    private val prefs: SharedPreferences =
        context.getSharedPreferences("model_prefs", Context.MODE_PRIVATE)

    init {
        checkBundledModel()
        checkModel()
    }

    private fun checkBundledModel() {
        viewModelScope.launch(Dispatchers.IO) {
            // Check if assets/models/ has any .gguf file
            val assetFiles = try {
                context.assets.list("models") ?: emptyArray()
            } catch (_: Exception) {
                emptyArray()
            }
            _bundledModelReady.value = assetFiles.any { it.endsWith(".gguf") }
        }
    }

    fun extractBundledModel() {
        viewModelScope.launch(Dispatchers.IO) {
            val assetFiles = try {
                context.assets.list("models") ?: emptyArray()
            } catch (_: Exception) {
                emptyArray()
            }
            val ggufFile = assetFiles.firstOrNull { it.endsWith(".gguf") } ?: return@launch
            val destDir = File(context.filesDir, "models/$SMALL_MODEL_DIR_NAME")
            destDir.mkdirs()
            val destFile = File(destDir, "model.gguf")
            context.assets.open("models/$ggufFile").use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            _bundledModelReady.value = true
        }
    }

    private fun checkModel() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = DownloadState.CHECKING
            _statusText.value = "Checking for AI model…"
            when {
                sentinelFile.exists() && modelDir.exists() -> {
                    _statusText.value = "Gemma 4 E2B model ready."
                    _state.value = DownloadState.DONE
                }
                smallModelFile.exists() -> {
                    _statusText.value = "Gemma 3 270M model ready."
                    _state.value = DownloadState.DONE
                }
                prefs.getBoolean("gemini_only", false) -> {
                    _statusText.value = "Using Gemini API (cloud)."
                    _state.value = DownloadState.DONE
                }
                else -> {
                    _statusText.value = "No AI model found. Choose an option below."
                    _state.value = DownloadState.NOT_FOUND
                }
            }
        }
    }

    fun startDownload() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = DownloadState.DOWNLOADING
            _progress.value = 0f
            _statusText.value = "Connecting to HuggingFace…"
            try {
                modelDir.mkdirs()
                val zipFile = File(modelDir, "gemma-4-e2b-it-int4.zip")

                downloadFile(MODEL_URL, zipFile, useAuth = true) { downloaded, total ->
                    _progress.value = if (total > 0) downloaded.toFloat() / total * 0.9f else 0f
                    val mb = downloaded / 1_048_576
                    val totalMb = if (total > 0) total / 1_048_576 else 4557
                    _statusText.value = "Downloading Gemma 4 E2B… ${mb}MB / ${totalMb}MB"
                }

                _state.value = DownloadState.EXTRACTING
                _statusText.value = "Extracting model files…"
                extractZip(zipFile, modelDir) { extracted, total ->
                    _progress.value = 0.9f + (if (total > 0) extracted.toFloat() / total * 0.1f else 0f)
                    _statusText.value = "Extracting… $extracted / $total files"
                }

                zipFile.delete()
                sentinelFile.writeText("ok")

                _statusText.value = "Gemma 4 E2B ready."
                _state.value = DownloadState.DONE

            } catch (e: Exception) {
                _statusText.value = "Failed: ${e.message}"
                _state.value = DownloadState.ERROR
            }
        }
    }

    fun downloadSmallModel() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = DownloadState.DOWNLOADING
            _progress.value = 0f
            _statusText.value = "Connecting to HuggingFace…"
            try {
                smallModelDir.mkdirs()

                downloadFile(SMALL_MODEL_URL, smallModelFile, useAuth = false) { downloaded, total ->
                    _progress.value = if (total > 0) downloaded.toFloat() / total else 0f
                    val mb = downloaded / 1_048_576
                    val totalMb = if (total > 0) total / 1_048_576 else 170
                    _statusText.value = "Downloading Gemma 3 270M… ${mb}MB / ${totalMb}MB"
                }

                _statusText.value = "Gemma 3 270M ready."
                _state.value = DownloadState.DONE

            } catch (e: Exception) {
                _statusText.value = "Failed: ${e.message}"
                _state.value = DownloadState.ERROR
            }
        }
    }

    fun useGeminiOnly(onContinue: () -> Unit) {
        prefs.edit().putBoolean("gemini_only", true).apply()
        _statusText.value = "Using Gemini API (cloud)."
        _state.value = DownloadState.DONE
        onContinue()
    }

    private suspend fun downloadFile(
        urlStr: String,
        dest: File,
        useAuth: Boolean,
        onProgress: suspend (Long, Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        val existingBytes = if (dest.exists()) dest.length() else 0L

        val url = URL(urlStr)
        var conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 30_000
        conn.readTimeout = 60_000
        if (useAuth) conn.setRequestProperty("Authorization", "Bearer $HF_TOKEN")
        if (existingBytes > 0) {
            conn.setRequestProperty("Range", "bytes=$existingBytes-")
        }

        var redirects = 0
        while (conn.responseCode in 300..399 && redirects < 10) {
            val location = conn.getHeaderField("Location")
            conn.disconnect()
            conn = URL(location).openConnection() as HttpURLConnection
            conn.connectTimeout = 30_000
            conn.readTimeout = 60_000
            if (useAuth) conn.setRequestProperty("Authorization", "Bearer $HF_TOKEN")
            if (existingBytes > 0) {
                conn.setRequestProperty("Range", "bytes=$existingBytes-")
            }
            redirects++
        }

        val responseCode = conn.responseCode
        val isResume = responseCode == 206
        val contentLength = conn.contentLengthLong
        val total = if (isResume) existingBytes + contentLength else contentLength
        var downloaded = if (isResume) existingBytes else 0L

        val fos = if (isResume) FileOutputStream(dest, true) else FileOutputStream(dest)
        fos.use { out ->
            conn.inputStream.use { input ->
                val buf = ByteArray(65_536)
                var n: Int
                while (input.read(buf).also { n = it } != -1) {
                    out.write(buf, 0, n)
                    downloaded += n
                    onProgress(downloaded, total)
                }
            }
        }
        conn.disconnect()
    }

    private suspend fun extractZip(
        zipFile: File,
        destDir: File,
        onProgress: suspend (Int, Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        var totalEntries = 0
        ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
            while (zis.nextEntry != null) { totalEntries++; zis.closeEntry() }
        }

        var extracted = 0
        ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val outFile = File(destDir, entry.name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { out ->
                        val buf = ByteArray(65_536)
                        var n: Int
                        while (zis.read(buf).also { n = it } != -1) {
                            out.write(buf, 0, n)
                        }
                    }
                }
                extracted++
                onProgress(extracted, totalEntries)
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    fun retryDownload() {
        sentinelFile.delete()
        startDownload()
    }
}
