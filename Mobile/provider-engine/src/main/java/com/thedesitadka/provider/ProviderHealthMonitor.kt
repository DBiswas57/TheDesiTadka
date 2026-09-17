package com.thedesitadka.provider

import com.thedesitadka.core.model.ProviderStatus
import com.thedesitadka.core.security.StreamHubLogger
import java.util.concurrent.ConcurrentHashMap

class ProviderHealthMonitor {

    private val errorCounts = ConcurrentHashMap<String, Int>()
    private val providerStatuses = ConcurrentHashMap<String, ProviderStatus>()

    fun recordSuccess(providerId: String) {
        errorCounts[providerId] = 0
        val previous = providerStatuses[providerId]
        if (previous != null && previous != ProviderStatus.ENABLED && previous != ProviderStatus.DISABLED) {
            providerStatuses[providerId] = ProviderStatus.ENABLED
            StreamHubLogger.i("HealthMonitor", "Provider '$providerId' recovered to ENABLED")
        }
    }

    fun recordError(providerId: String, isParserError: Boolean = false) {
        val currentErrors = (errorCounts[providerId] ?: 0) + 1
        errorCounts[providerId] = currentErrors

        val newStatus = when {
            isParserError -> ProviderStatus.PARSER_ERROR
            currentErrors >= 5 -> ProviderStatus.UNAVAILABLE
            currentErrors >= 2 -> ProviderStatus.DEGRADED
            else -> providerStatuses[providerId] ?: ProviderStatus.ENABLED
        }

        providerStatuses[providerId] = newStatus
        StreamHubLogger.w("HealthMonitor", "Provider '$providerId' error count: $currentErrors, status: $newStatus")
    }

    fun getStatus(providerId: String, defaultStatus: ProviderStatus = ProviderStatus.ENABLED): ProviderStatus {
        return providerStatuses[providerId] ?: defaultStatus
    }

    fun setStatus(providerId: String, status: ProviderStatus) {
        providerStatuses[providerId] = status
    }
}
