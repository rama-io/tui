package com.rama.tui.activities

import android.Manifest
import android.app.Fragment
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ListView
import com.rama.tui.R
import com.rama.tui.TrackAdapter
import com.rama.tui.managers.MusicManager

class HomeFragment : Fragment() {

    private lateinit var listView: ListView
    private lateinit var playPauseIcon: ImageView
    private lateinit var playPauseButton: FrameLayout
    private lateinit var nextButton: FrameLayout
    private lateinit var prevButton: FrameLayout

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

        listView = view.findViewById(R.id.task_list)
        playPauseButton = view.findViewById(R.id.play_pause_button)
        nextButton = view.findViewById(R.id.next_button)

        playPauseIcon = playPauseButton.getChildAt(0) as ImageView

        playPauseButton.setOnClickListener { MusicManager.togglePlayPause() }
        nextButton.setOnClickListener { MusicManager.next() }

        val bar = nextButton.parent as ViewGroup
        prevButton = bar.getChildAt(bar.indexOfChild(nextButton) - 1) as FrameLayout
        prevButton.setOnClickListener { MusicManager.prev() }

        MusicManager.onStateChanged = { activity?.runOnUiThread { refreshUi() } }

        loadOrRequestTracks()
    }

    override fun onDestroyView() {
        MusicManager.onStateChanged = null
        super.onDestroyView()
    }

    private fun loadOrRequestTracks() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            MusicManager.hasPermission(activity)
        ) {
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
        if (requestCode == REQ_AUDIO &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) {
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

        (listView.adapter as? TrackAdapter)?.notifyDataSetChanged()

        val idx = MusicManager.currentIndex
        if (idx >= 0) listView.smoothScrollToPosition(idx)
    }
}