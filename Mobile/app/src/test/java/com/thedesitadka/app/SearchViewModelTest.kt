package com.thedesitadka.app

import com.thedesitadka.app.ui.screens.SearchViewModel
import com.thedesitadka.core.model.Category
import com.thedesitadka.core.model.FeedPage
import com.thedesitadka.core.model.MediaSource
import com.thedesitadka.core.model.ProviderCapability
import com.thedesitadka.core.model.ProviderInfo
import com.thedesitadka.core.model.VideoItem
import com.thedesitadka.provider.ProviderAdapter
import com.thedesitadka.provider.ProviderEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class MockSearchAdapter(
        override val providerInfo: ProviderInfo,
        val delayMs: Long = 0L,
        val items: List<VideoItem> = emptyList()
    ) : ProviderAdapter {
        override suspend fun getCategories(): Result<List<Category>> = Result.success(emptyList())
        override suspend fun getHomeFeed(page: Int): Result<FeedPage> = Result.success(FeedPage(emptyList(), page, false))
        override suspend fun search(query: String, page: Int): Result<FeedPage> {
            if (delayMs > 0) kotlinx.coroutines.delay(delayMs)
            return Result.success(FeedPage(items, page, false))
        }
        override suspend fun getDetails(detailUrl: String): Result<VideoItem> = Result.failure(UnsupportedOperationException())
        override suspend fun getPlayableMedia(detailUrl: String): Result<List<MediaSource>> = Result.failure(UnsupportedOperationException())
        override suspend fun getRelatedContent(detailUrl: String): Result<List<VideoItem>> = Result.failure(UnsupportedOperationException())
    }

    @Test
    fun testProgressiveResultsDisplayAsSitesLoad() = runTest {
        val engine = ProviderEngine(ioDispatcher = testDispatcher)
        val fastItems = listOf(
            VideoItem(id = "f1", providerId = "fast_p", title = "Fast Video 1", detailUrl = "https://fast.com/1")
        )
        val slowItems = listOf(
            VideoItem(id = "s1", providerId = "slow_p", title = "Slow Video 1", detailUrl = "https://slow.com/1")
        )

        val fastAdapter = MockSearchAdapter(
            ProviderInfo(id = "fast_p", name = "Fast", baseUrl = "https://fast.com", capabilities = listOf(ProviderCapability.SEARCH)),
            delayMs = 50L,
            items = fastItems
        )
        val slowAdapter = MockSearchAdapter(
            ProviderInfo(id = "slow_p", name = "Slow", baseUrl = "https://slow.com", capabilities = listOf(ProviderCapability.SEARCH)),
            delayMs = 500L,
            items = slowItems
        )

        engine.registerAdapter(fastAdapter)
        engine.registerAdapter(slowAdapter)

        val viewModel = SearchViewModel()
        viewModel.onQueryChanged("desi", engine)

        // Advance past debounce (300ms)
        advanceTimeBy(301L)
        assertTrue(viewModel.isLoading)
        assertEquals(0, viewModel.searchResults.size)

        // Advance to when fast site finishes (300 + 60 = 360ms)
        advanceTimeBy(60L)
        // Fast site results MUST be visible immediately before slow site finishes!
        assertEquals(1, viewModel.searchResults.size)
        assertEquals("Fast Video 1", viewModel.searchResults[0].title)
        assertTrue("Still loading other sites in background", viewModel.isLoading)

        // Advance until slow site completes
        advanceUntilIdle()
        assertFalse("All sites completed", viewModel.isLoading)
        assertEquals(2, viewModel.searchResults.size)
        assertEquals("Slow Video 1", viewModel.searchResults[1].title)
    }

    @Test
    fun testShortQueryClearsResultsImmediately() = runTest {
        val engine = ProviderEngine(ioDispatcher = testDispatcher)
        val viewModel = SearchViewModel()
        viewModel.searchResults.add(VideoItem(id = "1", providerId = "p", title = "T", detailUrl = "u"))
        viewModel.onQueryChanged("a", engine)

        assertEquals(0, viewModel.searchResults.size)
        assertFalse(viewModel.isLoading)
    }
}
