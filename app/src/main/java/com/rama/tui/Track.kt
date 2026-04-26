package com.rama.tui

import java.io.File

data class Track(
    val file: File,
    val artists: List<String>,
    val title: String,
    val countries: List<String>,
    val languages: List<String>,
    val ext: String,
) {
    val displayArtists: String get() = artists.joinToString(", ")
    val displayCountries: String get() = countries.joinToString(", ") { localeCountry(it) }
    val displayLanguages: String get() = languages.joinToString(", ") { localeLanguage(it) }

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

        private fun localeCountry(code: String): String =
            java.util.Locale("", code).getDisplayCountry(java.util.Locale.ENGLISH)
                .takeIf { it.isNotBlank() && it != code } ?: code

        private fun localeLanguage(code: String): String =
            java.util.Locale(code).getDisplayLanguage(java.util.Locale.ENGLISH)
                .takeIf { it.isNotBlank() && it != code } ?: code

        private fun String.splitComma() = split(",").map { it.trim() }.filter { it.isNotEmpty() }

        val AUDIO_EXTENSIONS = setOf("mp3", "ogg", "flac", "wav", "m4a", "aac")
    }
}
