package com.example.data.repository

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "nova_share_settings")

class SettingsRepository(private val context: Context) {
    companion object {
        val KEY_DEVICE_NAME = stringPreferencesKey("device_name")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_PORT = intPreferencesKey("server_port")
        val KEY_AUTO_ACCEPT_TRUSTED = booleanPreferencesKey("auto_accept_trusted")
        val KEY_ENCRYPTION_ENABLED = booleanPreferencesKey("encryption_enabled")
        val KEY_BANDWIDTH_LIMIT_MB = intPreferencesKey("bandwidth_limit_mb")
        val KEY_DOWNLOAD_LOCATION = stringPreferencesKey("download_location")
    }

    val deviceName: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_DEVICE_NAME] ?: android.os.Build.MODEL
    }

    val themeMode: Flow<AppThemeMode> = context.dataStore.data.map { prefs ->
        val modeStr = prefs[KEY_THEME_MODE] ?: AppThemeMode.SYSTEM.name
        try { AppThemeMode.valueOf(modeStr) } catch (e: Exception) { AppThemeMode.SYSTEM }
    }

    val serverPort: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_PORT] ?: 8080
    }

    val autoAcceptTrusted: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_AUTO_ACCEPT_TRUSTED] ?: true
    }

    val isEncryptionEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_ENCRYPTION_ENABLED] ?: true
    }

    val bandwidthLimitMb: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_BANDWIDTH_LIMIT_MB] ?: 0 // 0 means unlimited
    }

    val downloadLocation: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_DOWNLOAD_LOCATION] ?: "/storage/emulated/0/Download/NovaShare"
    }

    suspend fun setDeviceName(name: String) {
        context.dataStore.edit { prefs -> prefs[KEY_DEVICE_NAME] = name }
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.dataStore.edit { prefs -> prefs[KEY_THEME_MODE] = mode.name }
    }

    suspend fun setAutoAcceptTrusted(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_AUTO_ACCEPT_TRUSTED] = enabled }
    }

    suspend fun setEncryptionEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_ENCRYPTION_ENABLED] = enabled }
    }

    suspend fun setBandwidthLimitMb(limitMb: Int) {
        context.dataStore.edit { prefs -> prefs[KEY_BANDWIDTH_LIMIT_MB] = limitMb }
    }
}
