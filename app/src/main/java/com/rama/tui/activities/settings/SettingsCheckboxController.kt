package com.rama.tui.activities.settings

import android.view.View
import com.rama.tui.R
import com.rama.tui.activities.SettingsActivity
import com.rama.bohio.widgets.WdCheckbox
import com.rama.bohio.objects.PrefKeys
import com.rama.tui.managers.PrefsManager.FileKeys

class SettingsCheckboxController(private val activity: SettingsActivity) {

    private val prefs get() = activity.prefs

    fun setup() {
        bindWdCheckbox(R.id.show_system_bar, PrefKeys.SYSTEM_BAR_VISIBLE, false)
        bindWdCheckbox(R.id.keep_screen_awake, PrefKeys.SYSTEM_PREVENT_SLEEP, false)
        bindWdCheckbox(R.id.keep_folder_files_together, FileKeys.LIST_SORT_KEEP_TOGETHER, false)
    }

    private fun bindWdCheckbox(
        wdCheckboxId: Int,
        key: String,
        defaultValue: Boolean,
        dependentViewIds: List<Int>? = null,
        onChange: ((Boolean) -> Unit)? = null
    ) {
        val checkbox = activity.findViewById<WdCheckbox>(wdCheckboxId)
        val dependents = dependentViewIds?.map { activity.findViewById<View>(it) }

        val isChecked = prefs.getBoolean(key, defaultValue)
        checkbox.setChecked(isChecked)

        dependents?.forEach {
            it.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        checkbox.setOnCheckedChangeListener { checked ->
            prefs.setBoolean(key, checked)
            dependents?.forEach {
                it.visibility = if (checked) View.VISIBLE else View.GONE
            }
            onChange?.invoke(checked)
        }
    }
}