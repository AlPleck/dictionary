package com.example.myapplication

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

internal data class Word(
    val id: String,
    val original: String,
    val translation: String,
    val originalLanguageTag: String = "en",
    val translationLanguageTag: String = "ru",
    val successCount: Int = 0,
    val nextReviewAtMillis: Long = 0L,
    val topicId: String? = null,
    val transcription: String? = null,
    val example: String? = null,
    val exampleTranslation: String? = null,
    val quizAttemptCount: Int = 0,
    val quizCorrectCount: Int = 0,
    val quizCorrectStreak: Int = 0,
    val introducedAtMillis: Long = 0L,
    val intervalDays: Int = 0,
    val topicIds: List<String> = listOfNotNull(topicId),
    val introducedInTopicId: String? = topicId
)

internal data class Topic(
    val id: String,
    val name: String,
    val originalLanguageTag: String = "en",
    val translationLanguageTag: String = "ru",
    val newCardsPerDay: Int = DEFAULT_NEW_CARDS_PER_DAY
)

internal fun Word.pronunciationText(): String =
    if (originalLanguageTag.substringBefore('-').equals("ja", ignoreCase = true) &&
        !transcription.isNullOrBlank()
    ) transcription else original

internal fun reorderTopics(topics: List<Topic>, sourceId: String, targetId: String): List<Topic> {
    val sourceIndex = topics.indexOfFirst { it.id == sourceId }
    val targetIndex = topics.indexOfFirst { it.id == targetId }
    if (sourceIndex < 0 || targetIndex < 0 || sourceIndex == targetIndex) return topics

    return topics.toMutableList().apply {
        val movedTopic = removeAt(sourceIndex)
        add(targetIndex, movedTopic)
    }
}

internal fun Word.matches(topic: Topic): Boolean =
    originalLanguageTag == topic.originalLanguageTag &&
        translationLanguageTag == topic.translationLanguageTag

internal fun Word.belongsTo(topicId: String): Boolean = topicId in topicIds || this.topicId == topicId

internal fun Word.resetStudyProgress(): Word = copy(
    successCount = 0,
    nextReviewAtMillis = 0L,
    introducedAtMillis = 0L,
    intervalDays = 0,
    introducedInTopicId = null
)

internal fun inferTopicLanguages(topicWords: List<Word>): Pair<String, String> =
    topicWords.groupingBy { it.originalLanguageTag to it.translationLanguageTag }
        .eachCount()
        .maxByOrNull { it.value }
        ?.key ?: ("en" to "ru")

internal fun reconcileDeckWords(words: List<Word>, topics: List<Topic>): List<Word> {
    val byId = topics.associateBy { it.id }
    return words.map { word ->
        val normalized = if (word.introducedAtMillis == 0L && word.successCount == 0 &&
            word.intervalDays == 0 && word.nextReviewAtMillis > 0L
        ) word.copy(nextReviewAtMillis = 0L) else word
        val memberships = (normalized.topicIds + listOfNotNull(normalized.topicId)).distinct()
            .filter { id -> byId[id]?.let(normalized::matches) == true }
        val introTopic = normalized.introducedInTopicId?.takeIf { it in memberships } ?: memberships.firstOrNull()
        normalized.copy(topicIds = memberships, topicId = memberships.firstOrNull(), introducedInTopicId = introTopic)
    }
}

internal class WordStorage(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("vocabulary", Context.MODE_PRIVATE)

    fun seedEnglish300IfNeeded() = seedEnglishDeckIfNeeded(
        assetName = ENGLISH_300_ASSET,
        topicName = ENGLISH_300_TOPIC_NAME,
        expectedWordCount = ENGLISH_300_WORD_COUNT,
        seededKey = KEY_ENGLISH_300_SEEDED
    )

    fun seedRuEng300IfNeeded() = seedDeckIfNeeded(
        assetName = ENGLISH_300_ASSET,
        topicName = RU_ENG_300_TOPIC_NAME,
        expectedWordCount = ENGLISH_300_WORD_COUNT,
        seededKey = KEY_RU_ENG_300_SEEDED,
        originalLanguageTag = "ru",
        translationLanguageTag = "en",
        reverseDirection = true
    )

    fun seedEnglish100NounsIfNeeded() = seedEnglishDeckIfNeeded(
        assetName = ENGLISH_100_NOUNS_ASSET,
        topicName = ENGLISH_100_NOUNS_TOPIC_NAME,
        expectedWordCount = ENGLISH_100_NOUNS_WORD_COUNT,
        seededKey = KEY_ENGLISH_100_NOUNS_SEEDED
    )

    fun seedRuEng100NounsIfNeeded() = seedDeckIfNeeded(
        assetName = ENGLISH_100_NOUNS_ASSET,
        topicName = RU_ENG_100_NOUNS_TOPIC_NAME,
        expectedWordCount = ENGLISH_100_NOUNS_WORD_COUNT,
        seededKey = KEY_RU_ENG_100_NOUNS_SEEDED,
        originalLanguageTag = "ru",
        translationLanguageTag = "en",
        reverseDirection = true
    )

    fun seedEnglish100VerbsIfNeeded() = seedEnglishDeckIfNeeded(
        assetName = ENGLISH_100_VERBS_ASSET,
        topicName = ENGLISH_100_VERBS_TOPIC_NAME,
        expectedWordCount = ENGLISH_100_VERBS_WORD_COUNT,
        seededKey = KEY_ENGLISH_100_VERBS_SEEDED
    )

    fun seedRuEng100VerbsIfNeeded() = seedDeckIfNeeded(
        assetName = ENGLISH_100_VERBS_ASSET,
        topicName = RU_ENG_100_VERBS_TOPIC_NAME,
        expectedWordCount = ENGLISH_100_VERBS_WORD_COUNT,
        seededKey = KEY_RU_ENG_100_VERBS_SEEDED,
        originalLanguageTag = "ru",
        translationLanguageTag = "en",
        reverseDirection = true
    )

    fun seedJapanese100IfNeeded() = seedDeckIfNeeded(
        assetName = JAPANESE_100_ASSET,
        topicName = JAPANESE_100_TOPIC_NAME,
        expectedWordCount = JAPANESE_100_WORD_COUNT,
        seededKey = KEY_JAPANESE_100_SEEDED,
        originalLanguageTag = "ja"
    )

    private fun seedEnglishDeckIfNeeded(
        assetName: String,
        topicName: String,
        expectedWordCount: Int,
        seededKey: String
    ) = seedDeckIfNeeded(assetName, topicName, expectedWordCount, seededKey, "en")

    private fun seedDeckIfNeeded(
        assetName: String,
        topicName: String,
        expectedWordCount: Int,
        seededKey: String,
        originalLanguageTag: String,
        translationLanguageTag: String = "ru",
        reverseDirection: Boolean = false
    ) {
        if (preferences.getBoolean(seededKey, false)) return
        val entries = runCatching {
            appContext.assets.open(assetName).bufferedReader().useLines { lines ->
                lines.mapNotNull { line ->
                    val parts = line.split('\t')
                    val first = parts.getOrNull(0)?.trim().orEmpty()
                    val second = parts.getOrNull(1)?.trim().orEmpty()
                    val original = if (reverseDirection) second else first
                    val translation = if (reverseDirection) first else second
                    val transcription = parts.getOrNull(2)?.trim()?.takeIf { it.isNotBlank() && !reverseDirection }
                    if (original.isBlank() || translation.isBlank()) null else Triple(original, translation, transcription)
                }.toList()
            }
        }.getOrDefault(emptyList())
        if (entries.size != expectedWordCount) return

        val savedTopics = loadTopics().toMutableList()
        val topic = savedTopics.firstOrNull {
            it.name.equals(topicName, ignoreCase = true) &&
                it.originalLanguageTag == originalLanguageTag &&
                it.translationLanguageTag == translationLanguageTag
        }
            ?: Topic(id = UUID.randomUUID().toString(), name = topicName,
                originalLanguageTag = originalLanguageTag, translationLanguageTag = translationLanguageTag)
                .also { savedTopics.add(it) }
        saveTopics(savedTopics)

        val transcriptions = entries
            .mapNotNull { (original, _, transcription) ->
                transcription?.let { original.trim().lowercase(Locale.ROOT) to it }
            }
            .toMap()
        var repaired = false
        val savedWords = load().map { word ->
            if (word.belongsTo(topic.id) && word.transcription.isNullOrBlank()) {
                val transcription = transcriptions[word.original.trim().lowercase(Locale.ROOT)]
                if (transcription != null) {
                    repaired = true
                    word.copy(transcription = transcription)
                } else word
            } else word
        }.toMutableList()
        val existingWords = savedWords
            .asSequence()
            .filter { it.belongsTo(topic.id) }
            .map { seededWordKey(it.original, it.translation, reverseDirection) }
            .toMutableSet()
        var added = false
        entries.forEach { (original, translation, transcription) ->
            if (existingWords.add(seededWordKey(original, translation, reverseDirection))) {
                added = true
                savedWords.add(
                    Word(
                        id = UUID.randomUUID().toString(),
                        original = original,
                        translation = translation,
                        originalLanguageTag = originalLanguageTag,
                        translationLanguageTag = translationLanguageTag,
                        topicId = topic.id,
                        topicIds = listOf(topic.id),
                        transcription = transcription
                    )
                )
            }
        }
        if (repaired || added) {
            save(savedWords)
        }
        preferences.edit().putBoolean(seededKey, true).apply()
    }

    private fun seededWordKey(original: String, translation: String, reverseDirection: Boolean): String {
        val originalKey = original.trim().lowercase(Locale.ROOT)
        return if (reverseDirection) "$originalKey\u0000${translation.trim().lowercase(Locale.ROOT)}" else originalKey
    }

    fun load(): List<Word> {
        val savedWords = parseArray(KEY_WORDS, KEY_WORDS_BACKUP) ?: return emptyList()
        return (0 until savedWords.length()).mapNotNull { index ->
            runCatching {
                val saved = savedWords.getJSONObject(index)
                Word(
                    id = saved.getString("id"),
                    original = saved.getString("original"),
                    translation = saved.getString("translation"),
                    originalLanguageTag = saved.optString("originalLanguageTag", "en"),
                    translationLanguageTag = saved.optString("translationLanguageTag", "ru"),
                    successCount = saved.optInt("successCount", 0),
                    nextReviewAtMillis = saved.optLong("nextReviewAtMillis", 0L),
                    topicId = if (saved.isNull("topicId")) null else saved.optString("topicId"),
                    topicIds = saved.optJSONArray("topicIds")?.let { ids ->
                        (0 until ids.length()).mapNotNull { ids.optString(it).takeIf(String::isNotBlank) }
                    } ?: listOfNotNull(if (saved.isNull("topicId")) null else saved.optString("topicId")),
                    transcription = if (saved.isNull("transcription")) null else saved.optString("transcription"),
                    example = if (saved.isNull("example")) null else saved.optString("example"),
                    exampleTranslation = if (saved.isNull("exampleTranslation")) null else saved.optString("exampleTranslation"),
                    quizAttemptCount = saved.optInt("quizAttemptCount", 0),
                    quizCorrectCount = saved.optInt("quizCorrectCount", 0),
                    quizCorrectStreak = saved.optInt("quizCorrectStreak", 0),
                    introducedAtMillis = saved.optLong("introducedAtMillis", 0L),
                    intervalDays = saved.optInt(
                        "intervalDays",
                        if (saved.optInt("successCount", 0) > 0) {
                            1 shl (saved.optInt("successCount", 0) - 1).coerceAtMost(9)
                        } else 0
                    ),
                    introducedInTopicId = if (saved.isNull("introducedInTopicId")) {
                        if (saved.isNull("topicId")) null else saved.optString("topicId")
                    } else saved.optString("introducedInTopicId")
                )
            }.getOrNull()
        }
    }

    fun loadTopics(): List<Topic> {
        val savedTopics = parseArray(KEY_TOPICS, KEY_TOPICS_BACKUP) ?: return emptyList()
        val wordsByTopic = load().flatMap { word -> word.topicIds.map { it to word } }.groupBy({ it.first }, { it.second })
        return (0 until savedTopics.length()).mapNotNull { index ->
            runCatching {
                val saved = savedTopics.getJSONObject(index)
                val id = saved.getString("id")
                val inferred = inferTopicLanguages(wordsByTopic[id].orEmpty())
                Topic(
                    id = id,
                    name = saved.getString("name"),
                    originalLanguageTag = saved.optString("originalLanguageTag", inferred.first),
                    translationLanguageTag = saved.optString("translationLanguageTag", inferred.second),
                    newCardsPerDay = saved.optInt("newCardsPerDay", DEFAULT_NEW_CARDS_PER_DAY).coerceAtLeast(0)
                )
            }.getOrNull()
        }
    }

    fun migrateDeckLanguages() {
        val raw = parseArray(KEY_TOPICS, KEY_TOPICS_BACKUP) ?: JSONArray()
        val needsMigration = (0 until raw.length()).any { index ->
            runCatching {
                val topic = raw.getJSONObject(index)
                !topic.has("originalLanguageTag") || !topic.has("translationLanguageTag")
            }.getOrDefault(false)
        }
        val existingTopics = loadTopics()
        if (needsMigration) saveTopics(existingTopics)
        val savedWords = load()
        val reconciled = reconcileDeckWords(savedWords, existingTopics).toMutableList()
        val migratedTopics = existingTopics.toMutableList()
        val pairs = reconciled.filter { it.topicIds.isEmpty() }
            .map { it.originalLanguageTag to it.translationLanguageTag }.distinct()
        pairs.forEach { (original, translation) ->
            val deck = migratedTopics.firstOrNull {
                it.originalLanguageTag == original && it.translationLanguageTag == translation
            } ?: Topic(
                id = UUID.randomUUID().toString(),
                name = "Мои слова (${original.uppercase()} → ${translation.uppercase()})",
                originalLanguageTag = original,
                translationLanguageTag = translation
            ).also(migratedTopics::add)
            reconciled.indices.forEach { index ->
                val word = reconciled[index]
                if (word.topicIds.isEmpty() && word.originalLanguageTag == original && word.translationLanguageTag == translation) {
                    reconciled[index] = word.copy(topicId = deck.id, topicIds = listOf(deck.id))
                }
            }
        }
        if (migratedTopics != existingTopics) saveTopics(migratedTopics)
        if (reconciled != savedWords) save(reconciled)
    }

    private fun parseArray(key: String, backupKey: String): JSONArray? {
        val raw = preferences.getString(key, "[]") ?: "[]"
        return runCatching { JSONArray(raw) }.getOrElse {
            preferences.edit().putString(backupKey, raw).apply()
            null
        }
    }

    fun save(words: List<Word>) {
        require(words.all { (it.topicIds + listOfNotNull(it.topicId)).isNotEmpty() }) {
            "Every word must belong to at least one deck"
        }
        val savedWords = JSONArray()
        words.forEach { word ->
            savedWords.put(
                JSONObject()
                    .put("id", word.id)
                    .put("original", word.original)
                    .put("translation", word.translation)
                    .put("originalLanguageTag", word.originalLanguageTag)
                    .put("translationLanguageTag", word.translationLanguageTag)
                    .put("successCount", word.successCount)
                    .put("nextReviewAtMillis", word.nextReviewAtMillis)
                    .put("topicId", word.topicId ?: JSONObject.NULL)
                    .put("topicIds", JSONArray((word.topicIds + listOfNotNull(word.topicId)).distinct()))
                    .put("transcription", word.transcription ?: JSONObject.NULL)
                    .put("example", word.example ?: JSONObject.NULL)
                    .put("exampleTranslation", word.exampleTranslation ?: JSONObject.NULL)
                    .put("quizAttemptCount", word.quizAttemptCount)
                    .put("quizCorrectCount", word.quizCorrectCount)
                    .put("quizCorrectStreak", word.quizCorrectStreak)
                    .put("introducedAtMillis", word.introducedAtMillis)
                    .put("intervalDays", word.intervalDays)
                    .put("introducedInTopicId", word.introducedInTopicId ?: JSONObject.NULL)
            )
        }
        preferences.edit().putString(KEY_WORDS, savedWords.toString()).apply()
    }

    fun saveTopics(topics: List<Topic>) {
        val savedTopics = JSONArray()
        topics.forEach { topic ->
            savedTopics.put(
                JSONObject()
                    .put("id", topic.id)
                    .put("name", topic.name)
                    .put("originalLanguageTag", topic.originalLanguageTag)
                    .put("translationLanguageTag", topic.translationLanguageTag)
                    .put("newCardsPerDay", topic.newCardsPerDay.coerceAtLeast(0))
            )
        }
        preferences.edit().putString(KEY_TOPICS, savedTopics.toString()).apply()
    }

    private companion object {
        const val KEY_WORDS = "words"
        const val KEY_TOPICS = "topics"
        const val KEY_WORDS_BACKUP = "words_corrupt_backup"
        const val KEY_TOPICS_BACKUP = "topics_corrupt_backup"
        const val KEY_ENGLISH_300_SEEDED = "english_300_seeded"
        const val KEY_RU_ENG_300_SEEDED = "ru_eng_300_seeded"
        const val KEY_ENGLISH_100_NOUNS_SEEDED = "english_100_nouns_seeded"
        const val KEY_RU_ENG_100_NOUNS_SEEDED = "ru_eng_100_nouns_seeded"
        const val KEY_ENGLISH_100_VERBS_SEEDED = "english_100_verbs_seeded"
        const val KEY_RU_ENG_100_VERBS_SEEDED = "ru_eng_100_verbs_seeded"
        const val KEY_JAPANESE_100_SEEDED = "japanese_100_seeded"
        const val ENGLISH_300_ASSET = "english_300.tsv"
        const val ENGLISH_300_TOPIC_NAME = "English 300"
        const val RU_ENG_300_TOPIC_NAME = "Ru-Eng 300"
        const val ENGLISH_300_WORD_COUNT = 300
        const val ENGLISH_100_NOUNS_ASSET = "english_100_nouns.tsv"
        const val ENGLISH_100_NOUNS_TOPIC_NAME = "English 100 Nouns"
        const val RU_ENG_100_NOUNS_TOPIC_NAME = "Ru-Eng 100 Существительные"
        const val ENGLISH_100_NOUNS_WORD_COUNT = 100
        const val ENGLISH_100_VERBS_ASSET = "english_100_verbs.tsv"
        const val ENGLISH_100_VERBS_TOPIC_NAME = "English 100 Verbs"
        const val RU_ENG_100_VERBS_TOPIC_NAME = "Ru-Eng 100 Глаголы"
        const val ENGLISH_100_VERBS_WORD_COUNT = 100
        const val JAPANESE_100_ASSET = "japanese_100.tsv"
        const val JAPANESE_100_TOPIC_NAME = "Japanese 100"
        const val JAPANESE_100_WORD_COUNT = 100
    }
}
