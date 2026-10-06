package com.lymow.toolkit.companion.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class ServerConfig(
    val serverUrl: String = "",
    val password: String = "",
    val connected: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
)

/** Persists the Toolkit server address, dashboard password and UI preferences. */
class SettingsStore(private val context: Context) {

    private object Keys {
        val SERVER_URL = stringPreferencesKey("server_url")
        val PASSWORD = stringPreferencesKey("password")
        val CONNECTED = booleanPreferencesKey("connected")
        val THEME = stringPreferencesKey("theme_mode")
    }

    val config: Flow<ServerConfig> = context.dataStore.data.map { prefs ->
        ServerConfig(
            serverUrl = prefs[Keys.SERVER_URL] ?: "",
            password = prefs[Keys.PASSWORD] ?: "",
            connected = prefs[Keys.CONNECTED] ?: false,
            themeMode = prefs[Keys.THEME]
                ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
        )
    }

    suspend fun saveServer(url: String, password: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SERVER_URL] = url
            prefs[Keys.PASSWORD] = password
            prefs[Keys.CONNECTED] = true
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs -> prefs[Keys.THEME] = mode.name }
    }

    suspend fun forgetServer() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.SERVER_URL)
            prefs.remove(Keys.PASSWORD)
            prefs[Keys.CONNECTED] = false
        }
    }
}
