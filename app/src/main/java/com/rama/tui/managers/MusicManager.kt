package com.rama.tui.managers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import com.rama.tui.Track
import java.io.File

object MusicManager {

    private var player: MediaPlayer? = null

    var tracks: List<Track> = emptyList()
        private set

    var currentIndex: Int = -1
        private set

    var isPlaying: Boolean = false
        private set

    var isRepeat: Boolean = false

    var onStateChanged: (() -> Unit)? = null

    val currentTrack: Track? get() = tracks.getOrNull(currentIndex)

    var allTracks: List<Track> = emptyList()
        private set

    fun setTracks(newTracks: List<Track>, index: Int = 0) {
        tracks = newTracks
        play(index)
    }

    fun seekTo(fraction: Float) {
        val p = player ?: return
        p.seekTo((p.duration * fraction.coerceIn(0f, 1f)).toInt())
    }

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

    fun loadTracks(context: Context): Boolean {
        if (!hasPermission(context)) return false
        val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        allTracks = scanDir(musicDir)
        tracks = allTracks
        if (tracks.isNotEmpty() && currentIndex < 0) currentIndex = 0
        return true
    }

    private fun scanDir(dir: File): List<Track> =
        dir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in Track.AUDIO_EXTENSIONS }
            ?.map { Track.fromFile(it) }
            ?.sortedBy { it.title.lowercase() }
            ?: emptyList()

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

    fun release() {
        player?.release()
        player = null
        isPlaying = false
        onStateChanged?.invoke()
    }

    fun playerProgress(): Float? {
        val p = player ?: return null
        if (p.duration <= 0) return null
        return p.currentPosition.toFloat() / p.duration
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

    private fun onTrackFinished() {
        when {
            isRepeat -> play(currentIndex)
            else -> next()
        }
    }
}
