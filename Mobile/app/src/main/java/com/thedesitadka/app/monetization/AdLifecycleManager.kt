package com.thedesitadka.app.monetization

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.thedesitadka.core.security.StreamHubLogger

class AdLifecycleManager(
    private val exoClickAdapter: ExoClickAdapter,
    private val juicyAdsAdapter: JuicyAdsAdapter
) : DefaultLifecycleObserver {

    override fun onResume(owner: LifecycleOwner) {
        StreamHubLogger.d("AdLifecycleManager", "Activity resumed: resuming active ad components")
        exoClickAdapter.resume()
        juicyAdsAdapter.resume()
    }

    override fun onPause(owner: LifecycleOwner) {
        StreamHubLogger.d("AdLifecycleManager", "Activity paused: pausing active ad components")
        exoClickAdapter.pause()
        juicyAdsAdapter.pause()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        StreamHubLogger.d("AdLifecycleManager", "Activity destroyed: releasing ad resources")
        exoClickAdapter.destroy()
        juicyAdsAdapter.destroy()
    }
}
