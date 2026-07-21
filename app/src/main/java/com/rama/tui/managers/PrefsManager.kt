package com.rama.tui.managers

import android.content.Context
import android.content.SharedPreferences
import com.rama.tui.R
import com.rama.bohio.objects.PrefKeys
import com.rama.bohio.objects.PrefTheme
import com.rama.bohio.managers.PrefsManager as BohioPrefsManager

class PrefsManager private constructor(context: Context) : BohioPrefsManager(context) {

    private val appContext = context.applicationContext

    override val defaultTheme: String = PrefTheme.TOKYO_NIGHT

    // Local preference keys
    object FileKeys {
        const val LIST_SORT_STYLE = "list:sort:style"
        const val LIST_SORT_KEEP_TOGETHER = "list:sort:keep_together"
        const val SD_TREE_URI = "storage:sd_tree_uri"
        const val DISABLED_FOLDERS = "folders:disabled"
        const val ALL_FOLDERS = "folders:all"
        const val RESPECT_NOMEDIA = "folders:respect_nomedia"

        private const val FORMAT_KEY_PREFIX = "list:includes:"

        fun formatKey(format: String) = "$FORMAT_KEY_PREFIX${format.lowercase()}"
    }

    object PrefSortStyle {
        val AZ = "az"
        val ZA = "za"
    }

    // Local InitPrefs
    override fun applyAppDefaults(editor: SharedPreferences.Editor) {
        editor.putString(FileKeys.LIST_SORT_STYLE, PrefSortStyle.AZ)
        editor.putBoolean(FileKeys.LIST_SORT_KEEP_TOGETHER, false)
        editor.putBoolean(PrefKeys.SETTINGS_SECTION_LIST, true)
        editor.putBoolean(PrefKeys.SETTINGS_SECTION_FOLDERS, true)
        editor.putBoolean(FileKeys.RESPECT_NOMEDIA, true)

        allSupportedAudioFormats().forEach { format ->
            editor.putBoolean(FileKeys.formatKey(format), true)
        }
    }

    fun allSupportedAudioFormats(): Array<String> =
        appContext.resources.getStringArray(R.array.supported_audio_formats)

    fun isRespectNomediaEnabled(): Boolean =
        prefs.getBoolean(FileKeys.RESPECT_NOMEDIA, true)

    fun setRespectNomediaEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(FileKeys.RESPECT_NOMEDIA, enabled).apply()
    }

    fun isAudioFormatEnabled(format: String): Boolean =
        prefs.getBoolean(FileKeys.formatKey(format), true)

    fun setAudioFormatEnabled(format: String, enabled: Boolean) {
        prefs.edit().putBoolean(FileKeys.formatKey(format), enabled).apply()
    }

    fun getEnabledAudioFormats(): Set<String> =
        allSupportedAudioFormats().filter { isAudioFormatEnabled(it) }.toSet()

    fun getDisabledFolders(): Set<String> {
        val raw = prefs.getString(FileKeys.DISABLED_FOLDERS, "") ?: ""
        return if (raw.isBlank()) emptySet() else raw.split("\n").filter { it.isNotBlank() }.toSet()
    }

    fun setDisabledFolders(folders: Set<String>) {
        prefs.edit().putString(FileKeys.DISABLED_FOLDERS, folders.joinToString("\n")).apply()
    }

    fun getAllFolders(): List<String> {
        val raw = prefs.getString(FileKeys.ALL_FOLDERS, "") ?: ""
        return if (raw.isBlank()) emptyList() else raw.split("\n").filter { it.isNotBlank() }
    }

    fun setAllFolders(folders: Set<String>) {
        prefs.edit().putString(FileKeys.ALL_FOLDERS, folders.sorted().joinToString("\n")).apply()
    }

    companion object {
        @Volatile
        private var INSTANCE: PrefsManager? = null

        fun getInstance(context: Context): PrefsManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: PrefsManager(context.applicationContext).also {
                    INSTANCE = it
                    register(it)
                }
            }
    }
}