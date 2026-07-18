package com.rama.tui.managers

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaPlayer
import android.os.Build
import android.os.Environment
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.provider.MediaStore
import com.rama.tui.MediaButtonReceiver
import com.rama.tui.MediaPlaybackService
import com.rama.tui.db.AppDatabase
import com.rama.tui.db.TrackEntity
import com.rama.tui.managers.PrefsManager.PrefSortStyle
import com.rama.tui.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import android.media.MediaMetadata

object MusicManager {

    private var player: MediaPlayer? = null
    private var mediaSession: MediaSession? = null
    private var audioFocusRequest: android.media.AudioFocusRequest? = null
    private var appContext: Context? = null

    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    var tracks: List<Track> = emptyList()
        private set

    var allTracks: List<Track> = emptyList()
        private set

    var currentIndex: Int = -1
        private set

    var isPlaying: Boolean = false
        private set

    var isRepeat: Boolean = false

    var onStateChanged: (() -> Unit)? = null
    var onNotificationChanged: (() -> Unit)? = null

    val currentTrack: Track? get() = tracks.getOrNull(currentIndex)

    val artists: List<String>
        get() = allTracks
            .flatMap { it.artists }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()

    // region Media Session

    fun initMediaSession(context: Context) {
        if (mediaSession != null) return
        appContext = context.applicationContext

        val receiver = ComponentName(context, MediaButtonReceiver::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        else
            android.app.PendingIntent.FLAG_UPDATE_CURRENT

        val mediaButtonIntent = android.app.PendingIntent.getBroadcast(
            context, 0,
            android.content.Intent(context, MediaButtonReceiver::class.java),
            flags
        )

        mediaSession = MediaSession(context, "TuiMediaSession").apply {
            setMediaButtonReceiver(mediaButtonIntent)
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    if (!isPlaying) togglePlayPause()
                }

                override fun onPause() {
                    if (isPlaying) togglePlayPause()
                }

                override fun onSkipToNext() {
                    next()
                }

                override fun onSkipToPrevious() {
                    prev()
                }
            })
            isActive = true
        }

        MediaPlaybackService.start(context)
    }

    private fun updateMetadata() {
        val track = currentTrack
        val duration = player?.duration?.toLong()?.coerceAtLeast(0L) ?: 0L

        mediaSession?.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, track?.title ?: "")
                .putString(MediaMetadata.METADATA_KEY_ARTIST, track?.displayArtists ?: "")
                .putString(MediaMetadata.METADATA_KEY_ALBUM, track?.file?.parentFile?.name ?: "")
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, track?.title ?: "")
                .putLong(MediaMetadata.METADATA_KEY_DURATION, duration)
                .build()
        )
    }

    fun releaseMediaSession() {
        mediaSession?.release()
        mediaSession = null
    }

    fun getSessionToken(): android.media.session.MediaSession.Token? {
        return mediaSession?.sessionToken
    }

    private fun updatePlaybackState() {
        val state =
            if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        mediaSession?.setPlaybackState(
            PlaybackState.Builder()
                .setState(state, player?.currentPosition?.toLong() ?: 0L, 1f)
                .setActions(
                    PlaybackState.ACTION_PLAY or
                            PlaybackState.ACTION_PAUSE or
                            PlaybackState.ACTION_PLAY_PAUSE or
                            PlaybackState.ACTION_SKIP_TO_NEXT or
                            PlaybackState.ACTION_SKIP_TO_PREVIOUS
                )
                .build()
        )
    }

    // region Audio Focus

    private var audioFocusRequested = false

    fun requestAudioFocus(context: Context) {
        if (audioFocusRequested) return
        audioFocusRequested = true
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Required on older Android for Bluetooth headset button routing
        @Suppress("DEPRECATION")
        am.registerMediaButtonEventReceiver(ComponentName(context, MediaButtonReceiver::class.java))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest =
                android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setOnAudioFocusChangeListener { focus ->
                        when (focus) {
                            AudioManager.AUDIOFOCUS_LOSS -> {
                                if (isPlaying) {
                                    togglePlayPause()
                                }
                            }

                            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                                if (isPlaying) {
                                    togglePlayPause()
                                }
                            }
                        }
                    }.build().also { am.requestAudioFocus(it) }
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(
                { focus ->
                    when (focus) {
                        AudioManager.AUDIOFOCUS_LOSS -> {
                            if (isPlaying) {
                                togglePlayPause()
                            }
                        }

                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                            if (isPlaying) {
                                togglePlayPause()
                            }
                        }
                    }
                },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
    }

    // region Permissions

    fun hasPermission(context: Context?): Boolean {
        if (context == null) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true

        // Android 11+ (API 30+): MANAGE_EXTERNAL_STORAGE is required for broad filesystem access
        // (needed to list arbitrary dirs and rename files on SD card).
        // READ_EXTERNAL_STORAGE alone is not sufficient on Android 10-12.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager()
        }

        // Android 13+ (API 33+): granular media permission replaces READ_EXTERNAL_STORAGE
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_AUDIO
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    // region Track Loading
    fun loadTracks(context: Context, onComplete: (Boolean) -> Unit = {}) {
        if (!hasPermission(context)) {
            onComplete(false)
            return
        }
        appContext = context.applicationContext
        val appCtx = appContext!!

        managerScope.launch {
            val prefs = PrefsManager.getInstance(appCtx)
            val sortStyle =
                prefs.getString(PrefsManager.FileKeys.LIST_SORT_STYLE, PrefSortStyle.AZ)
            val keepTogether =
                prefs.getBoolean(PrefsManager.FileKeys.LIST_SORT_KEEP_TOGETHER, false)

            // API 29 (Android 10): scoped storage blocks File.listFiles() on external storage
            // even with READ_EXTERNAL_STORAGE granted. Use MediaStore instead.
            val raw = if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                scanViaMediaStore(appCtx)
            } else {
                syncTracks(appCtx, getStorageRoots(appCtx))
            }

            // Persist the full folder list before filtering so settings can always show all folders
            val allFolders = raw.mapNotNull { it.file.parent }.distinct().sorted().toSet()
            prefs.setAllFolders(allFolders)

            val disabledFolders = prefs.getDisabledFolders()
            val filtered = if (disabledFolders.isEmpty()) raw
            else raw.filter { it.file.parent !in disabledFolders }

            withContext(Dispatchers.Main) {
                allTracks = sortTracks(filtered, sortStyle, keepTogether)
                tracks = allTracks
                if (tracks.isNotEmpty() && currentIndex < 0) currentIndex = 0
                onComplete(true)
            }
        }
    }

    fun reSort(context: Context) {
        val prefs = PrefsManager.getInstance(context)
        val sortStyle =
            prefs.getString(PrefsManager.FileKeys.LIST_SORT_STYLE, PrefSortStyle.AZ)
        val keepTogether = prefs.getBoolean(PrefsManager.FileKeys.LIST_SORT_KEEP_TOGETHER, false)
        allTracks = sortTracks(allTracks, sortStyle, keepTogether)
        tracks = allTracks
        currentIndex = tracks.indexOf(currentTrack).coerceAtLeast(0)
        onStateChanged?.invoke()
    }

    /** Sort tracks by title (az/za), optionally grouping files from the same folder together. */
    fun sortTracks(
        source: List<Track>,
        sortStyle: String,
        keepTogether: Boolean
    ): List<Track> {
        val comparator: Comparator<Track> = when (sortStyle) {
            PrefSortStyle.ZA -> compareByDescending { it.title.lowercase() }
            else -> compareBy { it.title.lowercase() }
        }

        return if (keepTogether) {
            // Group by parent folder, sort folder names, then sort within each folder
            val folderComparator: Comparator<String> = when (sortStyle) {
                PrefSortStyle.ZA -> compareByDescending { it.lowercase() }
                else -> compareBy { it.lowercase() }
            }
            source
                .groupBy { it.file.parent ?: "" }
                .entries
                .sortedWith(Comparator { a, b -> folderComparator.compare(a.key, b.key) })
                .flatMap { (_, folderTracks) -> folderTracks.sortedWith(comparator) }
        } else {
            source.sortedWith(comparator)
        }
    }

    // API 29 only: READ_EXTERNAL_STORAGE is granted but File.listFiles() is blocked by scoped
    // storage. Query MediaStore instead, which respects the permission correctly on this API level.
    private fun scanViaMediaStore(context: Context): List<Track> {
        val results = mutableListOf<Track>()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            "${MediaStore.Audio.Media.TITLE} ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val path = cursor.getString(dataCol) ?: continue
                val file = File(path)
                val durationMs = cursor.getLong(durationCol).takeIf { it > 0 } ?: continue
                val uri = android.content.ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                )

                val base = Track.fromFile(file)
                val msTitle = cursor.getString(titleCol)?.takeIf { it.isNotBlank() }
                val msArtist = cursor.getString(artistCol)
                    ?.takeIf { it.isNotBlank() && it != "<unknown>" }
                results.add(
                    if (msTitle != null || msArtist != null) {
                        base.copy(
                            title = msTitle ?: base.title,
                            artists = if (msArtist != null) listOf(msArtist) else base.artists,
                            contentUri = uri,
                            durationMs = durationMs,
                        )
                    } else base.copy(contentUri = uri, durationMs = durationMs)
                )
            }
        }

        return results
    }

    private fun getStorageRoots(context: Context): List<File> {
        val roots = mutableListOf<File>()

        // Internal storage root
        val internalRoot = Environment.getExternalStorageDirectory()
        if (internalRoot.exists()) roots.add(internalRoot)

        // SD card and any other mounted volumes. walk up from the app-specific dir to the volume root
        context.getExternalFilesDirs(null)
            .filterNotNull()
            .forEach { appSpecificDir ->
                // Path: /storage/<id>/Android/data/<pkg>/files. 4 levels up = volume root
                val volumeRoot =
                    appSpecificDir.parentFile?.parentFile?.parentFile?.parentFile
                if (volumeRoot != null && volumeRoot.exists() && volumeRoot !in roots)
                    roots.add(volumeRoot)
            }

        return roots.distinct().filter { it.exists() && it.isDirectory }
    }

    private fun probeDuration(file: File): Long? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            if (extractor.trackCount == 0) return null

            var durationUs: Long? = null
            var hasDecodableAudioTrack = false

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (!mime.startsWith("audio/")) continue

                // Not every format map carries KEY_DURATION; format.containsKey() itself needs
                // API 29+, so just try the read and fall back to the container-level duration.
                try {
                    durationUs = format.getLong(MediaFormat.KEY_DURATION)
                } catch (e: Exception) {
                    // leave durationUs as-is (may already be set from another track, or stay null)
                }

                if (MediaCodecList(MediaCodecList.REGULAR_CODECS).findDecoderForFormat(format) != null) {
                    hasDecodableAudioTrack = true
                }
            }

            if (!hasDecodableAudioTrack) return null
            durationUs?.let { it / 1000 }?.takeIf { it > 0 }
        } catch (e: Exception) {
            null
        } finally {
            // release(), not close() — MediaExtractor only implements Closeable/AutoCloseable
            // from API 28 onward, and minSdk here is 21.
            extractor.release()
        }
    }

    private suspend fun syncTracks(context: Context, roots: List<File>): List<Track> {
        val dao = AppDatabase.getInstance(context).trackDao()
        val cached = dao.getAll().associateBy { it.path }
        val seenPaths = HashSet<String>(cached.size)
        val toUpsert = mutableListOf<TrackEntity>()
        val result = mutableListOf<Track>()

        val stack = ArrayDeque<File>()
        roots.forEach { stack.addLast(it) }
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            // Skip Android system directories to avoid permission errors and irrelevant content
            if (current.name == "Android") continue

            if (File(current, ".nomedia").exists()) continue
            val children = current.listFiles() ?: continue
            for (child in children) {
                if (child.isDirectory) {
                    stack.addLast(child)
                    continue
                }
                if (!child.isFile || child.extension.lowercase() !in Track.AUDIO_EXTENSIONS) continue

                val path = child.absolutePath
                seenPaths.add(path)
                val cachedRow = cached[path]

                val entity = if (cachedRow != null && cachedRow.matches(child)) {
                    cachedRow // fingerprint unchanged — skip re-parsing and re-probing entirely
                } else {
                    val base = Track.fromFile(child)
                    val duration = probeDuration(child)
                    TrackEntity.from(child, base, duration).also { toUpsert.add(it) }
                }

                if (entity.durationMs != null) result.add(entity.toTrack())
            }
        }

        if (toUpsert.isNotEmpty()) dao.upsertAll(toUpsert)

        // Clean up rows for files that were removed/renamed since the last sync.
        val stalePaths = cached.keys - seenPaths
        if (stalePaths.isNotEmpty()) {
            stalePaths.chunked(900).forEach { dao.deleteByPaths(it) }
        }

        return result
    }

    var onPlaybackError: ((Track) -> Unit)? = null

    fun play(index: Int = currentIndex) {
        if (tracks.isEmpty() || index !in tracks.indices) return
        currentIndex = index
        val track = tracks[index]

        player?.release()
        player = null

        val newPlayer = MediaPlayer()
        try {
            newPlayer.apply {
                // API 29: raw file path access is blocked by scoped storage even with
                // READ_EXTERNAL_STORAGE granted. Use the content:// URI from MediaStore instead.
                if (track.contentUri != null) {
                    appContext!!.contentResolver.openFileDescriptor(track.contentUri, "r")
                        ?.use { pfd -> setDataSource(pfd.fileDescriptor) }
                        ?: throw java.io.IOException("Could not open URI: ${track.contentUri}")
                } else {
                    setDataSource(track.file.absolutePath)
                }
                setOnCompletionListener { onTrackFinished() }
                setOnErrorListener { _, _, _ ->
                    handlePlaybackFailure(track)
                    true
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            newPlayer.release()
            handlePlaybackFailure(track)
            return
        }

        player = newPlayer
        isPlaying = true
        onStateChanged?.invoke()
        onNotificationChanged?.invoke()
        updatePlaybackState()
        updateMetadata()
    }

    private fun handlePlaybackFailure(track: Track) {
        player = null
        isPlaying = false
        onPlaybackError?.invoke(track)
        onStateChanged?.invoke()
        onNotificationChanged?.invoke()
        updatePlaybackState()
    }

    fun togglePlayPause() {
        val p = player
        if (p == null) {
            play()
            return
        }
        if (isPlaying) {
            p.pause()
            isPlaying = false
        } else {
            p.start()
            isPlaying = true
        }
        onStateChanged?.invoke()
        onNotificationChanged?.invoke()
        updatePlaybackState()
    }

    fun next() {
        if (tracks.isEmpty()) return
        currentIndex = when {
            currentIndex < tracks.lastIndex -> currentIndex + 1
            isRepeat -> 0
            else -> return
        }
        play(currentIndex)
    }

    fun prev() {
        if (tracks.isEmpty()) return
        currentIndex = if (currentIndex > 0) currentIndex - 1 else tracks.lastIndex
        play(currentIndex)
    }

    fun seekTo(fraction: Float) {
        val p = player ?: return
        p.seekTo((p.duration * fraction.coerceIn(0f, 1f)).toInt())
    }

    fun release() {
        player?.release()
        player = null
        isPlaying = false
        onStateChanged?.invoke()
        onNotificationChanged?.invoke()
        updatePlaybackState()
    }

    // region Track Management

    fun setTracks(newTracks: List<Track>, index: Int = 0) {
        tracks = newTracks
        play(index)
    }

    fun restoreTracks(newTracks: List<Track>) {
        val playing = currentTrack
        tracks = newTracks
        currentIndex = newTracks.indexOf(playing).takeIf { it >= 0 } ?: currentIndex
        onStateChanged?.invoke()
    }

    fun shuffleTracks(keepTogether: Boolean = false) {
        if (tracks.isEmpty()) return
        val current = currentTrack

        tracks = if (keepTogether) {
            // Shuffle whole folder groups, keep tracks within each folder in their current order
            val groups = tracks.groupBy { it.file.parent ?: "" }.values.toMutableList().shuffled()
            val shuffled = groups.flatten().toMutableList()
            // Keep current track (and its folder block) at the front
            if (current != null) {
                val currentFolder = current.file.parent ?: ""
                val currentGroup = shuffled.filter { it.file.parent == currentFolder }
                val rest = shuffled.filter { it.file.parent != currentFolder }
                currentGroup + rest
            } else {
                shuffled
            }
        } else {
            val rest = tracks.toMutableList().also {
                if (current != null) it.remove(current)
            }.shuffled()
            if (current != null) listOf(current) + rest else rest
        }

        currentIndex = if (current != null) tracks.indexOf(current).coerceAtLeast(0) else 0
        onStateChanged?.invoke()
    }

    fun playerProgress(): Float? {
        val p = player ?: return null
        if (p.duration <= 0) return null
        return p.currentPosition.toFloat() / p.duration
    }

    // region Internal

    private fun onTrackFinished() {
        when {
            isRepeat -> play(currentIndex)
            else -> next()
        }
    }
}
