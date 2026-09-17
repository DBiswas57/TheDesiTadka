package com.thedesitadka.app.monetization

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ConsentStatus {
    CONSENTED,
    REVOKED,
    NOT_REQUIRED
}

class AdConsentManager {

    private val _consentState = MutableStateFlow(ConsentStatus.NOT_REQUIRED)
    val consentState: StateFlow<ConsentStatus> = _consentState.asStateFlow()

    fun updateConsent(status: ConsentStatus) {
        _consentState.value = status
    }

    fun canServePersonalizedAds(): Boolean {
        return _consentState.value == ConsentStatus.CONSENTED || _consentState.value == ConsentStatus.NOT_REQUIRED
    }

    /**
     * Ensures no sensitive application data (content provider tokens, cookies, download names)
     * is shared with advertisement networks.
     */
    fun sanitizeAdParameters(params: Map<String, String>): Map<String, String> {
        val prohibitedKeys = setOf("token", "cookie", "auth", "session", "user", "password", "file", "download")
        return params.filterKeys { key -> prohibitedKeys.none { prohibited -> key.contains(prohibited, ignoreCase = true) } }
    }
}
