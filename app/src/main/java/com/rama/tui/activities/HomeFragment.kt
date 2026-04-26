package com.rama.tui.activities

import android.Manifest
import android.app.Fragment
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import com.rama.tui.R
import com.rama.tui.TrackAdapter
import com.rama.tui.managers.MusicManager

class HomeFragment : Fragment() {

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
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.view_home, container, false)
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Bind views
        listView = view.findViewById(R.id.task_list)
        playPauseButton = view.findViewById(R.id.play_pause_button)
        prevButton = view.findViewById(R.id.prev_button)
        nextButton = view.findViewById(R.id.next_button)
        repeatButton = view.findViewById(R.id.repeat_button)
        shuffleButton = view.findViewById(R.id.shuffle_button)

        playPauseIcon = playPauseButton.getChildAt(0) as ImageView
        repeatIcon = repeatButton.getChildAt(0) as ImageView
        shuffleIcon = shuffleButton.getChildAt(0) as ImageView

        val nowPlaying = view.findViewById<FrameLayout>(R.id.currently_playing_display)
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

        val filterInput = view.findViewById<EditText>(R.id.filter_input)
        val clearButton = view.findViewById<FrameLayout>(R.id.clear_button)

        filterInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) =
                Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                (listView.adapter as? TrackAdapter)?.filter(s?.toString() ?: "")
            }
        })

        clearButton.setOnClickListener {
            filterInput.text.clear()
            (listView.adapter as? TrackAdapter)?.resetToFullLibrary()
            MusicManager.setTracks(MusicManager.allTracks) // see below
        }

        MusicManager.onStateChanged = { activity?.runOnUiThread { refreshUi() } }

        // Load data and start progress ticker
        loadOrRequestTracks()
        progressHandler.post(progressRunnable)
    }

    override fun onDestroyView() {
        MusicManager.onStateChanged = null
        progressHandler.removeCallbacks(progressRunnable)
        super.onDestroyView()
    }

    private fun loadOrRequestTracks() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || MusicManager.hasPermission(activity)) {
            loadTracks()
        } else {
            val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                Manifest.permission.READ_MEDIA_AUDIO
            else
                Manifest.permission.READ_EXTERNAL_STORAGE
            requestPermissions(arrayOf(permission), REQ_AUDIO)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQ_AUDIO && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            loadTracks()
        }
    }

    private fun loadTracks() {
        MusicManager.loadTracks(activity)
        listView.adapter = TrackAdapter(activity, MusicManager.tracks)
        refreshUi()
    }

    private fun refreshUi() {
        playPauseIcon.setImageResource(
            if (MusicManager.isPlaying) R.drawable.icon_pause_circle
            else R.drawable.icon_play_circle
        )
        repeatIcon.alpha = if (MusicManager.isRepeat) 1f else 0.3f

        val track = MusicManager.currentTrack
        currentlyPlayingText.text = track?.let { "${it.displayArtists} — ${it.title}" } ?: "—"

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