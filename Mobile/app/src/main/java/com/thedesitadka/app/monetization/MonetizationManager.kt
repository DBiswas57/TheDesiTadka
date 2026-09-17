package com.thedesitadka.app.monetization

import android.content.Context
import com.thedesitadka.core.security.StreamHubLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MonetizationManager(
    private val context: Context,
    val configRepository: AdConfigRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    val exoClickAdapter = ExoClickAdapter(configRepository.exoClickConfig.value)
    val juicyAdsAdapter = JuicyAdsAdapter(configRepository.juicyAdsConfig.value)

    val frequencyController = AdFrequencyController(
        defaultCooldownSeconds = configRepository.monetizationConfig.value.frequencyCapSeconds
    )

    val consentManager = AdConsentManager()
    val fallbackController = AdFallbackController(loadTimeoutMs = 5_000L)

    val placementEngine = AdPlacementEngine(
        exoClickAdapter = exoClickAdapter,
        juicyAdsAdapter = juicyAdsAdapter,
        frequencyController = frequencyController,
        consentManager = consentManager,
        fallbackController = fallbackController
    )

    val lifecycleManager = AdLifecycleManager(exoClickAdapter, juicyAdsAdapter)

    init {
        scope.launch {
            configRepository.exoClickConfig.collect { newConfig ->
                exoClickAdapter.updateConfig(newConfig)
            }
        }
        scope.launch {
            configRepository.juicyAdsConfig.collect { newConfig ->
                juicyAdsAdapter.updateConfig(newConfig)
            }
        }
    }

    suspend fun initialize(): Boolean {
        StreamHubLogger.i("MonetizationManager", "Initializing monetization subsystem...")
        val exoInit = exoClickAdapter.initialize(context)
        val juicyInit = juicyAdsAdapter.initialize(context)
        return exoInit || juicyInit
    }

    suspend fun loadPlacement(placement: AdPlacementType): AdLoadResult {
        val config = configRepository.monetizationConfig.value
        return placementEngine.requestAd(placement, config)
    }

    fun onAdDisplayed(placement: AdPlacementType, providerType: AdProviderType) {
        placementEngine.onAdDisplayed(placement, providerType)
    }

    fun onAdClicked(placement: AdPlacementType, providerType: AdProviderType) {
        placementEngine.onAdClicked(placement, providerType)
    }

    fun isMonetizationActive(): Boolean {
        val config = configRepository.monetizationConfig.value
        return config.enabled && (exoClickAdapter.isAvailable() || juicyAdsAdapter.isAvailable())
    }

    fun getStatusSummary(): String {
        return if (!configRepository.monetizationConfig.value.enabled) {
            "Monetization Disabled"
        } else {
            "Active (${exoClickAdapter.providerType.displayName}: ${exoClickAdapter.state.value.name}, " +
                    "${juicyAdsAdapter.providerType.displayName}: ${juicyAdsAdapter.state.value.name})"
        }
    }
}
