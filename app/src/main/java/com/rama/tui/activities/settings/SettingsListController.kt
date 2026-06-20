package com.rama.tui.activities.settings

import android.widget.RadioGroup
import com.rama.tui.R
import com.rama.tui.activities.SettingsActivity
import com.rama.tui.managers.PrefsManager.FileKeys
import com.rama.tui.managers.PrefsManager.PrefSortStyle

class SettingsListController(private val activity: SettingsActivity) {

    private val prefs get() = activity.prefs

    fun setup(onSortChanged: () -> Unit = {}) {
        val sortGroup = activity.findViewById<RadioGroup>(R.id.list_sort)

        // Restore saved sort style
        val currentStyle = prefs.getString(FileKeys.LIST_SORT_STYLE, PrefSortStyle.AZ)
        sortGroup.check(
            when (currentStyle) {
                PrefSortStyle.ZA -> R.id.sort_za
                else -> R.id.sort_az
            }
        )

        sortGroup.setOnCheckedChangeListener { _, checkedId ->
            val newStyle = when (checkedId) {
                R.id.sort_za -> PrefSortStyle.ZA
                else -> PrefSortStyle.AZ
            }
            prefs.setString(FileKeys.LIST_SORT_STYLE, newStyle)
            onSortChanged()
        }
    }
}
