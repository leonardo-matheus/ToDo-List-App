package com.example.todo.util

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "theme_settings")

class ThemeManager(private val context: Context) {
    
    companion object {
        private val THEME_MODE_KEY = intPreferencesKey("theme_mode")
        
        const val MODE_SYSTEM = 0  // Follow system
        const val MODE_LIGHT = 1   // Always light
        const val MODE_DARK = 2    // Always dark
    }
    
    val themeMode: Flow<Int> = context.themeDataStore.data.map { preferences ->
        preferences[THEME_MODE_KEY] ?: MODE_SYSTEM
    }
    
    suspend fun setThemeMode(mode: Int) {
        context.themeDataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = mode
        }
        applyTheme(mode)
    }
    
    fun applyTheme(mode: Int) {
        when (mode) {
            MODE_LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            MODE_DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }
    
    fun isSystemInDarkMode(): Boolean {
        return when (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_YES -> true
            else -> false
        }
    }
    
    fun isDarkModeEnabled(themeMode: Int): Boolean {
        return when (themeMode) {
            MODE_DARK -> true
            MODE_LIGHT -> false
            else -> isSystemInDarkMode()
        }
    }
}
