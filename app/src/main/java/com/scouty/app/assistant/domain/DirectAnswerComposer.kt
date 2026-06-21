package com.scouty.app.assistant.domain

import com.scouty.app.assistant.model.GenerationMode
import com.scouty.app.assistant.model.ModelRuntimeState
import com.scouty.app.assistant.model.ModelStatus
import com.scouty.app.assistant.model.ResponseSectionStyle
import com.scouty.app.assistant.model.StructuredAssistantOutput
import com.scouty.app.assistant.model.StructuredResponseSection
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

class DirectAnswerComposer(
    private val json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false }
) {
    fun compose(
        input: GenerationInput,
        polishedText: String,
        modelStatus: ModelStatus,
        generationMode: GenerationMode = GenerationMode.LOCAL_LLM
    ): StructuredAssistantOutput {
        val isRomanian = input.queryAnalysis.preferredLanguage == "ro"
        val primary = input.retrievedChunks.firstOrNull()
        val summary = sanitizeParagraph(polishedText)
            .takeIf { it.isNotBlank() }
            ?: primary?.shortAnswer?.let(::sanitizeParagraph)?.takeIf { it.isNotBlank() }
            ?: primary?.synthesizedAnswer?.let(::sanitizeParagraph)?.takeIf { it.isNotBlank() }
            ?: primary?.body?.let { sanitizeParagraph(it, 280) }
            ?: "Răspuns prudent pe baza informațiilor locale disponibile."
        val hasPolishedAnswer = sanitizeParagraph(polishedText).isNotBlank()
        val usePolishedOnly = generationMode == GenerationMode.LOCAL_LLM && hasPolishedAnswer

        val sections = mutableListOf<StructuredResponseSection>()
        primary?.safetyNote
            ?.let(::sanitizeParagraph)
            ?.takeIf { it.isNotBlank() && !containsNormalized(summary, it) }
            ?.let { note ->
                sections += StructuredResponseSection(
                    title = "Atenție",
                    body = note,
                    style = ResponseSectionStyle.IMPORTANT
                )
            }

        if (!usePolishedOnly) {
            primary
                ?.groundingCandidates(preferBody = false)
                ?.map { sanitizeParagraph(it, 320) }
                ?.firstOrNull { it.isNotBlank() && !isRedundantWithSummary(summary, it) }
                ?.let { body ->
                    sections += StructuredResponseSection(
                        title = "Baza locală",
                        body = stripLeadingDuplicate(summary, body),
                        style = ResponseSectionStyle.GUIDANCE
                    )
                }

            input.retrievedChunks
                .drop(1)
                .firstOrNull { chunk ->
                    primary == null || (chunk.domain == primary.domain && chunk.language == primary.language)
                }
                ?.synthesizedAnswerOrBody()
                ?.let { sanitizeParagraph(it, 220) }
                ?.takeIf { it.isNotBlank() && !containsNormalized(summary, it) }
                ?.let { detail ->
                    sections += StructuredResponseSection(
                        title = if (isRomanian) "Detalii utile" else "Useful detail",
                        body = detail,
                        style = ResponseSectionStyle.CONTEXT
                    )
                }
        }

        if (sections.isEmpty() && !usePolishedOnly) {
            sections += StructuredResponseSection(
                title = "Baza locală",
                body = summary,
                style = ResponseSectionStyle.GUIDANCE
            )
        }

        return StructuredAssistantOutput(
            summary = summary,
            sections = sections.take(4),
            generationMode = generationMode,
            reasoningType = input.queryAnalysis.reasoningType,
            resolvedTopic = primary?.topic ?: input.queryAnalysis.resolvedTopic,
            resolvedFamily = primary?.cardFamily ?: input.queryAnalysis.targetFamily,
            modelVersion = modelStatus.modelVersion.takeIf {
                modelStatus.availableOnDisk || modelStatus.state != ModelRuntimeState.MISSING
            },
            knowledgePackVersion = input.knowledgePackStatus.packVersion ?: primary?.packVersion
        )
    }

    fun composeFromTopRetrievedTile(
        input: GenerationInput,
        modelStatus: ModelStatus,
        generationMode: GenerationMode = GenerationMode.FALLBACK_STRUCTURED
    ): StructuredAssistantOutput {
        val isRomanian = input.queryAnalysis.preferredLanguage == "ro"
        val primary = input.retrievedChunks.firstOrNull()
        val metadata = primary.metadata()
        val summary = listOfNotNull(
            metadata?.stringValue("lead"),
            primary?.shortAnswer,
            primary?.synthesizedAnswer,
            primary?.body
        )
            .map { sanitizeParagraph(it, 700) }
            .firstOrNull { it.isNotBlank() }
            ?: if (isRomanian) {
                "Răspuns prudent pe baza celui mai relevant card local."
            } else {
                "Cautious answer based on the most relevant local card."
            }

        val sections = mutableListOf<StructuredResponseSection>()
        primary?.safetyNote
            ?.let(::sanitizeParagraph)
            ?.takeIf { it.isNotBlank() && !containsNormalized(summary, it) }
            ?.let { note ->
                sections += StructuredResponseSection(
                    title = if (isRomanian) "Atenție" else "Caution",
                    body = note,
                    style = ResponseSectionStyle.IMPORTANT
                )
            }

        metadata?.stringList("actions_now")
            ?.let { actions ->
                listSection(
                    title = if (isRomanian) "Pași acum" else "Do now",
                    items = actions,
                    style = ResponseSectionStyle.ACTIONS
                )
            }
            ?.takeIf { !containsNormalized(summary, it.body) }
            ?.let { sections += it }

        metadata?.stringList("avoid")
            ?.let { avoid ->
                listSection(
                    title = if (isRomanian) "Evită" else "Avoid",
                    items = avoid,
                    style = ResponseSectionStyle.IMPORTANT
                )
            }
            ?.takeIf { !containsNormalized(summary, it.body) }
            ?.let { sections += it }

        metadata?.stringList("watch_for")
            ?.let { watchFor ->
                listSection(
                    title = if (isRomanian) "Urmărește" else "Watch for",
                    items = watchFor,
                    style = ResponseSectionStyle.GUIDANCE
                )
            }
            ?.takeIf { !containsNormalized(summary, it.body) }
            ?.let { sections += it }

        primary
            ?.groundingCandidates(preferBody = true)
            ?.map { sanitizeParagraph(it, 520) }
            ?.firstOrNull { it.isNotBlank() && !isRedundantWithSummary(summary, it) }
            ?.let { body ->
                sections += StructuredResponseSection(
                    title = if (isRomanian) "Baza locală" else "Local card",
                    body = stripLeadingDuplicate(summary, body),
                    style = ResponseSectionStyle.GUIDANCE
                )
            }

        if (sections.isEmpty()) {
            primary
                ?.body
                ?.let { sanitizeParagraph(it, 520) }
                ?.let { stripLeadingDuplicate(summary, it) }
                ?.takeIf { it.isNotBlank() && !isRedundantWithSummary(summary, it) }
                ?.let { fallbackBody ->
                    sections += StructuredResponseSection(
                        title = if (isRomanian) "Baza locală" else "Local card",
                        body = fallbackBody,
                        style = ResponseSectionStyle.GUIDANCE
                    )
                }
        }
        if (sections.isEmpty()) {
            sections += StructuredResponseSection(
                title = if (isRomanian) "Baza locală" else "Local card",
                body = summary,
                style = ResponseSectionStyle.GUIDANCE
            )
        }

        return StructuredAssistantOutput(
            summary = summary,
            sections = sections.take(4),
            generationMode = generationMode,
            reasoningType = input.queryAnalysis.reasoningType,
            // Base-case "hub" cards carry standalone refinement questions in their
            // metadata. Surfacing them here turns into tappable follow-up chips that
            // re-query into the specific scenario card — deterministic routing, no LLM.
            followUpQuestions = metadata?.followUpQuestionStrings().orEmpty(),
            resolvedTopic = primary?.topic ?: input.queryAnalysis.resolvedTopic,
            resolvedFamily = primary?.cardFamily ?: input.queryAnalysis.targetFamily,
            modelVersion = modelStatus.modelVersion.takeIf {
                modelStatus.availableOnDisk || modelStatus.state != ModelRuntimeState.MISSING
            },
            knowledgePackVersion = input.knowledgePackStatus.packVersion ?: primary?.packVersion
        )
    }

    /**
     * Extracts the hub card's refinement questions from `metadata_json`. Accepts
     * both the object form (`[{"question": "..."}]`, matching the campfire/build
     * convention) and a plain string array.
     */
    private fun JsonObject.followUpQuestionStrings(): List<String> =
        (this["follow_up_questions"] as? JsonArray)
            ?.mapNotNull { element ->
                when (element) {
                    is JsonPrimitive -> element.contentOrNull
                    is JsonObject -> (element["question"] as? JsonPrimitive)?.contentOrNull
                    else -> null
                }
            }
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.distinct()
            ?.take(4)
            .orEmpty()

    private fun RetrievedChunk.synthesizedAnswerOrBody(): String =
        synthesizedAnswer?.takeIf { it.isNotBlank() } ?: body

    private fun RetrievedChunk.groundingCandidates(preferBody: Boolean): List<String> {
        val candidates = if (preferBody) {
            listOf(body, synthesizedAnswer, shortAnswer)
        } else {
            listOf(synthesizedAnswer, body, shortAnswer)
        }
        return candidates.mapNotNull { it?.takeIf { value -> value.isNotBlank() } }
    }

    private fun RetrievedChunk?.metadata(): JsonObject? =
        this?.metadataJson
            ?.takeIf { it.isNotBlank() }
            ?.let { raw -> runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() }

    private fun JsonObject.stringValue(key: String): String? =
        (this[key] as? JsonPrimitive)
            ?.contentOrNull
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    private fun JsonObject.stringList(key: String): List<String> =
        (this[key] as? JsonArray)
            ?.mapNotNull { value ->
                (value as? JsonPrimitive)
                    ?.contentOrNull
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
            }
            .orEmpty()

    private fun listSection(
        title: String,
        items: List<String>,
        style: ResponseSectionStyle
    ): StructuredResponseSection? {
        val body = items
            .map { sanitizeParagraph(it, 180) }
            .filter { it.isNotBlank() }
            .joinToString(" ") { item ->
                if (item.endsWith('.') || item.endsWith('!') || item.endsWith('?')) item else "$item."
            }
            .take(420)
            .trim()
        if (body.isBlank()) {
            return null
        }
        return StructuredResponseSection(
            title = title,
            body = body,
            style = style
        )
    }

    private fun sanitizeParagraph(value: String, maxLength: Int = 700): String =
        value
            .replace('\n', ' ')
            .replace("\\s+".toRegex(), " ")
            .trim()
            .trim('"')
            .take(maxLength)

    private fun containsNormalized(container: String, candidate: String): Boolean {
        val normalizedContainer = normalize(container)
        val normalizedCandidate = normalize(candidate)
        return normalizedCandidate.isNotBlank() &&
            (normalizedContainer.contains(normalizedCandidate) || normalizedCandidate.contains(normalizedContainer))
    }

    private fun isRedundantWithSummary(summary: String, candidate: String): Boolean {
        val normalizedSummary = normalize(summary)
        val normalizedCandidate = normalize(candidate)
        if (normalizedSummary.isBlank() || normalizedCandidate.isBlank()) {
            return false
        }
        if (normalizedSummary == normalizedCandidate) {
            return true
        }
        return normalizedCandidate.length <= normalizedSummary.length + 24 &&
            (normalizedSummary.contains(normalizedCandidate) || normalizedCandidate.contains(normalizedSummary))
    }

    /**
     * When the body starts with the same sentence(s) already shown as the summary
     * (e.g. the card lead is the first sentence of the body), drop that duplicated
     * prefix so it is not repeated in the answer. Returns the original body when
     * there is no clean leading overlap.
     */
    private fun stripLeadingDuplicate(summary: String, body: String): String {
        val normSummary = normalize(summary)
        if (normSummary.isBlank() || !normalize(body).startsWith(normSummary)) {
            return body
        }
        val accumulator = StringBuilder()
        var cutIndex = -1
        var i = 0
        while (i < body.length) {
            accumulator.append(body[i])
            val norm = normalize(accumulator.toString())
            if (norm == normSummary) {
                cutIndex = i + 1
                break
            }
            if (norm.length >= normSummary.length && !normSummary.startsWith(norm)) {
                break
            }
            i++
        }
        // No match, or the match cut through the middle of a word (partial prefix).
        if (cutIndex < 0 || (cutIndex < body.length && body[cutIndex].isLetterOrDigit())) {
            return body
        }
        val remainder = body.substring(cutIndex)
            .trimStart()
            .trimStart('.', ',', ';', ':', '-', '—', '–', '!', '?', ' ', '\n', '\t')
            .trimStart()
        return remainder.ifBlank { body }
    }

    private fun normalize(value: String): String =
        value.lowercase()
            .replace("[^\\p{L}\\p{N}]+".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
}
