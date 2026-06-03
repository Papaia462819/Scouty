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
    val sourcePriority: Double
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
            .let { batch ->
                val scores = scorePairs(query, batch)
                batch.mapIndexed { index, chunk ->
                    RerankedChunk(
                        chunk = chunk,
                        score = scores.getOrElse(index) { 0.0 },
                        originalRank = index,
                        sourcePriority = chunk.priority.toDouble()
                    )
                }
            }
            .sortedWith(
                compareByDescending<RerankedChunk> { it.score }
                    .thenBy { it.originalRank }
            )
    }

    private fun scorePairs(query: String, chunks: List<Chunk>): List<Double> {
        if (chunks.isEmpty()) {
            return emptyList()
        }
        val encoded = chunks.map { chunk -> encode(query, chunk) }
        val batchSize = encoded.size
        val sequenceLength = encoded.first().inputIds.size
        val feeds = buildMap<String, OnnxTensor> {
            put("input_ids", tensor(encoded.flatMapLongArray { it.inputIds }, batchSize, sequenceLength))
            put("attention_mask", tensor(encoded.flatMapLongArray { it.attentionMask }, batchSize, sequenceLength))
            if ("token_type_ids" in session.inputNames) {
                put("token_type_ids", tensor(encoded.flatMapLongArray { it.tokenTypeIds }, batchSize, sequenceLength))
            }
        }

        return feeds.values.useAll {
            session.run(feeds).use { result ->
                extractScores(result[0].value, batchSize)
            }
        }
    }

    private fun tensor(values: LongArray, batchSize: Int, sequenceLength: Int): OnnxTensor =
        OnnxTensor.createTensor(
            environment,
            LongBuffer.wrap(values),
            longArrayOf(batchSize.toLong(), sequenceLength.toLong())
        )

    @Suppress("UNCHECKED_CAST")
    private fun extractScores(value: Any?, batchSize: Int): List<Double> {
        val rawScores = when (value) {
            is FloatArray -> value.map { it.toDouble() }
            is DoubleArray -> value.toList()
            is Array<*> -> value.mapNotNull(::firstNumber)
            else -> listOfNotNull(firstNumber(value))
        }
        return rawScores
            .take(batchSize)
            .map(::sigmoid)
            .let { scores ->
                if (scores.size == batchSize) scores else scores + List(batchSize - scores.size) { 0.0 }
            }
    }

    private inline fun List<EncodedPair>.flatMapLongArray(
        selector: (EncodedPair) -> LongArray
    ): LongArray {
        val sequenceLength = selector(first()).size
        val flattened = LongArray(size * sequenceLength)
        forEachIndexed { rowIndex, encoded ->
            val row = selector(encoded)
            row.copyInto(
                destination = flattened,
                destinationOffset = rowIndex * sequenceLength,
                startIndex = 0,
                endIndex = sequenceLength
            )
        }
        return flattened
    }

    private fun encode(query: String, chunk: Chunk): EncodedPair {
        val passage = listOf(chunk.title, chunk.synthesizedAnswer?.takeIf { it.isNotBlank() } ?: chunk.body)
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
                runCatching { addNnapi() }
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
        private const val ModelFileName = "jina-reranker-v2-base-multilingual-int8.onnx"
        private const val TokenizerFileName = "jina-reranker-v2-base-multilingual-tokenizer.json"
        private const val ModelAssetPath = "ml/$ModelFileName"
        private const val TokenizerAssetPath = "ml/$TokenizerFileName"
        private const val ModelSha256 = "c5220cf8fe023f8aa0ed2a3eb787d4451a7f17cf53f6b787e35718dd4b8815c3"
        private const val TokenizerSha256 = "3a56def25aa40facc030ea8b0b87f3688e4b3c39eb8b45d5702b3a1300fe2a20"
        private const val MaxSequenceLength = 96
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
