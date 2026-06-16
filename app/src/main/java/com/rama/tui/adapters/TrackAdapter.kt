package com.rama.tui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.TextView
import com.rama.tui.Track.Companion.normalize
import com.rama.tui.managers.MusicManager
import com.rama.bohio.managers.ThemeManager

class TrackAdapter(
    private val context: Context,
    private val allTracks: List<Track>,
    private val onLongClick: ((Track) -> Unit)? = null,
) : BaseAdapter() {

    // A list item is either a Track or a String folder header
    sealed class Item {
        data class Header(val folderName: String) : Item()
        data class TrackItem(val track: Track) : Item()
    }

    private var items: List<Item> = buildItems(allTracks)
    private var filteredItems: List<Item> = items

    // The flat track list (headers excluded) for playback use
    val flatTracks: List<Track>
        get() = filteredItems.filterIsInstance<Item.TrackItem>().map { it.track }

    private fun buildItems(tracks: List<Track>): List<Item> {
        val result = mutableListOf<Item>()
        var lastFolder: String? = null
        for (track in tracks) {
            val folder = track.file.parentFile?.name ?: track.file.parent ?: ""
            if (folder != lastFolder) {
                result.add(Item.Header(folder))
                lastFolder = folder
            }
            result.add(Item.TrackItem(track))
        }
        return result
    }

    fun updateTracks(newTracks: List<Track>) {
        items = buildItems(newTracks)
        filteredItems = items
        notifyDataSetChanged()
    }

    fun resetToFullLibrary() {
        items = buildItems(allTracks)
        filteredItems = items
        notifyDataSetChanged()
    }

    fun filter(query: String) {
        val normalizedQuery = normalize(query)
        val baseTracks = items.filterIsInstance<Item.TrackItem>().map { it.track }

        val filtered = if (normalizedQuery.isBlank()) {
            baseTracks
        } else {
            baseTracks.filter { it.normalizedName.contains(normalizedQuery) }
        }

        filteredItems = buildItems(filtered)
        notifyDataSetChanged()
    }

    override fun getCount(): Int = filteredItems.size
    override fun getItem(position: Int): Any = filteredItems[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getViewTypeCount(): Int = 2
    override fun getItemViewType(position: Int): Int = when (filteredItems[position]) {
        is Item.Header -> 0
        is Item.TrackItem -> 1
    }

    override fun isEnabled(position: Int): Boolean = filteredItems[position] is Item.TrackItem

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        return when (val item = filteredItems[position]) {
            is Item.Header -> {
                val view = convertView
                    ?: LayoutInflater.from(context).inflate(R.layout.list_header, parent, false)
                view.findViewById<TextView>(R.id.title).text = item.folderName
                ThemeManager.applyTheme(context, view)
                view
            }

            is Item.TrackItem -> {
                val track = item.track
                val view = convertView
                    ?: LayoutInflater.from(context).inflate(R.layout.list_track, parent, false)

                view.findViewById<TextView>(R.id.track_title).text = track.title
                view.findViewById<TextView>(R.id.track_artist).text =
                    track.displayArtists.ifEmpty { "---" }
                view.findViewById<TextView>(R.id.track_country).text =
                    track.displayCountries.ifEmpty { "---" }
                view.findViewById<TextView>(R.id.track_lang).text =
                    track.displayLanguages.ifEmpty { "---" }
                view.findViewById<TextView>(R.id.track_ext).text = track.ext

                // Highlight currently playing row
                val isActive = MusicManager.tracks.indexOf(track) == MusicManager.currentIndex
                view.findViewById<FrameLayout>(R.id.disc).alpha = if (isActive) 1f else 0.3f

                ThemeManager.applyTheme(context, view)

                view.setOnClickListener {
                    val playableTracks = flatTracks
                    MusicManager.setTracks(playableTracks, playableTracks.indexOf(track))
                    updateTracks(playableTracks)
                }

                view.setOnLongClickListener {
                    onLongClick?.invoke(track)
                    true
                }

                view
            }
        }
    }
}
