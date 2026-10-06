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
    val sessionCookie: String = "",
    val connected: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
)

/**
 * Persists the Toolkit server address and the `lymow_session` cookie issued by
 * the Toolkit after sign-in (the dashboard password itself is never stored).
 */
class SettingsStore(private val context: Context) {

    private object Keys {
        val SERVER_URL = stringPreferencesKey("server_url")
        val SESSION = stringPreferencesKey("session_cookie")
        val CONNECTED = booleanPreferencesKey("connected")
        val THEME = stringPreferencesKey("theme_mode")
    }

    val config: Flow<ServerConfig> = context.dataStore.data.map { prefs ->
        ServerConfig(
            serverUrl = prefs[Keys.SERVER_URL] ?: "",
            sessionCookie = prefs[Keys.SESSION] ?: "",
            connected = prefs[Keys.CONNECTED] ?: false,
            themeMode = prefs[Keys.THEME]
                ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
        )
    }

    suspend fun saveServer(url: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SERVER_URL] = url
            prefs[Keys.CONNECTED] = true
        }
    }

    suspend fun saveSession(cookie: String) {
        context.dataStore.edit { prefs -> prefs[Keys.SESSION] = cookie }
    }

    suspend fun clearSession() {
        context.dataStore.edit { prefs -> prefs.remove(Keys.SESSION) }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs -> prefs[Keys.THEME] = mode.name }
    }

    suspend fun forgetServer() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.SERVER_URL)
            prefs.remove(Keys.SESSION)
            prefs[Keys.CONNECTED] = false
        }
    }
}
