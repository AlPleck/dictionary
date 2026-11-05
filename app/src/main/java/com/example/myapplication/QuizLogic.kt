package com.example.myapplication

import java.util.Locale

internal fun normalizeQuizAnswer(value: String): String =
    value.lowercase(Locale.ROOT)
        .replace(Regex("[\\p{P}\\p{S}]"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

internal fun isQuizAnswerCorrect(answer: String, acceptedTranslations: String): Boolean {
    val normalizedAnswer = normalizeQuizAnswer(answer)
    if (normalizedAnswer.isEmpty()) return false

    return normalizeQuizAnswer(acceptedTranslations) == normalizedAnswer ||
        acceptedTranslations.split(';')
            .map(::normalizeQuizAnswer)
            .any { it == normalizedAnswer }
}

internal fun translationsOverlap(first: String, second: String): Boolean =
    isQuizAnswerCorrect(first, second) || isQuizAnswerCorrect(second, first)

internal fun sameLanguagePair(first: Word, second: Word): Boolean =
    first.originalLanguageTag.equals(second.originalLanguageTag, ignoreCase = true) &&
        first.translationLanguageTag.equals(second.translationLanguageTag, ignoreCase = true)

internal fun quizPromptKey(word: Word): String = listOf(
    word.originalLanguageTag.lowercase(Locale.ROOT),
    word.translationLanguageTag.lowercase(Locale.ROOT),
    normalizeQuizAnswer(word.original)
).joinToString("\u0000")

internal fun sameQuizPrompt(first: Word, second: Word): Boolean = quizPromptKey(first) == quizPromptKey(second)

internal fun quizDistractorTranslations(
    question: Word,
    candidateWords: List<Word>,
    knownWords: List<Word>
): List<String> {
    val acceptedTranslations = knownWords.filter { sameQuizPrompt(it, question) }.map { it.translation }
    return candidateWords.asSequence()
        .filter { sameLanguagePair(it, question) && !sameQuizPrompt(it, question) }
        .map { it.translation }
        .distinctBy(::normalizeQuizAnswer)
        .filterNot { candidate -> acceptedTranslations.any { translationsOverlap(candidate, it) } }
        .toList()
}

internal fun buildQuizOptions(
    correctTranslation: String,
    candidateTranslations: List<String>,
    optionCount: Int = 4
): List<String> {
    val distractors = candidateTranslations
        .filterNot { translationsOverlap(it, correctTranslation) }
        .distinctBy(::normalizeQuizAnswer)
        .shuffled()
        .take((optionCount - 1).coerceAtLeast(0))
    return (distractors + correctTranslation).shuffled()
}
