package com.thedesitadka.provider

import com.thedesitadka.core.model.MediaSourceType
import com.thedesitadka.core.model.NavigationConfig
import com.thedesitadka.core.model.ProviderConfig
import com.thedesitadka.core.model.SelectorConfig
import com.thedesitadka.core.model.isCategoryUrl
import com.thedesitadka.provider.adapters.HtmlSelectorAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SixSitesPhaseWiseTest {

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
    // PHASE 1: netfapx.com
    // ==========================================
    @Test
    fun testPhase1Netfapx() {
        val baseDir = findEnglishSitesDir()
        val siteFolder = File(baseDir, "netfapx.com")
        if (!siteFolder.exists()) return

        val config = ProviderConfig(
            id = "netfapx",
            name = "Netfapx",
            baseUrl = "https://netfapx.com",
            adapter = "html_selector",
            navigation = NavigationConfig(
                home = "/",
                search = "/?s={query}",
                page = "/page/{page}/",
                categories = "/categories/"
            ),
            selectors = SelectorConfig(
                item = "article.pinbox, div.pinbox, article.post, article:not(.pfb-slider-group *)",
                title = "h2.entry-title a, h2 a, a[title]",
                thumbnail = "img",
                thumbnailAttr = "data-src, src",
                detailUrl = "h2.entry-title a, a[href*='netfapx.com/20'], a[href*='?cat='], a",
                duration = ".duration",
                player = "video, iframe",
                videoSource = "video source[src], video[src]"
            )
        )
        val adapter = HtmlSelectorAdapter(config)

        // 1. Content detection & item count
        val homeFile = File(siteFolder, "netfapx.com.html")
        if (homeFile.exists()) {
            val feed = adapter.parseListingHtml(homeFile.readText(Charsets.UTF_8), 1).getOrThrow()
            assertEquals("Netfapx must extract 15 articles on home page", 15, feed.items.size)
            assertTrue("All items must have non-blank titles", feed.items.all { it.title.isNotBlank() })
            assertTrue("All home items must be playable videos", feed.items.none { it.isCategory })
            assertTrue("All items must have valid thumbnails", feed.items.all { it.thumbnailUrl.startsWith("http") })
        }

        // 2. Pagination URL building
        val page2 = adapter.buildPagedUrl("https://netfapx.com", 2)
        assertEquals("https://netfapx.com/page/2/", page2)

        // 3. Media resolver
        val detailFile = File(siteFolder, "netfapx.com (2).html")
        if (detailFile.exists()) {
            val mediaRes = adapter.parsePlayableMediaHtml(detailFile.readText(Charsets.UTF_8), "https://netfapx.com/2021/07/not-over-you/")
            assertTrue("Netfapx detail media must parse successfully", mediaRes.isSuccess)
            val sources = mediaRes.getOrThrow()
            assertTrue("Sources must not be empty", sources.isNotEmpty())
            assertTrue("Direct MP4 stream from videos.netfapx.com", sources[0].url.contains("videos.netfapx.com"))
            assertEquals(MediaSourceType.PROGRESSIVE_MP4, sources[0].type)
            assertEquals("video/mp4", sources[0].mimeType)
            assertEquals("https://netfapx.com/", sources[0].headersRequired["Referer"])
        }
    }

    // ==========================================
    // PHASE 2: ok.porn
    // ==========================================
    @Test
    fun testPhase2OkPorn() {
        val baseDir = findEnglishSitesDir()
        val siteFolder = File(baseDir, "ok.porn")
        if (!siteFolder.exists()) return

        val config = ProviderConfig(
            id = "ok_porn",
            name = "OK.porn",
            baseUrl = "https://ok.porn",
            adapter = "html_selector",
            navigation = NavigationConfig(
                home = "/",
                search = "/search/{query}/",
                page = "/{page}/",
                categories = "/channels/"
            ),
            selectors = SelectorConfig(
                item = "div.list_video_wrapper div.item:not(.swiper-slide):not(.pfb-slider-group *):not(.page):not(.jump):not(.last):not(.next):not(.prev), div.item:not(.swiper-slide):not(.pfb-slider-group *):not(.page):not(.jump):not(.last):not(.next):not(.prev), div.thumb-bl:not(.item):not(.item *):not(.swiper-slide):not(.pfb-slider-group *):not(.page):not(.jump):not(.last):not(.next):not(.prev)",
                title = "a.title, [title], a",
                thumbnail = "img",
                thumbnailAttr = "data-src, src",
                detailUrl = "a[href*='/video/'], a[href*='/sites/'], a[href*='/models/'], a",
                duration = ".duration",
                player = "video",
                videoSource = "video source[src], video[src]"
            )
        )
        val adapter = HtmlSelectorAdapter(config)

        // 1. Content detection & item count
        val homeFile = File(siteFolder, "ok.porn.html")
        if (homeFile.exists()) {
            val feed = adapter.parseListingHtml(homeFile.readText(Charsets.UTF_8), 1).getOrThrow()
            assertTrue("OK.porn home page must extract at least 60 items", feed.items.size >= 60)
            assertTrue("Home videos must have /video/ detail URLs", feed.items.take(60).all { it.detailUrl.contains("/video/") })
            assertTrue("Home videos must not be classified as categories", feed.items.take(60).none { it.isCategory })
        }

        // 2. Channel & Model list taxonomy navigation
        val channelsFile = File(siteFolder, "ok.porn (4).html")
        if (channelsFile.exists()) {
            val chanFeed = adapter.parseListingHtml(channelsFile.readText(Charsets.UTF_8), 1).getOrThrow()
            assertEquals("Channels page must extract 50 channels", 50, chanFeed.items.size)
            assertTrue("All channels must be classified as categories", chanFeed.items.all { it.isCategory })
        }

        val modelsFile = File(siteFolder, "ok.porn (5).html")
        if (modelsFile.exists()) {
            val modelFeed = adapter.parseListingHtml(modelsFile.readText(Charsets.UTF_8), 1).getOrThrow()
            assertEquals("Models page must extract 50 models", 50, modelFeed.items.size)
            assertTrue("All models must be classified as categories", modelFeed.items.all { it.isCategory })
        }

        // 3. Pagination URL building
        val page2 = adapter.buildPagedUrl("https://ok.porn", 2)
        assertEquals("https://ok.porn/2/", page2)

        // 4. Media resolver
        val detailFile = File(siteFolder, "ok.porn (9).html")
        if (detailFile.exists()) {
            val mediaRes = adapter.parsePlayableMediaHtml(detailFile.readText(Charsets.UTF_8), "https://ok.porn/videos/787726/")
            assertTrue("OK.porn detail media must parse successfully", mediaRes.isSuccess)
            val sources = mediaRes.getOrThrow()
            assertTrue("Sources must not be empty", sources.isNotEmpty())
            assertTrue("Blob URLs must be filtered out", sources.none { it.url.startsWith("blob:") })
            assertEquals("720p", sources[0].quality)
            assertEquals(MediaSourceType.HLS, sources[0].type)
            assertEquals("application/x-mpegURL", sources[0].mimeType)
            assertEquals("https://ok.porn/", sources[0].headersRequired["Referer"])
        }
    }

    // ==========================================
    // PHASE 3: ok.xxx
    // ==========================================
    @Test
    fun testPhase3OkXxx() {
        val baseDir = findEnglishSitesDir()
        val siteFolder = File(baseDir, "ok.xxx")
        if (!siteFolder.exists()) return

        val config = ProviderConfig(
            id = "ok_xxx",
            name = "OK.xxx",
            baseUrl = "https://ok.xxx",
            adapter = "html_selector",
            navigation = NavigationConfig(
                home = "/",
                search = "/search/{query}/",
                page = "/{page}/",
                categories = "/channels/"
            ),
            selectors = SelectorConfig(
                item = "div.list_video_wrapper div.item:not(.swiper-slide):not(.pfb-slider-group *):not(.page):not(.jump):not(.last):not(.next):not(.prev), div.item:not(.swiper-slide):not(.pfb-slider-group *):not(.page):not(.jump):not(.last):not(.next):not(.prev), div.thumb-bl:not(.item):not(.item *):not(.swiper-slide):not(.pfb-slider-group *):not(.page):not(.jump):not(.last):not(.next):not(.prev)",
                title = "a.title, [title], a",
                thumbnail = "img",
                thumbnailAttr = "data-src, src",
                detailUrl = "a[href*='/video/'], a[href*='/sites/'], a[href*='/models/'], a",
                duration = ".duration",
                player = "video",
                videoSource = "video source[src], video[src]"
            )
        )
        val adapter = HtmlSelectorAdapter(config)

        // 1. Content detection & item count
        val homeFile = File(siteFolder, "ok.xxx.html")
        if (homeFile.exists()) {
            val feed = adapter.parseListingHtml(homeFile.readText(Charsets.UTF_8), 1).getOrThrow()
            assertTrue("OK.xxx home page must extract at least 60 items", feed.items.size >= 60)
            assertTrue("Home videos must have /video/ detail URLs", feed.items.take(60).all { it.detailUrl.contains("/video/") })
            assertTrue("Home videos must not be classified as categories", feed.items.take(60).none { it.isCategory })
        }

        // 2. Channel list taxonomy
        val chanFile = File(siteFolder, "ok.xxx (10).html")
        if (chanFile.exists()) {
            val chanFeed = adapter.parseListingHtml(chanFile.readText(Charsets.UTF_8), 1).getOrThrow()
            assertEquals("Channels page must extract 50 channels", 50, chanFeed.items.size)
            assertTrue("All channels must be classified as categories", chanFeed.items.all { it.isCategory })
        }

        // 3. Pagination URL building
        val page3 = adapter.buildPagedUrl("https://ok.xxx", 3)
        assertEquals("https://ok.xxx/3/", page3)

        // 4. Media resolver
        val detailFile = File(siteFolder, "ok.xxx (14).html")
        if (detailFile.exists()) {
            val mediaRes = adapter.parsePlayableMediaHtml(detailFile.readText(Charsets.UTF_8), "https://ok.xxx/videos/783534/")
            assertTrue("OK.xxx detail media must parse successfully", mediaRes.isSuccess)
            val sources = mediaRes.getOrThrow()
            assertTrue("Sources must not be empty", sources.isNotEmpty())
            assertTrue("Blob URLs must be filtered out", sources.none { it.url.startsWith("blob:") })
            assertEquals("720p", sources[0].quality)
            assertEquals(MediaSourceType.HLS, sources[0].type)
            assertEquals("https://ok.xxx/", sources[0].headersRequired["Referer"])
        }
    }

    // ==========================================
    // PHASE 4: perfectgirls.xxx
    // ==========================================
    @Test
    fun testPhase4PerfectGirls() {
        val baseDir = findEnglishSitesDir()
        val siteFolder = File(baseDir, "perfectgirls.xxx")
        if (!siteFolder.exists()) return

        val config = ProviderConfig(
            id = "perfectgirls",
            name = "PerfectGirls",
            baseUrl = "https://www.perfectgirls.xxx",
            adapter = "html_selector",
            navigation = NavigationConfig(
                home = "/",
                search = "/search/{query}/",
                page = "/{page}/",
                categories = "/channels/"
            ),
            selectors = SelectorConfig(
                item = "div.list_video_wrapper div.item:not(.swiper-slide):not(.pfb-slider-group *):not(.page):not(.jump):not(.last):not(.next):not(.prev), div.item:not(.swiper-slide):not(.pfb-slider-group *):not(.page):not(.jump):not(.last):not(.next):not(.prev), div.thumb-bl:not(.item):not(.item *):not(.swiper-slide):not(.pfb-slider-group *):not(.page):not(.jump):not(.last):not(.next):not(.prev)",
                title = "a.title, [title], a",
                thumbnail = "img",
                thumbnailAttr = "data-src, src",
                detailUrl = "a[href*='/video/'], a[href*='/videos/'], a[href*='/channels/'], a[href*='/pornstars/'], a",
                duration = ".duration",
                player = "video",
                videoSource = "video source[src], video[src]"
            )
        )
        val adapter = HtmlSelectorAdapter(config)

        // 1. Content detection & item count
        val homeFile = File(siteFolder, "perfectgirls.xxx.html")
        if (homeFile.exists()) {
            val feed = adapter.parseListingHtml(homeFile.readText(Charsets.UTF_8), 1).getOrThrow()
            assertTrue("PerfectGirls home page must extract at least 60 items", feed.items.size >= 60)
            assertTrue("Home videos must have /video/ detail URLs", feed.items.take(60).all { it.detailUrl.contains("/video/") })
            assertTrue("Home videos must not be classified as categories", feed.items.take(60).none { it.isCategory })
        }

        // 2. Pagination URL building
        val page2 = adapter.buildPagedUrl("https://www.perfectgirls.xxx", 2)
        assertEquals("https://www.perfectgirls.xxx/2/", page2)

        // 3. Media resolver
        val detailFile = File(siteFolder, "perfectgirls.xxx (12).html")
        if (detailFile.exists()) {
            val mediaRes = adapter.parsePlayableMediaHtml(detailFile.readText(Charsets.UTF_8), "https://www.perfectgirls.xxx/video/785678/")
            assertTrue("PerfectGirls detail media must parse successfully", mediaRes.isSuccess)
            val sources = mediaRes.getOrThrow()
            assertTrue("Sources must not be empty", sources.isNotEmpty())
            assertEquals("720p", sources[0].quality)
            assertEquals(MediaSourceType.HLS, sources[0].type)
            assertEquals("https://www.perfectgirls.xxx/", sources[0].headersRequired["Referer"])
        }
    }

    // ==========================================
    // PHASE 5: porn4days.pw
    // ==========================================
    @Test
    fun testPhase5Porn4Days() {
        val baseDir = findEnglishSitesDir()
        val siteFolder = File(baseDir, "porn4days.pw")
        if (!siteFolder.exists()) return

        val config = ProviderConfig(
            id = "porn4days",
            name = "Porn4Days",
            baseUrl = "https://porn4days.pw",
            adapter = "html_selector",
            navigation = NavigationConfig(
                home = "/newest",
                search = "/search/{query}",
                page = "/newest/page{page}/",
                categories = "/paysitelist"
            ),
            selectors = SelectorConfig(
                item = "div.card.video-card:not(.slick-slider *):not(.slick-slide):not(.slick-slide *)",
                title = "img[alt], a[title], h5, .card-title, a",
                thumbnail = "img.card-img-top, img",
                thumbnailAttr = "src, data-src",
                detailUrl = "a[href*='video/'], a",
                duration = ".duration, .badge, .time",
                player = "video",
                videoSource = "video source[src], video[src]"
            )
        )
        val adapter = HtmlSelectorAdapter(config)

        // 1. Content detection & slick carousel exclusion
        val pageFile = File(siteFolder, "porn4days.pw (1).html")
        if (pageFile.exists()) {
            val feed = adapter.parseListingHtml(pageFile.readText(Charsets.UTF_8), 1).getOrThrow()
            assertEquals("Porn4Days must extract exactly 24 content items, excluding 46 carousel items", 24, feed.items.size)
            assertTrue("All items must have valid titles", feed.items.all { it.title.isNotBlank() })
            assertTrue("All items must be playable videos", feed.items.all { !it.isCategory && it.detailUrl.contains("video/") })
            assertEquals("No duplicates allowed", 24, feed.items.map { it.detailUrl }.distinct().size)
        }

        // 2. Categories / Paysite shortcut detection
        val paysiteFile = File(siteFolder, "porn4days.pw (7).html")
        if (paysiteFile.exists()) {
            val categories = adapter.parseCategoriesHtml(paysiteFile.readText(Charsets.UTF_8))
            assertTrue("Paysitelist must extract studio/paysite shortcuts", categories.isNotEmpty())
            assertTrue("Categories must have non-blank names", categories.all { it.name.isNotBlank() })
            assertTrue("All paysite shortcuts should be classified as category URLs", categories.all { isCategoryUrl(it.url) })
        }

        // 3. Media resolver
        val detailFile = File(siteFolder, "porn4days.pw (9).html")
        if (detailFile.exists()) {
            val mediaRes = adapter.parsePlayableMediaHtml(detailFile.readText(Charsets.UTF_8), "https://porn4days.pw/video/nika-venom-nika-venoms-first-time-having-anal-sex-onlyfans")
            assertTrue("Porn4Days detail media must parse successfully", mediaRes.isSuccess)
            val sources = mediaRes.getOrThrow()
            assertTrue("Sources must not be empty", sources.isNotEmpty())
            assertTrue("Source URL must be on iceyfile CDN", sources[0].url.contains("iceyfile.net"))
            assertEquals(MediaSourceType.PROGRESSIVE_MP4, sources[0].type)
            assertEquals("video/mp4", sources[0].mimeType)
            assertEquals("https://porn4days.pw/", sources[0].headersRequired["Referer"])
        }
    }

    // ==========================================
    // PHASE 6: pornhat.com
    // ==========================================
    @Test
    fun testPhase6PornHat() {
        val baseDir = findEnglishSitesDir()
        val siteFolder = File(baseDir, "pornhat.com")
        if (!siteFolder.exists()) return

        val config = ProviderConfig(
            id = "pornhat",
            name = "PornHat",
            baseUrl = "https://www.pornhat.com",
            adapter = "html_selector",
            navigation = NavigationConfig(
                home = "/",
                search = "/search/{query}/",
                page = "/latest-updates/{page}/",
                categories = "/channels/"
            ),
            selectors = SelectorConfig(
                item = "div.list_video_wrapper div.item:not(.swiper-slide):not(.pfb-slider-group *):not(.page):not(.jump):not(.last):not(.next):not(.prev), div.item:not(.swiper-slide):not(.pfb-slider-group *):not(.page):not(.jump):not(.last):not(.next):not(.prev), div.thumb-bl:not(.item):not(.item *):not(.swiper-slide):not(.pfb-slider-group *):not(.page):not(.jump):not(.last):not(.next):not(.prev)",
                title = "a.title, [title], a",
                thumbnail = "img",
                thumbnailAttr = "data-src, src",
                detailUrl = "a[href*='/video/'], a[href*='/videos/'], a[href*='/channels/'], a",
                duration = ".duration",
                player = "video",
                videoSource = "video source[src], video[src]"
            )
        )
        val adapter = HtmlSelectorAdapter(config)

        // 1. Content detection & item count
        val homeFile = File(siteFolder, "pornhat.com.html")
        if (homeFile.exists()) {
            val feed = adapter.parseListingHtml(homeFile.readText(Charsets.UTF_8), 1).getOrThrow()
            assertTrue("PornHat home page must extract at least 60 items", feed.items.size >= 60)
            assertTrue("Home videos must have /video/ detail URLs", feed.items.take(60).all { it.detailUrl.contains("/video/") })
            assertTrue("Home videos must not be classified as categories", feed.items.take(60).none { it.isCategory })
        }

        // 2. Pagination URL building
        val page2 = adapter.buildPagedUrl("https://www.pornhat.com", 2)
        assertEquals("https://www.pornhat.com/2/", page2)

        // 3. Media resolver
        val detailFile = File(siteFolder, "pornhat.com (3).html")
        if (detailFile.exists()) {
            val mediaRes = adapter.parsePlayableMediaHtml(detailFile.readText(Charsets.UTF_8), "https://www.pornhat.com/video/789116/reagan-foxx-passionate-kiss-on-top/")
            assertTrue("PornHat detail media must parse successfully", mediaRes.isSuccess)
            val sources = mediaRes.getOrThrow()
            assertTrue("Sources must not be empty", sources.isNotEmpty())
            assertEquals("720p", sources[0].quality)
            assertEquals(MediaSourceType.HLS, sources[0].type)
            assertEquals("https://www.pornhat.com/", sources[0].headersRequired["Referer"])
        }
    }
}
