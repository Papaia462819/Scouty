package com.scouty.app.tracks.domain

import java.io.File

enum class TrackConfidenceBand(val labelRo: String) {
    PROBABIL("Probabil"),
    POSIBIL("Posibil"),
    INCERT("Incert"),
}

data class TrackBoundingBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
    val area: Float get() = width * height
}

data class TrackPrediction(
    val classId: Int,
    val className: String,
    val confidence: Float,
    val boundingBox: TrackBoundingBox,
)

data class TrackIdentificationResult(
    val imageFile: File?,
    val predictions: List<TrackPrediction>,
    val band: TrackConfidenceBand,
    val elapsedMs: Long,
) {
    val topPrediction: TrackPrediction? get() = predictions.firstOrNull()
}

data class LetterboxInfo(
    val sourceWidth: Int,
    val sourceHeight: Int,
    val inputSize: Int,
    val scale: Float,
    val padX: Float,
    val padY: Float,
)
