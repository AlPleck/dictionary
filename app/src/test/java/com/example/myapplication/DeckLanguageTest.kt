package com.example.myapplication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeckLanguageTest {
    @Test
    fun infersExistingDeckPairFromMostCommonWordLanguages() {
        val words = listOf(
            word("1", "es", "fr"),
            word("2", "en", "ru"),
            word("3", "es", "fr")
        )

        assertEquals("es" to "fr", inferTopicLanguages(words))
        assertEquals("en" to "ru", inferTopicLanguages(emptyList()))
    }

    @Test
    fun onlyMatchingWordsCanBelongToDeck() {
        val deck = Topic("deck", "Travel", originalLanguageTag = "es", translationLanguageTag = "fr")

        assertTrue(word("1", "es", "fr", "deck").matches(deck))
        assertFalse(word("2", "fr", "es", "deck").matches(deck))
        assertFalse(word("3", "es", "ru", "deck").matches(deck))
    }

    @Test
    fun reconciliationKeepsWordsButRemovesIncompatibleMemberships() {
        val deck = Topic("deck", "Travel", originalLanguageTag = "es", translationLanguageTag = "fr")
        val matching = word("1", "es", "fr", "deck")
        val incompatible = word("2", "en", "ru", "deck")
        val unassigned = word("3", "en", "ru")

        assertEquals(
            listOf(
                matching,
                incompatible.copy(topicId = null, topicIds = emptyList(), introducedInTopicId = null),
                unassigned
            ),
            reconcileDeckWords(listOf(matching, incompatible, unassigned), listOf(deck))
        )
    }

    @Test
    fun oneWordCanBelongToSeveralMatchingDecks() {
        val first = Topic("first", "Travel", originalLanguageTag = "es", translationLanguageTag = "fr")
        val second = Topic("second", "Food", originalLanguageTag = "es", translationLanguageTag = "fr")
        val shared = word("1", "es", "fr", "first").copy(topicIds = listOf("first", "second"))

        val reconciled = reconcileDeckWords(listOf(shared), listOf(first, second)).single()

        assertEquals(listOf("first", "second"), reconciled.topicIds)
        assertTrue(reconciled.belongsTo("first"))
        assertTrue(reconciled.belongsTo("second"))
    }

    @Test
    fun unseenQuizMistakeDoesNotBecomeAReviewOrBypassNewCardLimits() {
        val deck = Topic("deck", "Travel")
        val mistakenUnseen = word("1", "en", "ru", "deck").copy(nextReviewAtMillis = 1_000L)

        val reconciled = reconcileDeckWords(listOf(mistakenUnseen), listOf(deck)).single()

        assertTrue(isUnseen(reconciled))
    }

    private fun word(id: String, source: String, target: String, deck: String? = null) =
        Word(id, "hello", "bonjour", source, target, topicId = deck)
}
