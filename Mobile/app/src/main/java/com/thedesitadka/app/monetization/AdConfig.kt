package com.thedesitadka.app.monetization

import kotlinx.serialization.Serializable

@Serializable
data class MonetizationConfig(
    val enabled: Boolean = true,
    val providerMode: AdProviderMode = AdProviderMode.AUTO_FALLBACK,
    val fallbackEnabled: Boolean = true,
    val homeEnabled: Boolean = true,
    val detailEnabled: Boolean = true,
    val playerEnabled: Boolean = true,
    val downloadEnabled: Boolean = true,
    val frequencyCapSeconds: Long = 60L,
    val cooldownBetweenPlacementsMs: Long = 10_000L,
    val consentRequired: Boolean = false,
    val debugMode: Boolean = false
)

@Serializable
data class ExoClickConfig(
    val enabled: Boolean = true,
    val homeZoneId: String = "5671234",
    val detailZoneId: String = "5671235",
    val playerCompanionZoneId: String = "5671236",
    val playerVastTagUrl: String = "",
    val apiToken: String = "18659754dc6dbeb356e149bec57d25a2de3bf1d6"
)

@Serializable
data class JuicyAdsConfig(
    val enabled: Boolean = true,
    val homeZoneId: String = "1098765",
    val detailZoneId: String = "1098766",
    val playerCompanionZoneId: String = "1098767",
    val apiToken: String = "E0C8E750-3CE5-410D-D018-DE31243E943C"
)

sealed class AdLoadResult {
    data class Success(
        val providerType: AdProviderType,
        val placement: AdPlacementType,
        val format: AdFormat,
        val htmlContent: String? = null,
        val vastTagUrl: String? = null,
        val trackingData: Map<String, String> = emptyMap()
    ) : AdLoadResult()

    data class Failure(
        val providerType: AdProviderType,
        val placement: AdPlacementType,
        val errorCode: String,
        val errorMessage: String,
        val isNoFill: Boolean = false
    ) : AdLoadResult()
}
