package com.rama.tui.activities.settings

import android.content.Intent
import android.provider.Settings
import android.widget.*
import com.rama.tui.R
import com.rama.tui.activities.AboutActivity
import com.rama.tui.activities.MainActivity
import com.rama.tui.activities.SettingsActivity
import com.rama.tui.utils.SettingsUiUtils

class SettingsBasicController(private val activity: SettingsActivity) {

    private val prefs get() = activity.prefs

    fun setup() {
        SettingsUiUtils.setupButton(activity, R.id.about_button) {
            activity.startActivity(Intent(activity, AboutActivity::class.java))
        }

        SettingsUiUtils.setupButton(activity, R.id.close_button) {
            activity.finish()
        }
    }
}