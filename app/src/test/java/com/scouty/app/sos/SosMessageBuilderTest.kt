package com.scouty.app.sos

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SosMessageBuilderTest {
    @Test
    fun build_includesGpsBatteryTrailAndMedicalData() {
        val message = SosMessageBuilder.build(
            input = SosMessageInput(
                displayName = "Alex",
                latitude = 45.4523456,
                longitude = 25.5432123,
                altitudeMeters = 2341.0,
                accuracyMeters = 8.4f,
                gpsFixed = true,
                batteryPercent = 14,
                batterySafe = true,
                activeTrailName = "Babele - Omu",
                activeTrailRegion = "Bucegi",
                activeTrailProgressPercent = 42,
                activeTrailRemainingKm = 5.25,
                timestampEpochMillis = 1_800_000_000_000
            ),
            settings = SosSettings(
                senderName = "",
                bloodType = "O+",
                medicalNotes = "allergic to penicillin"
            )
        )

        assertTrue(message.contains("Name: Alex"))
        assertTrue(message.contains("45.452346, 25.543212"))
        assertTrue(message.contains("Map: https://maps.google.com/?q=45.452346,25.543212"))
        assertTrue(message.contains("Battery: 14% / Battery Safe"))
        assertTrue(message.contains("Trail: Babele - Omu | Bucegi | progress 42% | 5.3km left"))
        assertTrue(message.contains("Medical: blood O+; allergic to penicillin"))
    }

    @Test
    fun build_omitsMedicalDataWhenDisabled() {
        val message = SosMessageBuilder.build(
            input = SosMessageInput(displayName = "Alex", batteryPercent = 70),
            settings = SosSettings(
                bloodType = "A-",
                medicalNotes = "asthma",
                includeMedicalDetails = false
            )
        )

        assertFalse(message.contains("Medical:"))
    }
}
