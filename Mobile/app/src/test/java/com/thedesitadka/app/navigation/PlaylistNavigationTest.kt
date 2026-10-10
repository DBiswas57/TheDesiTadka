package com.thedesitadka.app.navigation

import com.thedesitadka.core.model.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistNavigationTest {

    @Test
    fun testNormalizeUrl() {
        assertEquals("example.com/video/123", VideoPlaylistHelper.normalizeUrl("https://example.com/video/123/"))
        assertEquals("example.com/video/123", VideoPlaylistHelper.normalizeUrl("http://example.com/video/123"))
        assertEquals("example.com/video/123", VideoPlaylistHelper.normalizeUrl("//example.com/video/123/"))
        assertEquals("example.com/video/123", VideoPlaylistHelper.normalizeUrl("  https://EXAMPLE.COM/video/123/  "))
        assertEquals("", VideoPlaylistHelper.normalizeUrl(""))
    }

    @Test
    fun testIsSameVideo_ById() {
        val v1 = VideoItem(id = "item_1", title = "Movie A", detailUrl = "https://site1.com/a", providerId = "p1")
        val v2 = VideoItem(id = "item_1", title = "Different Title", detailUrl = "https://site2.com/b", providerId = "p1")
        assertTrue(VideoPlaylistHelper.isSameVideo(v1, v2))
    }

    @Test
    fun testIsSameVideo_ByNormalizedDetailUrl() {
        val v1 = VideoItem(id = "item_1", title = "Movie A", detailUrl = "https://site.com/movie-a/", providerId = "p1")
        val v2 = VideoItem(id = "item_2", title = "Movie A", detailUrl = "http://site.com/movie-a", providerId = "p1")
        assertTrue(VideoPlaylistHelper.isSameVideo(v1, v2))
    }

    @Test
    fun testIsSameVideo_ByMirrorDomainPath() {
        val v1 = VideoItem(id = "item_1", title = "Episode 1", detailUrl = "https://mirror1.org/watch/ep1", providerId = "p1")
        val v2 = VideoItem(id = "item_2", title = "Episode 1", detailUrl = "https://mirror2.net/watch/ep1", providerId = "p1")
        assertTrue(VideoPlaylistHelper.isSameVideo(v1, v2))
    }

    @Test
    fun testIsSameVideo_ByProviderAndTitle() {
        val v1 = VideoItem(id = "id_a", title = "Desi Tadka Special", detailUrl = "https://site.com/video/100", providerId = "provider_alpha")
        val v2 = VideoItem(id = "id_b", title = "desi tadka special  ", detailUrl = "https://site.com/stream/999", providerId = "provider_alpha")
        assertTrue(VideoPlaylistHelper.isSameVideo(v1, v2))
    }

    @Test
    fun testIsSameVideo_DifferentVideos() {
        val v1 = VideoItem(id = "id_1", title = "Movie One", detailUrl = "https://site.com/movie-one", providerId = "p1")
        val v2 = VideoItem(id = "id_2", title = "Movie Two", detailUrl = "https://site.com/movie-two", providerId = "p1")
        assertFalse(VideoPlaylistHelper.isSameVideo(v1, v2))
    }

    @Test
    fun testFindVideoIndex() {
        val item1 = VideoItem(id = "v1", title = "First", detailUrl = "https://site.com/1", providerId = "p1")
        val item2 = VideoItem(id = "v2", title = "Second", detailUrl = "https://site.com/2", providerId = "p1")
        val item3 = VideoItem(id = "v3", title = "Third", detailUrl = "https://site.com/3", providerId = "p1")
        val playlist = listOf(item1, item2, item3)

        // Find exact
        assertEquals(0, VideoPlaylistHelper.findVideoIndex(playlist, item1))
        assertEquals(1, VideoPlaylistHelper.findVideoIndex(playlist, item2))
        assertEquals(2, VideoPlaylistHelper.findVideoIndex(playlist, item3))

        // Find with different URL scheme and casing
        val queryItem2 = VideoItem(id = "", title = "Second", detailUrl = "http://SITE.COM/2/", providerId = "p1")
        assertEquals(1, VideoPlaylistHelper.findVideoIndex(playlist, queryItem2))

        // Non existent
        val unknown = VideoItem(id = "unknown", title = "Unknown", detailUrl = "https://site.com/999", providerId = "p1")
        assertEquals(-1, VideoPlaylistHelper.findVideoIndex(playlist, unknown))
    }

    @Test
    fun testPaginationContinuationState() {
        val navCtx = PlaylistNavigationContext(
            sourceType = PlaylistSourceType.CATEGORY,
            providerId = "test_provider",
            categoryUrl = "/category/bollywood",
            currentPage = 2,
            hasNextPage = true
        )

        assertEquals(PlaylistSourceType.CATEGORY, navCtx.sourceType)
        assertEquals("test_provider", navCtx.providerId)
        assertEquals("/category/bollywood", navCtx.categoryUrl)
        assertEquals(2, navCtx.currentPage)
        assertTrue(navCtx.hasNextPage)
    }
}
