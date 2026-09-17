package com.thedesitadka.app.monetization

import android.content.Context
import com.thedesitadka.core.network.NetworkClient
import com.thedesitadka.core.security.StreamHubLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URLEncoder

/**
 * Official publisher adapter for JuicyAds.
 * Supports:
 * - Sandboxed HTML display zones (IAB standard banner sizes)
 * - Official JuicyAds REST API v1.0 publisher statistics queries
 */
class JuicyAdsAdapter(
    private var config: JuicyAdsConfig
) : AdProvider {

    override val providerType: AdProviderType = AdProviderType.JUICYADS

    private val _state = MutableStateFlow(AdProviderState.UNINITIALIZED)
    override val state: StateFlow<AdProviderState> = _state.asStateFlow()

    fun updateConfig(newConfig: JuicyAdsConfig) {
        this.config = newConfig
        if (!config.enabled) {
            _state.value = AdProviderState.UNAVAILABLE
        }
    }

    override suspend fun initialize(context: Context): Boolean {
        _state.value = AdProviderState.INITIALIZING
        return try {
            if (!config.enabled) {
                _state.value = AdProviderState.UNAVAILABLE
                StreamHubLogger.i("JuicyAdsAdapter", "JuicyAds is disabled in configuration")
                false
            } else {
                _state.value = AdProviderState.READY
                StreamHubLogger.i("JuicyAdsAdapter", "JuicyAds publisher adapter initialized successfully")
                true
            }
        } catch (e: Exception) {
            _state.value = AdProviderState.FAILED
            StreamHubLogger.e("JuicyAdsAdapter", "Initialization failed: ${e.message}")
            false
        }
    }

    override fun isAvailable(): Boolean {
        return config.enabled && (_state.value == AdProviderState.READY || _state.value == AdProviderState.LOADED || _state.value == AdProviderState.DISPLAYING)
    }

    override suspend fun loadAd(placement: AdPlacementType): AdLoadResult {
        if (!config.enabled) {
            return AdLoadResult.Failure(
                providerType = providerType,
                placement = placement,
                errorCode = "JUICYADS_DISABLED",
                errorMessage = "JuicyAds is disabled in configuration"
            )
        }

        _state.value = AdProviderState.LOADING
        AdEventTracker.trackEvent(AdEventType.REQUESTED, providerType, placement)

        val zoneId = when (placement) {
            AdPlacementType.HOME_FEED -> config.homeZoneId
            AdPlacementType.CONTENT_DETAIL -> config.detailZoneId
            AdPlacementType.PLAYER_COMPANION -> config.playerCompanionZoneId
            AdPlacementType.PLAYER_PREROLL -> config.playerCompanionZoneId
            AdPlacementType.DOWNLOAD_SCREEN -> config.homeZoneId
        }

        if (zoneId.isBlank()) {
            _state.value = AdProviderState.FAILED
            val err = "No valid JuicyAds zone ID configured for ${placement.trackingKey}"
            AdEventTracker.trackEvent(AdEventType.FAILED, providerType, placement, errorCode = "NO_ZONE_ID")
            return AdLoadResult.Failure(providerType, placement, "NO_ZONE_ID", err)
        }

        return try {
            val format = when (placement) {
                AdPlacementType.HOME_FEED -> AdFormat.BANNER_300x250
                AdPlacementType.CONTENT_DETAIL -> AdFormat.BANNER_300x250
                AdPlacementType.PLAYER_COMPANION -> AdFormat.BANNER_300x100
                AdPlacementType.PLAYER_PREROLL -> AdFormat.BANNER_300x250
                AdPlacementType.DOWNLOAD_SCREEN -> AdFormat.BANNER_300x100
            }

            // Generate official sandboxed HTML markup for JuicyAds jads.js snippet
            val html = generateSandboxedHtml(zoneId, format.widthDp, format.heightDp)
            _state.value = AdProviderState.LOADED
            AdEventTracker.trackEvent(AdEventType.LOADED, providerType, placement, details = "Zone $zoneId ready")

            AdLoadResult.Success(
                providerType = providerType,
                placement = placement,
                format = format,
                htmlContent = html,
                trackingData = mapOf("zoneId" to zoneId)
            )
        } catch (e: Exception) {
            _state.value = AdProviderState.FAILED
            AdEventTracker.trackEvent(AdEventType.FAILED, providerType, placement, errorCode = "LOAD_EXCEPTION", details = e.message)
            AdLoadResult.Failure(providerType, placement, "LOAD_EXCEPTION", e.message ?: "JuicyAds load exception")
        }
    }

    /**
     * Generates a secure, responsive HTML container for rendering the JuicyAds zone in an isolated WebView.
     */
    private fun generateSandboxedHtml(zoneId: String, width: Int, height: Int): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <style>
                    body {
                        margin: 0;
                        padding: 0;
                        background: transparent;
                        display: flex;
                        justify-content: center;
                        align-items: center;
                        width: 100%;
                        height: 100%;
                        overflow: hidden;
                    }
                    .ad-container {
                        width: ${width}px;
                        height: ${height}px;
                        display: flex;
                        justify-content: center;
                        align-items: center;
                    }
                </style>
            </head>
            <body>
                <div class="ad-container">
                    <!-- JuicyAds v3.0 Official Zone Tag -->
                    <script type="text/javascript" data-cfasync="false" async src="https://adserver.juicyads.com/js/jads.js"></script>
                    <ins id="$zoneId" data-width="$width" data-height="$height"></ins>
                    <script type="text/javascript" data-cfasync="false">
                        (adsbyjuicy = window.adsbyjuicy || []).push({'adzone': '$zoneId'});
                    </script>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * Queries official JuicyAds API v1.0 Popunder publisher statistics if an API token is provided.
     * Endpoints: https://api.juicyads.com/statistics/popunders/publisher/{token}/{start-date}/{end-date}
     */
    suspend fun queryPublisherStatistics(startDate: String, endDate: String): String? {
        val token = config.apiToken
        if (token.isBlank()) {
            StreamHubLogger.d("JuicyAdsAdapter", "JuicyAds API token not set; skipping remote stats query")
            return null
        }

        return try {
            val safeToken = URLEncoder.encode(token, "UTF-8")
            val safeStart = URLEncoder.encode(startDate, "UTF-8")
            val safeEnd = URLEncoder.encode(endDate, "UTF-8")
            val url = "https://api.juicyads.com/statistics/popunders/publisher/$safeToken/$safeStart/$safeEnd"

            NetworkClient.fetchString(url)
        } catch (e: Exception) {
            StreamHubLogger.w("JuicyAdsAdapter", "Failed to query JuicyAds stats: ${e.message}")
            null
        }
    }

    override fun pause() {
        if (_state.value == AdProviderState.DISPLAYING) {
            _state.value = AdProviderState.LOADED
        }
    }

    override fun resume() {
        if (_state.value == AdProviderState.LOADED) {
            _state.value = AdProviderState.DISPLAYING
        }
    }

    override fun destroy() {
        _state.value = AdProviderState.DESTROYED
    }

    override fun getStatusSummary(): String {
        return "JuicyAds (State: ${_state.value.name}, Enabled: ${config.enabled})"
    }
}
