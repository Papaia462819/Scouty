package com.scouty.app.assistant.domain

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import com.scouty.app.assistant.model.ModelRuntimeState
import com.scouty.app.assistant.model.OfflineChatModelState
import com.scouty.app.assistant.model.OfflineChatModelStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class OfflineChatModelController(
    context: Context,
    private val modelManager: ModelManager,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(0, TimeUnit.MILLISECONDS)
        .build(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
    private val installMutex = Mutex()
    private var installJob: Job? = null

    private val qwenInternalDir = File(appContext.noBackupFilesDir, "models/qwen-2.5-1.5b")
    private val qwenExternalDir = appContext.getExternalFilesDir(null)?.let { File(it, "models/qwen-2.5-1.5b") }
    private val targetModelFile = File(qwenInternalDir, QwenFileName)

    private val _state = MutableStateFlow(OfflineChatModelState())
    val state: StateFlow<OfflineChatModelState> = _state.asStateFlow()

    init {
        scope.launch {
            initializeFromPreference()
        }
    }

    fun requestEnable(forceMetered: Boolean = false) {
        if (installJob?.isActive == true) {
            return
        }
        installJob = scope.launch {
            enableOfflineChat(forceMetered = forceMetered)
        }
    }

    fun confirmMeteredDownload() {
        requestEnable(forceMetered = true)
    }

    fun cancelMeteredConfirmation() {
        prefs.edit().putBoolean(PrefEnabled, false).apply()
        _state.value = OfflineChatModelState()
    }

    fun requestDisable() {
        scope.launch {
            installJob?.takeIf { it.isActive }?.cancelAndJoin()
            installMutex.withLock {
                runCatching { modelManager.unloadModel() }
                deleteQwenBundle()
                prefs.edit().putBoolean(PrefEnabled, false).apply()
                runCatching { modelManager.refreshStatus() }
                _state.value = OfflineChatModelState(
                    enabled = false,
                    status = OfflineChatModelStatus.DISABLED,
                    message = "Chat local dezactivat."
                )
            }
        }
    }

    private suspend fun initializeFromPreference() {
        val userEnabled = prefs.getBoolean(PrefEnabled, false)
        modelManager.refreshStatus()
        if (!userEnabled) {
            _state.value = OfflineChatModelState(
                enabled = false,
                status = OfflineChatModelStatus.DISABLED,
                message = "Chat local este oprit."
            )
            return
        }
        if (!hasInstalledQwenBundle()) {
            prefs.edit().putBoolean(PrefEnabled, false).apply()
            _state.value = OfflineChatModelState(
                enabled = false,
                status = OfflineChatModelStatus.DISABLED,
                message = "Modelul local nu mai este instalat."
            )
            return
        }
        loadInstalledModel(showCompletionNotice = false)
    }

    private suspend fun enableOfflineChat(forceMetered: Boolean) {
        installMutex.withLock {
            modelManager.refreshStatus()
            if (hasInstalledQwenBundle()) {
                prefs.edit().putBoolean(PrefEnabled, true).apply()
                loadInstalledModel(showCompletionNotice = true)
                return
            }

            if (!forceMetered && isActiveNetworkMetered()) {
                _state.value = OfflineChatModelState(
                    enabled = true,
                    status = OfflineChatModelStatus.WAITING_METERED_CONFIRMATION,
                    message = "Modelul Qwen are aproximativ 1.1 GB. Confirmă descărcarea pe date mobile."
                )
                return
            }

            _state.value = OfflineChatModelState(
                enabled = true,
                status = OfflineChatModelStatus.DOWNLOADING,
                progressPercent = 0,
                message = "Se descarcă modelul Qwen pentru chat local."
            )

            runCatching {
                val modelFile = downloadModel()
                writeManifest(modelFile.parentFile ?: qwenInternalDir)
                prefs.edit().putBoolean(PrefEnabled, true).apply()
                _state.value = OfflineChatModelState(
                    enabled = true,
                    status = OfflineChatModelStatus.INSTALLING,
                    progressPercent = 100,
                    message = "Se finalizează instalarea modelului local."
                )
                loadInstalledModel(showCompletionNotice = true)
            }.getOrElse { error ->
                if (error is CancellationException) {
                    throw error
                }
                Log.w(LogTag, "Offline chat model installation failed", error)
                _state.value = OfflineChatModelState(
                    enabled = true,
                    status = OfflineChatModelStatus.FAILED,
                    progressPercent = null,
                    message = "Instalarea chatului local a eșuat.",
                    errorMessage = error.message ?: error::class.java.simpleName
                )
            }
        }
    }

    private suspend fun loadInstalledModel(showCompletionNotice: Boolean) {
        _state.update {
            it.copy(
                enabled = true,
                status = OfflineChatModelStatus.LOADING,
                progressPercent = 100,
                message = "Se încarcă modelul local."
            )
        }
        val loadedStatus = modelManager.ensureLoaded()
        if (loadedStatus.state == ModelRuntimeState.LOADED) {
            prefs.edit().putBoolean(PrefEnabled, true).apply()
            _state.value = OfflineChatModelState(
                enabled = true,
                status = OfflineChatModelStatus.READY,
                progressPercent = 100,
                message = "Chat local este gata.",
                completedEventId = if (showCompletionNotice) System.currentTimeMillis() else 0L
            )
        } else {
            _state.value = OfflineChatModelState(
                enabled = true,
                status = OfflineChatModelStatus.FAILED,
                progressPercent = null,
                message = "Modelul local nu a putut fi încărcat.",
                errorMessage = loadedStatus.lastError ?: loadedStatus.details
            )
        }
    }

    private suspend fun downloadModel(): File = withContext(Dispatchers.IO) {
        qwenInternalDir.mkdirs()
        val tempFile = File(qwenInternalDir, "$QwenFileName.part")
        val request = Request.Builder()
            .url(QwenDownloadUrl)
            .build()
        val digest = MessageDigest.getInstance("SHA-256")
        var copiedBytes = 0L
        var nextProgressUpdate = 0

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Model request failed with HTTP ${response.code}.")
                }
                val body = response.body ?: error("Empty model response.")
                val totalBytes = QwenSizeBytes.takeIf { it > 0L } ?: body.contentLength().takeIf { it > 0L }
                targetModelFile.parentFile?.mkdirs()
                tempFile.outputStream().use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            copiedBytes += read
                            if (totalBytes != null && totalBytes > 0L) {
                                val progress = ((copiedBytes * 100L) / totalBytes).toInt().coerceIn(0, 99)
                                if (progress >= nextProgressUpdate) {
                                    _state.update {
                                        it.copy(
                                            status = OfflineChatModelStatus.DOWNLOADING,
                                            progressPercent = progress,
                                            message = "Se descarcă Qwen $progress%."
                                        )
                                    }
                                    nextProgressUpdate = progress + 3
                                }
                            }
                        }
                    }
                }
            }

            if (QwenSizeBytes > 0L && copiedBytes != QwenSizeBytes) {
                tempFile.delete()
                error("Dimensiunea modelului descărcat nu corespunde.")
            }
            val actualSha256 = digest.digest().joinToString("") { "%02x".format(it) }
            if (!actualSha256.equals(QwenSha256, ignoreCase = true)) {
                tempFile.delete()
                error("Verificarea modelului descărcat nu corespunde.")
            }
            if (targetModelFile.exists() && !targetModelFile.delete()) {
                error("Nu se poate înlocui modelul local existent.")
            }
            if (!tempFile.renameTo(targetModelFile)) {
                tempFile.copyTo(targetModelFile, overwrite = true)
                tempFile.delete()
            }
            targetModelFile
        } catch (error: CancellationException) {
            tempFile.delete()
            throw error
        } catch (error: Throwable) {
            tempFile.delete()
            throw error
        }
    }

    private fun writeManifest(modelDir: File) {
        modelDir.mkdirs()
        File(modelDir, "scouty_model_manifest.json").writeText(
            """
            {
              "model_version": "$QwenModelVersion",
              "runtime": "llama_cpp",
              "preferred_backend": "CPU",
              "max_tokens": 8192
            }
            """.trimIndent()
        )
    }

    private fun deleteQwenBundle() {
        runCatching { qwenInternalDir.deleteRecursively() }
        qwenExternalDir?.let { externalDir ->
            runCatching { externalDir.deleteRecursively() }
        }
    }

    private fun hasInstalledQwenBundle(): Boolean =
        listOfNotNull(qwenInternalDir, qwenExternalDir).any { root ->
            root.exists() && root.walkTopDown()
                .maxDepth(3)
                .any { file ->
                    file.isFile &&
                        file.length() > 0L &&
                        file.extension.equals("gguf", ignoreCase = true) &&
                        file.name.contains("qwen", ignoreCase = true)
                }
        }

    private fun isActiveNetworkMetered(): Boolean {
        val connectivityManager =
            appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return connectivityManager.isActiveNetworkMetered
    }

    private companion object {
        private const val LogTag = "ScoutyOfflineChat"
        private const val PrefsName = "scouty_offline_chat"
        private const val PrefEnabled = "offline_chat_enabled"
        private const val QwenModelVersion = "qwen2.5-1.5b-instruct-q4_k_m"
        private const val QwenFileName = "qwen2.5-1.5b-instruct-q4_k_m.gguf"
        private const val QwenDownloadUrl =
            "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf?download=true"
        private const val QwenSha256 = "6A1A2EB6D15622BF3C96857206351BA97E1AF16C30D7A74EE38970E434E9407E"
        private const val QwenSizeBytes = 1_117_320_736L
    }
}
