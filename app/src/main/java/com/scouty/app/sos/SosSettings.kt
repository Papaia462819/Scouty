package com.scouty.app.sos

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class SosAction {
    CALL_112,
    CALL_SALVAMONT,
    TEXT_ONLY,
    TEXT_THEN_CALL_112,
    TEXT_THEN_CALL_SALVAMONT;

    val includesText: Boolean
        get() = this == TEXT_ONLY || this == TEXT_THEN_CALL_112 || this == TEXT_THEN_CALL_SALVAMONT

    val callNumber: String?
        get() = when (this) {
            CALL_112, TEXT_THEN_CALL_112 -> EmergencyNumber
            CALL_SALVAMONT, TEXT_THEN_CALL_SALVAMONT -> SalvamontDialNumber
            TEXT_ONLY -> null
        }

    val title: String
        get() = when (this) {
            CALL_112 -> "Call 112"
            CALL_SALVAMONT -> "Call Salvamont"
            TEXT_ONLY -> "Text contacts"
            TEXT_THEN_CALL_112 -> "Text, then call 112"
            TEXT_THEN_CALL_SALVAMONT -> "Text, then call Salvamont"
        }

    val description: String
        get() = when (this) {
            CALL_112 -> "Opens the dialer with 112."
            CALL_SALVAMONT -> "Opens the dialer with 0SALVAMONT."
            TEXT_ONLY -> "Opens SMS with your rescue message."
            TEXT_THEN_CALL_112 -> "Opens SMS first, then dials 112 when you return."
            TEXT_THEN_CALL_SALVAMONT -> "Opens SMS first, then dials Salvamont when you return."
        }

    companion object {
        const val EmergencyNumber = "112"
        const val SalvamontDialNumber = "0725826668"

        fun fromStored(value: String?): SosAction =
            entries.firstOrNull { it.name == value } ?: CALL_112
    }
}

data class SosSettings(
    val holdSeconds: Int = DefaultHoldSeconds,
    val action: SosAction = SosAction.CALL_112,
    val smsRecipientsRaw: String = "",
    val contacts: List<SosContact> = emptyList(),
    val senderName: String = "",
    val bloodType: String = "",
    val medicalNotes: String = "",
    val includeMedicalDetails: Boolean = true
) {
    val smsRecipients: List<String>
        get() = (contacts.filter { it.enabled }.map { it.phone } + legacyRawRecipients())
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

    fun normalized(): SosSettings =
        copy(
            holdSeconds = holdSeconds.coerceIn(MinHoldSeconds, MaxHoldSeconds),
            contacts = contacts.distinctBy { it.dedupeKey }
        )

    private fun legacyRawRecipients(): List<String> =
        smsRecipientsRaw
            .split(',', ';', '\n')
            .map { it.trim() }
            .filter { it.isNotBlank() }

    companion object {
        const val DefaultHoldSeconds = 5
        const val MinHoldSeconds = 2
        const val MaxHoldSeconds = 10
    }
}

@Serializable
data class SosContact(
    val id: String,
    val name: String,
    val phone: String,
    val enabled: Boolean = true
) {
    val dedupeKey: String
        get() = phone.filter(Char::isDigit).ifBlank { phone.trim().lowercase() }
}

class SosSettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): SosSettings =
        SosSettings(
            holdSeconds = prefs.getInt(KeyHoldSeconds, SosSettings.DefaultHoldSeconds),
            action = SosAction.fromStored(prefs.getString(KeyAction, null)),
            smsRecipientsRaw = prefs.getString(KeySmsRecipients, "").orEmpty(),
            contacts = loadContacts(),
            senderName = prefs.getString(KeySenderName, "").orEmpty(),
            bloodType = prefs.getString(KeyBloodType, "").orEmpty(),
            medicalNotes = prefs.getString(KeyMedicalNotes, "").orEmpty(),
            includeMedicalDetails = prefs.getBoolean(KeyIncludeMedicalDetails, true)
        ).normalized()

    fun save(settings: SosSettings) {
        val normalized = settings.normalized()
        prefs.edit()
            .putInt(KeyHoldSeconds, normalized.holdSeconds)
            .putString(KeyAction, normalized.action.name)
            .putString(KeySmsRecipients, normalized.smsRecipientsRaw.trim())
            .putString(KeyContacts, json.encodeToString(normalized.contacts))
            .putString(KeySenderName, normalized.senderName.trim())
            .putString(KeyBloodType, normalized.bloodType.trim())
            .putString(KeyMedicalNotes, normalized.medicalNotes.trim())
            .putBoolean(KeyIncludeMedicalDetails, normalized.includeMedicalDetails)
            .apply()
    }

    private fun loadContacts(): List<SosContact> {
        val payload = prefs.getString(KeyContacts, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(SosContact.serializer()), payload)
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val PreferencesName = "scouty_sos_settings"
        const val KeyHoldSeconds = "hold_seconds"
        const val KeyAction = "action"
        const val KeySmsRecipients = "sms_recipients"
        const val KeyContacts = "contacts_json"
        const val KeySenderName = "sender_name"
        const val KeyBloodType = "blood_type"
        const val KeyMedicalNotes = "medical_notes"
        const val KeyIncludeMedicalDetails = "include_medical_details"
    }
}
