package io.github.ocpabdurrahim.skilltrack.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

enum class ThemeMode(val label: String) {
    SYSTEM("System default"),
    LIGHT("Light"),
    DARK("Dark"),
}

/** This phone's own settings; none of these are shared with other staff or the web. */
data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    /** Null when the app lock is off. */
    val pinHash: String? = null,
    val biometric: Boolean = false,
    /** Download and install new releases by itself. */
    val autoUpdate: Boolean = true,
    /** The center opened last, per signed-in user ("uid:centerId"). */
    val lastCenter: String? = null,
)

private val Context.skillDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsStore(context: Context) {
    private val store = context.applicationContext.skillDataStore

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val pinHash = stringPreferencesKey("pin_hash")
        val biometric = booleanPreferencesKey("biometric")
        val autoUpdate = booleanPreferencesKey("auto_update")
        val lastCenter = stringPreferencesKey("last_center")
    }

    val settings: Flow<Settings> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map(::read)

    suspend fun update(transform: (Settings) -> Settings) {
        store.edit { prefs ->
            val s = transform(read(prefs))
            prefs[Keys.theme] = s.theme.name
            prefs[Keys.dynamicColor] = s.dynamicColor
            val pin = s.pinHash
            if (pin == null) prefs.remove(Keys.pinHash) else prefs[Keys.pinHash] = pin
            prefs[Keys.biometric] = s.biometric
            prefs[Keys.autoUpdate] = s.autoUpdate
            val last = s.lastCenter
            if (last == null) prefs.remove(Keys.lastCenter) else prefs[Keys.lastCenter] = last
        }
    }

    private fun read(p: Preferences): Settings {
        val d = Settings()
        return Settings(
            theme = p[Keys.theme]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: d.theme,
            dynamicColor = p[Keys.dynamicColor] ?: d.dynamicColor,
            pinHash = p[Keys.pinHash],
            biometric = p[Keys.biometric] ?: d.biometric,
            autoUpdate = p[Keys.autoUpdate] ?: d.autoUpdate,
            lastCenter = p[Keys.lastCenter],
        )
    }
}
