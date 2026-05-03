package com.scouty.app.tracks.domain

import kotlin.math.max
import kotlin.math.min

class TrackPostprocessor(
    private val classNames: List<String> = DEFAULT_CLASS_NAMES,
    private val confidenceThreshold: Float = 0.25f,
    private val iouThreshold: Float = 0.45f,
    private val maxDetections: Int = 10,
) {
    fun process(output: Array<FloatArray>, letterbox: LetterboxInfo): List<TrackPrediction> {
        require(output.size == 4 + classNames.size) {
            "Unexpected YOLO output channels: ${output.size}"
        }
        val anchorCount = output[0].size
        require(anchorCount > 0) {
            "Unexpected empty YOLO output"
        }
        require(output.all { it.size == anchorCount }) {
            "Inconsistent YOLO output anchor count"
        }
        val candidates = ArrayList<TrackPrediction>()

        for (anchor in 0 until anchorCount) {
            var bestClass = 0
            var bestScore = output[4][anchor]
            for (classIndex in 1 until classNames.size) {
                val score = output[4 + classIndex][anchor]
                if (score > bestScore) {
                    bestScore = score
                    bestClass = classIndex
                }
            }
            if (bestScore < confidenceThreshold) continue

            val centerX = output[0][anchor]
            val centerY = output[1][anchor]
            val width = output[2][anchor]
            val height = output[3][anchor]
            val box = toSourceBox(centerX, centerY, width, height, letterbox)
            if (box.area <= 1f) continue

            candidates += TrackPrediction(
                classId = bestClass,
                className = classNames[bestClass],
                confidence = bestScore.coerceIn(0f, 1f),
                boundingBox = box,
            )
        }

        return nms(candidates.sortedByDescending { it.confidence })
            .take(maxDetections)
    }

    private fun toSourceBox(
        centerX: Float,
        centerY: Float,
        width: Float,
        height: Float,
        letterbox: LetterboxInfo,
    ): TrackBoundingBox {
        val left = ((centerX - width / 2f) - letterbox.padX) / letterbox.scale
        val top = ((centerY - height / 2f) - letterbox.padY) / letterbox.scale
        val right = ((centerX + width / 2f) - letterbox.padX) / letterbox.scale
        val bottom = ((centerY + height / 2f) - letterbox.padY) / letterbox.scale
        return TrackBoundingBox(
            left = left.coerceIn(0f, letterbox.sourceWidth.toFloat()),
            top = top.coerceIn(0f, letterbox.sourceHeight.toFloat()),
            right = right.coerceIn(0f, letterbox.sourceWidth.toFloat()),
            bottom = bottom.coerceIn(0f, letterbox.sourceHeight.toFloat()),
        )
    }

    private fun nms(predictions: List<TrackPrediction>): List<TrackPrediction> {
        val kept = ArrayList<TrackPrediction>()
        predictions.forEach { candidate ->
            val overlapsExisting = kept.any { existing ->
                candidate.classId == existing.classId &&
                    iou(candidate.boundingBox, existing.boundingBox) > iouThreshold
            }
            if (!overlapsExisting) {
                kept += candidate
            }
        }
        return kept
    }

    private fun iou(a: TrackBoundingBox, b: TrackBoundingBox): Float {
        val intersectionLeft = max(a.left, b.left)
        val intersectionTop = max(a.top, b.top)
        val intersectionRight = min(a.right, b.right)
        val intersectionBottom = min(a.bottom, b.bottom)
        val intersectionWidth = (intersectionRight - intersectionLeft).coerceAtLeast(0f)
        val intersectionHeight = (intersectionBottom - intersectionTop).coerceAtLeast(0f)
        val intersection = intersectionWidth * intersectionHeight
        val union = a.area + b.area - intersection
        return if (union <= 0f) 0f else intersection / union
    }

    companion object {
        val DEFAULT_CLASS_NAMES = listOf(
            "bear_brown",
            "wolf_gray",
            "lynx_eurasian",
            "red_deer",
            "wild_boar",
            "red_fox",
            "dog_domestic",
        )
    }
}
