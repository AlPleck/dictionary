package com.example.myapplication

import org.junit.Assert.assertEquals
import org.junit.Test

class PronunciationTextTest {
    @Test
    fun usesKanaReadingForJapaneseWordsWithKanji() {
        val word = Word(
            id = "iku",
            original = "行く",
            translation = "идти",
            originalLanguageTag = "ja",
            transcription = "いく"
        )

        assertEquals("いく", word.pronunciationText())
    }

    @Test
    fun usesOriginalTextWhenThereIsNoJapaneseReading() {
        val word = Word(
            id = "hello",
            original = "hello",
            translation = "привет",
            originalLanguageTag = "en",
            transcription = "həˈloʊ"
        )

        assertEquals("hello", word.pronunciationText())
    }
}
