package com.rama.tui.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.rama.tui.Track
import java.io.File

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val path: String,
    val size: Long,
    val lastModified: Long,
    val ext: String,
    val title: String,
    val artists: String,
    val countries: String,
    val languages: String,
    val durationMs: Long?,
) {
    companion object {
        // Unit separator — safe delimiter for joining list fields since it won't appear
        // in filenames or the user-facing artist/country/language strings.
        private const val DELIM = "\u241F"

        private fun List<String>.pack(): String = joinToString(DELIM)
        private fun String.unpack(): List<String> =
            if (isEmpty()) emptyList() else split(DELIM)

        /** Builds a cache row from a freshly-scanned file. [durationMs] should come from
         *  MusicManager.probeDuration() — this function does no I/O itself. */
        fun from(file: File, base: Track, durationMs: Long?): TrackEntity = TrackEntity(
            path = file.absolutePath,
            size = file.length(),
            lastModified = file.lastModified(),
            ext = base.ext,
            title = base.title,
            artists = base.artists.pack(),
            countries = base.countries.pack(),
            languages = base.languages.pack(),
            durationMs = durationMs,
        )
    }

    /** Rehydrates the app-facing [Track] model from this cached row. */
    fun toTrack(): Track = Track(
        file = File(path),
        artists = artists.unpack(),
        title = title,
        countries = countries.unpack(),
        languages = languages.unpack(),
        ext = ext,
        durationMs = durationMs,
    )

    /** Whether this row's fingerprint still matches the file on disk. */
    fun matches(file: File): Boolean =
        size == file.length() && lastModified == file.lastModified()
}
