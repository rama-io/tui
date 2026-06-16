package com.rama.tui.activities.settings

import com.rama.tui.R
import com.rama.tui.activities.SettingsActivity
import com.rama.tui.managers.PrefsManager
import com.rama.bohio.managers.ThemeManager
import com.rama.bohio.widgets.WdCheckbox
import com.rama.bohio.widgets.WdCollapsibleSection

class SettingsFoldersController(private val activity: SettingsActivity) {

    private val prefs get() = PrefsManager.getInstance(activity)

    fun setup(onFoldersChanged: () -> Unit) {
        populateFolders(onFoldersChanged)
    }

    fun populateFolders(onFoldersChanged: () -> Unit) {
        val section = activity.findViewById<WdCollapsibleSection>(R.id.folders_section)
        section.clearItems()

        // Always read the full folder list from prefs — never from allTracks,
        // which is already filtered and would lose disabled folders after reload
        val allFolders = prefs.getAllFolders()

        if (allFolders.isEmpty()) return

        val disabled = prefs.getDisabledFolders().toMutableSet()

        for (folder in allFolders) {
            val checkbox = WdCheckbox(activity)
            val label = folder.split("/").takeLast(2).joinToString("/")
            checkbox.setText(label)

            // Set state BEFORE attaching listener to avoid triggering onChange during init
            checkbox.setChecked(folder !in disabled)

            checkbox.setOnCheckedChangeListener { isChecked ->
                if (isChecked) disabled.remove(folder) else disabled.add(folder)
                prefs.setDisabledFolders(disabled)
                onFoldersChanged()
            }

            ThemeManager.applyTheme(activity, checkbox)
            section.addItem(checkbox)
        }
    }
}
