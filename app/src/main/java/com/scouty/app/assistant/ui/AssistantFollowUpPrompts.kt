package com.scouty.app.assistant.ui

import com.scouty.app.assistant.model.AssistantQuickReplyUiModel

data class AssistantFollowUpPrompt(
    val question: String,
    val suggestedReplies: List<AssistantQuickReplyUiModel> = emptyList()
)

fun buildSequentialFollowUpPrompt(followUpQuestions: List<String>): AssistantFollowUpPrompt? {
    val question = followUpQuestions.firstOrNull()?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return AssistantFollowUpPrompt(
        question = question,
        suggestedReplies = extractSuggestedReplies(question)
    )
}

fun extractSuggestedReplies(question: String): List<AssistantQuickReplyUiModel> {
    val trimmed = question.trim().removeSuffix("?").trim()
    if (trimmed.isBlank()) {
        return emptyList()
    }

    val optionArea = when {
        ":" in trimmed -> trimmed.substringAfter(":").trim()
        trimmed.startsWith("Ai ", ignoreCase = true) -> trimmed.drop(3).trim()
        trimmed.startsWith("Have ", ignoreCase = true) -> trimmed.drop(5).trim()
        else -> return emptyList()
    }

    return optionArea
        .split(Regex("\\s*,\\s*|\\s+sau\\s+|\\s+ori\\s+|\\s+or\\s+"))
        .map(::cleanSuggestedReply)
        .filter { it.length in 2..40 }
        .map { label ->
            AssistantQuickReplyUiModel(
                label = label,
                query = suggestedReplyQuery(question = trimmed, label = label)
            )
        }
        .distinctBy { it.query.lowercase() }
        .let { replies -> replies.takeIf { replies.size in 2..4 } ?: emptyList() }
}

private fun cleanSuggestedReply(value: String): String {
    val cleaned = value
        .trim()
        .removeSuffix("?")
        .replace(Regex("^e\\s+", RegexOption.IGNORE_CASE), "")
        .replace(Regex("^este\\s+", RegexOption.IGNORE_CASE), "")
        .replace(Regex("^un\\s+", RegexOption.IGNORE_CASE), "")
        .replace(Regex("^o\\s+", RegexOption.IGNORE_CASE), "")
        .replace(Regex("^deja\\s+", RegexOption.IGNORE_CASE), "")
        .trim()

    return cleaned.replaceFirstChar { character ->
        if (character.isLowerCase()) character.titlecase() else character.toString()
    }
}

private fun suggestedReplyQuery(
    question: String,
    label: String
): String {
    val trimmedQuestion = question.trim()
    return when {
        trimmedQuestion.startsWith("Ai ", ignoreCase = true) -> answerForAiQuestion(label)
        trimmedQuestion.startsWith("Have ", ignoreCase = true) -> answerForHaveQuestion(label)
        else -> label
    }
}

private fun answerForAiQuestion(label: String): String {
    val normalizedLabel = label
        .replace(Regex("\\bdin ce ai la tine\\b", RegexOption.IGNORE_CASE), "din ce am la mine")
        .replace(Regex("\\bla tine\\b", RegexOption.IGNORE_CASE), "la mine")
        .trim()

    val answer = when {
        normalizedLabel.startsWith("Am ", ignoreCase = true) ||
            normalizedLabel.startsWith("Nu am", ignoreCase = true) ||
            normalizedLabel.startsWith("N-am", ignoreCase = true) ||
            normalizedLabel.startsWith("Improviz", ignoreCase = true) ||
            normalizedLabel.startsWith("Trebuie", ignoreCase = true) ||
            normalizedLabel.startsWith("Pot", ignoreCase = true) ||
            normalizedLabel.startsWith("Vreau", ignoreCase = true) ||
            normalizedLabel.startsWith("Caut", ignoreCase = true) ->
            normalizedLabel

        else -> "Am ${normalizedLabel.replaceFirstChar { it.lowercase() }}"
    }

    return answer.replaceFirstChar { character ->
        if (character.isLowerCase()) character.titlecase() else character.toString()
    }
}

private fun answerForHaveQuestion(label: String): String {
    val normalizedLabel = label.trim()
    val answer = if (normalizedLabel.startsWith("I have ", ignoreCase = true)) {
        normalizedLabel
    } else {
        "I have ${normalizedLabel.replaceFirstChar { it.lowercase() }}"
    }
    return answer.replaceFirstChar { character ->
        if (character.isLowerCase()) character.titlecase() else character.toString()
    }
}
