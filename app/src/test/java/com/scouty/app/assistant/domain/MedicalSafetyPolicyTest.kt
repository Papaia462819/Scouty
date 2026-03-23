package com.scouty.app.assistant.domain

import com.scouty.app.assistant.model.SafetyOutcome
import org.junit.Assert.assertEquals
import org.junit.Test

class MedicalSafetyPolicyTest {
    private val policy = MedicalSafetyPolicy()

    @Test
    fun emergencyMarkers_triggerEscalation() {
        val outcome = policy.evaluate(
            query = "Am deformare și nu pot să calc deloc",
            retrievedChunks = emptyList()
        )

        assertEquals(SafetyOutcome.EMERGENCY_ESCALATION, outcome)
    }

    @Test
    fun cautionMarkers_triggerCaution() {
        val outcome = policy.evaluate(
            query = "Mi-am sucit glezna pe traseu",
            retrievedChunks = emptyList()
        )

        assertEquals(SafetyOutcome.CAUTION, outcome)
    }
}
