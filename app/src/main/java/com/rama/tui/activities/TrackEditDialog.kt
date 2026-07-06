package com.rama.tui.activities

import android.app.Activity
import android.app.Dialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
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
import com.rama.tui.managers.MusicManager
import com.rama.tui.managers.PrefsManager
import com.rama.bohio.managers.ThemeManager
import java.io.File

object TrackEditDialog {

    private const val TAG = "TrackEditDialog"

    internal const val REQ_SD_TREE = 2001

    // Pending file operation held across the SAF picker round-trip
    private var pendingOperation: (() -> Unit)? = null

    fun onActivityResult(activity: Activity, requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != REQ_SD_TREE) return
        if (resultCode != Activity.RESULT_OK || data == null) {
            pendingOperation = null
            Toast.makeText(activity, "SD card access denied", Toast.LENGTH_SHORT).show()
            return
        }
        val treeUri = data.data ?: run {
            pendingOperation = null
            return
        }
        // Persist permission so we don't ask again
        activity.contentResolver.takePersistableUriPermission(
            treeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        PrefsManager.getInstance(activity)
            .setString(PrefsManager.FileKeys.SD_TREE_URI, treeUri.toString())

        // Execute the deferred rename now that we have access
        pendingOperation?.invoke()
        pendingOperation = null
    }

    // ── SAF helpers ──────────────────────────────────────────────────────────

    /** True if [file] lives on primary external storage (writable via File APIs). */
    private fun isOnPrimaryStorage(file: File): Boolean {
        val primary = Environment.getExternalStorageDirectory().canonicalPath
        return file.canonicalPath.startsWith(primary)
    }

    /**
     * Returns a persisted SAF tree URI for the volume that [file] lives on,
     * or null if none has been granted yet.
     */
    private fun getSafTreeUri(context: Context, file: File): Uri? {
        val raw = PrefsManager.getInstance(context)
            .getString(PrefsManager.FileKeys.SD_TREE_URI, "") ?: return null
        val treeUri = Uri.parse(raw)

        // Verify we still hold the permission
        val persisted = context.contentResolver.persistedUriPermissions
        if (persisted.none { it.uri == treeUri && it.isWritePermission }) return null

        return treeUri
    }

    /**
     * Renames [src] to [dest] using raw DocumentsContract SAF APIs (no DocumentFile needed).
     */
    private fun renameViaSaf(context: Context, treeUri: Uri, src: File, newName: String): Boolean {
        return try {
            val docUri = findDocumentUri(context, treeUri, src) ?: return false
            DocumentsContract.renameDocument(context.contentResolver, docUri, newName) != null
        } catch (e: Exception) {
            Log.e(TAG, "renameViaSaf failed: ${e.message}")
            false
        }
    }

    /**
     * Resolves the document URI for [target] under [treeUri] by building the
     * document ID from the volume ID + relative path.
     */
    private fun findDocumentUri(context: Context, treeUri: Uri, target: File): Uri? {
        // treeUri last path segment looks like "0B81-0B31:" or "primary:"
        val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val volumeId = treeDocId.trimEnd(':')
        val volumeMount = File("/storage/$volumeId").takeIf { it.exists() } ?: return null
        val relative = target.canonicalPath
            .removePrefix(volumeMount.canonicalPath)
            .trimStart('/')
        val docId = "$volumeId:$relative"
        return DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
    }

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

        // Hide metadata strip button on API < 26 (jaudiotagger 3.x requires java.nio)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            deleteMetaBtn.visibility = android.view.View.GONE
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

        // Delete song — routes through SAF for SD card paths
        deleteSongBtn.setOnClickListener {
            deleteFile(activity, track.file) { ok ->
                if (ok) {
                    Toast.makeText(activity, "Track deleted", Toast.LENGTH_SHORT).show()
                    onChanged()
                    dialog.dismiss()
                } else if (pendingOperation == null) {
                    Toast.makeText(activity, "Could not delete file", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Rename: try atomic renameTo first; fall back to copy+delete for cross-fs or API quirks
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

            renameFile(activity, track.file, dest) { ok ->
                if (ok) {
                    Toast.makeText(activity, "Track renamed", Toast.LENGTH_SHORT).show()
                    onChanged()
                    dialog.dismiss()
                } else if (pendingOperation == null) {
                    // Only show failure if we didn't just launch the SAF picker
                    Toast.makeText(activity, "Could not rename file", Toast.LENGTH_SHORT).show()
                }
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
     * Renames [src] to [dest], routing through SAF for SD card paths on API 21+.
     * If SAF permission hasn't been granted yet, launches the tree picker and
     * stores [onComplete] to retry after the user grants access.
     * Returns true if the rename completed synchronously, false if it failed or
     * was deferred (SAF picker launched).
     */
    private fun renameFile(
        activity: Activity,
        src: File,
        dest: File,
        onComplete: (success: Boolean) -> Unit,
    ) {
        if (src == dest) {
            onComplete(true); return
        }

        // Primary storage: use File APIs directly
        if (isOnPrimaryStorage(src)) {
            // API 29: renameTo() is unreliable on external primary storage under scoped storage.
            // Use MediaStore.Audio.Media to update the filename — the system allows this with
            // READ_EXTERNAL_STORAGE granted on Q, and it keeps the MediaStore index in sync.
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                try {
                    val values = ContentValues().apply {
                        put(MediaStore.Audio.Media.DISPLAY_NAME, dest.name)
                        put(MediaStore.Audio.Media.DATA, dest.absolutePath)
                    }
                    val updated = activity.contentResolver.update(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        values,
                        "${MediaStore.Audio.Media.DATA} = ?",
                        arrayOf(src.absolutePath)
                    )
                    if (updated > 0) { onComplete(true); return }
                } catch (e: Exception) {
                    Log.e(TAG, "API 29 MediaStore rename failed, falling back: ${e.message}")
                }
            }
            onComplete(renameFileNative(src, dest))
            return
        }

        // SD card path — need SAF
        val treeUri = getSafTreeUri(activity, src)
        if (treeUri != null) {
            val ok = renameViaSaf(activity, treeUri, src, dest.name)
            onComplete(ok)
        } else {
            // No permission yet — stash the rename and launch the picker
            pendingOperation = {
                val uri = getSafTreeUri(activity, src)
                if (uri != null) {
                    val ok = renameViaSaf(activity, uri, src, dest.name)
                    onComplete(ok)
                } else {
                    onComplete(false)
                }
            }
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Pre-select the SD card volume in the picker
                val volumeId = src.canonicalPath
                    .removePrefix("/storage/").substringBefore("/")
                intent.putExtra(
                    DocumentsContract.EXTRA_INITIAL_URI,
                    Uri.parse("content://com.android.externalstorage.documents/tree/$volumeId%3A")
                )
            }
            activity.startActivityForResult(intent, REQ_SD_TREE)
        }
    }

    /** Atomic rename with copy+delete fallback, for primary storage only. */
    private fun renameFileNative(src: File, dest: File): Boolean {
        if (src.renameTo(dest) && dest.exists()) return true

        // API 29: renameTo() can fail on external storage even on primary due to scoped storage.
        // Fall back to updating the filename via MediaStore, which the system allows with
        // READ_EXTERNAL_STORAGE granted on this API level.
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            try {
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, dest.name)
                    put(MediaStore.Audio.Media.DATA, dest.absolutePath)
                }
                // We don't have a Context reference here, so delegate back via a flag —
                // the caller (renameFile) handles the Q path directly before reaching this.
            } catch (_: Exception) { }
        }

        return try {
            src.copyTo(dest, overwrite = true)
            if (dest.length() == src.length()) {
                src.delete(); true
            } else {
                dest.delete(); false
            }
        } catch (e: Exception) {
            Log.e(TAG, "renameFileNative fallback failed: ${e.message}")
            dest.delete(); false
        }
    }

    /**
     * Deletes [file], routing through SAF for SD card paths.
     * Mirrors the same permission flow as [renameFile].
     */
    private fun deleteFile(
        activity: Activity,
        file: File,
        onComplete: (success: Boolean) -> Unit,
    ) {
        if (isOnPrimaryStorage(file)) {
            onComplete(file.delete())
            return
        }

        val treeUri = getSafTreeUri(activity, file)
        if (treeUri != null) {
            val ok = deleteViaSaf(activity, treeUri, file)
            onComplete(ok)
        } else {
            pendingOperation = {
                val uri = getSafTreeUri(activity, file)
                if (uri != null) {
                    val ok = deleteViaSaf(activity, uri, file)
                    onComplete(ok)
                } else {
                    onComplete(false)
                }
            }
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val volumeId = file.canonicalPath
                    .removePrefix("/storage/").substringBefore("/")
                intent.putExtra(
                    DocumentsContract.EXTRA_INITIAL_URI,
                    Uri.parse("content://com.android.externalstorage.documents/tree/$volumeId%3A")
                )
            }
            activity.startActivityForResult(intent, REQ_SD_TREE)
        }
    }

    private fun deleteViaSaf(context: Context, treeUri: Uri, file: File): Boolean {
        return try {
            val docUri = findDocumentUri(context, treeUri, file) ?: return false
            DocumentsContract.deleteDocument(context.contentResolver, docUri)
        } catch (e: Exception) {
            Log.e(TAG, "deleteViaSaf failed: ${e.message}")
            false
        }
    }

    /* This removes tag headers from the file on disk.
    * Do NOT call the instance audioFile.delete(), that deletes the file itself.
    */
    private fun stripEmbeddedMetadata(activity: Activity, track: Track): Boolean {
        val supported = setOf(
    "mp3", "m4a", "aac", "flac", "ogg", "wav", "aiff", "wma", "alac", "ape", "wv", "tta", "dsf", "dff", "opus", "amr", "mka"
)
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
