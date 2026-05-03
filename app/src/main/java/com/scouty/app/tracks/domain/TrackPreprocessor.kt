package com.scouty.app.tracks.domain

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import java.nio.FloatBuffer
import kotlin.math.min
import kotlin.math.roundToInt

data class PreprocessedTrackImage(
    val input: FloatBuffer,
    val letterbox: LetterboxInfo,
)

class TrackPreprocessor(
    private val inputSize: Int = 640,
    private val padColor: Int = Color.rgb(114, 114, 114),
) {
    fun preprocess(bitmap: Bitmap): PreprocessedTrackImage {
        val scale = min(
            inputSize.toFloat() / bitmap.width.toFloat(),
            inputSize.toFloat() / bitmap.height.toFloat(),
        )
        val scaledWidth = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val scaledHeight = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        val padX = (inputSize - scaledWidth) / 2f
        val padY = (inputSize - scaledHeight) / 2f

        val letterboxed = Bitmap.createBitmap(inputSize, inputSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(letterboxed)
        canvas.drawColor(padColor)
        canvas.drawBitmap(
            bitmap,
            null,
            RectF(padX, padY, padX + scaledWidth.toFloat(), padY + scaledHeight.toFloat()),
            Paint(Paint.FILTER_BITMAP_FLAG),
        )

        val pixels = IntArray(inputSize * inputSize)
        letterboxed.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        val floats = FloatArray(3 * inputSize * inputSize)
        val planeSize = inputSize * inputSize
        pixels.forEachIndexed { index, color ->
            floats[index] = Color.red(color) / 255f
            floats[planeSize + index] = Color.green(color) / 255f
            floats[2 * planeSize + index] = Color.blue(color) / 255f
        }

        letterboxed.recycle()

        return PreprocessedTrackImage(
            input = FloatBuffer.wrap(floats),
            letterbox = LetterboxInfo(
                sourceWidth = bitmap.width,
                sourceHeight = bitmap.height,
                inputSize = inputSize,
                scale = scale,
                padX = padX,
                padY = padY,
            ),
        )
    }
}
