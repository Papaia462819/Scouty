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
        language: SosMessageLanguage = SosMessageLanguage.Romanian
    ): String {
        val lines = mutableListOf<String>()
        val name = settings.senderName.ifBlank { input.displayName.orEmpty() }.trim()

        lines += when (language) {
            SosMessageLanguage.English -> "Ajutor! (SOS Scouty)"
            SosMessageLanguage.Romanian -> "Ajutor! (SOS Scouty)"
        }
        if (name.isNotBlank()) {
            lines += when (language) {
                SosMessageLanguage.English -> "Nume: $name"
                SosMessageLanguage.Romanian -> "Nume: $name"
            }
        }

        if (input.latitude != null && input.longitude != null) {
            val lat = formatCoordinate(input.latitude)
            val lon = formatCoordinate(input.longitude)
            val accuracy = input.accuracyMeters?.let { " +/-${it.toInt()}m" }.orEmpty()
            val altitude = input.altitudeMeters?.let { " alt ${it.toInt()}m" }.orEmpty()
            lines += when (language) {
                SosMessageLanguage.English -> "Locație: $lat, $lon$accuracy$altitude"
                SosMessageLanguage.Romanian -> "Locație: $lat, $lon$accuracy$altitude"
            }
            lines += when (language) {
                SosMessageLanguage.English -> "Hartă: https://maps.google.com/?q=$lat,$lon"
                SosMessageLanguage.Romanian -> "Hartă: https://maps.google.com/?q=$lat,$lon"
            }
        } else {
            val label = input.locationName.ifBlank { "fără etichetă recentă de locație" }
            lines += when (language) {
                SosMessageLanguage.English -> "Locație: fără fix GPS ($label)"
                SosMessageLanguage.Romanian -> "Locație: fără fix GPS ($label)"
            }
        }

        lines += when (language) {
            SosMessageLanguage.English ->
                "Baterie: ${input.batteryPercent}%${if (input.batterySafe) " / economisire activă" else ""}"
            SosMessageLanguage.Romanian ->
                "Baterie: ${input.batteryPercent}%${if (input.batterySafe) " / economisire activă" else ""}"
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
            SosMessageLanguage.English -> "Ora: ${formatTimestamp(input.timestampEpochMillis)}"
            SosMessageLanguage.Romanian -> "Ora: ${formatTimestamp(input.timestampEpochMillis)}"
        }
        return lines.joinToString(separator = "\n")
    }

    fun buildBilingual(input: SosMessageInput, settings: SosSettings): String =
        build(input, settings, SosMessageLanguage.Romanian)

    private fun buildTrailLine(input: SosMessageInput, language: SosMessageLanguage): String? {
        val trailName = input.activeTrailName?.takeIf { it.isNotBlank() } ?: return null
        val parts = mutableListOf(
            when (language) {
                SosMessageLanguage.English -> "Traseu: $trailName"
                SosMessageLanguage.Romanian -> "Traseu: $trailName"
            }
        )
        input.activeTrailRegion?.takeIf { it.isNotBlank() }?.let { parts += it }
        input.activeTrailProgressPercent?.let {
            parts += when (language) {
                SosMessageLanguage.English -> "progres $it%"
                SosMessageLanguage.Romanian -> "progres $it%"
            }
        }
        input.activeTrailRemainingKm?.let {
            parts += when (language) {
                SosMessageLanguage.English -> "${String.format(Locale.US, "%.1f", it)} km rămași"
                SosMessageLanguage.Romanian -> "${String.format(Locale.US, "%.1f", it)} km rămași"
            }
        }
        return parts.joinToString(" | ")
    }

    private fun buildMedicalLine(settings: SosSettings, language: SosMessageLanguage): String? {
        if (!settings.includeMedicalDetails) return null

        val medicalParts = mutableListOf<String>()
        settings.bloodType.trim().takeIf { it.isNotBlank() }?.let {
            medicalParts += when (language) {
                SosMessageLanguage.English -> "grupa $it"
                SosMessageLanguage.Romanian -> "grupa $it"
            }
        }
        settings.medicalNotes.trim().takeIf { it.isNotBlank() }?.let { medicalParts += it }
        if (medicalParts.isEmpty()) return null
        return when (language) {
            SosMessageLanguage.English -> "Date medicale: ${medicalParts.joinToString("; ")}"
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
