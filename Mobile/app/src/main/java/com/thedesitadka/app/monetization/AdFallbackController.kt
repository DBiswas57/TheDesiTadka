package com.thedesitadka.app.monetization

import com.thedesitadka.core.security.StreamHubLogger
import kotlinx.coroutines.withTimeoutOrNull

class AdFallbackController(
    private val loadTimeoutMs: Long = 5_000L
) {

    /**
     * Executes ad loading with strict timeout and fallback to alternate provider if enabled.
     * Guaranteed never to throw uncaught exceptions or block caller.
     */
    suspend fun executeWithFallback(
        placement: AdPlacementType,
        primaryProvider: AdProvider,
        fallbackProvider: AdProvider?,
        isFallbackEnabled: Boolean
    ): AdLoadResult {
        // 1. Attempt primary provider within bounded timeout
        val primaryResult = withTimeoutOrNull(loadTimeoutMs) {
            try {
                primaryProvider.loadAd(placement)
            } catch (e: Exception) {
                AdLoadResult.Failure(
                    providerType = primaryProvider.providerType,
                    placement = placement,
                    errorCode = "PRIMARY_EXCEPTION",
                    errorMessage = e.message ?: "Unknown error"
                )
            }
        } ?: AdLoadResult.Failure(
            providerType = primaryProvider.providerType,
            placement = placement,
            errorCode = "TIMEOUT",
            errorMessage = "Primary ad load timed out after ${loadTimeoutMs}ms"
        )

        if (primaryResult is AdLoadResult.Success) {
            return primaryResult
        }

        StreamHubLogger.w(
            "AdFallbackController",
            "Primary provider (${primaryProvider.providerType.displayName}) failed on ${placement.trackingKey}: " +
                    (primaryResult as? AdLoadResult.Failure)?.errorMessage
        )

        // 2. Attempt secondary fallback provider if available and permitted
        if (isFallbackEnabled && fallbackProvider != null && fallbackProvider.isAvailable()) {
            StreamHubLogger.i(
                "AdFallbackController",
                "Attempting fallback to ${fallbackProvider.providerType.displayName} on ${placement.trackingKey}"
            )

            val fallbackResult = withTimeoutOrNull(loadTimeoutMs) {
                try {
                    fallbackProvider.loadAd(placement)
                } catch (e: Exception) {
                    AdLoadResult.Failure(
                        providerType = fallbackProvider.providerType,
                        placement = placement,
                        errorCode = "FALLBACK_EXCEPTION",
                        errorMessage = e.message ?: "Unknown error"
                    )
                }
            } ?: AdLoadResult.Failure(
                providerType = fallbackProvider.providerType,
                placement = placement,
                errorCode = "FALLBACK_TIMEOUT",
                errorMessage = "Fallback ad load timed out after ${loadTimeoutMs}ms"
            )

            if (fallbackResult is AdLoadResult.Success) {
                return fallbackResult
            }

            StreamHubLogger.w(
                "AdFallbackController",
                "Fallback provider (${fallbackProvider.providerType.displayName}) also failed on ${placement.trackingKey}"
            )
        }

        // 3. Both failed or fallback disabled: return primary failure to collapse slot
        return primaryResult
    }
}
