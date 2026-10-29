package com.example.sreproductor.ui.activity

import android.app.Application
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

data class DownloadUiState(
    val url: String = "",
    val isBusy: Boolean = false,
    val showConfirmation: Boolean = false,
    val suggestedFileName: String = "archivo",
    val statusMessage: String = ""
)

class DownloadViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = kotlinx.coroutines.flow.MutableStateFlow(DownloadUiState())
    val uiState = _uiState.asStateFlow()

    fun onUrlChange(value: String) {
        _uiState.value = _uiState.value.copy(url = value, statusMessage = "")
    }

    fun checkUrl() {
        val input = _uiState.value.url.trim()
        val parsed = runCatching { URL(input) }.getOrNull()
        if (parsed == null || parsed.protocol !in setOf("http", "https")) {
            _uiState.value = _uiState.value.copy(statusMessage = "Ingresa una URL HTTP o HTTPS válida.")
            return
        }

        _uiState.value = _uiState.value.copy(isBusy = true, statusMessage = "Consultando el enlace…")
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { inspectMediaFile(input) }
            }.onSuccess { mediaInfo ->
                _uiState.value = _uiState.value.copy(
                    isBusy = false,
                    showConfirmation = true,
                    suggestedFileName = mediaInfo.fileName,
                    statusMessage = ""
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isBusy = false,
                    statusMessage = error.message ?: "No se pudo consultar el archivo."
                )
            }
        }
    }

    fun dismissConfirmation() {
        _uiState.value = _uiState.value.copy(showConfirmation = false)
    }

    fun downloadTo(destination: Uri) {
        val source = _uiState.value.url.trim()
        _uiState.value = _uiState.value.copy(isBusy = true, statusMessage = "Descargando…")
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val connection = URL(source).openConnection() as HttpURLConnection
                    connection.connectTimeout = 15_000
                    connection.readTimeout = 30_000
                    connection.instanceFollowRedirects = true
                    connection.requestMethod = "GET"
                    try {
                        val response = connection.responseCode
                        require(response in 200..299) { "El servidor respondió con error ($response)." }
                        val mediaMimeType = resolveMediaMimeType(
                            connection.contentType,
                            URL(connection.url.toString()).path
                        )
                        require(mediaMimeType != null) {
                            "El enlace no entrega directamente un archivo de audio o video."
                        }
                        val resolver = getApplication<Application>().contentResolver
                        val output = resolver.openOutputStream(destination)
                            ?: error("No se pudo abrir la ubicación elegida.")
                        connection.inputStream.use { input -> output.use { input.copyTo(it) } }
                    } finally {
                        connection.disconnect()
                    }
                }
            }.onSuccess {
                _uiState.value = _uiState.value.copy(isBusy = false, statusMessage = "Descarga completada.")
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isBusy = false,
                    statusMessage = error.message ?: "No se pudo descargar el archivo."
                )
            }
        }
    }

    private data class MediaInfo(val fileName: String, val mimeType: String)

    private fun inspectMediaFile(input: String): MediaInfo {
        val connection = URL(input).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        connection.instanceFollowRedirects = true
        connection.requestMethod = "HEAD"
        try {
            val response = connection.responseCode
            require(response in 200..299) { "El enlace no está disponible (respuesta $response)." }
            val path = URL(connection.url.toString()).path
            val mimeType = resolveMediaMimeType(connection.contentType, path)
            require(mimeType != null) {
                "El enlace debe apuntar directamente a un archivo de audio o video (por ejemplo, MP3 o MP4)."
            }
            val rawName = path.substringAfterLast('/').ifBlank { "archivo" }
            val decodedName = runCatching { URLDecoder.decode(rawName, "UTF-8") }.getOrDefault(rawName)
            val safeName = decodedName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val fileName = if (safeName.substringAfterLast('.', "").isBlank()) {
                val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType).orEmpty()
                "$safeName.${extension.ifBlank { "bin" }}"
            } else {
                safeName
            }
            return MediaInfo(fileName, mimeType)
        } finally {
            connection.disconnect()
        }
    }

    private fun resolveMediaMimeType(contentType: String?, path: String): String? {
        val responseMimeType = contentType.orEmpty().substringBefore(';').trim().lowercase()
        if (responseMimeType.startsWith("audio/") || responseMimeType.startsWith("video/")) {
            return responseMimeType
        }
        val extension = path.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?.takeIf { it.startsWith("audio/") || it.startsWith("video/") }
    }
}
