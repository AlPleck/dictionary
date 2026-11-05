package com.example.myapplication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class StudyProgressTest {
    @Test
    fun newCardsRespectDeckLimitAndDailyIntroductions() {
        val now = Calendar.getInstance().apply { set(2026, Calendar.JANUARY, 12, 12, 0) }.timeInMillis
        val words = (1..4).map { index ->
            Word("$index", "word$index", "слово$index", topicId = "deck")
        } + Word("other", "other", "другое", topicId = "other-deck")

        assertEquals(2, availableNewCardsCount(words, "deck", 2, now))
        assertEquals("1", nextStudyWordId(words, "deck", 2, now))
        val introduced = words.map {
            if (it.id == "1") it.copy(introducedAtMillis = now, nextReviewAtMillis = now) else it
        }
        assertEquals(1, availableNewCardsCount(introduced, "deck", 2, now))
        assertEquals(0, availableNewCardsCount(introduced, "deck", 1, now))
    }

    @Test
    fun sharedNewCardAppearsInEachDeckAndCanBeSelectedFromEither() {
        val now = Calendar.getInstance().apply { set(2026, Calendar.JANUARY, 12, 12, 0) }.timeInMillis
        val shared = Word("shared", "word", "слово", topicId = "deck-a", topicIds = listOf("deck-a", "deck-b"))

        assertEquals(1, availableNewCardsCount(listOf(shared), "deck-a", 1, now))
        assertEquals(1, availableNewCardsCount(listOf(shared), "deck-b", 1, now))
        assertEquals("shared", nextStudyWordId(listOf(shared), "deck-b", 1, now))
    }

    @Test
    fun sharedNewCardIsCountedOnlyOnceOnTheGlobalStudyButton() {
        val now = Calendar.getInstance().apply { set(2026, Calendar.JANUARY, 12, 12, 0) }.timeInMillis
        val shared = Word("shared", "word", "слово", topicId = "deck-a", topicIds = listOf("deck-a", "deck-b"))
        val onlyA = Word("only-a", "other", "другое", topicId = "deck-a")
        val limits = mapOf("deck-a" to 1, "deck-b" to 1, null to DEFAULT_NEW_CARDS_PER_DAY)

        assertEquals(1, availableGlobalNewCardsCount(listOf(shared, onlyA), limits, now))
    }

    @Test
    fun sharedCardIntroductionConsumesOnlyTheSelectedDeckLimit() {
        val now = Calendar.getInstance().apply { set(2026, Calendar.JANUARY, 12, 12, 0) }.timeInMillis
        val sharedIntroducedInA = Word(
            "shared", "word", "слово", topicId = "deck-a", topicIds = listOf("deck-a", "deck-b"),
            introducedAtMillis = now, introducedInTopicId = "deck-a", nextReviewAtMillis = now
        )
        val onlyB = Word("only-b", "other", "другое", topicId = "deck-b")
        val words = listOf(sharedIntroducedInA, onlyB)

        assertEquals(0, availableNewCardsCount(words, "deck-a", 1, now))
        assertEquals(1, availableNewCardsCount(words, "deck-b", 1, now))
    }

    @Test
    fun globalStudyChargesSharedCardToFirstDeckWithRemainingAllowance() {
        val now = Calendar.getInstance().apply { set(2026, Calendar.JANUARY, 12, 12, 0) }.timeInMillis
        val alreadyIntroduced = Word(
            "introduced", "old", "старое", topicId = "deck-a", topicIds = listOf("deck-a", "deck-b"),
            introducedAtMillis = now, introducedInTopicId = "deck-a", nextReviewAtMillis = now
        )
        val freshShared = Word("fresh", "new", "новое", topicId = "deck-a", topicIds = listOf("deck-a", "deck-b"))
        val limits = mapOf("deck-a" to 1, "deck-b" to 1, null to DEFAULT_NEW_CARDS_PER_DAY)

        assertEquals("deck-b", introductionTopicIdFor(freshShared, listOf(alreadyIntroduced, freshShared), null, false, limits, now))
    }

    @Test
    fun changingCardContentCanStartItsStudyProgressOver() {
        val learned = Word(
            "1", "old", "старое", successCount = 5, nextReviewAtMillis = 5000,
            introducedAtMillis = 1000, intervalDays = 16, topicId = "deck", introducedInTopicId = "deck"
        )

        val reset = learned.resetStudyProgress()

        assertEquals(0, reset.successCount)
        assertEquals(0L, reset.nextReviewAtMillis)
        assertEquals(0L, reset.introducedAtMillis)
        assertEquals(0, reset.intervalDays)
        assertEquals(null, reset.introducedInTopicId)
        assertTrue(isUnseen(reset))
    }

    @Test
    fun aNewCardMovesToTomorrowAfterTwoConsecutiveCorrectAnswers() {
        val now = Calendar.getInstance().apply { set(2026, Calendar.JANUARY, 12, 12, 0) }.timeInMillis
        val newCard = Word("1", "word", "слово", introducedAtMillis = now, nextReviewAtMillis = now)

        val firstCorrect = scheduleStudyAnswer(newCard, correct = true, nowMillis = now)
        assertEquals(1, firstCorrect.successCount)
        assertEquals(0, firstCorrect.intervalDays)
        assertEquals(now, firstCorrect.nextReviewAtMillis)

        val secondCorrect = scheduleStudyAnswer(firstCorrect, correct = true, nowMillis = now)
        assertEquals(2, secondCorrect.successCount)
        assertEquals(1, secondCorrect.intervalDays)
        assertTrue(secondCorrect.nextReviewAtMillis > now)

        val incorrect = scheduleStudyAnswer(firstCorrect, correct = false, nowMillis = now)
        assertEquals(0, incorrect.successCount)
        assertEquals(0, incorrect.intervalDays)
        assertEquals(now, incorrect.nextReviewAtMillis)
    }

    @Test
    fun inconsistentOldIntervalCannotSkipTheTwoCorrectLearningSteps() {
        val now = Calendar.getInstance().apply { set(2026, Calendar.JANUARY, 12, 12, 0) }.timeInMillis
        val inconsistent = Word(
            "1", "word", "слово", successCount = 0, intervalDays = 16,
            introducedAtMillis = now, nextReviewAtMillis = now
        )

        val firstCorrect = scheduleStudyAnswer(inconsistent, correct = true, nowMillis = now)
        val secondCorrect = scheduleStudyAnswer(firstCorrect, correct = true, nowMillis = now)

        assertEquals(0, firstCorrect.intervalDays)
        assertEquals(now, firstCorrect.nextReviewAtMillis)
        assertEquals(1, secondCorrect.intervalDays)
        assertTrue(secondCorrect.nextReviewAtMillis > now)
    }

    @Test
    fun dailyStatsCountAnswersAndRetention() {
        val now = Calendar.getInstance().apply { set(2026, Calendar.JANUARY, 12, 12, 0) }.timeInMillis
        val start = localDayStartMillis(now)
        val stats = dailyStudyStats(
            listOf(
                StudyEvent(start + 1000, true),
                StudyEvent(start + 2000, false),
                StudyEvent(start - 1000, true)
            ),
            now
        )

        assertEquals(2, stats.answered)
        assertEquals(1, stats.correct)
        assertEquals(50, stats.retentionPercent)
    }

    @Test
    fun masteredCardsStartAtTwentyOneDayInterval() {
        val word = Word("1", "word", "слово", successCount = 7, intervalDays = 32)
        val next = scheduleStudyAnswer(word, correct = true, nowMillis = 1000L)

        assertEquals(64, next.intervalDays)
        assertTrue(next.intervalDays >= 21)
        assertFalse(isUnseen(next))
    }

    @Test
    fun aCardScheduledLaterTodayIsCountedAsDueToday() {
        val now = Calendar.getInstance().apply { set(2026, Calendar.JANUARY, 12, 9, 0) }.timeInMillis
        val laterToday = Calendar.getInstance().apply { set(2026, Calendar.JANUARY, 12, 23, 0) }.timeInMillis
        val card = Word("1", "word", "слово", nextReviewAtMillis = laterToday, intervalDays = 1)

        assertEquals(1, dueCardsCount(listOf(card), now))
    }

    @Test
    fun dueWordListMatchesCountAndIsOrderedByReviewTime() {
        val now = Calendar.getInstance().apply { set(2026, Calendar.JANUARY, 12, 9, 0) }.timeInMillis
        val first = Word("first", "first", "первое", nextReviewAtMillis = now - 2_000, intervalDays = 1)
        val second = Word("second", "second", "второе", nextReviewAtMillis = now - 1_000, intervalDays = 1)
        val tomorrow = Word("tomorrow", "tomorrow", "завтра", nextReviewAtMillis = now + 86_400_000, intervalDays = 1)

        val due = dueCards(listOf(second, tomorrow, first), now)

        assertEquals(listOf("first", "second"), due.map { it.id })
        assertEquals(due.size, dueCardsCount(listOf(second, tomorrow, first), now))
    }

    @Test
    fun unfinishedLearningCardFromYesterdayPrecedesNewCards() {
        val now = Calendar.getInstance().apply { set(2026, Calendar.JANUARY, 12, 9, 0) }.timeInMillis
        val yesterday = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, -1)
        }.timeInMillis
        val learning = Word(
            "learning", "word", "слово", successCount = 1,
            nextReviewAtMillis = yesterday, topicId = "deck", introducedAtMillis = yesterday
        )
        val fresh = Word("fresh", "new", "новый", topicId = "deck")

        assertEquals("learning", nextStudyWordId(listOf(learning, fresh), "deck", 10, now))
    }
}
