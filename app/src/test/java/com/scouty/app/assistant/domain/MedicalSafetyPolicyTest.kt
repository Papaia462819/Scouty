package com.scouty.app.assistant.domain

import com.scouty.app.assistant.model.SafetyOutcome
import org.junit.Assert.assertEquals
import org.junit.Test

class MedicalSafetyPolicyTest {
    private val policy = MedicalSafetyPolicy()

    // --- Emergency escalation: Romanian ---

    @Test
    fun deformity_ro_triggersEscalation() {
        val outcome = policy.evaluate("Am deformare și nu pot să calc deloc", emptyList())
        assertEquals(SafetyOutcome.EMERGENCY_ESCALATION, outcome)
    }

    @Test
    fun massiveBleeding_ro_triggersEscalation() {
        val outcome = policy.evaluate("Am sângerare masivă", emptyList())
        assertEquals(SafetyOutcome.EMERGENCY_ESCALATION, outcome)
    }

    @Test
    fun unconscious_ro_triggersEscalation() {
        val outcome = policy.evaluate("Persoana e inconștientă", emptyList())
        assertEquals(SafetyOutcome.EMERGENCY_ESCALATION, outcome)
    }

    @Test
    fun notBreathing_ro_triggersEscalation() {
        val outcome = policy.evaluate("Nu respiră, ce fac?", emptyList())
        assertEquals(SafetyOutcome.EMERGENCY_ESCALATION, outcome)
    }

    @Test
    fun chestPain_ro_triggersEscalation() {
        val outcome = policy.evaluate("Am durere în piept", emptyList())
        assertEquals(SafetyOutcome.EMERGENCY_ESCALATION, outcome)
    }

    @Test
    fun seizure_ro_triggersEscalation() {
        val outcome = policy.evaluate("Convulsii, ce fac?", emptyList())
        assertEquals(SafetyOutcome.EMERGENCY_ESCALATION, outcome)
    }

    @Test
    fun fainting_ro_triggersEscalation() {
        val outcome = policy.evaluate("Colegul meu a leșinat pe traseu", emptyList())
        assertEquals(SafetyOutcome.EMERGENCY_ESCALATION, outcome)
    }

    @Test
    fun cantWalk_ro_triggersEscalation() {
        val outcome = policy.evaluate("Nu pot să calc deloc", emptyList())
        assertEquals(SafetyOutcome.EMERGENCY_ESCALATION, outcome)
    }

    // --- Emergency escalation: English ---

    @Test
    fun cantWalk_en_triggersEscalation() {
        val outcome = policy.evaluate("I can't walk, my leg is deformed", emptyList())
        assertEquals(SafetyOutcome.EMERGENCY_ESCALATION, outcome)
    }

    @Test
    fun unconscious_en_triggersEscalation() {
        val outcome = policy.evaluate("Person is unconscious and cold", emptyList())
        assertEquals(SafetyOutcome.EMERGENCY_ESCALATION, outcome)
    }

    @Test
    fun notBreathing_en_triggersEscalation() {
        val outcome = policy.evaluate("Person is not breathing, what do I do?", emptyList())
        assertEquals(SafetyOutcome.EMERGENCY_ESCALATION, outcome)
    }

    @Test
    fun chestPain_en_triggersEscalation() {
        val outcome = policy.evaluate("I have chest pain and can't breathe well", emptyList())
        assertEquals(SafetyOutcome.EMERGENCY_ESCALATION, outcome)
    }

    @Test
    fun seizure_en_triggersEscalation() {
        val outcome = policy.evaluate("My friend is having a seizure", emptyList())
        assertEquals(SafetyOutcome.EMERGENCY_ESCALATION, outcome)
    }

    @Test
    fun collapsed_en_triggersEscalation() {
        val outcome = policy.evaluate("Someone collapsed from heat", emptyList())
        assertEquals(SafetyOutcome.EMERGENCY_ESCALATION, outcome)
    }

    @Test
    fun massiveBleeding_en_triggersEscalation() {
        val outcome = policy.evaluate("There is massive bleeding", emptyList())
        assertEquals(SafetyOutcome.EMERGENCY_ESCALATION, outcome)
    }

    // --- Caution: Romanian ---

    @Test
    fun ankleSprain_ro_triggersCaution() {
        val outcome = policy.evaluate("Mi-am sucit glezna pe traseu", emptyList())
        assertEquals(SafetyOutcome.CAUTION, outcome)
    }

    @Test
    fun bear_ro_triggersCaution() {
        val outcome = policy.evaluate("Am văzut urme de urs", emptyList())
        assertEquals(SafetyOutcome.CAUTION, outcome)
    }

    @Test
    fun snake_ro_triggersCaution() {
        val outcome = policy.evaluate("M-a mușcat un șarpe", emptyList())
        assertEquals(SafetyOutcome.CAUTION, outcome)
    }

    @Test
    fun lightning_ro_triggersCaution() {
        val outcome = policy.evaluate("E furtună cu fulgere", emptyList())
        assertEquals(SafetyOutcome.CAUTION, outcome)
    }

    @Test
    fun hypothermia_ro_triggersCaution() {
        val outcome = policy.evaluate("Tremur puternic, confuzie", emptyList())
        assertEquals(SafetyOutcome.CAUTION, outcome)
    }

    @Test
    fun dehydration_ro_triggersCaution() {
        val outcome = policy.evaluate("Cred că am deshidratare", emptyList())
        assertEquals(SafetyOutcome.CAUTION, outcome)
    }

    // --- Caution: English ---

    @Test
    fun ankle_en_triggersCaution() {
        val outcome = policy.evaluate("I twisted my ankle", emptyList())
        assertEquals(SafetyOutcome.CAUTION, outcome)
    }

    @Test
    fun bear_en_triggersCaution() {
        val outcome = policy.evaluate("I see bear tracks nearby", emptyList())
        assertEquals(SafetyOutcome.CAUTION, outcome)
    }

    @Test
    fun snake_en_triggersCaution() {
        val outcome = policy.evaluate("I got bitten by a snake", emptyList())
        assertEquals(SafetyOutcome.CAUTION, outcome)
    }

    @Test
    fun lightning_en_triggersCaution() {
        val outcome = policy.evaluate("Lightning storm approaching", emptyList())
        assertEquals(SafetyOutcome.CAUTION, outcome)
    }

    @Test
    fun dehydration_en_triggersCaution() {
        val outcome = policy.evaluate("Signs of dehydration", emptyList())
        assertEquals(SafetyOutcome.CAUTION, outcome)
    }

    @Test
    fun bleeding_en_triggersCaution() {
        val outcome = policy.evaluate("How do I stop a bleeding wound?", emptyList())
        assertEquals(SafetyOutcome.CAUTION, outcome)
    }

    // --- Normal ---

    @Test
    fun waterPurification_ro_isNormal() {
        val outcome = policy.evaluate("Cum pot purifica apa?", emptyList())
        assertEquals(SafetyOutcome.NORMAL, outcome)
    }

    @Test
    fun waterPurification_en_isNormal() {
        val outcome = policy.evaluate("How can I purify water?", emptyList())
        assertEquals(SafetyOutcome.NORMAL, outcome)
    }

    @Test
    fun campfire_isNormal() {
        val outcome = policy.evaluate("How to build a safe campfire?", emptyList())
        assertEquals(SafetyOutcome.NORMAL, outcome)
    }

    @Test
    fun navigation_lost_triggersCaution() {
        // "lost" is a caution marker - being lost on a mountain is a safety concern
        val outcome = policy.evaluate("I'm lost on the mountain", emptyList())
        assertEquals(SafetyOutcome.CAUTION, outcome)
    }

    @Test
    fun shelter_isNormal() {
        val outcome = policy.evaluate("Cum îmi fac un adăpost improvizat?", emptyList())
        assertEquals(SafetyOutcome.NORMAL, outcome)
    }

    // --- Edge cases ---

    @Test
    fun emptyQuery_isNormal() {
        val outcome = policy.evaluate("", emptyList())
        assertEquals(SafetyOutcome.NORMAL, outcome)
    }

    @Test
    fun escalationFromChunks_notJustQuery() {
        val chunks = listOf(
            RetrievedChunk(
                topic = "bleeding",
                sourceTitle = "Test",
                sectionTitle = "Sângerare masivă",
                body = "Hemoragie care pune viața în pericol",
                score = 10
            )
        )
        val outcome = policy.evaluate("ajutor", chunks)
        assertEquals(SafetyOutcome.EMERGENCY_ESCALATION, outcome)
    }
}
