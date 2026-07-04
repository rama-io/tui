package com.rama.tui.activities

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import com.rama.tui.CsActivity
import com.rama.tui.R
import com.rama.bohio.R as BohioR
import com.rama.tui.managers.MusicManager
import com.rama.tui.activities.settings.SettingsAppearanceController
import com.rama.tui.activities.settings.SettingsBasicController
import com.rama.tui.activities.settings.SettingsCheckboxController
import com.rama.tui.activities.settings.SettingsLanguageController
import com.rama.tui.activities.settings.SettingsFoldersController
import com.rama.tui.activities.settings.SettingsListController

class SettingsActivity : CsActivity() {
    private lateinit var appearanceController: SettingsAppearanceController
    private lateinit var basicController: SettingsBasicController
    private lateinit var foldersController: SettingsFoldersController
    private lateinit var settingsRootView: View
    val FONT_PICK_REQUEST = 1002

    companion object {
        const val REQ_MEDIA_PERM = 2010
        const val REQ_STORAGE_PERM = 2011
        const val REQ_NOTIFICATION_PERM = 2012
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.view_settings)

        settingsRootView = findViewById(R.id.settings_root)
        applyEdgeToEdgePadding(settingsRootView)
        applyCurrentTheme(settingsRootView)

        basicController = SettingsBasicController(this).also { it.setup() }
        appearanceController = SettingsAppearanceController(this).also { it.setup() }
        SettingsLanguageController(this).setup()
        SettingsCheckboxController(this).setup()
        SettingsListController(this).setup {
            MusicManager.reSort(this)
            setResult(Activity.RESULT_OK)
        }

        foldersController = SettingsFoldersController(this).also {
            it.setup {
                MusicManager.loadTracks(this)
                setResult(Activity.RESULT_OK)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        basicController.refreshPermissionButtonStates()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_MEDIA_PERM || requestCode == REQ_STORAGE_PERM) {
            basicController.refreshPermissionButtonStates()
            if (grantResults.isNotEmpty() &&
                grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            ) {
                MusicManager.loadTracks(this)
                foldersController.populateFolders { MusicManager.loadTracks(this) }
                setResult(Activity.RESULT_OK)
            }
        }

        if (requestCode == REQ_NOTIFICATION_PERM) {
            basicController.refreshPermissionButtonStates()
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                com.rama.tui.MediaPlaybackService.start(this)
            }
        }
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        appearanceController.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQ_STORAGE_PERM) {
            basicController.refreshPermissionButtonStates()
            MusicManager.loadTracks(this)
            foldersController.populateFolders { MusicManager.loadTracks(this) }
            setResult(Activity.RESULT_OK)
        }
    }
}