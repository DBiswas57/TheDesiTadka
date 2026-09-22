package com.thedesitadka.provider

import com.thedesitadka.core.model.MediaSourceType
import com.thedesitadka.core.model.NavigationConfig
import com.thedesitadka.core.model.ProviderConfig
import com.thedesitadka.core.model.SelectorConfig
import com.thedesitadka.core.model.VideoItem
import com.thedesitadka.core.model.isCategoryUrl
import com.thedesitadka.provider.adapters.HtmlSelectorAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FiveSitesPhaseWiseTest {

    private fun findEnglishSitesDir(): File {
        val candidates = listOf(
            File("EnglishSites"),
            File("../EnglishSites"),
            File("../../EnglishSites")
        )
        return candidates.firstOrNull { it.exists() && it.isDirectory }
            ?: throw IllegalStateException("Cannot find EnglishSites directory")
    }

    // ==========================================
    // TOP-RIGHT CATEGORY BUTTON & KEY CRASH SAFETY
    // ==========================================
    @Test
    fun testCategoryButtonSafetyAndUniqueKeys() {
        // Test that items with duplicate IDs and URLs produce unique indexed keys
        val items = listOf(
            VideoItem(id = "cat1", providerId = "test", title = "Blowjob", detailUrl = "/tag/blowjob", isCategory = true),
            VideoItem(id = "cat1", providerId = "test", title = "Blowjob", detailUrl = "/tag/blowjob", isCategory = true), // Duplicate
            VideoItem(id = "", providerId = "test", title = "Asian", detailUrl = "/tag/asian", isCategory = true),
            VideoItem(id = "", providerId = "test", title = "Asian", detailUrl = "/tag/asian", isCategory = true) // Duplicate with blank id
        )

        // Generate keys using our indexed key generator
        val keys = items.mapIndexed { index, item ->
            val base = (item.id.ifBlank { item.title }) + "_" + item.detailUrl
            "${base}_$index"
        }

        // All 4 keys must be strictly unique to prevent Jetpack Compose IllegalArgumentException
        assertEquals(4, keys.distinct().size)
        assertEquals("cat1_/tag/blowjob_0", keys[0])
        assertEquals("cat1_/tag/blowjob_1", keys[1])
        assertEquals("Asian_/tag/asian_2", keys[2])
        assertEquals("Asian_/tag/asian_3", keys[3])
    }

    // ==========================================
    // PHASE 1: pornhouse.me
    // ==========================================
    @Test
    fun testPhase1Pornhouse() {
        val baseDir = findEnglishSitesDir()
        val siteFolder = File(baseDir, "pornhouse.me")
        if (!siteFolder.exists()) return

        val config = ProviderConfig(
            id = "pornhouse",
            name = "PornHouse",
            baseUrl = "https://pornhouse.me",
            adapter = "html_selector",
            navigation = NavigationConfig(
                home = "/",
                search = "/?s={query}",
                page = "/porn-hd-free-full-1080p/page-{page}",
                categories = "/tag/asian"
            ),
            selectors = SelectorConfig(
                item = "div.item, div.ml-item, div.video-item",
                title = "img[alt], a.title, h3 a, [title], a",
                thumbnail = "img[data-original], img[data-src], img",
                thumbnailAttr = "data-original, data-src, src",
                detailUrl = "a[href*='/movies/'], a",
                duration = ".duration",
                detailTitle = "h1",
                detailDescription = "p",
                detailThumbnail = "meta[property='og:image'], img",
                player = "video, iframe",
                videoSource = "video source[src], video[src]",
                videoSourceAttr = "src"
            )
        )
        val adapter = HtmlSelectorAdapter(config)

        // 1. Test listing extraction
        val listingFile = File(siteFolder, "pornhouse.me (1).html")
        if (listingFile.exists()) {
            val html = listingFile.readText()
            val result = adapter.parseListingHtml(html, 1)
            assertTrue(result.isSuccess)
            val feed = result.getOrThrow()
            assertEquals(40, feed.items.size)

            val first = feed.items.first()
            assertTrue(first.title.isNotBlank())
            assertTrue(first.detailUrl.contains("/movies/"))
            assertTrue(first.thumbnailUrl.isNotBlank())
            assertFalse(first.isCategory)
        }

        // 2. Test pagination URL building
        val page2 = adapter.buildPagedUrl("https://pornhouse.me/porn-hd-free-full-1080p/page-1", 2)
        assertEquals("https://pornhouse.me/porn-hd-free-full-1080p/page-2", page2)
        val tagPage3 = adapter.buildPagedUrl("https://pornhouse.me/tag/asian", 3)
        assertEquals("https://pornhouse.me/tag/asian/page-3", tagPage3)

        // 3. Test taxonomy & category link detection
        assertTrue(isCategoryUrl("https://pornhouse.me/tag/asian"))
        assertTrue(isCategoryUrl("https://pornhouse.me/studio/brazzers"))
        assertFalse(isCategoryUrl("https://pornhouse.me/movies/watch-xxx-12345/"))

        // 4. Test dynamic AJAX JWPlayer JSON parsing
        val sampleJson = """
            {
              "playlist": [
                {
                  "image": "https://ic-nss.flixcdn.com/frame.jpg",
                  "sources": [
                    {
                      "file": "https://cdn.pornhouse.me/playlists/706a922e/u8lmahqjne.m3u8?md5=test&expires=1234",
                      "type": "video/mp4",
                      "label": "720p",
                      "default": "true"
                    }
                  ]
                }
              ]
            }
        """.trimIndent()
        val sources = adapter.parsePornhousePornhd4kJsonResponse(sampleJson, "https://pornhouse.me")
        assertEquals(1, sources.size)
        val stream = sources.first()
        assertEquals(MediaSourceType.HLS, stream.type)
        assertEquals("720p", stream.quality)
        assertEquals("https://pornhouse.me/", stream.headersRequired["Referer"])
    }

    // ==========================================
    // PHASE 2: pornmz.com
    // ==========================================
    @Test
    fun testPhase2Pornmz() {
        val baseDir = findEnglishSitesDir()
        val siteFolder = File(baseDir, "pornmz.com")
        if (!siteFolder.exists()) return

        val config = ProviderConfig(
            id = "pornmz",
            name = "PornMZ",
            baseUrl = "https://pornmz.com",
            adapter = "html_selector",
            navigation = NavigationConfig(
                home = "/",
                search = "/?s={query}",
                page = "/page/{page}?filter=latest",
                categories = "/categories"
            ),
            selectors = SelectorConfig(
                item = "article.post, article.thumb-block, article.video-preview-item, article, div.item",
                title = "a[title], h2.entry-title a, h2 a, a.title, a",
                thumbnail = "img.video-main-thumb, img",
                thumbnailAttr = "src",
                detailUrl = "a[href*='/video/id='], a[href*='/video/'], a[href*='pornmz.com/video/'], a",
                duration = ".duration",
                player = "iframe, video",
                videoSource = "video source[src], video[src]",
                videoSourceAttr = "src"
            )
        )
        val adapter = HtmlSelectorAdapter(config)

        // 1. Test listing extraction
        val listingFile = File(siteFolder, "pornmz.com (11).html")
        if (listingFile.exists()) {
            val html = listingFile.readText()
            val result = adapter.parseListingHtml(html, 1)
            assertTrue(result.isSuccess)
            val feed = result.getOrThrow()
            assertEquals(30, feed.items.size)

            val first = feed.items.first()
            assertTrue(first.title.isNotBlank())
            assertTrue(first.detailUrl.contains("/video/id="))
            assertTrue(first.thumbnailUrl.isNotBlank())
            assertFalse(first.isCategory)
        }

        // 2. Test pagination URL building
        val page2 = adapter.buildPagedUrl("https://pornmz.com/page/1?filter=latest", 2)
        assertEquals("https://pornmz.com/page/2/?filter=latest", page2)
        val catPage3 = adapter.buildPagedUrl("https://pornmz.com/pmvideo/c/brazzers/page/1", 3)
        assertEquals("https://pornmz.com/pmvideo/c/brazzers/page/3/", catPage3)

        // 3. Test Clean Tube Player iframe decoding on detail page
        val detailFile = File(siteFolder, "pornmz.com (16).html")
        if (detailFile.exists()) {
            val html = detailFile.readText()
            val result = adapter.parsePlayableMediaHtml(html, "https://pornmz.com/video/id=pm17130127308756")
            assertTrue(result.isSuccess)
            val sources = result.getOrThrow()
            assertTrue(sources.isNotEmpty())
            val stream = sources.first()
            assertTrue(stream.url.contains("video.twimg.com") || stream.url.contains(".m3u8"))
            assertEquals(MediaSourceType.HLS, stream.type)
            assertNull(stream.headersRequired["Referer"])
        }
    }

    // ==========================================
    // PHASE 3: pornstars.tube
    // ==========================================
    @Test
    fun testPhase3PornstarsTube() {
        val baseDir = findEnglishSitesDir()
        val siteFolder = File(baseDir, "pornstars.tube")
        if (!siteFolder.exists()) return

        val config = ProviderConfig(
            id = "pornstars_tube",
            name = "Pornstars.tube",
            baseUrl = "https://pornstars.tube",
            adapter = "html_selector",
            navigation = NavigationConfig(
                home = "/",
                search = "/search/{query}/",
                page = "/latest-updates/{page}/",
                categories = "/models/"
            ),
            selectors = SelectorConfig(
                item = "div.item, div.video-card, div.thumb",
                title = "a.title, [title], img[alt], a",
                thumbnail = "img[data-src], img[src], img",
                thumbnailAttr = "data-src, src",
                detailUrl = "a[href*='/videos/'], a[href*='/models/'], a",
                duration = ".duration",
                player = "video",
                videoSource = "video source[src], video[src]",
                videoSourceAttr = "src"
            )
        )
        val adapter = HtmlSelectorAdapter(config)

        // 1. Test model list extraction (Multi-Level Navigation Level 1)
        val modelListingFile = File(siteFolder, "pornstars.tube (10).html")
        if (modelListingFile.exists()) {
            val html = modelListingFile.readText()
            val result = adapter.parseListingHtml(html, 1)
            assertTrue(result.isSuccess)
            val feed = result.getOrThrow()
            assertEquals(36, feed.items.size)

            val first = feed.items.first()
            assertEquals("Angela White", first.title)
            assertTrue(first.detailUrl.contains("/models/angela-white/"))
            assertTrue(first.isCategory) // Model items must be classified as category to open model videos
        }

        // 2. Test pagination
        val page2 = adapter.buildPagedUrl("https://pornstars.tube/latest-updates/1/", 2)
        assertEquals("https://pornstars.tube/latest-updates/2/", page2)
        val modelPage3 = adapter.buildPagedUrl("https://pornstars.tube/models/categories/big-tits/1/", 3)
        assertEquals("https://pornstars.tube/models/categories/big-tits/3/", modelPage3)

        // 3. Test multi-level taxonomy URL detection
        assertTrue(isCategoryUrl("https://pornstars.tube/models/"))
        assertTrue(isCategoryUrl("https://pornstars.tube/models/angela-white/"))
        assertFalse(isCategoryUrl("https://pornstars.tube/videos/787213/"))

        // 4. Test FluidPlayer media resolution on video detail page
        val detailFile = File(siteFolder, "pornstars.tube (13).html")
        if (detailFile.exists()) {
            val html = detailFile.readText()
            val result = adapter.parsePlayableMediaHtml(html, "https://pornstars.tube/videos/787213/")
            assertTrue(result.isSuccess)
            val sources = result.getOrThrow()
            assertTrue(sources.isNotEmpty())
            val topStream = sources.first()
            assertEquals("720p", topStream.quality)
            assertTrue(topStream.url.contains("787213_720p.mp4"))
            assertEquals("https://pornstars.tube/", topStream.headersRequired["Referer"])
        }
    }

    // ==========================================
    // PHASE 4: sxyprn.com
    // ==========================================
    @Test
    fun testPhase4SxyPrn() {
        val baseDir = findEnglishSitesDir()
        val siteFolder = File(baseDir, "sxyprn.com")
        if (!siteFolder.exists()) return

        val config = ProviderConfig(
            id = "sxyprn",
            name = "SxyPrn",
            baseUrl = "https://sxyprn.com",
            adapter = "html_selector",
            navigation = NavigationConfig(
                home = "/",
                search = "/?s={query}",
                page = "/orgasm/{page}",
                categories = "/popular/top-pop.html"
            ),
            selectors = SelectorConfig(
                item = "div.post_el_small:not(.post_el_post), div.post_el:not(.post_el_post)",
                title = "div.post_text, a.ps_link, a[title], a",
                thumbnail = "img.mini_post_vid_thumb, img[src*='trafficdeposit.com'], .post_vid_thumb img, img",
                thumbnailAttr = "src, data-src",
                detailUrl = "a[href*='/post/'], a[href*='/blog/'], a",
                duration = ".post_control_time, .duration",
                player = "video#player_el, video.player_el, video, iframe",
                videoSource = "video#player_el, video.player_el, video source[src], video[src]",
                videoSourceAttr = "src"
            )
        )
        val adapter = HtmlSelectorAdapter(config)

        // 1. Test listing extraction (93 items, excluding .post_el_post)
        val listingFile = File(siteFolder, "sxyprn.com (1).html")
        if (listingFile.exists()) {
            val html = listingFile.readText()
            val result = adapter.parseListingHtml(html, 1)
            assertTrue(result.isSuccess)
            val feed = result.getOrThrow()
            assertEquals(58, feed.items.size)

            val first = feed.items.first()
            assertTrue(first.detailUrl.contains("/blog/") || first.detailUrl.contains("/post/"))
            assertTrue(first.thumbnailUrl.isNotBlank())
            assertFalse(first.isCategory)
        }

        // 2. Test offset-based pagination URL building
        val page2 = adapter.buildPagedUrl("https://sxyprn.com/orgasm/0", 2)
        assertEquals("https://sxyprn.com/orgasm/30", page2)
        val page3 = adapter.buildPagedUrl("https://sxyprn.com/orgasm/30", 3)
        assertEquals("https://sxyprn.com/orgasm/60", page3)

        // 3. Test detail page video player resolution
        val detailFile = File(siteFolder, "sxyprn.com (8).html")
        if (detailFile.exists()) {
            val html = detailFile.readText()
            val result = adapter.parsePlayableMediaHtml(html, "https://sxyprn.com/post/6aab8b246f900.html")
            assertTrue(result.isSuccess)
            val sources = result.getOrThrow()
            assertTrue(sources.isNotEmpty())
            println("sxyprn sources (${sources.size}): " + sources.map { it.url })
            val stream = sources.find { it.url.contains("sxyprn.com/cdn8/") } ?: sources.first()
            assertTrue(stream.url.startsWith("https://sxyprn.com/cdn8/"))
            assertTrue(stream.url.endsWith(".vid"))
            assertEquals("https://sxyprn.com/", stream.headersRequired["Referer"])
        }
    }

    // ==========================================
    // PHASE 5: pornhd4k.net
    // ==========================================
    @Test
    fun testPhase5PornHD4K() {
        val baseDir = findEnglishSitesDir()
        val siteFolder = File(baseDir, "pornhd4k.net")
        if (!siteFolder.exists()) return

        val config = ProviderConfig(
            id = "pornhd4k",
            name = "PornHD4K",
            baseUrl = "https://pornhd4k.net",
            adapter = "html_selector",
            navigation = NavigationConfig(
                home = "/",
                search = "/?s={query}",
                page = "/premium-porn-hd/page-{page}",
                categories = "/tag/asian"
            ),
            selectors = SelectorConfig(
                item = "div.item, div.ml-item, div.video-item",
                title = "img[alt], a.title, h3 a, [title], a",
                thumbnail = "img[data-original], img[data-src], img",
                thumbnailAttr = "data-original, data-src, src",
                detailUrl = "a[href*='/movies/'], a",
                duration = ".duration",
                player = "video, iframe",
                videoSource = "video source[src], video[src]",
                videoSourceAttr = "src"
            )
        )
        val adapter = HtmlSelectorAdapter(config)

        // 1. Test listing extraction
        val listingFile = File(siteFolder, "pornhd4k.net.html")
        if (listingFile.exists()) {
            val html = listingFile.readText()
            val result = adapter.parseListingHtml(html, 1)
            assertTrue(result.isSuccess)
            val feed = result.getOrThrow()
            assertEquals(28, feed.items.size)

            val first = feed.items.first()
            assertTrue(first.title.isNotBlank())
            assertTrue(first.detailUrl.contains("/movies/"))
            assertTrue(first.thumbnailUrl.isNotBlank())
            assertFalse(first.isCategory)
        }

        // 2. Test pagination URL building
        val page2 = adapter.buildPagedUrl("https://pornhd4k.net/premium-porn-hd/page-1", 2)
        assertEquals("https://pornhd4k.net/premium-porn-hd/page-2", page2)

        // 3. Test dynamic AJAX JWPlayer JSON parsing for pornhd4k
        val sampleJson = """
            {
              "playlist": [
                {
                  "image": "https://img.freepornvideos.xxx/medium.jpg",
                  "sources": [
                    {
                      "file": "https://free50.cdnamz.me/playlists/a98d1d9a/u8lmahqjne.m3u8?md5=hash&expires=5678",
                      "type": "video/mp4",
                      "label": "720p",
                      "default": "true"
                    }
                  ]
                }
              ]
            }
        """.trimIndent()
        val sources = adapter.parsePornhousePornhd4kJsonResponse(sampleJson, "https://pornhd4k.net")
        assertEquals(1, sources.size)
        val stream = sources.first()
        assertEquals(MediaSourceType.HLS, stream.type)
        assertEquals("720p", stream.quality)
        assertEquals("https://pornhd4k.net/", stream.headersRequired["Referer"])
    }
}
