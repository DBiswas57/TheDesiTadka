package com.thedesitadka.app.monetization

import com.thedesitadka.core.security.StreamHubLogger
import java.util.concurrent.ConcurrentHashMap

class AdFrequencyController(
    private val defaultCooldownSeconds: Long = 60L,
    private val globalCooldownMs: Long = 10_000L
) {

    private val lastImpressionTimes = ConcurrentHashMap<AdPlacementType, Long>()
    private val lastRequestTimes = ConcurrentHashMap<AdPlacementType, Long>()
    private var lastGlobalImpressionTime = 0L

    /**
     * Determines whether an ad can be requested for a given placement
     * without violating frequency caps or cooldown periods.
     */
    fun canRequestAd(placement: AdPlacementType, frequencyCapSeconds: Long = defaultCooldownSeconds): Boolean {
        val now = System.currentTimeMillis()

        // 1. Check global cooldown between any placements to prevent spam
        if (now - lastGlobalImpressionTime < globalCooldownMs) {
            StreamHubLogger.d("AdFrequencyController", "Blocked ad request for ${placement.trackingKey}: global cooldown active (${now - lastGlobalImpressionTime}ms < ${globalCooldownMs}ms)")
            return false
        }

        // 2. Check per-placement debounce against rapid requests (e.g. fast scrolling or recomposition)
        val lastReq = lastRequestTimes[placement] ?: 0L
        if (now - lastReq < 3_000L) {
            StreamHubLogger.d("AdFrequencyController", "Debounced ad request for ${placement.trackingKey}: last request was ${now - lastReq}ms ago")
            return false
        }

        // 3. Check frequency cap cooldown since last impression for this placement
        val lastImp = lastImpressionTimes[placement] ?: 0L
        val capMs = frequencyCapSeconds * 1000L
        if (now - lastImp < capMs) {
            StreamHubLogger.d("AdFrequencyController", "Frequency cap active for ${placement.trackingKey}: cooldown remaining ${(capMs - (now - lastImp)) / 1000}s")
            return false
        }

        lastRequestTimes[placement] = now
        return true
    }

    /**
     * Records a genuine impression callback to reset the cooldown timer.
     */
    fun recordImpression(placement: AdPlacementType) {
        val now = System.currentTimeMillis()
        lastImpressionTimes[placement] = now
        lastGlobalImpressionTime = now
        StreamHubLogger.d("AdFrequencyController", "Recorded impression for ${placement.trackingKey} at $now")
    }

    /**
     * Resets frequency tracking (e.g. for user refresh or test scenarios).
     */
    fun reset() {
        lastImpressionTimes.clear()
        lastRequestTimes.clear()
        lastGlobalImpressionTime = 0L
    }
}
