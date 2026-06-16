package com.rama.tui.activities.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.*
import com.rama.tui.R
import com.rama.tui.activities.AboutActivity
import com.rama.tui.activities.SettingsActivity
import com.rama.tui.managers.MusicManager
import com.rama.bohio.util.UiActions

class SettingsBasicController(private val activity: SettingsActivity) {

    private val prefs get() = activity.prefs

    fun setup() {
        UiActions.setupButton(activity, R.id.about_button) {
            activity.startActivity(Intent(activity, AboutActivity::class.java))
        }

        UiActions.setupButton(activity, R.id.close_button) {
            activity.finish()
        }

        setupPermissionButtons()
    }

    private fun setupPermissionButtons() {
        val mediaBtn = activity.findViewById<View>(R.id.media_permission_button)
        val storageBtn = activity.findViewById<View>(R.id.storage_permission_button)

        // Media permission button
        mediaBtn.setOnClickListener {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                    activity.requestPermissions(
                        arrayOf(Manifest.permission.READ_MEDIA_AUDIO),
                        SettingsActivity.REQ_MEDIA_PERM
                    )

                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                    activity.requestPermissions(
                        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                        SettingsActivity.REQ_MEDIA_PERM
                    )

                else ->
                    // API 21/22: permissions are install-time; open app settings so user can toggle
                    activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = android.net.Uri.parse("package:${activity.packageName}")
                    })
            }
        }

        // Storage permission button
        storageBtn.setOnClickListener {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                    activity.startActivityForResult(
                        Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            android.net.Uri.parse("package:${activity.packageName}")
                        ), SettingsActivity.REQ_STORAGE_PERM
                    )

                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                    activity.requestPermissions(
                        arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                        SettingsActivity.REQ_STORAGE_PERM
                    )

                else ->
                    activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = android.net.Uri.parse("package:${activity.packageName}")
                    })
            }
        }

        // Reflect current grant state in button text
        refreshPermissionButtonStates()
    }

    fun refreshPermissionButtonStates() {
        val mediaBtn = activity.findViewById<android.widget.Button>(R.id.media_permission_button)
        val storageBtn =
            activity.findViewById<android.widget.Button>(R.id.storage_permission_button)

        val hasMedia = MusicManager.hasPermission(activity)
        mediaBtn.alpha = if (hasMedia) 0.4f else 1.0f
        mediaBtn.isEnabled = !hasMedia

        val hasStorage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            Environment.isExternalStorageManager()
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        else true

        storageBtn.alpha = if (hasStorage) 0.4f else 1.0f
        storageBtn.isEnabled = !hasStorage
    }
}