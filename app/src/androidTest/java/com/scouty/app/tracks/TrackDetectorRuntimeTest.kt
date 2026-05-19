package com.scouty.app.tracks

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.scouty.app.tracks.data.TrackModelAssets
import com.scouty.app.tracks.domain.TrackDetector
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackDetectorRuntimeTest {
    @Test
    fun yoloTrackModelCopiesAndRunsInferenceOnDevice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelFile = TrackModelAssets(context).ensureModelReady()

        assertTrue("Track ONNX model was not prepared on device.", modelFile.isFile)
        assertTrue("Track ONNX model is unexpectedly small.", modelFile.length() > 1_000_000L)

        val bitmap = Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(114, 114, 114))
        }

        TrackDetector(context).use { detector ->
            val detections = detector.detect(bitmap)
            assertTrue("Track detector returned a negative runtime.", detections.elapsedMs >= 0L)
            assertTrue("Track detector returned too many boxes.", detections.predictions.size <= 10)
        }
        bitmap.recycle()
    }
}
