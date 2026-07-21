package com.rama.tui

import java.io.File
import java.text.Normalizer

data class Track(
    val file: File,
    val artists: List<String>,
    val title: String,
    val countries: List<String>,
    val languages: List<String>,
    val ext: String,
    val normalizedName: String = normalize(file.nameWithoutExtension),
    val contentUri: android.net.Uri? = null,
    val durationMs: Long? = null,
) {
    val displayArtists: String get() = artists.joinToString(", ")
    val displayCountries: String get() = countries.joinToString(", ") { localeCountry(it) }
    val displayLanguages: String get() = languages.joinToString(", ") { localeLanguage(it) }

    val displayDuration: String
        get() {
            val ms = durationMs ?: return ""
            val totalSeconds = ms / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }

    companion object {
        // Expected format: "Artist1, Artist2 - Title - Country1, Country2 - Lang1, Lang2"
        fun fromFile(file: File): Track {
            val name = file.nameWithoutExtension
            val parts = name.split(" - ").map { it.trim() }

            val hasArtist = parts.size > 1

            return Track(
                file = file,
                artists = if (hasArtist)
                    parts[0].splitComma()
                else
                    emptyList(),

                title = if (hasArtist)
                    parts.getOrNull(1) ?: name
                else
                    name,

                countries = parts.getOrNull(2)?.splitComma() ?: emptyList(),
                languages = parts.getOrNull(3)?.splitComma() ?: emptyList(),
                ext = file.extension,
            )
        }

        fun normalize(text: String): String {
            return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
                .lowercase()
                .trim()
                .replace("\\s+".toRegex(), " ") // collapse spaces
        }

        private fun localeCountry(code: String): String =
            java.util.Locale("", code).getDisplayCountry(java.util.Locale.ENGLISH)
                .takeIf { it.isNotBlank() && it != code } ?: code

        private fun localeLanguage(code: String): String =
            java.util.Locale(code).getDisplayLanguage(java.util.Locale.ENGLISH)
                .takeIf { it.isNotBlank() && it != code } ?: code

        private fun String.splitComma() = split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }
}
