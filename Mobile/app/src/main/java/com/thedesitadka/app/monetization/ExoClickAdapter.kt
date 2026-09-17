package com.thedesitadka.app.monetization

import android.content.Context
import com.thedesitadka.core.network.NetworkClient
import com.thedesitadka.core.security.StreamHubLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder

/**
 * Official publisher adapter for ExoClick.
 * Supports:
 * - Sandboxed HTML display zones (IAB standard formats)
 * - IAB VAST 2.0/3.0/4.0 video ad tags
 * - ExoClick Public API v2 statistics queries
 */
class ExoClickAdapter(
    private var config: ExoClickConfig
) : AdProvider {

    override val providerType: AdProviderType = AdProviderType.EXOCLICK

    private val _state = MutableStateFlow(AdProviderState.UNINITIALIZED)
    override val state: StateFlow<AdProviderState> = _state.asStateFlow()

    @Volatile
    private var cachedBearerToken: String? = null
    @Volatile
    private var tokenExpiresAtEpochMs: Long = 0L

    fun updateConfig(newConfig: ExoClickConfig) {
        if (this.config.apiToken != newConfig.apiToken) {
            cachedBearerToken = null
            tokenExpiresAtEpochMs = 0L
        }
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
                StreamHubLogger.i("ExoClickAdapter", "ExoClick is disabled in configuration")
                false
            } else {
                _state.value = AdProviderState.READY
                StreamHubLogger.i("ExoClickAdapter", "ExoClick publisher adapter initialized successfully")
                true
            }
        } catch (e: Exception) {
            _state.value = AdProviderState.FAILED
            StreamHubLogger.e("ExoClickAdapter", "Initialization failed: ${e.message}")
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
                errorCode = "EXOCLICK_DISABLED",
                errorMessage = "ExoClick is disabled in configuration"
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
            val err = "No valid ExoClick zone ID configured for ${placement.trackingKey}"
            AdEventTracker.trackEvent(AdEventType.FAILED, providerType, placement, errorCode = "NO_ZONE_ID")
            return AdLoadResult.Failure(providerType, placement, "NO_ZONE_ID", err)
        }

        return try {
            val format = when (placement) {
                AdPlacementType.HOME_FEED -> AdFormat.BANNER_300x250
                AdPlacementType.CONTENT_DETAIL -> AdFormat.BANNER_300x250
                AdPlacementType.PLAYER_COMPANION -> AdFormat.BANNER_300x100
                AdPlacementType.PLAYER_PREROLL -> AdFormat.VAST_VIDEO
                AdPlacementType.DOWNLOAD_SCREEN -> AdFormat.BANNER_300x100
            }

            if (format == AdFormat.VAST_VIDEO) {
                val vastUrl = if (config.playerVastTagUrl.isNotBlank()) {
                    config.playerVastTagUrl
                } else {
                    "https://syndication.exoclick.com/splash.php?idzone=$zoneId&type=8"
                }

                _state.value = AdProviderState.LOADED
                AdEventTracker.trackEvent(AdEventType.LOADED, providerType, placement, details = "VAST URL ready")

                AdLoadResult.Success(
                    providerType = providerType,
                    placement = placement,
                    format = format,
                    vastTagUrl = vastUrl
                )
            } else {
                // Generate official sandboxed HTML markup for the ExoClick publisher zone
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
            }
        } catch (e: Exception) {
            _state.value = AdProviderState.FAILED
            AdEventTracker.trackEvent(AdEventType.FAILED, providerType, placement, errorCode = "LOAD_EXCEPTION", details = e.message)
            AdLoadResult.Failure(providerType, placement, "LOAD_EXCEPTION", e.message ?: "ExoClick load exception")
        }
    }

    /**
     * Generates a secure, responsive HTML container for rendering the ExoClick zone in an isolated WebView.
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
                    <script type="application/javascript">
                        var ad_idzone = "$zoneId";
                        var ad_width = "$width";
                        var ad_height = "$height";
                    </script>
                    <script type="application/javascript" src="https://a.exoclick.com/tag.php"></script>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * Authenticates the configured API token with ExoClick API v2 to retrieve a short-lived Bearer token.
     * Official specification: POST https://api.exoclick.com/v2/login with {"api_token":"..."}
     */
    suspend fun obtainBearerToken(): String? {
        val apiToken = config.apiToken.trim()
        if (apiToken.isBlank()) {
            StreamHubLogger.d("ExoClickAdapter", "ExoClick API token not set; skipping bearer token generation")
            return null
        }

        val now = System.currentTimeMillis()
        val current = cachedBearerToken
        if (current != null && now < tokenExpiresAtEpochMs) {
            return current
        }

        return try {
            val jsonPayload = """{"api_token":"$apiToken"}"""
            val body = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url("https://api.exoclick.com/v2/login")
                .post(body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .build()

            NetworkClient.okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    StreamHubLogger.w("ExoClickAdapter", "ExoClick login failed with HTTP ${response.code}")
                    return null
                }
                val responseBody = response.body?.string() ?: return null
                val jsonElement = Json.parseToJsonElement(responseBody).jsonObject
                val token = jsonElement["token"]?.jsonPrimitive?.content ?: return null
                val expiresInSec = jsonElement["expires_in"]?.jsonPrimitive?.longOrNull ?: 43200L

                cachedBearerToken = token
                // Buffer by 5 minutes to avoid expiring mid-flight
                tokenExpiresAtEpochMs = now + ((expiresInSec - 300).coerceAtLeast(60L) * 1000L)
                StreamHubLogger.i("ExoClickAdapter", "Successfully obtained ExoClick Bearer token (valid for ${expiresInSec}s)")
                token
            }
        } catch (e: Exception) {
            StreamHubLogger.e("ExoClickAdapter", "Failed to authenticate ExoClick API token: ${e.message}")
            null
        }
    }

    /**
     * Queries ExoClick API v2 statistics for publisher reporting using the authenticated session Bearer token.
     * Endpoints follow official documentation: https://api.exoclick.com/v2/statistics/p/date
     */
    suspend fun queryPublisherStatistics(dateFrom: String, dateTo: String): String? {
        val bearerToken = obtainBearerToken() ?: run {
            StreamHubLogger.w("ExoClickAdapter", "ExoClick Bearer token unavailable; skipping stats query")
            return null
        }

        return try {
            val url = "https://api.exoclick.com/v2/statistics/p/date?date-from=" +
                    URLEncoder.encode(dateFrom, "UTF-8") +
                    "&date-to=" + URLEncoder.encode(dateTo, "UTF-8")

            val headers = mapOf(
                "Authorization" to "Bearer $bearerToken",
                "Accept" to "application/json"
            )

            NetworkClient.fetchString(url, headers = headers)
        } catch (e: Exception) {
            StreamHubLogger.w("ExoClickAdapter", "Failed to query ExoClick stats: ${e.message}")
            null
        }
    }

    /**
     * Queries the publisher's registered sites and compliance status from ExoClick API v2.
     * Endpoint: https://api.exoclick.com/v2/sites
     */
    suspend fun queryPublisherSites(): String? {
        val bearerToken = obtainBearerToken() ?: return null
        return try {
            val url = "https://api.exoclick.com/v2/sites"
            val headers = mapOf(
                "Authorization" to "Bearer $bearerToken",
                "Accept" to "application/json"
            )
            NetworkClient.fetchString(url, headers = headers)
        } catch (e: Exception) {
            StreamHubLogger.w("ExoClickAdapter", "Failed to query ExoClick sites: ${e.message}")
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
        return "ExoClick (State: ${_state.value.name}, Enabled: ${config.enabled})"
    }
}
