package com.rama.tui.activities

import android.app.Activity
import android.app.Dialog
import android.media.MediaMetadataRetriever
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.MultiAutoCompleteTextView
import android.widget.TextView
import android.widget.Toast
import com.rama.tui.R
import com.rama.tui.Track
import com.rama.tui.managers.FontManager
import com.rama.tui.managers.MusicManager
import com.rama.tui.managers.ThemeManager
import java.io.File

object TrackEditDialog {

    private const val TAG = "TrackEditDialog"

    fun onActivityResult(activity: Activity, requestCode: Int, resultCode: Int) = Unit

    private fun normalizeCsv(input: String): String {
        return input
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(", ")
    }

    fun show(activity: Activity, track: Track, onChanged: () -> Unit) {
        val dialog = Dialog(activity, android.R.style.Theme_DeviceDefault)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_track_edit)

        dialog.window?.let { win ->
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            activity.windowManager.defaultDisplay.getMetrics(dm)
            val params = win.attributes
            params.width = ViewGroup.LayoutParams.MATCH_PARENT
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT
            params.gravity = Gravity.CENTER
            params.dimAmount = 0.6f
            win.attributes = params
            win.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }

        val artistInput = dialog.findViewById<MultiAutoCompleteTextView>(R.id.artist)
        val titleInput = dialog.findViewById<EditText>(R.id.title)
        val countryInput = dialog.findViewById<EditText>(R.id.country)
        val languageInput = dialog.findViewById<EditText>(R.id.language)
        val summaryView = dialog.findViewById<TextView>(R.id.display)
        val metadataView = dialog.findViewById<TextView>(R.id.display_metadata)

        val deleteMetaBtn = dialog.findViewById<Button>(R.id.delete_metadata_button)
        val deleteSongBtn = dialog.findViewById<Button>(R.id.delete_button)
        val updateBtn = dialog.findViewById<Button>(R.id.update_button)
        val cancelBtn = dialog.findViewById<Button>(R.id.cancel_button)

        titleInput.setText(track.title)
        artistInput.setText(track.artists.joinToString(", "))
        countryInput.setText(track.countries.joinToString(", "))
        languageInput.setText(track.languages.joinToString(", "))

        val artistAdapter = ArrayAdapter(
            activity,
            android.R.layout.simple_dropdown_item_1line,
            MusicManager.artists
        )
        artistInput.setAdapter(artistAdapter)
        artistInput.setTokenizer(MultiAutoCompleteTextView.CommaTokenizer())
        artistInput.threshold = 1  // show suggestions after 1 character

        fun refreshSummary() {
            val t = titleInput.text.toString().trim()
            val a = normalizeCsv(artistInput.text.toString())
            val c = countryInput.text.toString().trim()
            val l = languageInput.text.toString().trim()
            summaryView.text = buildString {
                if (a.isNotEmpty()) {
                    append(a); append(" - ")
                }
                append(t)
                if (c.isNotEmpty()) {
                    append(" - "); append(c)
                }
                if (l.isNotEmpty()) {
                    append(" - "); append(l)
                }
            }
        }
        refreshSummary()

        val watcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) = Unit
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) = refreshSummary()
        }
        titleInput.addTextChangedListener(watcher)
        artistInput.addTextChangedListener(watcher)
        countryInput.addTextChangedListener(watcher)
        languageInput.addTextChangedListener(watcher)

        val embeddedMeta = readEmbeddedMetadata(track.file)
        metadataView.text = if (embeddedMeta.isEmpty()) {
            "(no embedded metadata found)"
        } else {
            embeddedMeta.entries.joinToString("\n") { (k, v) -> "$k: $v" }
        }

        ThemeManager.applyTheme(activity, dialog.findViewById(android.R.id.content))

        // Strip metadata
        deleteMetaBtn.setOnClickListener {
            if (embeddedMeta.isEmpty()) {
                Toast.makeText(activity, "No embedded metadata to remove", Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }
            val ok = stripEmbeddedMetadata(activity, track)
            if (ok) {
                metadataView.text = "(metadata removed)"
                Toast.makeText(activity, "Embedded metadata removed", Toast.LENGTH_SHORT).show()
                onChanged()
            } else {
                Toast.makeText(activity, "Failed to remove metadata", Toast.LENGTH_SHORT).show()
            }
        }

        // Delete song
        deleteSongBtn.setOnClickListener {
            if (track.file.delete()) {
                Toast.makeText(activity, "Track deleted", Toast.LENGTH_SHORT).show()
                onChanged()
                dialog.dismiss()
            } else {
                Toast.makeText(activity, "Could not delete file", Toast.LENGTH_SHORT).show()
            }
        }

        // Rename
        updateBtn.setOnClickListener {
            val newTitle = titleInput.text.toString().trim()
            val newArtists = normalizeCsv(artistInput.text.toString())
            val newCountries = countryInput.text.toString().trim()
            val newLanguages = languageInput.text.toString().trim()

            if (newTitle.isEmpty()) {
                Toast.makeText(activity, "Title cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val newFileName = buildString {
                if (newArtists.isNotEmpty()) {
                    append(newArtists); append(" - ")
                }
                append(newTitle)
                if (newCountries.isNotEmpty()) {
                    append(" - "); append(newCountries)
                }
                if (newLanguages.isNotEmpty()) {
                    append(" - "); append(newLanguages)
                }
            } + ".${track.ext}"

            val dest = File(track.file.parent, newFileName)
            if (track.file.renameTo(dest)) {
                Toast.makeText(activity, "Track renamed", Toast.LENGTH_SHORT).show()
                onChanged()
            } else {
                Toast.makeText(activity, "Could not rename file", Toast.LENGTH_SHORT).show()
            }
        }

        cancelBtn.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    // Metadata

    private val META_KEYS = listOf(
        MediaMetadataRetriever.METADATA_KEY_TITLE to "Title",
        MediaMetadataRetriever.METADATA_KEY_ARTIST to "Artist",
        MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST to "Album Artist",
        MediaMetadataRetriever.METADATA_KEY_ALBUM to "Album",
        MediaMetadataRetriever.METADATA_KEY_YEAR to "Year",
        MediaMetadataRetriever.METADATA_KEY_GENRE to "Genre",
        MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER to "Disc #",
        MediaMetadataRetriever.METADATA_KEY_COMPOSER to "Composer",
        MediaMetadataRetriever.METADATA_KEY_DURATION to "Duration (ms)",
        MediaMetadataRetriever.METADATA_KEY_BITRATE to "Bitrate",
        MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO to "Has Audio",
    )

    private fun readEmbeddedMetadata(file: File): Map<String, String> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val result = linkedMapOf<String, String>()
            val art = retriever.embeddedPicture
            if (art != null) result["Cover Art"] = "${art.size} bytes"
            for ((key, label) in META_KEYS) {
                val value = retriever.extractMetadata(key)
                if (!value.isNullOrBlank()) result[label] = value
            }
            result
        } catch (e: Exception) {
            emptyMap()
        } finally {
            retriever.release()
        }
    }

    /**
     * Strips all embedded tags using the *static* AudioFileIO.delete(audioFile).
     * This removes tag headers from the file on disk.
     * Do NOT call the instance audioFile.delete() — that deletes the file itself.
     */
    private fun stripEmbeddedMetadata(activity: Activity, track: Track): Boolean {
        val supported = setOf("mp3", "m4a", "aac", "flac", "ogg", "wav", "aiff", "wma")
        if (track.ext.lowercase() !in supported) {
            Toast.makeText(
                activity,
                "Metadata removal not supported for .${track.ext} files",
                Toast.LENGTH_LONG
            ).show()
            return false
        }
        return try {
            val audioFile = org.jaudiotagger.audio.AudioFileIO.read(track.file)
            org.jaudiotagger.audio.AudioFileIO.delete(audioFile)
            true
        } catch (e: Exception) {
            Log.e(TAG, "stripEmbeddedMetadata failed", e)
            false
        }
    }
}
