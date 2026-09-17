package com.thedesitadka.app.monetization

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExoClickAdapterTest {

    @Test
    fun testDefaultConfigUsesSuppliedApiToken() {
        val config = ExoClickConfig()
        assertEquals("18659754dc6dbeb356e149bec57d25a2de3bf1d6", config.apiToken)
        assertTrue(config.enabled)
        assertEquals("5671234", config.homeZoneId)
    }

    @Test
    fun testLoadAdHomeFeedGeneratesValidHtml() = runBlocking {
        val adapter = ExoClickAdapter(ExoClickConfig())
        val result = adapter.loadAd(AdPlacementType.HOME_FEED)

        assertTrue(result is AdLoadResult.Success)
        val success = result as AdLoadResult.Success
        assertEquals(AdProviderType.EXOCLICK, success.providerType)
        assertEquals(AdPlacementType.HOME_FEED, success.placement)
        assertEquals(AdFormat.BANNER_300x250, success.format)
        assertNotNull(success.htmlContent)
        assertTrue(success.htmlContent!!.contains("ad_idzone = \"5671234\""))
        assertTrue(success.htmlContent!!.contains("ad_width = \"300\""))
        assertTrue(success.htmlContent!!.contains("ad_height = \"250\""))
        assertTrue(success.htmlContent!!.contains("https://a.exoclick.com/tag.php"))
    }

    @Test
    fun testLoadAdPlayerPrerollGeneratesVastUrl() = runBlocking {
        val adapter = ExoClickAdapter(ExoClickConfig())
        val result = adapter.loadAd(AdPlacementType.PLAYER_PREROLL)

        assertTrue(result is AdLoadResult.Success)
        val success = result as AdLoadResult.Success
        assertEquals(AdFormat.VAST_VIDEO, success.format)
        assertNotNull(success.vastTagUrl)
        assertTrue(success.vastTagUrl!!.contains("syndication.exoclick.com/splash.php"))
        assertTrue(success.vastTagUrl!!.contains("idzone=5671236"))
    }

    @Test
    fun testLoadAdDisabledReturnsFailure() = runBlocking {
        val adapter = ExoClickAdapter(ExoClickConfig(enabled = false))
        val result = adapter.loadAd(AdPlacementType.HOME_FEED)

        assertTrue(result is AdLoadResult.Failure)
        val failure = result as AdLoadResult.Failure
        assertEquals("EXOCLICK_DISABLED", failure.errorCode)
    }

    @Test
    fun testMissingZoneIdReturnsFailure() = runBlocking {
        val adapter = ExoClickAdapter(ExoClickConfig(homeZoneId = ""))
        val result = adapter.loadAd(AdPlacementType.HOME_FEED)

        assertTrue(result is AdLoadResult.Failure)
        val failure = result as AdLoadResult.Failure
        assertEquals("NO_ZONE_ID", failure.errorCode)
    }

    @Test
    fun testObtainBearerTokenBlankReturnsNull() = runBlocking {
        val adapter = ExoClickAdapter(ExoClickConfig(apiToken = ""))
        val token = adapter.obtainBearerToken()
        assertNull(token)
    }

    @Test
    fun testLifecycleTransitions() {
        val adapter = ExoClickAdapter(ExoClickConfig())
        adapter.pause()
        assertEquals(AdProviderState.UNINITIALIZED, adapter.state.value)

        adapter.resume()
        assertEquals(AdProviderState.UNINITIALIZED, adapter.state.value)

        adapter.destroy()
        assertEquals(AdProviderState.DESTROYED, adapter.state.value)
    }
}
