package com.scouty.app.tracks.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File

class TrackIdentificationUseCase(
    context: Context,
    private val detector: TrackDetector = TrackDetector(context),
    private val confidencePolicy: TrackConfidencePolicy = TrackConfidencePolicy(),
) {
    fun identify(imageFile: File): TrackIdentificationResult {
        val bitmap = decodeBitmapWithExifRotation(imageFile)
        val timed = detector.detect(bitmap)
        bitmap.recycle()
        return TrackIdentificationResult(
            imageFile = imageFile,
            predictions = timed.predictions,
            band = confidencePolicy.band(timed.predictions),
            elapsedMs = timed.elapsedMs,
        )
    }

    private fun decodeBitmapWithExifRotation(file: File): Bitmap {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            ?: error("Could not decode image ${file.absolutePath}")
        val exif = ExifInterface(file.absolutePath)
        val rotation = when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (rotation == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(rotation) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        bitmap.recycle()
        return rotated
    }
}
