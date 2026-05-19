package com.rama.tui.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import com.rama.tui.CsActivity
import com.rama.tui.R
import com.rama.tui.activities.settings.SettingsAppearanceController
import com.rama.tui.activities.settings.SettingsBasicController
import com.rama.tui.activities.settings.SettingsCheckboxController
import com.rama.tui.activities.settings.SettingsLanguageController
import com.rama.tui.managers.PrefsManager

class SettingsActivity : CsActivity() {
    private lateinit var appearanceController: SettingsAppearanceController
    private lateinit var settingsRootView: View
    val FONT_PICK_REQUEST = 1002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.view_settings)

        settingsRootView = findViewById(R.id.settings_root)
        applyEdgeToEdgePadding(settingsRootView)
        applyCurrentTheme(settingsRootView)

        SettingsBasicController(this).setup()
        appearanceController = SettingsAppearanceController(this).also { it.setup() }
        SettingsLanguageController(this).setup()
        SettingsCheckboxController(this).setup()
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        appearanceController.onActivityResult(requestCode, resultCode, data)
    }
}