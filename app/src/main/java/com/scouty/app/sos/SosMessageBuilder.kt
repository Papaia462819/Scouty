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

object SosMessageBuilder {
    private val TimestampFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z", Locale.US)

    fun build(input: SosMessageInput, settings: SosSettings): String {
        val lines = mutableListOf<String>()
        val name = settings.senderName.ifBlank { input.displayName.orEmpty() }.trim()

        lines += "SOS Scouty"
        if (name.isNotBlank()) {
            lines += "Name: $name"
        }

        if (input.latitude != null && input.longitude != null) {
            val lat = formatCoordinate(input.latitude)
            val lon = formatCoordinate(input.longitude)
            val accuracy = input.accuracyMeters?.let { " +/-${it.toInt()}m" }.orEmpty()
            val altitude = input.altitudeMeters?.let { " alt ${it.toInt()}m" }.orEmpty()
            lines += "Location: $lat, $lon$accuracy$altitude"
            lines += "Map: https://maps.google.com/?q=$lat,$lon"
        } else {
            val label = input.locationName.ifBlank { "no recent location label" }
            lines += "Location: no GPS fix ($label)"
        }

        lines += "Battery: ${input.batteryPercent}%${if (input.batterySafe) " / Battery Safe" else ""}"

        val trailLine = buildTrailLine(input)
        if (trailLine != null) {
            lines += trailLine
        }

        val medicalLine = buildMedicalLine(settings)
        if (medicalLine != null) {
            lines += medicalLine
        }

        lines += "Time: ${formatTimestamp(input.timestampEpochMillis)}"
        lines += "Please reply by SMS if possible."
        return lines.joinToString(separator = "\n")
    }

    private fun buildTrailLine(input: SosMessageInput): String? {
        val trailName = input.activeTrailName?.takeIf { it.isNotBlank() } ?: return null
        val parts = mutableListOf("Trail: $trailName")
        input.activeTrailRegion?.takeIf { it.isNotBlank() }?.let { parts += it }
        input.activeTrailProgressPercent?.let { parts += "progress $it%" }
        input.activeTrailRemainingKm?.let { parts += "${String.format(Locale.US, "%.1f", it)}km left" }
        return parts.joinToString(" | ")
    }

    private fun buildMedicalLine(settings: SosSettings): String? {
        if (!settings.includeMedicalDetails) return null

        val medicalParts = mutableListOf<String>()
        settings.bloodType.trim().takeIf { it.isNotBlank() }?.let { medicalParts += "blood $it" }
        settings.medicalNotes.trim().takeIf { it.isNotBlank() }?.let { medicalParts += it }
        if (medicalParts.isEmpty()) return null
        return "Medical: ${medicalParts.joinToString("; ")}"
    }

    private fun formatCoordinate(value: Double): String =
        String.format(Locale.US, "%.6f", value)

    private fun formatTimestamp(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .format(TimestampFormatter)
}
