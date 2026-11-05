package com.example.myapplication

import java.util.Calendar

internal const val DEFAULT_NEW_CARDS_PER_DAY = 10

internal data class StudyEvent(val timestampMillis: Long, val correct: Boolean)

internal data class DailyStudyStats(val answered: Int, val correct: Int) {
    val retentionPercent: Int get() = if (answered == 0) 0 else correct * 100 / answered
}

internal fun isUnseen(word: Word): Boolean =
    word.introducedAtMillis == 0L && word.successCount == 0 && word.nextReviewAtMillis == 0L

internal fun localDayStartMillis(timestampMillis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = timestampMillis
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

internal fun isSameLocalDay(firstMillis: Long, secondMillis: Long): Boolean =
    localDayStartMillis(firstMillis) == localDayStartMillis(secondMillis)

internal fun scheduleStudyAnswer(word: Word, correct: Boolean, nowMillis: Long): Word {
    if (!correct) {
        return word.copy(successCount = 0, intervalDays = 0, nextReviewAtMillis = nowMillis)
    }
    val intervalDays = when {
        word.successCount == 0 -> 0
        word.intervalDays > 0 -> (word.intervalDays * 2).coerceAtMost(365)
        else -> 1
    }
    val nextReview = if (intervalDays == 0) nowMillis else Calendar.getInstance().apply {
        timeInMillis = localDayStartMillis(nowMillis)
        add(Calendar.DAY_OF_YEAR, intervalDays)
    }.timeInMillis
    return word.copy(
        successCount = word.successCount + 1,
        intervalDays = intervalDays,
        nextReviewAtMillis = nextReview
    )
}

internal fun dailyStudyStats(events: List<StudyEvent>, nowMillis: Long): DailyStudyStats {
    val start = localDayStartMillis(nowMillis)
    val end = Calendar.getInstance().apply {
        timeInMillis = start
        add(Calendar.DAY_OF_YEAR, 1)
    }.timeInMillis
    val today = events.filter { it.timestampMillis in start until end }
    return DailyStudyStats(today.size, today.count { it.correct })
}

internal fun dueCards(words: List<Word>, nowMillis: Long): List<Word> =
    words.filter { !isUnseen(it) && localDayStartMillis(it.nextReviewAtMillis) <= localDayStartMillis(nowMillis) }
        .sortedBy { it.nextReviewAtMillis }

internal fun dueCardsCount(words: List<Word>, nowMillis: Long): Int = dueCards(words, nowMillis).size

internal fun availableNewCardsCount(
    words: List<Word>,
    topicId: String?,
    dailyLimit: Int,
    nowMillis: Long,
    scopeToTopic: Boolean = true
): Int {
    val scoped = if (scopeToTopic) words.filter { topicId != null && it.belongsTo(topicId) } else words
    val introducedToday = scoped.count {
        it.introducedAtMillis > 0L && isSameLocalDay(it.introducedAtMillis, nowMillis) &&
            (it.introducedInTopicId == topicId || (it.introducedInTopicId == null && it.topicId == topicId))
    }
    val unseen = scoped.count(::isUnseen)
    return minOf(unseen, (dailyLimit.coerceAtLeast(0) - introducedToday).coerceAtLeast(0))
}

internal fun availableGlobalNewCardsCount(
    words: List<Word>,
    topicLimits: Map<String?, Int>,
    nowMillis: Long
): Int {
    val remaining = topicLimits.mapValues { (topicId, limit) ->
        val introducedToday = words.count { word ->
            word.introducedAtMillis > 0L && isSameLocalDay(word.introducedAtMillis, nowMillis) &&
                (word.introducedInTopicId == topicId || (word.introducedInTopicId == null && word.topicId == topicId))
        }
        (limit.coerceAtLeast(0) - introducedToday).coerceAtLeast(0)
    }.toMutableMap()
    var available = 0
    words.filter(::isUnseen).forEach { word ->
        val membership = (word.topicIds + listOfNotNull(word.topicId)).distinct()
            .firstOrNull { (remaining[it] ?: 0) > 0 }
        if (membership != null) {
            remaining[membership] = (remaining[membership] ?: 0) - 1
            available++
        }
    }
    return available
}

internal fun introductionTopicIdFor(
    word: Word,
    words: List<Word>,
    topicId: String?,
    scopedToTopic: Boolean,
    topicLimits: Map<String?, Int>,
    nowMillis: Long
): String? {
    if (scopedToTopic) return topicId
    return (word.topicIds + listOfNotNull(word.topicId)).distinct().firstOrNull { membership ->
        availableNewCardsCount(
            words, membership, topicLimits[membership] ?: DEFAULT_NEW_CARDS_PER_DAY, nowMillis
        ) > 0
    }
}

internal fun nextStudyWordId(
    words: List<Word>,
    topicId: String?,
    dailyLimit: Int,
    nowMillis: Long,
    scopeToTopic: Boolean = true,
    topicLimits: Map<String?, Int> = emptyMap()
): String? {
    val scoped = if (scopeToTopic) words.filter { topicId != null && it.belongsTo(topicId) } else words
    val dueReviews = scoped.filter {
        !isUnseen(it) && localDayStartMillis(it.nextReviewAtMillis) <= localDayStartMillis(nowMillis) &&
            !(it.introducedAtMillis > 0L && it.intervalDays == 0)
    }
    dueReviews.minByOrNull { it.nextReviewAtMillis }?.let { return it.id }

    scoped.filter {
        !isUnseen(it) && localDayStartMillis(it.nextReviewAtMillis) <= localDayStartMillis(nowMillis) &&
            it.introducedAtMillis > 0L && it.intervalDays == 0 &&
            !isSameLocalDay(it.introducedAtMillis, nowMillis)
    }.minByOrNull { it.introducedAtMillis }?.let { return it.id }

    if (scopeToTopic) {
        val newSlots = availableNewCardsCount(words, topicId, dailyLimit, nowMillis, true)
        if (newSlots > 0) scoped.firstOrNull(::isUnseen)?.let { return it.id }
    } else {
        scoped.firstOrNull { word ->
            isUnseen(word) && (word.topicIds + listOfNotNull(word.topicId)).distinct().any { membership ->
                availableNewCardsCount(
                    words, membership, topicLimits[membership] ?: DEFAULT_NEW_CARDS_PER_DAY, nowMillis
                ) > 0
            }
        }?.let { return it.id }
    }

    return scoped.filter {
        !isUnseen(it) && localDayStartMillis(it.nextReviewAtMillis) <= localDayStartMillis(nowMillis) &&
            it.introducedAtMillis > 0L && it.intervalDays == 0
    }.minByOrNull { it.introducedAtMillis }?.id
}
