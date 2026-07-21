package com.rama.tui.activities.settings

import android.widget.LinearLayout
import com.rama.tui.R
import com.rama.tui.activities.SettingsActivity
import com.rama.tui.managers.PrefsManager
import com.rama.bohio.managers.ThemeManager
import com.rama.bohio.widgets.WdCheckbox

class SettingsAudioFormatsController(private val activity: SettingsActivity) {

    private val prefs get() = PrefsManager.getInstance(activity)

    fun setup(onFormatsChanged: () -> Unit) {
        val container = activity.findViewById<LinearLayout>(R.id.audio_formats_container)
        container.removeAllViews()

        val formats = prefs.allSupportedAudioFormats()

        for (format in formats) {
            val checkbox = WdCheckbox(activity)
            checkbox.setText(format)

            checkbox.setChecked(prefs.isAudioFormatEnabled(format))

            checkbox.setOnCheckedChangeListener { isChecked ->
                prefs.setAudioFormatEnabled(format, isChecked)
                onFormatsChanged()
            }

            ThemeManager.applyTheme(activity, checkbox)
            container.addView(checkbox)
        }
    }
}
