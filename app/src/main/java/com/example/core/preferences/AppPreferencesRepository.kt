package com.example.core.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.MurmurAccentTheme
import com.example.ui.theme.MurmurNavBarStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.murmurDataStore: DataStore<Preferences> by preferencesDataStore(name = "murmur_prefs")

data class AppearanceSettings(
    val isDarkTheme: Boolean = true,
    val accentTheme: MurmurAccentTheme = MurmurAccentTheme.PURPLE,
    val navBarStyle: MurmurNavBarStyle = MurmurNavBarStyle.DOCKED,
    val hasCompletedSetup: Boolean = false
)

class AppPreferencesRepository(private val context: Context) {
    private val darkThemeKey = booleanPreferencesKey("dark_theme_enabled")
    private val accentThemeKey = stringPreferencesKey("accent_theme_key")
    private val navBarStyleKey = stringPreferencesKey("nav_bar_style_key")
    private val appearanceSetupDoneKey = booleanPreferencesKey("appearance_setup_completed")
    private val searchHistoryKey = stringPreferencesKey("search_history_joined")

    val appearanceSettings: Flow<AppearanceSettings> = context.murmurDataStore.data.map { prefs ->
        AppearanceSettings(
            isDarkTheme = prefs[darkThemeKey] ?: true,
            accentTheme = MurmurAccentTheme.fromKey(prefs[accentThemeKey]),
            navBarStyle = MurmurNavBarStyle.fromKey(prefs[navBarStyleKey]),
            hasCompletedSetup = prefs[appearanceSetupDoneKey] ?: false
        )
    }

    val isDarkTheme: Flow<Boolean> = context.murmurDataStore.data.map { prefs ->
        prefs[darkThemeKey] ?: true
    }

    val searchHistory: Flow<List<String>> = context.murmurDataStore.data.map { prefs ->
        val raw = prefs[searchHistoryKey].orEmpty()
        if (raw.isBlank()) emptyList() else raw.split("||").filter { it.isNotBlank() }
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.murmurDataStore.edit { prefs ->
            prefs[darkThemeKey] = enabled
        }
    }

    suspend fun setAccentTheme(accent: MurmurAccentTheme) {
        context.murmurDataStore.edit { prefs ->
            prefs[accentThemeKey] = accent.key
        }
    }

    suspend fun setNavBarStyle(style: MurmurNavBarStyle) {
        context.murmurDataStore.edit { prefs ->
            prefs[navBarStyleKey] = style.key
        }
    }

    suspend fun markAppearanceSetupCompleted() {
        context.murmurDataStore.edit { prefs ->
            prefs[appearanceSetupDoneKey] = true
        }
    }

    suspend fun saveAppearance(
        isDarkTheme: Boolean,
        accentTheme: MurmurAccentTheme,
        navBarStyle: MurmurNavBarStyle,
        markCompleted: Boolean = true
    ) {
        context.murmurDataStore.edit { prefs ->
            prefs[darkThemeKey] = isDarkTheme
            prefs[accentThemeKey] = accentTheme.key
            prefs[navBarStyleKey] = navBarStyle.key
            if (markCompleted) {
                prefs[appearanceSetupDoneKey] = true
            }
        }
    }

    suspend fun addSearchQuery(query: String) {
        val clean = query.trim()
        if (clean.isBlank()) return
        context.murmurDataStore.edit { prefs ->
            val current = prefs[searchHistoryKey].orEmpty()
                .split("||")
                .filter { it.isNotBlank() && !it.equals(clean, ignoreCase = true) }
            val updated = (listOf(clean) + current).take(10)
            prefs[searchHistoryKey] = updated.joinToString("||")
        }
    }

    suspend fun removeSearchQuery(query: String) {
        context.murmurDataStore.edit { prefs ->
            val current = prefs[searchHistoryKey].orEmpty()
                .split("||")
                .filter { it.isNotBlank() && it != query }
            prefs[searchHistoryKey] = current.joinToString("||")
        }
    }

    suspend fun clearSearchHistory() {
        context.murmurDataStore.edit { prefs ->
            prefs.remove(searchHistoryKey)
        }
    }
}
