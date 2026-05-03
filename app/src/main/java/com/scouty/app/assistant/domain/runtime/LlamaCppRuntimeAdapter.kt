package com.scouty.app.assistant.domain.runtime

import com.scouty.app.assistant.domain.LocalLlmGenerationOptions
import com.scouty.app.assistant.domain.LocalLlmLoadedModelHandle
import com.scouty.app.assistant.domain.LocalLlmPromptCacheHint
import com.scouty.app.assistant.domain.LocalLlmRuntimeAdapter
import com.scouty.app.assistant.domain.LocalLlmSamplerParams
import com.scouty.app.assistant.domain.LocalModelArtifact
import com.scouty.app.assistant.domain.LocalModelRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min

data class LlamaCppLoadParams(
    val contextTokens: Int = 8_192,
    val batchTokens: Int = 512,
    val threads: Int = defaultLlamaThreadCount(),
    val useMmap: Boolean = true,
    val useMlock: Boolean = false
)

class LlamaCppRuntimeAdapter(
    private val nativeBridgeFactory: () -> LlamaCppNativeBridge = { LlamaCppNativeBridge() }
) : LocalLlmRuntimeAdapter {
    override val runtimeLabel: String = "llama.cpp"

    override suspend fun load(artifact: LocalModelArtifact): LocalLlmLoadedModelHandle {
        require(artifact.runtime == LocalModelRuntime.LLAMA_CPP) {
            "llama.cpp runtime cannot load ${artifact.runtime}"
        }
        return loadModel(
            path = artifact.preparedFile.absolutePath,
            params = LlamaCppLoadParams(contextTokens = artifact.maxTokens)
        )
    }

    suspend fun loadModel(
        path: String,
        params: LlamaCppLoadParams = LlamaCppLoadParams()
    ): LlamaCppLoadedModel = withContext(Dispatchers.Default) {
        require(File(path).isFile) { "GGUF model does not exist: $path" }
        val nativeBridge = nativeBridgeFactory()
        val handle = nativeBridge.loadModel(
            modelPath = path,
            contextTokens = params.contextTokens,
            batchTokens = params.batchTokens,
            threads = params.threads,
            useMmap = params.useMmap,
            useMlock = params.useMlock
        )
        LlamaCppLoadedModel(
            nativeHandle = handle,
            nativeBridge = nativeBridge,
            promptTemplate = LlamaCppPromptTemplate.forModelPath(path)
        )
    }

}

private fun defaultLlamaThreadCount(): Int =
    min(4, max(2, Runtime.getRuntime().availableProcessors() - 1))

class LlamaCppLoadedModel internal constructor(
    private var nativeHandle: Long,
    private val nativeBridge: LlamaCppNativeBridge,
    private val promptTemplate: LlamaCppPromptTemplate = LlamaCppPromptTemplate.Raw
) : LocalLlmLoadedModelHandle {
    override suspend fun generate(prompt: String): String =
        generate(prompt, grammar = null, sampler = LocalLlmSamplerParams())

    override suspend fun generate(prompt: String, options: LocalLlmGenerationOptions): String =
        generate(prompt, grammar = options.grammar, sampler = options.sampler, options = options)

    suspend fun generate(
        prompt: String,
        grammar: String?,
        sampler: LocalLlmSamplerParams
    ): String = generate(
        prompt = prompt,
        grammar = grammar,
        sampler = sampler,
        options = LocalLlmGenerationOptions(grammar = grammar, sampler = sampler)
    )

    private suspend fun generate(
        prompt: String,
        grammar: String?,
        sampler: LocalLlmSamplerParams,
        options: LocalLlmGenerationOptions
    ): String = withContext(Dispatchers.Default) {
        val handle = requireLoaded()
        val formatted = promptTemplate.format(prompt, options.promptCacheHint)
        nativeBridge.generate(
            handle = handle,
            prompt = formatted.prompt,
            grammar = grammar,
            maxTokens = sampler.maxTokens,
            temperature = sampler.temperature,
            topK = sampler.topK,
            topP = sampler.topP,
            randomSeed = sampler.randomSeed,
            promptCacheKey = formatted.promptCacheHint?.key,
            promptCachePrefix = formatted.promptCacheHint?.cacheablePrefix
        )
    }

    suspend fun release() {
        close()
    }

    override suspend fun close() = withContext(Dispatchers.Default) {
        val handle = nativeHandle
        if (handle != 0L) {
            nativeBridge.release(handle)
            nativeHandle = 0L
        }
    }

    private fun requireLoaded(): Long =
        nativeHandle.takeIf { it != 0L } ?: error("llama.cpp model is not loaded")
}

internal data class FormattedPrompt(
    val prompt: String,
    val promptCacheHint: LocalLlmPromptCacheHint?
)

internal sealed class LlamaCppPromptTemplate {
    internal abstract fun format(prompt: String, promptCacheHint: LocalLlmPromptCacheHint?): FormattedPrompt

    data object Raw : LlamaCppPromptTemplate() {
        override fun format(prompt: String, promptCacheHint: LocalLlmPromptCacheHint?): FormattedPrompt =
            FormattedPrompt(prompt = prompt, promptCacheHint = promptCacheHint)
    }

    data object QwenChatMl : LlamaCppPromptTemplate() {
        override fun format(prompt: String, promptCacheHint: LocalLlmPromptCacheHint?): FormattedPrompt {
            if (prompt.trimStart().startsWith("<|im_start|>")) {
                return FormattedPrompt(prompt = prompt, promptCacheHint = promptCacheHint)
            }
            val userPrompt = prompt.trim()
            val prefix = buildString {
                append("<|im_start|>system\n")
                append(QwenSystemPrompt)
                append("\n<|im_end|>\n")
                append("<|im_start|>user\n")
            }
            val suffix = "\n<|im_end|>\n<|im_start|>assistant\n"
            val formattedCacheHint = promptCacheHint?.let { hint ->
                LocalLlmPromptCacheHint(
                    key = hint.key,
                    cacheablePrefix = prefix + hint.cacheablePrefix
                )
            }
            return FormattedPrompt(
                prompt = prefix + userPrompt + suffix,
                promptCacheHint = formattedCacheHint
            )
        }
    }

    companion object {
        fun forModelPath(path: String): LlamaCppPromptTemplate {
            val fileName = File(path).name.lowercase()
            return if (fileName.contains("qwen")) {
                QwenChatMl
            } else {
                Raw
            }
        }
    }
}

private val QwenSystemPrompt = """
Esti Scouty, asistentul offline pentru drumetii in Romania.
Raspunde in romana naturala si concisa, cu prudenta pentru siguranta pe munte.
Foloseste doar informatiile primite in prompt si nu inventa fapte.
""".trimIndent()

class LlamaCppNativeBridge {
    init {
        System.loadLibrary("scouty_llama_jni")
    }

    external fun loadModel(
        modelPath: String,
        contextTokens: Int,
        batchTokens: Int,
        threads: Int,
        useMmap: Boolean,
        useMlock: Boolean
    ): Long

    external fun generate(
        handle: Long,
        prompt: String,
        grammar: String?,
        maxTokens: Int,
        temperature: Float,
        topK: Int,
        topP: Float,
        randomSeed: Int,
        promptCacheKey: String?,
        promptCachePrefix: String?
    ): String

    external fun release(handle: Long)
}
