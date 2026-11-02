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
    val topicId: String? = null
)

internal data class Topic(
    val id: String,
    val name: String,
    val languageTag: String = "ru"
)

internal class WordStorage(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("vocabulary", Context.MODE_PRIVATE)

    fun seedEnglish300IfNeeded() {
        if (preferences.getBoolean(KEY_ENGLISH_300_SEEDED, false)) return

        val entries = runCatching {
            appContext.assets.open(ENGLISH_300_ASSET).bufferedReader().useLines { lines ->
                lines.mapNotNull { line ->
                    val separator = line.indexOf('\t')
                    if (separator <= 0 || separator == line.lastIndex) return@mapNotNull null
                    val original = line.substring(0, separator).trim()
                    val translation = line.substring(separator + 1).trim()
                    if (original.isBlank() || translation.isBlank()) null else original to translation
                }.toList()
            }
        }.getOrDefault(emptyList())
        if (entries.size != ENGLISH_300_WORD_COUNT) return

        val savedTopics = loadTopics().toMutableList()
        val topic = savedTopics.firstOrNull { it.name.equals(ENGLISH_300_TOPIC_NAME, ignoreCase = true) }
            ?: Topic(id = UUID.randomUUID().toString(), name = ENGLISH_300_TOPIC_NAME, languageTag = "en")
                .also { savedTopics.add(it) }
        saveTopics(savedTopics)

        val savedWords = load().toMutableList()
        val existingWords = savedWords
            .asSequence()
            .filter { it.topicId == topic.id }
            .map { it.original.trim().lowercase(Locale.ROOT) }
            .toMutableSet()
        entries.forEach { (original, translation) ->
            if (existingWords.add(original.lowercase(Locale.ROOT))) {
                savedWords.add(
                    Word(
                        id = UUID.randomUUID().toString(),
                        original = original,
                        translation = translation,
                        originalLanguageTag = "en",
                        translationLanguageTag = "ru",
                        topicId = topic.id
                    )
                )
            }
        }
        save(savedWords)
        preferences.edit().putBoolean(KEY_ENGLISH_300_SEEDED, true).apply()
    }

    fun load(): List<Word> = runCatching {
        val savedWords = JSONArray(preferences.getString(KEY_WORDS, "[]"))
        List(savedWords.length()) { index ->
            val saved = savedWords.getJSONObject(index)
            Word(
                id = saved.getString("id"),
                original = saved.getString("original"),
                translation = saved.getString("translation"),
                originalLanguageTag = saved.optString("originalLanguageTag", "en"),
                translationLanguageTag = saved.optString("translationLanguageTag", "ru"),
                successCount = saved.optInt("successCount", 0),
                nextReviewAtMillis = saved.optLong("nextReviewAtMillis", 0L),
                topicId = if (saved.isNull("topicId")) null else saved.optString("topicId")
            )
        }
    }.getOrDefault(emptyList())

    fun loadTopics(): List<Topic> = runCatching {
        val savedTopics = JSONArray(preferences.getString(KEY_TOPICS, "[]"))
        List(savedTopics.length()) { index ->
            val saved = savedTopics.getJSONObject(index)
            Topic(
                id = saved.getString("id"),
                name = saved.getString("name"),
                languageTag = saved.optString("languageTag", "ru")
            )
        }
    }.getOrDefault(emptyList())

    fun save(words: List<Word>) {
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
                    .put("languageTag", topic.languageTag)
            )
        }
        preferences.edit().putString(KEY_TOPICS, savedTopics.toString()).apply()
    }

    private companion object {
        const val KEY_WORDS = "words"
        const val KEY_TOPICS = "topics"
        const val KEY_ENGLISH_300_SEEDED = "english_300_seeded"
        const val ENGLISH_300_ASSET = "english_300.tsv"
        const val ENGLISH_300_TOPIC_NAME = "English 300"
        const val ENGLISH_300_WORD_COUNT = 300
    }
}
