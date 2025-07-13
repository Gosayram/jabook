package com.jabook.app.shared.ui

import android.app.Application
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class AppThemeMode { SYSTEM, LIGHT, DARK }

private val Application.dataStore by preferencesDataStore(name = "settings")

class ThemeViewModel(app: Application) : AndroidViewModel(app) {
    private val themeModeKey = booleanPreferencesKey("dark_theme_enabled")
    private val useSystemKey = booleanPreferencesKey("use_system_theme")

    private val _themeMode = MutableStateFlow(AppThemeMode.SYSTEM)
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    init {
        viewModelScope.launch {
            val prefs = app.dataStore.data.first()
            _themeMode.value = when {
                prefs[useSystemKey] == true -> AppThemeMode.SYSTEM
                prefs.contains(themeModeKey) -> if (prefs[themeModeKey] == true) AppThemeMode.DARK else AppThemeMode.LIGHT
                else -> AppThemeMode.SYSTEM
            }
        }
    }

    fun toggleTheme() {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val current = _themeMode.value
            val next = when (current) {
                AppThemeMode.SYSTEM -> AppThemeMode.LIGHT
                AppThemeMode.LIGHT -> AppThemeMode.DARK
                AppThemeMode.DARK -> AppThemeMode.SYSTEM
            }
            _themeMode.value = next
            app.dataStore.edit { prefs ->
                prefs[useSystemKey] = next == AppThemeMode.SYSTEM
                prefs[themeModeKey] = next == AppThemeMode.DARK
            }
        }
    }
}
