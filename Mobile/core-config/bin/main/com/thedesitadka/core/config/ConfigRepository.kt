package com.thedesitadka.core.config

import com.thedesitadka.core.model.ProviderConfig
import com.thedesitadka.core.model.ProviderManifest
import com.thedesitadka.core.network.NetworkClient
import com.thedesitadka.core.security.StreamHubLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ConfigRepository(
    private val configCache: ConfigCache,
    private val appVersion: Int = 1,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    private val _manifestFlow = MutableStateFlow<ProviderManifest>(
        configCache.loadCurrent() ?: ProviderManifest()
    )
    val manifestFlow: StateFlow<ProviderManifest> = _manifestFlow.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncError = MutableStateFlow<String?>(null)
    val lastSyncError: StateFlow<String?> = _lastSyncError.asStateFlow()

    init {
        val initial = configCache.loadCurrent()
        if (initial != null) {
            _manifestFlow.value = initial
        }
    }

    suspend fun syncRemoteConfig(
        remoteUrl: String,
        publicKeyBase64: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        _isSyncing.value = true
        _lastSyncError.value = null
        try {
            StreamHubLogger.i("ConfigRepository", "Fetching remote configuration from $remoteUrl...")
            val signedJson = NetworkClient.fetchString(remoteUrl)

            val currentVersion = _manifestFlow.value.configVersion
            val verificationResult = if (publicKeyBase64 != null) {
                ConfigVerifier.verifySignedPayload(
                    signedJson = signedJson,
                    currentVersion = currentVersion,
                    currentAppVersion = appVersion,
                    publicKeyBase64 = publicKeyBase64
                )
            } else {
                ConfigVerifier.verifySignedPayload(
                    signedJson = signedJson,
                    currentVersion = currentVersion,
                    currentAppVersion = appVersion
                )
            }

            if (!verificationResult.isSuccess || verificationResult.manifest == null) {
                val errorMsg = verificationResult.error?.message ?: "Verification failed"
                _lastSyncError.value = errorMsg
                StreamHubLogger.w("ConfigRepository", "Sync rejected: $errorMsg. Maintaining previous known-good config.")
                return@withContext false
            }

            val newManifest = verificationResult.manifest
            configCache.saveValidatedConfig(newManifest, markAsLastKnownGood = true)
            _manifestFlow.value = newManifest
            StreamHubLogger.i("ConfigRepository", "Remote config v${newManifest.configVersion} activated successfully")
            true
        } catch (e: Exception) {
            val errorMsg = when {
                e.message?.contains("404") == true -> "Remote catalog server returned 404. Active catalog v${_manifestFlow.value.configVersion} (${_manifestFlow.value.providers.size} providers) maintained."
                else -> e.message ?: "Network error during sync"
            }
            _lastSyncError.value = errorMsg
            StreamHubLogger.e("ConfigRepository", "Failed to sync remote config: ${e.message}")
            false
        } finally {
            _isSyncing.value = false
        }
    }

    fun updateManifest(newManifest: ProviderManifest) {
        _manifestFlow.value = newManifest
        configCache.saveValidatedConfig(newManifest, markAsLastKnownGood = true)
        StreamHubLogger.i("ConfigRepository", "Catalog updated to v${newManifest.configVersion} (${newManifest.providers.size} providers)")
    }

    fun setProviderEnabled(providerId: String, enabled: Boolean) {
        val current = _manifestFlow.value
        val updatedProviders = current.providers.map { provider ->
            if (provider.id == providerId) {
                provider.copy(enabled = enabled)
            } else {
                provider
            }
        }
        val updatedManifest = current.copy(providers = updatedProviders)
        _manifestFlow.value = updatedManifest
        configCache.saveValidatedConfig(updatedManifest, markAsLastKnownGood = false)
        StreamHubLogger.i("ConfigRepository", "Provider '$providerId' enabled status changed to: $enabled")
    }

    fun rollbackToPrevious(): Boolean {
        val previous = configCache.rollback()
        return if (previous != null) {
            _manifestFlow.value = previous
            true
        } else {
            false
        }
    }

    fun getActiveProviders(): List<ProviderConfig> {
        return _manifestFlow.value.providers.filter { it.enabled }
    }

    fun getProvider(id: String): ProviderConfig? {
        return _manifestFlow.value.providers.find { it.id == id }
    }
}
