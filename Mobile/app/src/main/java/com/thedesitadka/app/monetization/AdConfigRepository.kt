package com.thedesitadka.app.monetization

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.thedesitadka.app.storage.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class AdConfigRepository(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    companion object {
        private val MONETIZATION_ENABLED = booleanPreferencesKey("monetization_enabled")
        private val MONETIZATION_CONFIG_JSON = stringPreferencesKey("monetization_config_json")
        private val EXOCLICK_CONFIG_JSON = stringPreferencesKey("exoclick_config_json")
        private val JUICYADS_CONFIG_JSON = stringPreferencesKey("juicyads_config_json")
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _monetizationConfig = MutableStateFlow(MonetizationConfig())
    val monetizationConfig: StateFlow<MonetizationConfig> = _monetizationConfig.asStateFlow()

    private val _exoClickConfig = MutableStateFlow(ExoClickConfig())
    val exoClickConfig: StateFlow<ExoClickConfig> = _exoClickConfig.asStateFlow()

    private val _juicyAdsConfig = MutableStateFlow(JuicyAdsConfig())
    val juicyAdsConfig: StateFlow<JuicyAdsConfig> = _juicyAdsConfig.asStateFlow()

    init {
        scope.launch {
            loadPersistedConfig()
        }
    }

    private suspend fun loadPersistedConfig() {
        try {
            val prefs = context.dataStore.data.first()

            prefs[MONETIZATION_CONFIG_JSON]?.let { raw ->
                try {
                    _monetizationConfig.value = json.decodeFromString(raw)
                } catch (e: Exception) {}
            }

            prefs[EXOCLICK_CONFIG_JSON]?.let { raw ->
                try {
                    val parsed = json.decodeFromString<ExoClickConfig>(raw)
                    _exoClickConfig.value = if (parsed.apiToken.isBlank()) {
                        parsed.copy(apiToken = ExoClickConfig().apiToken)
                    } else {
                        parsed
                    }
                } catch (e: Exception) {}
            }

            prefs[JUICYADS_CONFIG_JSON]?.let { raw ->
                try {
                    _juicyAdsConfig.value = json.decodeFromString(raw)
                } catch (e: Exception) {}
            }
        } catch (e: Exception) {}
    }

    suspend fun updateMonetizationConfig(config: MonetizationConfig) {
        _monetizationConfig.value = config
        context.dataStore.edit {
            it[MONETIZATION_CONFIG_JSON] = json.encodeToString(MonetizationConfig.serializer(), config)
            it[MONETIZATION_ENABLED] = config.enabled
        }
    }

    suspend fun updateExoClickConfig(config: ExoClickConfig) {
        _exoClickConfig.value = config
        context.dataStore.edit {
            it[EXOCLICK_CONFIG_JSON] = json.encodeToString(ExoClickConfig.serializer(), config)
        }
    }

    suspend fun updateJuicyAdsConfig(config: JuicyAdsConfig) {
        _juicyAdsConfig.value = config
        context.dataStore.edit {
            it[JUICYADS_CONFIG_JSON] = json.encodeToString(JuicyAdsConfig.serializer(), config)
        }
    }
}
