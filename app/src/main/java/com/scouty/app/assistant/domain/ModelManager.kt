package com.scouty.app.assistant.domain

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.PromptTemplates
import com.scouty.app.assistant.domain.runtime.LlamaCppRuntimeAdapter
import com.scouty.app.assistant.model.GenerationMode
import com.scouty.app.assistant.model.ModelRuntimeState
import com.scouty.app.assistant.model.ModelStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

private const val DefaultModelVersion = "gemma-3-1b-it-int4"
private const val DefaultMaxTokens = 4_096
private const val QwenModelVersion = "qwen2.5-1.5b-instruct-q4_k_m"

data class LocalLlmSamplerParams(
    val maxTokens: Int = 512,
    val temperature: Float = 0.0f,
    val topK: Int = 1,
    val topP: Float = 0.2f,
    val randomSeed: Int = 7
)

data class LocalLlmPromptCacheHint(
    val key: String,
    val cacheablePrefix: String
)

data class LocalLlmGenerationOptions(
    val grammar: String? = null,
    val sampler: LocalLlmSamplerParams = LocalLlmSamplerParams(),
    val promptCacheHint: LocalLlmPromptCacheHint? = null,
    val stopSequences: List<String> = emptyList()
)

data class LocalLlmGenerationResult(
    val text: String,
    val modelStatus: ModelStatus
)

interface LocalModelLocator {
    suspend fun inspect(): LocalModelDiscovery

    suspend fun prepare(discovery: LocalModelDiscovery): LocalModelArtifact
}

interface LocalLlmRuntimeAdapter {
    val runtimeLabel: String

    suspend fun load(artifact: LocalModelArtifact): LocalLlmLoadedModelHandle
}

interface LocalLlmLoadedModelHandle {
    suspend fun generate(prompt: String): String

    suspend fun generate(prompt: String, options: LocalLlmGenerationOptions): String =
        generate(prompt)

    suspend fun close()
}

data class LocalModelDiscovery(
    val modelVersion: String = DefaultModelVersion,
    val availableOnDisk: Boolean = false,
    val needsPrepare: Boolean = false,
    val sourceFile: File? = null,
    val preparedFile: File? = null,
    val backend: LocalModelBackend = LocalModelBackend.CPU,
    val runtime: LocalModelRuntime = LocalModelRuntime.MEDIAPIPE,
    val maxTokens: Int = DefaultMaxTokens,
    val fileSizeBytes: Long? = null,
    val details: String,
    val state: ModelRuntimeState = if (availableOnDisk) ModelRuntimeState.UNLOADED else ModelRuntimeState.MISSING
)

data class LocalModelArtifact(
    val modelVersion: String,
    val sourceFile: File,
    val preparedFile: File,
    val backend: LocalModelBackend = LocalModelBackend.CPU,
    val runtime: LocalModelRuntime = LocalModelRuntime.MEDIAPIPE,
    val maxTokens: Int = DefaultMaxTokens,
    val fileSizeBytes: Long = preparedFile.length()
)

enum class LocalModelBackend {
    CPU,
    GPU
}

enum class LocalModelRuntime {
    MEDIAPIPE,
    LLAMA_CPP
}

class ModelManager(
    private val modelLocator: LocalModelLocator,
    private val runtimeAdapter: LocalLlmRuntimeAdapter
) {
    constructor(context: Context) : this(
        modelLocator = AndroidLocalModelLocator(context.applicationContext),
        runtimeAdapter = MediaPipeLocalLlmRuntimeAdapter(context.applicationContext)
    )

    constructor(context: Context, featureFlags: RuntimeFeatureFlags) : this(
        modelLocator = AndroidLocalModelLocator(
            context = context.applicationContext,
            includeLlamaCpp = featureFlags.useLlamaCpp
        ),
        runtimeAdapter = if (featureFlags.useLlamaCpp) {
            LlamaCppRuntimeAdapter()
        } else {
            MediaPipeLocalLlmRuntimeAdapter(context.applicationContext)
        }
    )

    private val mutex = Mutex()
    private val _status = MutableStateFlow(
        ModelStatus(runtimeLabel = runtimeAdapter.runtimeLabel)
    )
    val status: StateFlow<ModelStatus> = _status.asStateFlow()

    private var loadedRuntime: LoadedRuntime? = null

    suspend fun refreshStatus(): ModelStatus = mutex.withLock {
        val current = _status.value
        if (loadedRuntime != null && current.state == ModelRuntimeState.LOADED) {
            val refreshed = current.copy(lastCheckedEpochMs = System.currentTimeMillis())
            _status.value = refreshed
            return refreshed
        }

        val discovery = modelLocator.inspect()
        return updateStatus(statusFromDiscovery(discovery))
    }

    suspend fun ensureLoaded(): ModelStatus = mutex.withLock {
        ensureLoadedLocked()
    }

    suspend fun unloadModel(): ModelStatus = mutex.withLock {
        loadedRuntime?.close()
        loadedRuntime = null

        val discovery = modelLocator.inspect()
        return if (!discovery.availableOnDisk) {
            updateStatus(statusFromDiscovery(discovery))
        } else {
            updateStatus(
                statusFromDiscovery(discovery).copy(
                    state = ModelRuntimeState.UNLOADED,
                    details = "Local model unloaded. Bundle remains available for the next request."
                )
            )
        }
    }

    suspend fun generate(prompt: String): LocalLlmGenerationResult =
        generate(prompt, LocalLlmGenerationOptions())

    suspend fun generate(
        prompt: String,
        options: LocalLlmGenerationOptions
    ): LocalLlmGenerationResult = mutex.withLock {
        val readyStatus = ensureLoadedLocked()
        val runtime = loadedRuntime
        if (readyStatus.state != ModelRuntimeState.LOADED || runtime == null) {
            throw IllegalStateException(readyStatus.details)
        }

        return runCatching {
            val text = runtime.handle.generate(prompt, options).trim()
            if (text.isBlank()) {
                error("Local runtime returned an empty response")
            }
            LocalLlmGenerationResult(text = text, modelStatus = _status.value)
        }.getOrElse { error ->
            runCatching {
                Log.e(LogTag, "Local model generation failed", error)
            }
            runCatching { runtime.close() }
            loadedRuntime = null
            updateStatus(
                _status.value.copy(
                    state = ModelRuntimeState.FAILED,
                    details = "Local model generation failed. Structured fallback remains active.",
                    lastError = error.message ?: error::class.java.simpleName,
                    lastCheckedEpochMs = System.currentTimeMillis(),
                    loadedAtEpochMs = null
                )
            )
            throw error
        }
    }

    fun currentStatus(): ModelStatus = _status.value

    fun currentGenerationMode(): GenerationMode =
        if (_status.value.canGenerateLocally) {
            GenerationMode.LOCAL_LLM
        } else {
            GenerationMode.FALLBACK_STRUCTURED
        }

    private suspend fun ensureLoadedLocked(): ModelStatus {
        val current = _status.value
        if (loadedRuntime != null && current.state == ModelRuntimeState.LOADED) {
            return current
        }

        val discovery = modelLocator.inspect()
        if (!discovery.availableOnDisk || discovery.sourceFile == null || discovery.preparedFile == null) {
            return updateStatus(statusFromDiscovery(discovery))
        }

        return runCatching {
            if (discovery.needsPrepare) {
                updateStatus(
                    statusFromDiscovery(discovery).copy(
                        state = ModelRuntimeState.PREPARING,
                        details = "Preparing local model bundle for ${runtimeAdapter.runtimeLabel}."
                    )
                )
            }

            val artifact = modelLocator.prepare(discovery)
            updateStatus(statusFromArtifact(artifact, ModelRuntimeState.PREPARING, "Initializing ${runtimeAdapter.runtimeLabel}."))

            val handle = runtimeAdapter.load(artifact)
            loadedRuntime?.close()
            loadedRuntime = LoadedRuntime(artifact, handle)

            updateStatus(
                statusFromArtifact(
                    artifact = artifact,
                    state = ModelRuntimeState.LOADED,
                    details = "Local ${artifact.modelVersion} runtime loaded successfully."
                ).copy(loadedAtEpochMs = System.currentTimeMillis())
            )
        }.getOrElse { error ->
            runCatching {
                Log.e(LogTag, "Failed to load local model bundle", error)
            }
            loadedRuntime = null
            updateStatus(
                statusFromDiscovery(discovery).copy(
                    state = ModelRuntimeState.FAILED,
                    details = "Failed to load the local ${discovery.modelVersion} bundle. Structured fallback remains active.",
                    lastError = error.message ?: error::class.java.simpleName,
                    lastCheckedEpochMs = System.currentTimeMillis(),
                    loadedAtEpochMs = null
                )
            )
        }
    }

    private fun statusFromDiscovery(discovery: LocalModelDiscovery): ModelStatus =
        ModelStatus(
            runtimeLabel = runtimeAdapter.runtimeLabel,
            modelVersion = discovery.modelVersion,
            state = discovery.state,
            details = discovery.details,
            modelPath = discovery.preparedFile?.absolutePath,
            sourcePath = discovery.sourceFile?.absolutePath,
            availableOnDisk = discovery.availableOnDisk,
            backend = discovery.backend.name,
            fileSizeBytes = discovery.fileSizeBytes,
            lastCheckedEpochMs = System.currentTimeMillis()
        )

    private fun statusFromArtifact(
        artifact: LocalModelArtifact,
        state: ModelRuntimeState,
        details: String
    ): ModelStatus =
        ModelStatus(
            runtimeLabel = runtimeAdapter.runtimeLabel,
            modelVersion = artifact.modelVersion,
            state = state,
            details = details,
            modelPath = artifact.preparedFile.absolutePath,
            sourcePath = artifact.sourceFile.absolutePath,
            availableOnDisk = true,
            backend = artifact.backend.name,
            fileSizeBytes = artifact.fileSizeBytes,
            lastCheckedEpochMs = System.currentTimeMillis()
        )

    private fun updateStatus(next: ModelStatus): ModelStatus {
        _status.value = next
        return next
    }

    private data class LoadedRuntime(
        val artifact: LocalModelArtifact,
        val handle: LocalLlmLoadedModelHandle
    ) {
        suspend fun close() {
            handle.close()
        }
    }

    private companion object {
        private const val LogTag = "ScoutyModelManager"
    }
}

private class AndroidLocalModelLocator(
    context: Context,
    private val includeLlamaCpp: Boolean = false,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : LocalModelLocator {
    private val gemmaInternalDir = File(context.noBackupFilesDir, "models/gemma-3-1b")
    private val gemmaExternalDir = context.getExternalFilesDir(null)?.let { File(it, "models/gemma-3-1b") }
    private val qwenInternalDir = File(context.noBackupFilesDir, "models/qwen-2.5-1.5b")
    private val qwenExternalDir = context.getExternalFilesDir(null)?.let { File(it, "models/qwen-2.5-1.5b") }

    private val searchRoots: List<ModelSearchRoot> = buildList {
        add(ModelSearchRoot(gemmaInternalDir, LocalModelRuntime.MEDIAPIPE, "internal app storage"))
        gemmaExternalDir?.let { add(ModelSearchRoot(it, LocalModelRuntime.MEDIAPIPE, "external app storage")) }
        if (includeLlamaCpp) {
            add(ModelSearchRoot(qwenInternalDir, LocalModelRuntime.LLAMA_CPP, "internal app storage"))
            qwenExternalDir?.let { add(ModelSearchRoot(it, LocalModelRuntime.LLAMA_CPP, "external app storage")) }
        }
    }

    override suspend fun inspect(): LocalModelDiscovery = withContext(Dispatchers.IO) {
        searchRoots.firstNotNullOfOrNull { root ->
            findCandidate(root)?.let { candidate -> root to candidate }
        }?.let { (root, candidate) ->
            return@withContext LocalModelDiscovery(
                modelVersion = candidate.modelVersion,
                availableOnDisk = true,
                needsPrepare = false,
                sourceFile = candidate.file,
                preparedFile = candidate.file,
                backend = candidate.backend,
                runtime = candidate.runtime,
                maxTokens = candidate.maxTokens,
                fileSizeBytes = candidate.file.length(),
                details = "Local ${candidate.runtime.displayName} bundle found in ${root.label} and ready to load."
            )
        }

        LocalModelDiscovery(
            modelVersion = if (includeLlamaCpp) QwenModelVersion else DefaultModelVersion,
            runtime = if (includeLlamaCpp) LocalModelRuntime.LLAMA_CPP else LocalModelRuntime.MEDIAPIPE,
            details = buildString {
                append("No supported local model bundle detected.")
                append(" Checked ")
                append(searchRoots.joinToString(separator = ", ") { it.dir.absolutePath })
                append(". Expected ")
                append(if (includeLlamaCpp) "Qwen GGUF or Gemma MediaPipe bundle" else "Gemma 3 1B `.task` or `.litertlm` bundle")
                append(". Structured fallback remains active.")
            }
        )
    }

    override suspend fun prepare(discovery: LocalModelDiscovery): LocalModelArtifact = withContext(Dispatchers.IO) {
        val sourceFile = requireNotNull(discovery.sourceFile) { "Source model file is missing" }
        val preparedFile = requireNotNull(discovery.preparedFile) { "Prepared model path is missing" }

        preparedFile.parentFile?.mkdirs()
        if (discovery.needsPrepare || sourceFile.absolutePath != preparedFile.absolutePath) {
            copyAtomically(sourceFile, preparedFile)
        }

        LocalModelArtifact(
            modelVersion = discovery.modelVersion,
            sourceFile = sourceFile,
            preparedFile = preparedFile,
            backend = discovery.backend,
            runtime = discovery.runtime,
            maxTokens = discovery.maxTokens,
            fileSizeBytes = preparedFile.length()
        )
    }

    private fun findCandidate(root: ModelSearchRoot): Candidate? {
        if (!root.dir.exists()) {
            return null
        }

        return root.dir.walkTopDown()
            .maxDepth(3)
            .filter { it.isFile && it.length() > 0L }
            .filter { candidate -> isSupportedFile(candidate, root.runtime) }
            .filter { candidate -> matchesExpectedModel(candidate.name, root.runtime) }
            .map { file ->
                val manifest = readManifest(file)
                val runtime = manifest?.runtime ?: root.runtime
                Candidate(
                    file = file,
                    modelVersion = manifest?.modelVersion?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension,
                    backend = manifest?.preferredBackend ?: LocalModelBackend.CPU,
                    runtime = runtime,
                    maxTokens = manifest?.maxTokens ?: DefaultMaxTokens
                )
            }
            .filter { it.runtime == root.runtime }
            .sortedWith(
                compareByDescending<Candidate> { candidate -> preferredRank(candidate.file.name, candidate.runtime) }
                    .thenByDescending { it.file.lastModified() }
            )
            .firstOrNull()
    }

    private fun isSupportedFile(file: File, runtime: LocalModelRuntime): Boolean =
        file.extension.lowercase() in when (runtime) {
            LocalModelRuntime.MEDIAPIPE -> MediaPipeExtensions
            LocalModelRuntime.LLAMA_CPP -> LlamaCppExtensions
        }

    private fun matchesExpectedModel(fileName: String, runtime: LocalModelRuntime): Boolean {
        val normalized = fileName.lowercase()
        return when (runtime) {
            LocalModelRuntime.MEDIAPIPE -> normalized.contains("gemma") && normalized.contains("1b")
            LocalModelRuntime.LLAMA_CPP -> QwenFilenameMarkers.any(normalized::contains)
        }
    }

    private fun preferredRank(fileName: String, runtime: LocalModelRuntime): Int {
        val preferred = when (runtime) {
            LocalModelRuntime.MEDIAPIPE -> PreferredMediaPipeModelNames
            LocalModelRuntime.LLAMA_CPP -> PreferredLlamaCppModelNames
        }
        return preferred.indexOf(fileName.lowercase())
            .takeIf { it >= 0 }
            ?.let { preferred.size - it }
            ?: 0
    }

    private fun readManifest(modelFile: File): LocalModelManifest? {
        val sidecars = listOf(
            File(modelFile.parentFile, "scouty_model_manifest.json"),
            File(modelFile.parentFile, "${modelFile.nameWithoutExtension}.json")
        )
        val manifestFile = sidecars.firstOrNull { it.exists() && it.isFile } ?: return null
        return runCatching {
            json.decodeFromString<LocalModelManifest>(manifestFile.readText())
        }.getOrNull()
    }

    private fun copyAtomically(source: File, target: File) {
        val tempFile = File(target.parentFile, "${target.name}.tmp")
        source.inputStream().use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        if (target.exists()) {
            target.delete()
        }
        if (!tempFile.renameTo(target)) {
            tempFile.copyTo(target, overwrite = true)
            tempFile.delete()
        }
    }

    @Serializable
    private data class LocalModelManifest(
        @SerialName("model_version")
        val modelVersion: String? = null,
        @SerialName("preferred_backend")
        val preferredBackendRaw: String? = null,
        @SerialName("runtime")
        val runtimeRaw: String? = null,
        @SerialName("max_tokens")
        val maxTokens: Int? = null
    ) {
        val preferredBackend: LocalModelBackend
            get() = when (preferredBackendRaw?.uppercase()) {
                "GPU" -> LocalModelBackend.GPU
                else -> LocalModelBackend.CPU
            }

        val runtime: LocalModelRuntime?
            get() = when (runtimeRaw?.lowercase()) {
                "llama_cpp" -> LocalModelRuntime.LLAMA_CPP
                "mediapipe" -> LocalModelRuntime.MEDIAPIPE
                else -> null
            }
    }

    private data class ModelSearchRoot(
        val dir: File,
        val runtime: LocalModelRuntime,
        val label: String
    )

    private data class Candidate(
        val file: File,
        val modelVersion: String,
        val backend: LocalModelBackend,
        val runtime: LocalModelRuntime,
        val maxTokens: Int
    )

    private companion object {
        private val MediaPipeExtensions = setOf("task", "litertlm")
        private val LlamaCppExtensions = setOf("gguf")
        private val QwenFilenameMarkers = listOf("qwen2.5", "qwen2", "qwen")
        private val PreferredMediaPipeModelNames = listOf(
            "gemma-3-1b-it-int4.task",
            "gemma3-1b-it-int4.task",
            "gemma-3-1b-it.task",
            "gemma3-1b-it.task",
            "gemma-3-1b.task",
            "gemma3-1b.task",
            "gemma-3-1b-it-int4.litertlm",
            "gemma3-1b-it-int4.litertlm",
            "gemma-3-1b-it.litertlm",
            "gemma3-1b-it.litertlm",
            "gemma-3-1b.litertlm",
            "gemma3-1b.litertlm"
        )
        private val PreferredLlamaCppModelNames = listOf(
            "qwen2.5-1.5b-instruct-q4_k_m.gguf",
            "qwen2_5-1.5b-instruct-q4_k_m.gguf",
            "qwen2.5-1.5b-instruct.gguf",
            "qwen2-5-1.5b-instruct-q4_k_m.gguf"
        )
    }
}

private val LocalModelRuntime.displayName: String
    get() = when (this) {
        LocalModelRuntime.MEDIAPIPE -> "MediaPipe"
        LocalModelRuntime.LLAMA_CPP -> "llama.cpp"
    }

private class MediaPipeLocalLlmRuntimeAdapter(
    private val context: Context
) : LocalLlmRuntimeAdapter {
    override val runtimeLabel: String = "Google AI Edge MediaPipe"

    override suspend fun load(artifact: LocalModelArtifact): LocalLlmLoadedModelHandle = withContext(Dispatchers.Default) {
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(artifact.preparedFile.absolutePath)
            .setMaxTokens(artifact.maxTokens)
            .setPreferredBackend(
                when (artifact.backend) {
                    LocalModelBackend.CPU -> LlmInference.Backend.CPU
                    LocalModelBackend.GPU -> LlmInference.Backend.GPU
                }
            )
            .build()

        val llmInference = LlmInference.createFromOptions(context, options)
        MediaPipeLoadedModel(llmInference)
    }
}

private class MediaPipeLoadedModel(
    private val llmInference: LlmInference
) : LocalLlmLoadedModelHandle {
    override suspend fun generate(prompt: String): String = withContext(Dispatchers.Default) {
        val promptTemplates = PromptTemplates.builder()
            .setUserPrefix("<start_of_turn>user\n")
            .setUserSuffix("\n<end_of_turn>\n")
            .setModelPrefix("<start_of_turn>model\n")
            .setModelSuffix("\n<end_of_turn>\n")
            .setSystemPrefix("<start_of_turn>system\n")
            .setSystemSuffix("\n<end_of_turn>\n")
            .build()
        val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTopK(1)
            .setTopP(0.2f)
            .setTemperature(0.0f)
            .setRandomSeed(7)
            .setPromptTemplates(promptTemplates)
            .build()

        LlmInferenceSession.createFromOptions(llmInference, sessionOptions).use { session ->
            session.addQueryChunk(prompt)
            session.generateResponse()
        }
    }

    override suspend fun close() = withContext(Dispatchers.Default) {
        llmInference.close()
    }
}
