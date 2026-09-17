package com.thedesitadka.app.monetization

import com.thedesitadka.core.security.StreamHubLogger

class AdPlacementEngine(
    private val exoClickAdapter: ExoClickAdapter,
    private val juicyAdsAdapter: JuicyAdsAdapter,
    private val frequencyController: AdFrequencyController,
    private val consentManager: AdConsentManager,
    private val fallbackController: AdFallbackController
) {

    /**
     * Determines eligibility and loads an advertisement for the requested placement slot.
     */
    suspend fun requestAd(
        placement: AdPlacementType,
        config: MonetizationConfig
    ): AdLoadResult {
        // 1. Check global master switch
        if (!config.enabled || config.providerMode == AdProviderMode.DISABLED) {
            return AdLoadResult.Failure(
                providerType = AdProviderType.EXOCLICK,
                placement = placement,
                errorCode = "MONETIZATION_DISABLED",
                errorMessage = "Monetization is globally disabled"
            )
        }

        // 2. Check placement-specific switch
        val isPlacementEnabled = when (placement) {
            AdPlacementType.HOME_FEED -> config.homeEnabled
            AdPlacementType.CONTENT_DETAIL -> config.detailEnabled
            AdPlacementType.PLAYER_COMPANION, AdPlacementType.PLAYER_PREROLL -> config.playerEnabled
            AdPlacementType.DOWNLOAD_SCREEN -> config.downloadEnabled
        }

        if (!isPlacementEnabled) {
            return AdLoadResult.Failure(
                providerType = AdProviderType.EXOCLICK,
                placement = placement,
                errorCode = "PLACEMENT_DISABLED",
                errorMessage = "Placement ${placement.trackingKey} is disabled in config"
            )
        }

        // 3. Enforce frequency capping and anti-spam cooldowns
        if (!frequencyController.canRequestAd(placement, config.frequencyCapSeconds)) {
            return AdLoadResult.Failure(
                providerType = AdProviderType.EXOCLICK,
                placement = placement,
                errorCode = "FREQUENCY_CAPPED",
                errorMessage = "Cooldown active for ${placement.trackingKey}"
            )
        }

        // 4. Select primary and fallback providers
        val (primary, fallback) = when (config.providerMode) {
            AdProviderMode.AUTO_FALLBACK -> {
                // Round-robin or priority: ExoClick primary, JuicyAds fallback
                Pair(exoClickAdapter, juicyAdsAdapter)
            }
            AdProviderMode.EXOCLICK_ONLY -> Pair(exoClickAdapter, null)
            AdProviderMode.JUICYADS_ONLY -> Pair(juicyAdsAdapter, null)
            AdProviderMode.DISABLED -> return AdLoadResult.Failure(
                AdProviderType.EXOCLICK, placement, "DISABLED", "Disabled"
            )
        }

        StreamHubLogger.i(
            "AdPlacementEngine",
            "Dispatching ad request for ${placement.trackingKey} via ${primary.providerType.displayName}"
        )

        return fallbackController.executeWithFallback(
            placement = placement,
            primaryProvider = primary,
            fallbackProvider = fallback,
            isFallbackEnabled = config.fallbackEnabled
        )
    }

    /**
     * Reports when an ad slot is actually rendered on screen to record real impressions.
     */
    fun onAdDisplayed(placement: AdPlacementType, providerType: AdProviderType) {
        frequencyController.recordImpression(placement)
        AdEventTracker.trackEvent(AdEventType.IMPRESSION, providerType, placement)
    }

    /**
     * Reports when a user interacts with / clicks an ad.
     */
    fun onAdClicked(placement: AdPlacementType, providerType: AdProviderType) {
        AdEventTracker.trackEvent(AdEventType.CLICK, providerType, placement)
    }
}
