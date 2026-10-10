package com.xiangqi.app.ui

import android.app.Application
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiangqi.app.BuildConfig
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val android.content.Context.dataStore by preferencesDataStore(name = "settings")
private val KEY_GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx = app.applicationContext

    val geminiApiKey: StateFlow<String> = ctx.dataStore.data
        .map { prefs ->
            // DataStore key takes precedence; fall back to the build-time key (from local.properties)
            prefs[KEY_GEMINI_API_KEY]?.takeIf { it.isNotBlank() }
                ?: BuildConfig.GEMINI_API_KEY
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, BuildConfig.GEMINI_API_KEY)

    fun saveApiKey(key: String) {
        viewModelScope.launch {
            ctx.dataStore.edit { prefs -> prefs[KEY_GEMINI_API_KEY] = key.trim() }
        }
    }
}
