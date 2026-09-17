package com.thedesitadka.app.storage

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "thedesitadka_settings")

class PreferenceStore(private val context: Context) {

    companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode") // "system", "dark", "light"
        val WIFI_ONLY_DOWNLOADS = booleanPreferencesKey("wifi_only_downloads")
        val TELEMETRY_ENABLED = booleanPreferencesKey("telemetry_enabled")
        val DEFAULT_QUALITY = stringPreferencesKey("default_quality")
        val REMOTE_CONFIG_URL = stringPreferencesKey("remote_config_url")

        const val DEFAULT_CONFIG_URL = "https://raw.githubusercontent.com/DBiswas57/TheDesiTadka/main/config-tools/signed-manifest.json"
    }

    val themeModeFlow: Flow<String> = context.dataStore.data.map { it[THEME_MODE] ?: "dark" }
    val wifiOnlyFlow: Flow<Boolean> = context.dataStore.data.map { it[WIFI_ONLY_DOWNLOADS] ?: true }
    val telemetryFlow: Flow<Boolean> = context.dataStore.data.map { it[TELEMETRY_ENABLED] ?: false }
    val qualityFlow: Flow<String> = context.dataStore.data.map { it[DEFAULT_QUALITY] ?: "auto" }
    val remoteConfigUrlFlow: Flow<String> = context.dataStore.data.map { it[REMOTE_CONFIG_URL] ?: DEFAULT_CONFIG_URL }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[THEME_MODE] = mode }
    }

    suspend fun setWifiOnly(enabled: Boolean) {
        context.dataStore.edit { it[WIFI_ONLY_DOWNLOADS] = enabled }
    }

    suspend fun setTelemetry(enabled: Boolean) {
        context.dataStore.edit { it[TELEMETRY_ENABLED] = enabled }
    }

    suspend fun setDefaultQuality(quality: String) {
        context.dataStore.edit { it[DEFAULT_QUALITY] = quality }
    }

    suspend fun setRemoteConfigUrl(url: String) {
        context.dataStore.edit { it[REMOTE_CONFIG_URL] = url }
    }
}
