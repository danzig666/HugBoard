package com.pckeyboard.ime.theme

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.preference.PreferenceManager

/**
 * Persists the selected theme and any user-built custom themes.
 *
 * Storage layout:
 *  - "selected_theme_id"   : id of the active theme
 *  - "custom_theme_ids"    : comma-separated list of saved custom theme ids
 *  - "theme_<id>_<field>"  : individual field of a custom theme
 *
 * Lives in **device-protected storage** so the user's theme is honoured
 * on the lock screen too — see KeyboardPrefs for the rationale and the
 * one-time CE→DE migration that brings legacy installs forward (theme
 * keys are migrated by KeyboardPrefs since they're not in its
 * filter-out list).
 */
class ThemeRepository(context: Context) {

    private val appContext = context.applicationContext

    private val storeContext: Context =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            appContext.createDeviceProtectedStorageContext()
        } else {
            appContext
        }

    private val prefs: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(storeContext)

    fun getSelectedTheme(): KeyboardTheme {
        migrateToGboardThemeOnce()
        val fallback = defaultTheme()
        val id = prefs.getString(KEY_SELECTED, fallback.id) ?: fallback.id
        return getThemeById(id) ?: fallback
    }

    /** Gboard Dark / Light following the system's dark mode. */
    private fun defaultTheme(): KeyboardTheme {
        val night = appContext.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return if (night == android.content.res.Configuration.UI_MODE_NIGHT_YES)
            Themes.GBOARD_DARK else Themes.GBOARD_LIGHT
    }

    /** One-time switch to the Gboard palette when the Gboard layout
     *  arrived: a stock theme maps to its Gboard counterpart, while a
     *  user-built custom theme is left alone. */
    private fun migrateToGboardThemeOnce() {
        if (prefs.getBoolean(KEY_GBOARD_MIGRATED, false)) return
        val edit = prefs.edit().putBoolean(KEY_GBOARD_MIGRATED, true)
        when (prefs.getString(KEY_SELECTED, null)) {
            null -> {}
            Themes.LIGHT.id -> edit.putString(KEY_SELECTED, Themes.GBOARD_LIGHT.id)
            Themes.DARK.id, Themes.BLACK.id -> edit.putString(KEY_SELECTED, Themes.GBOARD_DARK.id)
        }
        edit.apply()
    }

    fun selectTheme(id: String) {
        prefs.edit().putString(KEY_SELECTED, id).apply()
    }

    fun getThemeById(id: String): KeyboardTheme? {
        val builtIn = Themes.builtIn.firstOrNull { it.id == id }
        if (builtIn != null) return builtIn
        return loadCustom(id)
    }

    fun listCustomThemes(): List<KeyboardTheme> =
        readCustomIds().mapNotNull { loadCustom(it) }

    fun saveCustomTheme(theme: KeyboardTheme) {
        val ids = readCustomIds().toMutableSet()
        ids.add(theme.id)
        prefs.edit()
            .putStringSet(KEY_CUSTOM_IDS, ids)
            .apply()
        val editor = prefs.edit()
        theme.toMap().forEach { (k, v) ->
            editor.putString("$KEY_PREFIX${theme.id}_$k", v)
        }
        editor.apply()
    }

    fun deleteCustomTheme(id: String) {
        val ids = readCustomIds().toMutableSet()
        if (!ids.remove(id)) return
        val editor = prefs.edit().putStringSet(KEY_CUSTOM_IDS, ids)
        for (k in prefs.all.keys.filter { it.startsWith("$KEY_PREFIX${id}_") }) {
            editor.remove(k)
        }
        editor.apply()
        if (prefs.getString(KEY_SELECTED, "") == id) selectTheme(defaultTheme().id)
    }

    fun allThemes(): List<KeyboardTheme> = Themes.builtIn + listCustomThemes()

    private fun loadCustom(id: String): KeyboardTheme? {
        val map = mutableMapOf<String, String>()
        for (k in prefs.all.keys.filter { it.startsWith("$KEY_PREFIX${id}_") }) {
            val field = k.removePrefix("$KEY_PREFIX${id}_")
            val value = prefs.getString(k, null) ?: continue
            map[field] = value
        }
        if (map.isEmpty()) return null
        if (!map.containsKey("id")) map["id"] = id
        return KeyboardTheme.fromMap(map)
    }

    private fun readCustomIds(): Set<String> =
        prefs.getStringSet(KEY_CUSTOM_IDS, emptySet()) ?: emptySet()

    companion object {
        private const val KEY_SELECTED = "selected_theme_id"
        private const val KEY_CUSTOM_IDS = "custom_theme_ids"
        private const val KEY_PREFIX = "theme_"
        private const val KEY_GBOARD_MIGRATED = "kb_gboard_theme_migrated_v1"
    }
}
