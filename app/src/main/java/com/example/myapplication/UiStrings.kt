package com.example.myapplication

import android.content.res.AssetManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

internal class UiStrings private constructor(
    private val entries: Map<String, Map<String, String>>
) {
    fun text(language: String, source: String): String =
        entries[source]?.get(language) ?: source

    fun format(language: String, source: String, values: Map<String, String>): String {
        var result = text(language, source)
        values.forEach { (key, value) -> result = result.replace("{$key}", value) }
        return result
    }

    companion object {
        val empty = UiStrings(emptyMap())

        fun load(assets: AssetManager): UiStrings {
            val entries = runCatching {
                val translations = assets.open("ui_translations.tsv").bufferedReader().useLines { lines ->
                    lines.mapNotNull { line ->
                        val fields = line.split('\t')
                        if (fields.size != 5) return@mapNotNull null
                        fields[0] to mapOf(
                            "en" to fields[1],
                            "es" to fields[2],
                            "fr" to fields[3],
                            "de" to fields[4]
                        )
                    }.toMap()
                }.toMutableMap()
                assets.open("ui_translations_ja_zh.tsv").bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        val fields = line.split('\t')
                        if (fields.size == 3) {
                            translations[fields[0]] = translations[fields[0]].orEmpty() + mapOf(
                                "ja" to fields[1],
                                "zh" to fields[2]
                            )
                        }
                    }
                }
                translations.toMap()
            }.getOrDefault(emptyMap())
            return UiStrings(entries)
        }
    }
}

internal val LocalAppLanguage = staticCompositionLocalOf { "ru" }
internal val LocalUiStrings = staticCompositionLocalOf { UiStrings.empty }

@Composable
internal fun uiText(source: String): String =
    LocalUiStrings.current.text(LocalAppLanguage.current, source)

@Composable
internal fun uiFormat(source: String, values: Map<String, String>): String =
    LocalUiStrings.current.format(LocalAppLanguage.current, source, values)
