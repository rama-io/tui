package com.rama.tui.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import com.rama.tui.CsActivity
import com.rama.tui.MediaPlaybackService
import com.rama.tui.R
import com.rama.tui.TrackAdapter
import com.rama.tui.managers.MusicManager

class MainActivity : CsActivity() {

    private lateinit var listView: ListView
    private lateinit var playPauseIcon: ImageView
    private lateinit var playPauseButton: FrameLayout
    private lateinit var prevButton: FrameLayout
    private lateinit var nextButton: FrameLayout
    private lateinit var repeatButton: FrameLayout
    private lateinit var shuffleButton: FrameLayout
    private lateinit var repeatIcon: ImageView
    private lateinit var shuffleIcon: ImageView
    private lateinit var progressBg: View
    private lateinit var currentlyPlayingText: TextView

    private val progressHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            updateProgress()
            progressHandler.postDelayed(this, 500)
        }
    }

    companion object {
        private const val REQ_AUDIO = 1001
        private const val REQ_MANAGE = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MusicManager.initMediaSession(this)
        MusicManager.requestAudioFocus(this)
        setContentView(R.layout.activity_main)

        val root = findViewById<View>(R.id.root)
        applyEdgeToEdgePadding(root)
        applyCurrentTheme(root)

        val openSettingsBtn = findViewById<FrameLayout>(R.id.open_settings)
        openSettingsBtn.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            true
        }

        // Bind views
        listView = findViewById(R.id.track_list)
        playPauseButton = findViewById(R.id.play_pause_button)
        prevButton = findViewById(R.id.prev_button)
        nextButton = findViewById(R.id.next_button)
        repeatButton = findViewById(R.id.repeat_button)
        shuffleButton = findViewById(R.id.shuffle_button)

        playPauseIcon = playPauseButton.getChildAt(0) as ImageView
        repeatIcon = repeatButton.getChildAt(0) as ImageView
        shuffleIcon = shuffleButton.getChildAt(0) as ImageView

        val nowPlaying = findViewById<FrameLayout>(R.id.currently_playing_display)
        progressBg = nowPlaying.findViewById(R.id.progress_bg)
        currentlyPlayingText = nowPlaying.getChildAt(1) as TextView

        // Wire listeners
        playPauseButton.setOnClickListener { MusicManager.togglePlayPause() }
        prevButton.setOnClickListener { MusicManager.prev() }
        nextButton.setOnClickListener { MusicManager.next() }
        repeatButton.setOnClickListener {
            MusicManager.isRepeat = !MusicManager.isRepeat
            refreshUi()
        }
        shuffleButton.setOnClickListener {
            MusicManager.shuffleTracks()
            (listView.adapter as? TrackAdapter)?.updateTracks(MusicManager.tracks)
            listView.smoothScrollToPosition(0)
        }

        nowPlaying.setOnTouchListener { v, event ->
            if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                val fraction = event.x / v.width
                MusicManager.seekTo(fraction)
                v.performClick()
            }
            true
        }

        val filterInput = findViewById<EditText>(R.id.filter_input)
        val clearButton = findViewById<FrameLayout>(R.id.clear_button)

        filterInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) =
                Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                (listView.adapter as? TrackAdapter)?.filter(s?.toString() ?: "")
                refreshUi()
            }
        })

        clearButton.setOnClickListener {
            filterInput.text.clear()
            (listView.adapter as? TrackAdapter)?.resetToFullLibrary()
            MusicManager.restoreTracks(MusicManager.allTracks)
            refreshUi()
        }

        MusicManager.onStateChanged = { runOnUiThread { refreshUi() } }

        // Load data and start progress ticker
        loadOrRequestTracks()
        progressHandler.post(progressRunnable)
    }

    override fun onDestroy() {
        MusicManager.onStateChanged = null
        progressHandler.removeCallbacks(progressRunnable)
        if (!MusicManager.isPlaying) {
            MediaPlaybackService.stop(this)
        }
        super.onDestroy()
    }

    private fun loadOrRequestTracks() {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !MusicManager.hasPermission(this) -> {
                val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    Manifest.permission.READ_MEDIA_AUDIO
                else
                    Manifest.permission.READ_EXTERNAL_STORAGE
                requestPermissions(arrayOf(permission), REQ_AUDIO)
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                    !Environment.isExternalStorageManager() -> {
                val intent = Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${packageName}")
                )
                startActivityForResult(intent, REQ_MANAGE)
            }

            else -> loadTracks()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQ_AUDIO && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            loadOrRequestTracks()
        }
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: android.content.Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_MANAGE) {
            loadTracks()
        }
        TrackEditDialog.onActivityResult(this, requestCode, resultCode)
    }

    private fun loadTracks() {
        MusicManager.loadTracks(this)
        listView.adapter = TrackAdapter(this, MusicManager.tracks) { track ->
            TrackEditDialog.show(this, track) {
                MusicManager.loadTracks(this)
                (listView.adapter as? TrackAdapter)?.let { adapter ->
                    adapter.updateTracks(MusicManager.tracks)
                } ?: run {
                    listView.adapter = TrackAdapter(this, MusicManager.tracks) { t ->
                        TrackEditDialog.show(this, t) { loadTracks() }
                    }
                }
                refreshUi()
            }
        }
        refreshUi()
    }

    private fun refreshUi() {
        playPauseIcon.setImageResource(
            if (MusicManager.isPlaying) R.drawable.icon_pause_circle
            else R.drawable.icon_play_circle
        )
        repeatIcon.alpha = if (MusicManager.isRepeat) 1f else 0.7f

        val track = MusicManager.currentTrack
        currentlyPlayingText.text = track?.let { "${it.displayArtists} - ${it.title}" } ?: "-"

        (listView.adapter as? TrackAdapter)?.notifyDataSetChanged()

        val idx = MusicManager.currentIndex
        if (idx >= 0) listView.smoothScrollToPosition(idx)
    }

    private fun updateProgress() {
        val progress = MusicManager.playerProgress() ?: return
        progressBg.post {
            val parent = progressBg.parent as View
            progressBg.layoutParams.width = (parent.width * progress).toInt()
            progressBg.requestLayout()
        }
    }
}
