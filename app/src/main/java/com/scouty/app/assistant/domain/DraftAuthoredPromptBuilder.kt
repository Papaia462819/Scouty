package com.scouty.app.assistant.domain

import com.scouty.app.assistant.model.ModelStatus

class DraftAuthoredPromptBuilder {
    fun build(
        input: GenerationInput,
        modelStatus: ModelStatus
    ): String {
        val isRomanian = input.queryAnalysis.preferredLanguage == "ro"
        val primary = input.retrievedChunks.firstOrNull()
        val draft = draftFor(primary, isRomanian)
        val safetyNote = primary?.safetyNote?.let(::sanitizeParagraph).orEmpty()

        return buildString {
            input.conversationHistory?.let { history ->
                append(history.promptCacheHint.cacheablePrefix)
                appendLine()
            } ?: appendLine("You are Scouty's grounded offline assistant running locally on Android.")
            appendLine("Language: ${if (isRomanian) "Romanian" else "English"}.")
            appendLine("Task: polish DRAFT into a natural, concise answer for the user's question.")
            appendLine("Rules:")
            appendLine("- Use only DRAFT and SAFETY_NOTE.")
            appendLine("- Do not add numbers, place names, route names, distances, durations, or claims not present in DRAFT.")
            appendLine("- If DRAFT lacks the needed information, return DRAFT unchanged.")
            appendLine("- Keep the answer under 60 words.")
            appendLine("- Return plain text only, no markdown and no JSON.")
            appendLine("QUESTION: ${sanitizeSingleLine(input.query, 160)}")
            appendLine("FIELD_CONTEXT: ${buildFieldContext(input)}")
            if (safetyNote.isNotBlank()) {
                appendLine("SAFETY_NOTE: $safetyNote")
            }
            appendLine("RUNTIME: model=${sanitizeSingleLine(modelStatus.modelVersion, 80)} pack=${sanitizeSingleLine(input.knowledgePackStatus.packVersion ?: "unknown", 48)}")
            appendLine("DRAFT: $draft")
            appendLine("ANSWER:")
        }
    }

    fun draftFor(input: GenerationInput): String =
        draftFor(input.retrievedChunks.firstOrNull(), input.queryAnalysis.preferredLanguage == "ro")

    fun draftFor(chunk: RetrievedChunk?, isRomanian: Boolean): String =
        chunk?.synthesizedAnswer?.takeIf { it.isNotBlank() }?.let(::sanitizeParagraph)
            ?: chunk?.shortAnswer?.takeIf { it.isNotBlank() }?.let(::sanitizeParagraph)
            ?: chunk?.body?.let { sanitizeParagraph(it, BodyFallbackChars) }
            ?: if (isRomanian) {
                "Nu există suficient grounding offline pentru un răspuns precis; răspunde conservator și cere detalii concrete."
            } else {
                "There is not enough offline grounding for a precise answer; stay conservative and ask for concrete details."
            }

    private fun buildFieldContext(input: GenerationInput): String {
        val parts = mutableListOf<String>()
        parts += "Battery=${input.context.batteryPercent}%${if (input.context.batterySafe) " BatterySafe" else ""}"
        parts += "GPS=${if (input.context.gpsFixed) "available" else "unstable"}"
        if (input.queryAnalysis.routeContextQuery) {
            input.context.trail?.let { trail ->
                parts += "Trail=${sanitizeSingleLine(trail.name, 80)}"
                trail.markingLabel?.takeIf { it.isNotBlank() }?.let {
                    parts += "Marker=${sanitizeSingleLine(it, 32)}"
                }
                val endpoints = listOfNotNull(trail.fromName, trail.toName).joinToString(" -> ")
                if (endpoints.isNotBlank()) {
                    parts += "Endpoints=${sanitizeSingleLine(endpoints, 80)}"
                }
            }
        }
        if (input.queryAnalysis.gearQuery && input.context.recommendedGear.isNotEmpty()) {
            parts += "Gear=${sanitizeSingleLine(input.context.recommendedGear.joinToString(", "), 120)}"
        }
        return parts.joinToString(" | ").take(320)
    }

    private fun sanitizeSingleLine(value: String?, maxLength: Int): String =
        value.orEmpty().replace("\\s+".toRegex(), " ").trim().take(maxLength)

    private fun sanitizeParagraph(value: String, maxLength: Int = DraftMaxChars): String =
        value
            .replace('\n', ' ')
            .replace("\\s+".toRegex(), " ")
            .trim()
            .take(maxLength)

    private companion object {
        private const val DraftMaxChars = 600
        private const val BodyFallbackChars = 280
    }
}
