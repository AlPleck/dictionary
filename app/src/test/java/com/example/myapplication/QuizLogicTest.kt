package com.example.myapplication

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizLogicTest {
    @Test
    fun acceptsCaseInsensitiveAnswerAndPunctuationDifferences() {
        assertTrue(isQuizAnswerCorrect("  HELLO! ", "hello; greetings"))
    }

    @Test
    fun acceptsAnySemicolonSeparatedTranslation() {
        assertTrue(isQuizAnswerCorrect("о", "из; о; принадлежность"))
        assertTrue(isQuizAnswerCorrect("из; о; принадлежность", "из; о; принадлежность"))
    }

    @Test
    fun rejectsBlankAndIncorrectAnswers() {
        assertFalse(isQuizAnswerCorrect(" ", "hello"))
        assertFalse(isQuizAnswerCorrect("goodbye", "hello; greetings"))
    }

    @Test
    fun quizOptionsIncludeTheCorrectTranslationAndUniqueDistractors() {
        val options = buildQuizOptions(
            correctTranslation = "яблоко",
            candidateTranslations = listOf("груша", "яблоко", "груша", "слива", "персик", "дыня")
        )

        assertTrue(options.contains("яблоко"))
        assertTrue(options.size <= 4)
        assertEquals(options.size, options.map(::normalizeQuizAnswer).distinct().size)
    }

    @Test
    fun sameSpellingInDifferentLanguagePairsIsNotTheSameQuizPrompt() {
        val english = Word("en", "no", "нет", "en", "ru")
        val spanish = Word("es", "no", "не", "es", "ru")

        assertFalse(sameQuizPrompt(english, spanish))
    }

    @Test
    fun quizDistractorsStayInTheQuestionLanguagePairAndExcludeAliases() {
        val question = Word("q", "bank", "банк; берег", "en", "ru")
        val alias = Word("alias", "bank", "берег", "en", "ru")
        val valid = Word("valid", "river", "река", "en", "ru")
        val otherLanguage = Word("other", "rive", "берег", "fr", "ru")

        assertEquals(
            listOf("река"),
            quizDistractorTranslations(question, listOf(question, alias, valid, otherLanguage), listOf(question, alias, valid, otherLanguage))
        )
    }
}
