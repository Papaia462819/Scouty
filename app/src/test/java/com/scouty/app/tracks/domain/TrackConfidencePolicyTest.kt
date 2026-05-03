package com.scouty.app.tracks.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TrackConfidencePolicyTest {
    private val policy = TrackConfidencePolicy()
    private val box = TrackBoundingBox(0f, 0f, 10f, 10f)

    @Test
    fun probableWhenTopConfidenceIsHighAndClear() {
        val band = policy.band(
            listOf(
                TrackPrediction(0, "bear_brown", 0.82f, box),
                TrackPrediction(6, "dog_domestic", 0.20f, box),
            )
        )

        assertEquals(TrackConfidenceBand.PROBABIL, band)
    }

    @Test
    fun possibleWhenTopConfidenceIsMediumAndClear() {
        val band = policy.band(
            listOf(
                TrackPrediction(5, "red_fox", 0.55f, box),
                TrackPrediction(6, "dog_domestic", 0.20f, box),
            )
        )

        assertEquals(TrackConfidenceBand.POSIBIL, band)
    }

    @Test
    fun uncertainWhenTopTwoAreTooClose() {
        val band = policy.band(
            listOf(
                TrackPrediction(6, "dog_domestic", 0.66f, box),
                TrackPrediction(1, "wolf_gray", 0.60f, box),
            )
        )

        assertEquals(TrackConfidenceBand.INCERT, band)
    }

    @Test
    fun uncertainBelowPossibleThreshold() {
        val band = policy.band(
            listOf(
                TrackPrediction(4, "wild_boar", 0.39f, box),
                TrackPrediction(6, "dog_domestic", 0.10f, box),
            )
        )

        assertEquals(TrackConfidenceBand.INCERT, band)
    }
}
