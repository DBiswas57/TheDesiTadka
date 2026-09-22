package com.thedesitadka.app.storage

import com.thedesitadka.core.model.Category
import com.thedesitadka.core.model.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NavigationStateStoreTest {

    @Before
    fun setUp() {
        NavigationStateStore.clearAll()
    }

    @Test
    fun testSaveAndGetState() {
        val testKey = "provider_brazzpw_page"
        val sampleItems = listOf(
            VideoItem(id = "1", providerId = "brazzpw", title = "Video 1", detailUrl = "https://brazzpw.xyz/video/1/"),
            VideoItem(id = "2", providerId = "brazzpw", title = "Video 2", detailUrl = "https://brazzpw.xyz/video/2/")
        )
        val state = SavedListState(
            items = sampleItems,
            currentPage = 2,
            hasNextPage = true,
            firstVisibleItemIndex = 4,
            firstVisibleItemScrollOffset = 120
        )

        NavigationStateStore.save(testKey, state)
        val retrieved = NavigationStateStore.get(testKey)

        assertNotNull("Retrieved state must not be null", retrieved)
        assertEquals(2, retrieved?.items?.size)
        assertEquals("Video 1", retrieved?.items?.get(0)?.title)
        assertEquals(2, retrieved?.currentPage)
        assertTrue(retrieved?.hasNextPage == true)
        assertEquals(4, retrieved?.firstVisibleItemIndex)
        assertEquals(120, retrieved?.firstVisibleItemScrollOffset)
    }

    @Test
    fun testUpdateScrollPreservesItemsAndPagination() {
        val testKey = "category_milf"
        val sampleItems = listOf(
            VideoItem(id = "10", providerId = "brazzpw", title = "MILF 1", detailUrl = "https://brazzpw.xyz/video/10/")
        )
        NavigationStateStore.save(
            testKey,
            SavedListState(
                items = sampleItems,
                currentPage = 1,
                hasNextPage = true,
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 0
            )
        )

        // Update scroll position as user scrolls down
        NavigationStateStore.updateScroll(testKey, index = 8, offset = 45)

        val updated = NavigationStateStore.get(testKey)
        assertNotNull(updated)
        assertEquals(8, updated?.firstVisibleItemIndex)
        assertEquals(45, updated?.firstVisibleItemScrollOffset)
        assertEquals(1, updated?.items?.size)
        assertEquals("MILF 1", updated?.items?.get(0)?.title)
        assertEquals(1, updated?.currentPage)
    }

    @Test
    fun testUpdateItemsExistingAndNew() {
        val testKey = "multi_level_site"
        val item1 = VideoItem(id = "v1", providerId = "brazzpw", title = "Scene 1", detailUrl = "https://brazzpw.xyz/video/v1/")
        val item2 = VideoItem(id = "v2", providerId = "brazzpw", title = "Scene 2", detailUrl = "https://brazzpw.xyz/video/v2/")

        // 1. Initial items update creates state if not present
        NavigationStateStore.updateItems(
            key = testKey,
            items = listOf(item1),
            page = 1,
            hasNext = true
        )

        val state1 = NavigationStateStore.get(testKey)
        assertNotNull(state1)
        assertEquals(1, state1?.items?.size)
        assertEquals(1, state1?.currentPage)

        // 2. Next page appends items and updates page count
        NavigationStateStore.updateItems(
            key = testKey,
            items = listOf(item1, item2),
            page = 2,
            hasNext = false
        )

        val state2 = NavigationStateStore.get(testKey)
        assertNotNull(state2)
        assertEquals(2, state2?.items?.size)
        assertEquals(2, state2?.currentPage)
        assertFalse(state2?.hasNextPage == true)
    }

    @Test
    fun testClearKeyAndClearAll() {
        NavigationStateStore.save("key1", SavedListState())
        NavigationStateStore.save("key2", SavedListState())

        assertNotNull(NavigationStateStore.get("key1"))
        assertNotNull(NavigationStateStore.get("key2"))

        NavigationStateStore.clear("key1")
        assertNull(NavigationStateStore.get("key1"))
        assertNotNull(NavigationStateStore.get("key2"))

        NavigationStateStore.clearAll()
        assertNull(NavigationStateStore.get("key2"))
    }
}
