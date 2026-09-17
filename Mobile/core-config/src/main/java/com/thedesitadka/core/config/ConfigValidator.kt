package com.thedesitadka.core.config

import com.thedesitadka.core.model.ProviderConfig
import com.thedesitadka.core.model.ProviderManifest
import com.thedesitadka.core.model.StreamHubError
import com.thedesitadka.core.security.StreamHubLogger
import com.thedesitadka.core.security.UrlSecurityValidator

object ConfigValidator {

    private val ALLOWED_ADAPTERS = setOf(
        "html_selector",
        "wordpress_rest",
        "rss",
        "json_api",
        "embedded_player"
    )

    private val FORBIDDEN_SELECTOR_PATTERNS = listOf(
        "javascript:",
        "<script",
        "eval(",
        "document.cookie",
        "window.",
        "function(",
        "->",
        "__proto__"
    )

    /**
     * Validates a [ProviderManifest] against security, schema, and operational requirements.
     * Throws [StreamHubError.ConfigurationError] if validation fails.
     */
    fun validate(
        manifest: ProviderManifest,
        currentVersion: Int = 0,
        currentAppVersion: Int = 1,
        currentTimeMillis: Long = System.currentTimeMillis()
    ) {
        if (manifest.schemaVersion < 1) {
            throw StreamHubError.ConfigurationError("Invalid schemaVersion: ${manifest.schemaVersion}. Must be >= 1")
        }

        if (manifest.configVersion < currentVersion) {
            throw StreamHubError.ConfigurationError(
                "Rollback/replay attack detected: incoming configVersion ${manifest.configVersion} < currentVersion $currentVersion"
            )
        }

        if (manifest.expiresAt in 1 until currentTimeMillis) {
            throw StreamHubError.ConfigurationError(
                "Configuration expired at ${manifest.expiresAt}, current time is $currentTimeMillis"
            )
        }

        if (manifest.minimumAppVersion > currentAppVersion && manifest.forceUpdate) {
            StreamHubLogger.w("ConfigValidator", "Application update required: minVersion ${manifest.minimumAppVersion} > current $currentAppVersion")
            throw StreamHubError.ConfigurationError(
                "Application update required: installed version ($currentAppVersion) is below minimum required version (${manifest.minimumAppVersion}). Please update to continue."
            )
        }

        for (provider in manifest.providers) {
            validateProvider(provider)
        }
    }

    fun validateProvider(provider: ProviderConfig) {
        if (provider.id.isBlank()) {
            throw StreamHubError.ConfigurationError("Provider id cannot be blank")
        }
        if (provider.name.isBlank()) {
            throw StreamHubError.ConfigurationError("Provider name cannot be blank for provider: ${provider.id}")
        }
        if (!ALLOWED_ADAPTERS.contains(provider.adapter)) {
            throw StreamHubError.ConfigurationError(
                "Unsupported adapter: '${provider.adapter}' for provider: ${provider.id}. Must be one of $ALLOWED_ADAPTERS"
            )
        }
        if (!UrlSecurityValidator.isUrlSafe(provider.baseUrl)) {
            throw StreamHubError.ConfigurationError(
                "Provider baseUrl rejected by security guardrails: '${provider.baseUrl}' for provider: ${provider.id}"
            )
        }

        // Validate Selectors DSL safety
        provider.selectors?.let { selectors ->
            validateSafeSelector(selectors.item, "item", provider.id)
            validateSafeSelector(selectors.title, "title", provider.id)
            validateSafeSelector(selectors.thumbnail, "thumbnail", provider.id)
            validateSafeSelector(selectors.detailUrl, "detailUrl", provider.id)
            selectors.player?.let { validateSafeSelector(it, "player", provider.id) }
            selectors.videoSource?.let { validateSafeSelector(it, "videoSource", provider.id) }
        }
    }

    private fun validateSafeSelector(selector: String, fieldName: String, providerId: String) {
        val lower = selector.lowercase()
        for (forbidden in FORBIDDEN_SELECTOR_PATTERNS) {
            if (lower.contains(forbidden)) {
                throw StreamHubError.ConfigurationError(
                    "Malicious or executable selector detected in '$fieldName' for provider $providerId: '$selector'"
                )
            }
        }
    }
}
