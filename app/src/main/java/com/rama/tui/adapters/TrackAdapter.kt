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

    // Folder names currently collapsed (hidden tracks, header still shown)
    private val collapsedFolders = mutableSetOf<String>()

    // Full grouped list (headers + all matching tracks, ignoring collapse state)
    private var items: List<Item> = buildItems(allTracks)

    // What's actually rendered — items with collapsed folders' tracks stripped out
    private var displayItems: List<Item> = applyCollapse(items)

    // The flat track list (headers excluded, collapse-independent) for playback use
    val flatTracks: List<Track>
        get() = items.filterIsInstance<Item.TrackItem>().map { it.track }

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

    // Removes TrackItems that fall under a collapsed header
    private fun applyCollapse(source: List<Item>): List<Item> {
        val result = mutableListOf<Item>()
        var currentCollapsed = false
        for (item in source) {
            when (item) {
                is Item.Header -> {
                    currentCollapsed = collapsedFolders.contains(item.folderName)
                    result.add(item)
                }
                is Item.TrackItem -> {
                    if (!currentCollapsed) result.add(item)
                }
            }
        }
        return result
    }

    private fun refreshDisplay() {
        displayItems = applyCollapse(items)
    }

    fun updateTracks(newTracks: List<Track>) {
        items = buildItems(newTracks)
        refreshDisplay()
        notifyDataSetChanged()
    }

    fun resetToFullLibrary() {
        items = buildItems(allTracks)
        refreshDisplay()
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

        items = buildItems(filtered)
        refreshDisplay()
        notifyDataSetChanged()
    }

    override fun getCount(): Int = displayItems.size
    override fun getItem(position: Int): Any = displayItems[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getViewTypeCount(): Int = 2
    override fun getItemViewType(position: Int): Int = when (displayItems[position]) {
        is Item.Header -> 0
        is Item.TrackItem -> 1
    }

    override fun isEnabled(position: Int): Boolean = displayItems[position] is Item.TrackItem

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        return when (val item = displayItems[position]) {
            is Item.Header -> {
                // Headers get recreated fresh each time rather than reused via convertView,
                // since collapse state changes their click listener target folder.
                val view = if (convertView != null && convertView.getTag(R.id.list_header) == true) {
                    convertView
                } else {
                    LayoutInflater.from(context).inflate(R.layout.list_header, parent, false).also {
                        it.setTag(R.id.list_header, true)
                    }
                }

                val isCollapsed = collapsedFolders.contains(item.folderName)
                val prefix = if (isCollapsed) "[+] " else "[-] "
                view.findViewById<TextView>(R.id.title).text = prefix + item.folderName

                view.setOnClickListener {
                    if (isCollapsed) {
                        collapsedFolders.remove(item.folderName)
                    } else {
                        collapsedFolders.add(item.folderName)
                    }
                    refreshDisplay()
                    notifyDataSetChanged()
                }

                ThemeManager.applyTheme(context, view)
                view
            }

            is Item.TrackItem -> {
                val track = item.track
                val view = if (convertView != null && convertView.getTag(R.id.list_header) != true) {
                    convertView
                } else {
                    LayoutInflater.from(context).inflate(R.layout.list_track, parent, false)
                }

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