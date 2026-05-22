package com.scouty.app.sos

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class SosMessageInput(
    val displayName: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitudeMeters: Double? = null,
    val accuracyMeters: Float? = null,
    val gpsFixed: Boolean = false,
    val locationName: String = "",
    val batteryPercent: Int = 0,
    val batterySafe: Boolean = false,
    val activeTrailName: String? = null,
    val activeTrailRegion: String? = null,
    val activeTrailProgressPercent: Int? = null,
    val activeTrailRemainingKm: Double? = null,
    val timestampEpochMillis: Long = System.currentTimeMillis()
)

enum class SosMessageLanguage {
    English,
    Romanian
}

object SosMessageBuilder {
    private val TimestampFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z", Locale.US)

    fun build(
        input: SosMessageInput,
        settings: SosSettings,
        language: SosMessageLanguage = SosMessageLanguage.English
    ): String {
        val lines = mutableListOf<String>()
        val name = settings.senderName.ifBlank { input.displayName.orEmpty() }.trim()

        lines += when (language) {
            SosMessageLanguage.English -> "Help! (SOS Scouty)"
            SosMessageLanguage.Romanian -> "Ajutor! (SOS Scouty)"
        }
        if (name.isNotBlank()) {
            lines += when (language) {
                SosMessageLanguage.English -> "Name: $name"
                SosMessageLanguage.Romanian -> "Nume: $name"
            }
        }

        if (input.latitude != null && input.longitude != null) {
            val lat = formatCoordinate(input.latitude)
            val lon = formatCoordinate(input.longitude)
            val accuracy = input.accuracyMeters?.let { " +/-${it.toInt()}m" }.orEmpty()
            val altitude = input.altitudeMeters?.let { " alt ${it.toInt()}m" }.orEmpty()
            lines += when (language) {
                SosMessageLanguage.English -> "Location: $lat, $lon$accuracy$altitude"
                SosMessageLanguage.Romanian -> "Locatie: $lat, $lon$accuracy$altitude"
            }
            lines += when (language) {
                SosMessageLanguage.English -> "Map: https://maps.google.com/?q=$lat,$lon"
                SosMessageLanguage.Romanian -> "Harta: https://maps.google.com/?q=$lat,$lon"
            }
        } else {
            val label = input.locationName.ifBlank { "no recent location label" }
            lines += when (language) {
                SosMessageLanguage.English -> "Location: no GPS fix ($label)"
                SosMessageLanguage.Romanian -> "Locatie: fara fix GPS ($label)"
            }
        }

        lines += when (language) {
            SosMessageLanguage.English ->
                "Battery: ${input.batteryPercent}%${if (input.batterySafe) " / Battery Safe" else ""}"
            SosMessageLanguage.Romanian ->
                "Baterie: ${input.batteryPercent}%${if (input.batterySafe) " / Battery Safe activ" else ""}"
        }

        val trailLine = buildTrailLine(input, language)
        if (trailLine != null) {
            lines += trailLine
        }

        val medicalLine = buildMedicalLine(settings, language)
        if (medicalLine != null) {
            lines += medicalLine
        }

        lines += when (language) {
            SosMessageLanguage.English -> "Time: ${formatTimestamp(input.timestampEpochMillis)}"
            SosMessageLanguage.Romanian -> "Ora: ${formatTimestamp(input.timestampEpochMillis)}"
        }
        return lines.joinToString(separator = "\n")
    }

    fun buildBilingual(input: SosMessageInput, settings: SosSettings): String =
        listOf(
            build(input, settings, SosMessageLanguage.Romanian),
            build(input, settings, SosMessageLanguage.English)
        ).joinToString(separator = "\n\n---\n\n")

    private fun buildTrailLine(input: SosMessageInput, language: SosMessageLanguage): String? {
        val trailName = input.activeTrailName?.takeIf { it.isNotBlank() } ?: return null
        val parts = mutableListOf(
            when (language) {
                SosMessageLanguage.English -> "Trail: $trailName"
                SosMessageLanguage.Romanian -> "Traseu: $trailName"
            }
        )
        input.activeTrailRegion?.takeIf { it.isNotBlank() }?.let { parts += it }
        input.activeTrailProgressPercent?.let {
            parts += when (language) {
                SosMessageLanguage.English -> "progress $it%"
                SosMessageLanguage.Romanian -> "progres $it%"
            }
        }
        input.activeTrailRemainingKm?.let {
            parts += when (language) {
                SosMessageLanguage.English -> "${String.format(Locale.US, "%.1f", it)}km left"
                SosMessageLanguage.Romanian -> "${String.format(Locale.US, "%.1f", it)} km ramasi"
            }
        }
        return parts.joinToString(" | ")
    }

    private fun buildMedicalLine(settings: SosSettings, language: SosMessageLanguage): String? {
        if (!settings.includeMedicalDetails) return null

        val medicalParts = mutableListOf<String>()
        settings.bloodType.trim().takeIf { it.isNotBlank() }?.let {
            medicalParts += when (language) {
                SosMessageLanguage.English -> "blood $it"
                SosMessageLanguage.Romanian -> "grupa $it"
            }
        }
        settings.medicalNotes.trim().takeIf { it.isNotBlank() }?.let { medicalParts += it }
        if (medicalParts.isEmpty()) return null
        return when (language) {
            SosMessageLanguage.English -> "Medical: ${medicalParts.joinToString("; ")}"
            SosMessageLanguage.Romanian -> "Date medicale: ${medicalParts.joinToString("; ")}"
        }
    }

    private fun formatCoordinate(value: Double): String =
        String.format(Locale.US, "%.6f", value)

    private fun formatTimestamp(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .format(TimestampFormatter)
}
