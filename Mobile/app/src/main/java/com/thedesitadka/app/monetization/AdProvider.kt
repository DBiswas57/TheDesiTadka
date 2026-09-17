package com.thedesitadka.app.monetization

import android.content.Context
import kotlinx.coroutines.flow.StateFlow

/**
 * Common contract for all ad network publisher adapters (ExoClick, JuicyAds).
 * Every implementation maintains a formal lifecycle state machine and
 * communicates solely via genuine provider responses.
 */
interface AdProvider {

    val providerType: AdProviderType

    val state: StateFlow<AdProviderState>

    suspend fun initialize(context: Context): Boolean

    fun isAvailable(): Boolean

    suspend fun loadAd(placement: AdPlacementType): AdLoadResult

    fun pause()

    fun resume()

    fun destroy()

    fun getStatusSummary(): String
}
