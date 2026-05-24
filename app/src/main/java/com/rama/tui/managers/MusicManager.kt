package com.rama.tui.managers

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Environment
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.content.ContextCompat
import com.rama.tui.MediaButtonReceiver
import com.rama.tui.MediaPlaybackService
import com.rama.tui.Track
import java.io.File

object MusicManager {

    private var player: MediaPlayer? = null
    private var mediaSession: MediaSessionCompat? = null
    private var audioFocusRequest: android.media.AudioFocusRequest? = null
    private var appContext: Context? = null

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

        mediaSession =
            MediaSessionCompat(context, "TuiMediaSession", receiver, mediaButtonIntent).apply {
                setMediaButtonReceiver(mediaButtonIntent)
                setCallback(object : MediaSessionCompat.Callback() {
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

    fun releaseMediaSession() {
        mediaSession?.release()
        mediaSession = null
    }

    private fun updatePlaybackState() {
        val state =
            if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        mediaSession?.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(state, player?.currentPosition?.toLong() ?: 0L, 1f)
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                            PlaybackStateCompat.ACTION_PAUSE or
                            PlaybackStateCompat.ACTION_PLAY_PAUSE or
                            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                )
                .build()
        )
    }

    // region Audio Focus

    fun requestAudioFocus(context: Context) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Required on older Android for Bluetooth headset button routing
        @Suppress("DEPRECATION")
        am.registerMediaButtonEventReceiver(ComponentName(context, MediaButtonReceiver::class.java))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest =
                android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setOnAudioFocusChangeListener { focus ->
                        when (focus) {
                            AudioManager.AUDIOFOCUS_LOSS -> if (isPlaying) togglePlayPause()
                            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> if (isPlaying) togglePlayPause()
                            AudioManager.AUDIOFOCUS_GAIN -> if (!isPlaying) togglePlayPause()
                        }
                    }.build().also { am.requestAudioFocus(it) }
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(
                { focus ->
                    when (focus) {
                        AudioManager.AUDIOFOCUS_LOSS -> if (isPlaying) togglePlayPause()
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> if (isPlaying) togglePlayPause()
                        AudioManager.AUDIOFOCUS_GAIN -> if (!isPlaying) togglePlayPause()
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

        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_AUDIO
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

        return ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    // region Track Loading

    fun loadTracks(context: Context): Boolean {
        if (!hasPermission(context)) return false
        val dirs = getStorageRoots(context)
        allTracks = dirs.flatMap { scanDir(it) }.sortedBy { it.title.lowercase() }
        tracks = allTracks
        if (tracks.isNotEmpty() && currentIndex < 0) currentIndex = 0
        return true
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

    private fun scanDir(dir: File): List<Track> {
        val results = mutableListOf<Track>()
        val stack = ArrayDeque<File>()
        stack.addLast(dir)
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            // Skip Android system directories to avoid permission errors and irrelevant content
            if (current.name == "Android") continue
            val children = current.listFiles() ?: continue
            for (child in children) {
                when {
                    child.isDirectory -> stack.addLast(child)
                    child.isFile && child.extension.lowercase() in Track.AUDIO_EXTENSIONS ->
                        results.add(Track.fromFile(child))
                }
            }
        }
        return results
    }

    // region Playback Control

    fun play(index: Int = currentIndex) {
        if (tracks.isEmpty() || index !in tracks.indices) return
        currentIndex = index

        player?.release()
        player = MediaPlayer().apply {
            setDataSource(tracks[index].file.absolutePath)
            setOnCompletionListener { onTrackFinished() }
            prepare()
            start()
        }
        isPlaying = true
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

    fun shuffleTracks() {
        if (tracks.isEmpty()) return
        val current = currentTrack
        val rest = tracks.toMutableList().also {
            if (current != null) it.remove(current)
        }.shuffled()
        tracks = if (current != null) listOf(current) + rest else rest
        currentIndex = 0
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
