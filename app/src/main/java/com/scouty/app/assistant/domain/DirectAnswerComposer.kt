package com.scouty.app.assistant.domain

import com.scouty.app.assistant.model.GenerationMode
import com.scouty.app.assistant.model.ModelRuntimeState
import com.scouty.app.assistant.model.ModelStatus
import com.scouty.app.assistant.model.ResponseSectionStyle
import com.scouty.app.assistant.model.StructuredAssistantOutput
import com.scouty.app.assistant.model.StructuredResponseSection

class DirectAnswerComposer {
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

        val grounded = primary?.synthesizedAnswer?.takeIf { it.isNotBlank() }
            ?: primary?.body?.takeIf { it.isNotBlank() }
        grounded
            ?.let { sanitizeParagraph(it, 320) }
            ?.takeIf { it.isNotBlank() && !containsNormalized(summary, it) }
            ?.let { body ->
                sections += StructuredResponseSection(
                    title = "Baza locală",
                    body = body,
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

        if (sections.isEmpty()) {
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

    private fun RetrievedChunk.synthesizedAnswerOrBody(): String =
        synthesizedAnswer?.takeIf { it.isNotBlank() } ?: body

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

    private fun normalize(value: String): String =
        value.lowercase()
            .replace("[^\\p{L}\\p{N}]+".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
}
