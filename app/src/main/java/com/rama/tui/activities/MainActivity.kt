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
import android.view.KeyEvent
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import com.rama.tui.CsActivity
import com.rama.tui.MediaPlaybackService
import com.rama.tui.R
import com.rama.bohio.R as BohioR
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
        private const val REQ_SETTINGS = 1003
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
            startActivityForResult(Intent(this, SettingsActivity::class.java), REQ_SETTINGS)
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
            val keepTogether = prefs.getBoolean(
                com.rama.tui.managers.PrefsManager.FileKeys.LIST_SORT_KEEP_TOGETHER, false
            )
            MusicManager.shuffleTracks(keepTogether)
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
            // Android 11+ (API 30+): need MANAGE_EXTERNAL_STORAGE for SD card access & renaming.
            // Skip READ_EXTERNAL_STORAGE on these versions — it can't list arbitrary dirs anyway.
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                    !Environment.isExternalStorageManager() -> {
                val intent = Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${packageName}")
                )
                startActivityForResult(intent, REQ_MANAGE)
            }

            // Android 6–10 (API 23–29): need READ_EXTERNAL_STORAGE (and WRITE for renaming).
            // READ_MEDIA_AUDIO doesn't exist yet; READ_EXTERNAL_STORAGE covers listing.
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !MusicManager.hasPermission(this) -> {
                requestPermissions(
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    ),
                    REQ_AUDIO
                )
            }

            else -> loadTracks()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQ_AUDIO && grantResults.isNotEmpty() &&
            grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        ) {
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
            // Only proceed if the user actually granted All Files Access.
            // If they denied/dismissed, show the request again so the app doesn't silently load nothing.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
                loadTracks()
            } else {
                loadOrRequestTracks()
            }
        }
        if (requestCode == REQ_SETTINGS && resultCode == RESULT_OK) {
            (listView.adapter as? TrackAdapter)?.updateTracks(MusicManager.tracks)
            refreshUi()
        }
        TrackEditDialog.onActivityResult(this, requestCode, resultCode, data)
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
            if (MusicManager.isPlaying) BohioR.drawable.px_pause_circle
            else BohioR.drawable.px_play_circle
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

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_UP) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY,
                KeyEvent.KEYCODE_MEDIA_PAUSE,
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                    MusicManager.togglePlayPause()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_NEXT -> {
                    MusicManager.next()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                    MusicManager.prev()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
