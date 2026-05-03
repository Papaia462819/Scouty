package com.scouty.app.tracks.domain

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import com.scouty.app.tracks.data.TrackModelAssets
import java.io.Closeable
import kotlin.system.measureTimeMillis

class TrackDetector(
    context: Context,
    private val preprocessor: TrackPreprocessor = TrackPreprocessor(),
    private val postprocessor: TrackPostprocessor = TrackPostprocessor(),
) : Closeable {
    private val appContext = context.applicationContext
    private val environment: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var sessionRef: OrtSession? = null
    private val session: OrtSession
        get() {
            sessionRef?.let { return it }
            val model = TrackModelAssets(appContext).ensureModelReady()
            val options = OrtSession.SessionOptions().apply {
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }
            return environment.createSession(model.absolutePath, options).also {
                sessionRef = it
            }
        }

    fun detect(bitmap: Bitmap): TimedTrackDetections {
        lateinit var predictions: List<TrackPrediction>
        val elapsedMs = measureTimeMillis {
            val preprocessed = preprocessor.preprocess(bitmap)
            OnnxTensor.createTensor(
                environment,
                preprocessed.input,
                longArrayOf(1, 3, 640, 640),
            ).use { tensor ->
                session.run(mapOf(inputName() to tensor)).use { result ->
                    val output = result[0].value.toYoloOutput()
                    predictions = postprocessor.process(output, preprocessed.letterbox)
                }
            }
        }
        return TimedTrackDetections(predictions = predictions, elapsedMs = elapsedMs)
    }

    private fun inputName(): String = session.inputNames.first()

    @Suppress("UNCHECKED_CAST")
    private fun Any.toYoloOutput(): Array<FloatArray> {
        val batch = this as Array<Array<FloatArray>>
        return batch.first()
    }

    override fun close() {
        sessionRef?.close()
        sessionRef = null
    }
}

data class TimedTrackDetections(
    val predictions: List<TrackPrediction>,
    val elapsedMs: Long,
)
