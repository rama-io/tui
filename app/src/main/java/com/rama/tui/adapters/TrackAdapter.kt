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
    private val allTracks: List<Track>,
) : BaseAdapter() {

    private var tracks: List<Track> = allTracks
    private var filtered: List<Track> = allTracks

    fun updateTracks(newTracks: List<Track>) {
        tracks = newTracks
        filtered = newTracks
        notifyDataSetChanged()
    }

    fun resetToFullLibrary() {
        tracks = allTracks
        filtered = allTracks
        notifyDataSetChanged()
    }

    fun filter(query: String) {
        filtered = if (query.isBlank()) tracks
        else tracks.filter { it.file.nameWithoutExtension.contains(query, ignoreCase = true) }
        notifyDataSetChanged()
    }

    override fun getCount(): Int = filtered.size
    override fun getItem(position: Int): Track = filtered[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView
            ?: LayoutInflater.from(context).inflate(R.layout.list_track, parent, false)

        val track = filtered[position]

        view.findViewById<TextView>(R.id.track_title).text = track.title
        view.findViewById<TextView>(R.id.track_artist).text =
            track.displayArtists.ifEmpty { "---" }
        view.findViewById<TextView>(R.id.track_country).text =
            track.displayCountries.ifEmpty { "---" }
        view.findViewById<TextView>(R.id.track_lang).text =
            track.displayLanguages.ifEmpty { "---" }
        view.findViewById<TextView>(R.id.track_ext).text = track.ext

        // Highlight currently playing row by original index
        val isActive = tracks.indexOf(track) == MusicManager.currentIndex
        view.alpha = if (isActive) 1f else 0.6f

        FontManager.applyToView(context, view)

        view.setOnClickListener {
            val filteredList = filtered.toList()
            MusicManager.setTracks(filteredList, filtered.indexOf(track))
            updateTracks(filteredList) // base list is now the filtered list
        }

        return view
    }
}