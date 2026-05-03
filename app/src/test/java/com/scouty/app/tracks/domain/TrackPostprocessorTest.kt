package com.scouty.app.tracks.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackPostprocessorTest {
    private val letterbox = LetterboxInfo(
        sourceWidth = 640,
        sourceHeight = 640,
        inputSize = 640,
        scale = 1f,
        padX = 0f,
        padY = 0f,
    )

    @Test
    fun decodesYoloOutputAndAppliesNms() {
        val output = Array(11) { FloatArray(3) }
        // Anchor 0: bear.
        output[0][0] = 100f
        output[1][0] = 100f
        output[2][0] = 40f
        output[3][0] = 40f
        output[4][0] = 0.90f

        // Anchor 1: overlapping bear, lower score, should be suppressed.
        output[0][1] = 102f
        output[1][1] = 102f
        output[2][1] = 40f
        output[3][1] = 40f
        output[4][1] = 0.80f

        // Anchor 2: dog, separate box.
        output[0][2] = 400f
        output[1][2] = 400f
        output[2][2] = 60f
        output[3][2] = 60f
        output[10][2] = 0.70f

        val predictions = TrackPostprocessor().process(output, letterbox)

        assertEquals(2, predictions.size)
        assertEquals("bear_brown", predictions[0].className)
        assertEquals("dog_domestic", predictions[1].className)
        assertTrue(predictions[0].boundingBox.left in 79f..81f)
    }

    @Test
    fun keepsOverlappingBoxesForDifferentClasses() {
        val output = Array(11) { FloatArray(2) }
        output[0][0] = 100f
        output[1][0] = 100f
        output[2][0] = 40f
        output[3][0] = 40f
        output[4][0] = 0.90f

        output[0][1] = 102f
        output[1][1] = 102f
        output[2][1] = 40f
        output[3][1] = 40f
        output[10][1] = 0.80f

        val predictions = TrackPostprocessor().process(output, letterbox)

        assertEquals(2, predictions.size)
        assertEquals("bear_brown", predictions[0].className)
        assertEquals("dog_domestic", predictions[1].className)
    }

    @Test
    fun mapsLetterboxedBoxesBackToSourceCoordinates() {
        val output = Array(11) { FloatArray(1) }
        output[0][0] = 320f
        output[1][0] = 320f
        output[2][0] = 100f
        output[3][0] = 50f
        output[4][0] = 0.90f

        val predictions = TrackPostprocessor().process(
            output,
            LetterboxInfo(
                sourceWidth = 1280,
                sourceHeight = 720,
                inputSize = 640,
                scale = 0.5f,
                padX = 0f,
                padY = 140f,
            ),
        )

        val box = predictions.single().boundingBox
        assertTrue(box.left in 539f..541f)
        assertTrue(box.top in 309f..311f)
        assertTrue(box.right in 739f..741f)
        assertTrue(box.bottom in 409f..411f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnexpectedChannelCount() {
        TrackPostprocessor().process(Array(12) { FloatArray(1) }, letterbox)
    }
}
