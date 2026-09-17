package com.thedesitadka.app.monetization

import com.thedesitadka.core.security.StreamHubLogger
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.ConcurrentLinkedQueue

enum class AdEventType {
    REQUESTED,
    LOADED,
    IMPRESSION,
    CLICK,
    STARTED,
    FIRST_QUARTILE,
    MIDPOINT,
    THIRD_QUARTILE,
    COMPLETED,
    CLOSED,
    FAILED,
    TIMED_OUT
}

data class AdEvent(
    val type: AdEventType,
    val provider: AdProviderType,
    val placement: AdPlacementType,
    val timestamp: Long = System.currentTimeMillis(),
    val details: String? = null,
    val errorCode: String? = null
)

object AdEventTracker {

    private val _eventsFlow = MutableSharedFlow<AdEvent>(extraBufferCapacity = 64)
    val eventsFlow: SharedFlow<AdEvent> = _eventsFlow.asSharedFlow()

    private val eventHistory = ConcurrentLinkedQueue<AdEvent>()
    private const val MAX_HISTORY = 100

    /**
     * Records a genuine ad event triggered by real provider or player callbacks.
     * Enforces anti-fraud: does not manufacture fake impressions or clicks.
     */
    fun trackEvent(
        type: AdEventType,
        provider: AdProviderType,
        placement: AdPlacementType,
        details: String? = null,
        errorCode: String? = null
    ) {
        val event = AdEvent(
            type = type,
            provider = provider,
            placement = placement,
            timestamp = System.currentTimeMillis(),
            details = details,
            errorCode = errorCode
        )

        eventHistory.add(event)
        while (eventHistory.size > MAX_HISTORY) {
            eventHistory.poll()
        }

        _eventsFlow.tryEmit(event)

        val logMsg = "AdEvent: ${type.name} for ${provider.displayName} on ${placement.trackingKey}" +
                (if (!details.isNullOrBlank()) " ($details)" else "") +
                (if (!errorCode.isNullOrBlank()) " [Error: $errorCode]" else "")

        when (type) {
            AdEventType.FAILED, AdEventType.TIMED_OUT -> StreamHubLogger.w("AdEventTracker", logMsg)
            AdEventType.IMPRESSION, AdEventType.CLICK -> StreamHubLogger.i("AdEventTracker", logMsg)
            else -> StreamHubLogger.d("AdEventTracker", logMsg)
        }
    }

    fun getRecentEvents(): List<AdEvent> = eventHistory.toList()

    fun getImpressionCount(placement: AdPlacementType): Int {
        return eventHistory.count { it.placement == placement && it.type == AdEventType.IMPRESSION }
    }

    fun getClickCount(placement: AdPlacementType): Int {
        return eventHistory.count { it.placement == placement && it.type == AdEventType.CLICK }
    }
}
