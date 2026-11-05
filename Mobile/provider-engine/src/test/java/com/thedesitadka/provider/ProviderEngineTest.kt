package com.thedesitadka.provider

import com.thedesitadka.core.model.Category
import com.thedesitadka.core.model.FeedPage
import com.thedesitadka.core.model.MediaSource
import com.thedesitadka.core.model.ProviderCapability
import com.thedesitadka.core.model.ProviderConfig
import com.thedesitadka.core.model.ProviderInfo
import com.thedesitadka.core.model.ProviderManifest
import com.thedesitadka.core.model.ProviderStatus
import com.thedesitadka.core.model.VideoItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ProviderEngineTest {

    @Test
    fun testUpdateFromManifestDynamicRegistration() {
        val engine = ProviderEngine()

        val manifest1 = ProviderManifest(
            schemaVersion = 1,
            configVersion = 1,
            providers = listOf(
                ProviderConfig(
                    id = "provider_a",
                    name = "Provider A",
                    baseUrl = "https://a.com",
                    adapter = "html_selector",
                    capabilities = listOf(ProviderCapability.HOME)
                )
            )
        )

        engine.updateFromManifest(manifest1)
        assertNotNull(engine.getAdapter("provider_a"))
        assertEquals(1, engine.getActiveProviders().size)

        // Remote server adds Provider B without APK rebuild
        val manifest2 = ProviderManifest(
            schemaVersion = 1,
            configVersion = 2,
            providers = listOf(
                ProviderConfig(
                    id = "provider_a",
                    name = "Provider A",
                    baseUrl = "https://a.com",
                    adapter = "html_selector",
                    capabilities = listOf(ProviderCapability.HOME)
                ),
                ProviderConfig(
                    id = "provider_b",
                    name = "Provider B",
                    baseUrl = "https://b.com",
                    adapter = "rss",
                    capabilities = listOf(ProviderCapability.HOME, ProviderCapability.STREAM)
                )
            )
        )

        engine.updateFromManifest(manifest2)
        assertNotNull(engine.getAdapter("provider_a"))
        assertNotNull(engine.getAdapter("provider_b"))
        assertEquals(2, engine.getActiveProviders().size)

        // Remote server retires Provider A
        val manifest3 = ProviderManifest(
            schemaVersion = 1,
            configVersion = 3,
            providers = listOf(
                ProviderConfig(
                    id = "provider_b",
                    name = "Provider B",
                    baseUrl = "https://b.com",
                    adapter = "rss"
                )
            )
        )

        engine.updateFromManifest(manifest3)
        assertNull("Provider A must be unloaded", engine.getAdapter("provider_a"))
        assertNotNull(engine.getAdapter("provider_b"))
        assertEquals(1, engine.getActiveProviders().size)
    }

    @Test
    fun testHealthMonitorStateTransitions() {
        val monitor = ProviderHealthMonitor()
        assertEquals(ProviderStatus.ENABLED, monitor.getStatus("prov_1"))

        // 2 errors -> DEGRADED
        monitor.recordError("prov_1")
        monitor.recordError("prov_1")
        assertEquals(ProviderStatus.DEGRADED, monitor.getStatus("prov_1"))

        // 5 errors -> UNAVAILABLE
        monitor.recordError("prov_1")
        monitor.recordError("prov_1")
        monitor.recordError("prov_1")
        assertEquals(ProviderStatus.UNAVAILABLE, monitor.getStatus("prov_1"))

        // Success -> recovered to ENABLED
        monitor.recordSuccess("prov_1")
        assertEquals(ProviderStatus.ENABLED, monitor.getStatus("prov_1"))
    }

    @Test
    fun testGetActiveProvidersAlphabeticalSorting() {
        val engine = ProviderEngine()
        val manifest = ProviderManifest(
            schemaVersion = 1,
            configVersion = 1,
            providers = listOf(
                ProviderConfig(id = "z_prov", name = "Zebra Provider", baseUrl = "https://z.com", adapter = "html_selector"),
                ProviderConfig(id = "a_prov", name = "Alpha Provider", baseUrl = "https://a.com", adapter = "html_selector"),
                ProviderConfig(id = "m_prov", name = "Mango Provider", baseUrl = "https://m.com", adapter = "html_selector")
            )
        )
        engine.updateFromManifest(manifest)
        val active = engine.getActiveProviders()
        assertEquals(3, active.size)
        assertEquals("Alpha Provider", active[0].name)
        assertEquals("Mango Provider", active[1].name)
        assertEquals("Zebra Provider", active[2].name)
    }

    private class FakeSearchAdapter(
        override val providerInfo: ProviderInfo,
        val delayMs: Long = 0L,
        val resultItems: List<VideoItem> = emptyList(),
        val shouldFail: Boolean = false
    ) : ProviderAdapter {
        override suspend fun getCategories(): Result<List<Category>> = Result.success(emptyList())
        override suspend fun getHomeFeed(page: Int): Result<FeedPage> = Result.success(FeedPage(emptyList(), page, false))
        override suspend fun search(query: String, page: Int): Result<FeedPage> {
            if (delayMs > 0) kotlinx.coroutines.delay(delayMs)
            if (shouldFail) return Result.failure(RuntimeException("Fake error"))
            return Result.success(FeedPage(resultItems, page, false))
        }
        override suspend fun getDetails(detailUrl: String): Result<VideoItem> = Result.failure(UnsupportedOperationException())
        override suspend fun getPlayableMedia(detailUrl: String): Result<List<MediaSource>> = Result.failure(UnsupportedOperationException())
        override suspend fun getRelatedContent(detailUrl: String): Result<List<VideoItem>> = Result.failure(UnsupportedOperationException())
    }

    @Test
    fun testSearchFlowEmitsProgressivelyWithoutWaitingForAllSites() = runBlocking {
        val engine = ProviderEngine()

        val fastItems = listOf(
            VideoItem(id = "fast_1", providerId = "fast_p", title = "Fast Video", detailUrl = "https://fast.com/v1")
        )
        val slowItems = listOf(
            VideoItem(id = "slow_1", providerId = "slow_p", title = "Slow Video", detailUrl = "https://slow.com/v1")
        )

        val fastAdapter = FakeSearchAdapter(
            providerInfo = ProviderInfo("fast_p", "Fast Provider", baseUrl = "https://fast.com", capabilities = listOf(ProviderCapability.SEARCH)),
            delayMs = 20L,
            resultItems = fastItems
        )
        val slowAdapter = FakeSearchAdapter(
            providerInfo = ProviderInfo("slow_p", "Slow Provider", baseUrl = "https://slow.com", capabilities = listOf(ProviderCapability.SEARCH)),
            delayMs = 200L,
            resultItems = slowItems
        )

        engine.registerAdapter(fastAdapter)
        engine.registerAdapter(slowAdapter)

        val events = mutableListOf<SearchEvent>()
        engine.searchFlow("test", providerId = null).collect { event ->
            events.add(event)
        }

        // Fast result MUST arrive before slow result
        val resultEvents = events.filterIsInstance<SearchEvent.ProviderResult>()
        assertEquals(2, resultEvents.size)
        assertEquals("fast_p", resultEvents[0].providerId)
        assertEquals("slow_p", resultEvents[1].providerId)

        val completedEvent = events.filterIsInstance<SearchEvent.Completed>().firstOrNull()
        assertNotNull(completedEvent)
        assertEquals(2, completedEvent!!.totalItemsFound)
    }

    @Test
    fun testSearchFlowPerProviderTimeoutDoesNotBlockOthers() = runBlocking {
        val engine = ProviderEngine()

        val goodItems = listOf(
            VideoItem(id = "good_1", providerId = "good_p", title = "Good Video", detailUrl = "https://good.com/v1")
        )

        val goodAdapter = FakeSearchAdapter(
            providerInfo = ProviderInfo("good_p", "Good Provider", baseUrl = "https://good.com", capabilities = listOf(ProviderCapability.SEARCH)),
            delayMs = 10L,
            resultItems = goodItems
        )
        val hangingAdapter = FakeSearchAdapter(
            providerInfo = ProviderInfo("hanging_p", "Hanging Provider", baseUrl = "https://hanging.com", capabilities = listOf(ProviderCapability.SEARCH)),
            delayMs = 2000L,
            resultItems = emptyList()
        )

        engine.registerAdapter(goodAdapter)
        engine.registerAdapter(hangingAdapter)

        val events = mutableListOf<SearchEvent>()
        engine.searchFlow("test", providerId = null, perProviderTimeoutMs = 50L).collect { event ->
            events.add(event)
        }

        val resultEvents = events.filterIsInstance<SearchEvent.ProviderResult>()
        assertEquals(1, resultEvents.size)
        assertEquals("good_p", resultEvents[0].providerId)

        val completedEvent = events.filterIsInstance<SearchEvent.Completed>().firstOrNull()
        assertNotNull(completedEvent)
        assertEquals(1, completedEvent!!.totalItemsFound)
    }
}
