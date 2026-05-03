package com.scouty.app.tracks.domain

class TrackConfidencePolicy(
    private val probableThreshold: Float = 0.70f,
    private val possibleThreshold: Float = 0.40f,
    private val ambiguousMargin: Float = 0.08f,
) {
    fun band(predictions: List<TrackPrediction>): TrackConfidenceBand {
        val top1 = predictions.getOrNull(0)?.confidence ?: return TrackConfidenceBand.INCERT
        val top2 = predictions.getOrNull(1)?.confidence ?: 0f
        if (top1 < possibleThreshold || top1 - top2 < ambiguousMargin) {
            return TrackConfidenceBand.INCERT
        }
        return if (top1 >= probableThreshold) {
            TrackConfidenceBand.PROBABIL
        } else {
            TrackConfidenceBand.POSIBIL
        }
    }
}
