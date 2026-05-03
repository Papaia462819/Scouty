package com.scouty.app.assistant.domain.retrieval

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import com.scouty.app.assistant.model.KnowledgeChunkRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.nio.LongBuffer
import java.security.MessageDigest
import kotlin.math.exp

typealias Chunk = KnowledgeChunkRecord

data class RerankedChunk(
    val chunk: Chunk,
    val score: Double,
    val originalRank: Int,
    val originalScore: Double
)

class CrossEncoderReranker(
    context: Context,
    private val maxSequenceLength: Int = MaxSequenceLength,
    private val modelAssetPath: String = ModelAssetPath,
    private val tokenizerAssetPath: String = TokenizerAssetPath,
    private val expectedModelSha256: String = ModelSha256,
    private val expectedTokenizerSha256: String = TokenizerSha256
) : Closeable {
    private val appContext = context.applicationContext
    private val environment: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var sessionRef: OrtSession? = null
    private var tokenizerRef: HuggingFaceTokenizer? = null

    suspend fun rerank(
        query: String,
        candidates: List<Chunk>,
        topK: Int
    ): List<RerankedChunk> = withContext(Dispatchers.Default) {
        if (query.isBlank() || candidates.isEmpty() || topK <= 0) {
            return@withContext emptyList()
        }

        candidates
            .take(topK)
            .mapIndexed { index, chunk ->
                val score = scorePair(query, chunk)
                RerankedChunk(
                    chunk = chunk,
                    score = score,
                    originalRank = index,
                    originalScore = chunk.priority.toDouble()
                )
            }
            .sortedWith(
                compareByDescending<RerankedChunk> { it.score }
                    .thenBy { it.originalRank }
            )
    }

    private fun scorePair(query: String, chunk: Chunk): Double {
        val encoded = encode(query, chunk)
        val feeds = buildMap<String, OnnxTensor> {
            put("input_ids", tensor(encoded.inputIds))
            put("attention_mask", tensor(encoded.attentionMask))
            if ("token_type_ids" in session.inputNames) {
                put("token_type_ids", tensor(encoded.tokenTypeIds))
            }
        }

        feeds.values.forEach { tensor ->
            require(tensor.info.shape.contentEquals(longArrayOf(1, encoded.inputIds.size.toLong()))) {
                "Unexpected reranker tensor shape for ${chunk.chunkId}"
            }
        }

        return feeds.values.useAll {
            session.run(feeds).use { result ->
                sigmoid(firstNumber(result[0].value) ?: 0.0)
            }
        }
    }

    private fun encode(query: String, chunk: Chunk): EncodedPair {
        val passage = listOf(chunk.title, chunk.body)
            .joinToString("\n")
            .replace("\\s+".toRegex(), " ")
            .trim()
            .take(MaxPassageChars)
        val encoding = tokenizer.encode(query.take(MaxQueryChars), passage, true, false)
        val rawIds = encoding.ids.take(maxSequenceLength).toLongArray()
        val rawMask = encoding.attentionMask.take(maxSequenceLength).toLongArray()
        val rawTypeIds = encoding.typeIds.take(maxSequenceLength).toLongArray().takeIf { it.isNotEmpty() }
            ?: LongArray(rawIds.size) { 0L }
        return EncodedPair(
            inputIds = rawIds.pad(maxSequenceLength, 1L),
            attentionMask = rawMask.pad(maxSequenceLength, 0L),
            tokenTypeIds = rawTypeIds.pad(maxSequenceLength, 0L)
        )
    }

    private fun tensor(values: LongArray): OnnxTensor =
        OnnxTensor.createTensor(
            environment,
            LongBuffer.wrap(values),
            longArrayOf(1, values.size.toLong())
        )

    private val session: OrtSession
        get() {
            sessionRef?.let { return it }
            val model = ensureAssetReady(
                assetPath = modelAssetPath,
                targetName = ModelFileName,
                expectedSha256 = expectedModelSha256
            )
            val options = OrtSession.SessionOptions().apply {
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }
            return environment.createSession(model.absolutePath, options).also { sessionRef = it }
        }

    private val tokenizer: HuggingFaceTokenizer
        get() {
            tokenizerRef?.let { return it }
            val tokenizerFile = ensureAssetReady(
                assetPath = tokenizerAssetPath,
                targetName = TokenizerFileName,
                expectedSha256 = expectedTokenizerSha256
            )
            return HuggingFaceTokenizer.newInstance(tokenizerFile.toPath()).also { tokenizerRef = it }
        }

    private fun ensureAssetReady(
        assetPath: String,
        targetName: String,
        expectedSha256: String
    ): File {
        val targetDir = File(appContext.noBackupFilesDir, "assistant_reranker").apply { mkdirs() }
        val target = File(targetDir, targetName)
        if (!target.exists() || sha256(target) != expectedSha256) {
            appContext.assets.open(assetPath).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
        val actualHash = sha256(target)
        require(actualHash == expectedSha256) {
            "Reranker asset checksum mismatch for $assetPath. Expected $expectedSha256, got $actualHash"
        }
        return target
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    override fun close() {
        sessionRef?.close()
        sessionRef = null
        tokenizerRef?.close()
        tokenizerRef = null
    }

    private data class EncodedPair(
        val inputIds: LongArray,
        val attentionMask: LongArray,
        val tokenTypeIds: LongArray
    )

    private companion object {
        private const val ModelFileName = "bge-reranker-v2-m3-int8.onnx"
        private const val TokenizerFileName = "bge-reranker-v2-m3-tokenizer.json"
        private const val ModelAssetPath = "ml/$ModelFileName"
        private const val TokenizerAssetPath = "ml/$TokenizerFileName"
        private const val ModelSha256 = "58a25e9fe3b1b10356722c622469b20ca400d24cad0f4b573f1898e54d9d9af5"
        private const val TokenizerSha256 = "dc1d276a8940eeae7b509d43d26cdca9e02d550af445d42db1ec9ff79b2ce147"
        private const val MaxSequenceLength = 512
        private const val MaxQueryChars = 320
        private const val MaxPassageChars = 1800
    }
}

private fun LongArray.pad(size: Int, padValue: Long): LongArray =
    if (this.size >= size) {
        copyOf(size)
    } else {
        LongArray(size) { index -> getOrNull(index) ?: padValue }
    }

private inline fun <T> Iterable<AutoCloseable>.useAll(block: () -> T): T {
    var closeError: Throwable? = null
    try {
        return block()
    } finally {
        forEach { closeable ->
            try {
                closeable.close()
            } catch (error: Throwable) {
                if (closeError == null) {
                    closeError = error
                }
            }
        }
        closeError?.let { throw it }
    }
}

private fun firstNumber(value: Any?): Double? =
    when (value) {
        is Number -> value.toDouble()
        is FloatArray -> value.firstOrNull()?.toDouble()
        is DoubleArray -> value.firstOrNull()
        is LongArray -> value.firstOrNull()?.toDouble()
        is IntArray -> value.firstOrNull()?.toDouble()
        is Array<*> -> value.firstNotNullOfOrNull(::firstNumber)
        else -> null
    }

private fun sigmoid(value: Double): Double {
    val bounded = value.coerceIn(-50.0, 50.0)
    return 1.0 / (1.0 + exp(-bounded))
}
