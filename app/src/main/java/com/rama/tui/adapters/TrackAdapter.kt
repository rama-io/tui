package com.rama.tui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import com.rama.tui.managers.FontManager
import com.rama.tui.managers.MusicManager

class TrackAdapter(
    private val context: Context,
    private val tracks: List<Track>,
) : BaseAdapter() {

    override fun getCount(): Int = tracks.size
    override fun getItem(position: Int): Track = tracks[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView
            ?: LayoutInflater.from(context).inflate(R.layout.list_track, parent, false)

        val track = tracks[position]

        view.findViewById<TextView>(R.id.track_title).text = track.title
        view.findViewById<TextView>(R.id.track_artist).text =
            if (track.displayArtists.isNotEmpty())
                track.artists.joinToString(", ")
            else
                "---"

        view.findViewById<TextView>(R.id.track_country).text =
            if (track.displayCountries.isNotEmpty())
                track.countries.joinToString(", ")
            else
                "---"

        view.findViewById<TextView>(R.id.track_lang).text =
            if (track.displayLanguages.isNotEmpty())
                track.languages.joinToString(", ")
            else
                "---"

        // Highlight currently playing row
        val isActive = position == MusicManager.currentIndex
        view.alpha = if (isActive) 1f else 0.6f

        FontManager.applyToView(context, view)

        view.setOnClickListener {
            MusicManager.play(position)
        }

        return view
    }
}
