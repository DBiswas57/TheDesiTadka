package com.thedesitadka.provider

import com.thedesitadka.core.model.MediaSourceType
import com.thedesitadka.core.model.NavigationConfig
import com.thedesitadka.core.model.ProviderCapability
import com.thedesitadka.core.model.ProviderConfig
import com.thedesitadka.core.model.SelectorConfig
import com.thedesitadka.provider.adapters.HtmlSelectorAdapter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NineRecommendedSitesTest {

    private fun createAdapter(id: String, baseUrl: String, nav: NavigationConfig, selectors: SelectorConfig): HtmlSelectorAdapter {
        val config = ProviderConfig(
            id = id,
            name = id.replaceFirstChar { it.uppercase() },
            enabled = true,
            baseUrl = baseUrl,
            adapter = "html_selector",
            capabilities = listOf(
                ProviderCapability.HOME,
                ProviderCapability.CATEGORY,
                ProviderCapability.SEARCH,
                ProviderCapability.DETAILS,
                ProviderCapability.STREAM,
                ProviderCapability.DOWNLOAD
            ),
            navigation = nav,
            selectors = selectors
        )
        return HtmlSelectorAdapter(config)
    }

    @Test
    fun testYouJizzListingAndStreamResolution() = runBlocking {
        val adapter = createAdapter(
            id = "youjizz",
            baseUrl = "https://www.youjizz.com",
            nav = NavigationConfig(home = "/", page = "/most-popular/{page}.html", search = "/search/{query}-{page}.html", categories = "/categories"),
            selectors = SelectorConfig(
                item = "div.video-thumb, div.thumb",
                title = "a.title, a[title], .video-title",
                thumbnail = "img.lazy, img[data-original], img",
                thumbnailAttr = "data-original, src",
                detailUrl = "a[href*='/videos/'], a.thumb",
                duration = "span.time, .duration"
            )
        )

        // 1. Pagination
        val p2 = adapter.buildPagedUrl("https://www.youjizz.com/most-popular/1.html", 2)
        assertEquals("https://www.youjizz.com/most-popular/2.html", p2)

        // 2. Listing parsing
        val sampleHtml = """
            <div class="video-thumb">
                <a href="/videos/family-tradition-44240591.html" class="thumb">
                    <img class="img-responsive lazy" data-original="//cdne-pics.youjizz.com/pic123.jpg" alt="Family Tradition Video" />
                    <span class="time">15:20</span>
                    <span class="video-title">Family Tradition</span>
                </a>
            </div>
        """.trimIndent()
        val feed = adapter.parseListingHtml(sampleHtml, 1).getOrThrow()
        assertEquals(1, feed.items.size)
        val item = feed.items[0]
        assertEquals("Family Tradition", item.title)
        assertTrue(item.detailUrl.contains("/videos/family-tradition-44240591.html"))
        assertEquals("https://cdne-pics.youjizz.com/pic123.jpg", item.thumbnailUrl)

        // 3. Media stream parsing (dataEncodings array)
        val detailHtml = """
            <html>
            <script>
            var dataEncodings = [{"quality":"360","filename":"\/\/cdne-mobile.youjizz.com\/videos\/test-360-h264.mp4?hash=123","name":"360p"},{"quality":"720","filename":"\/\/cdne-mobile.youjizz.com\/videos\/test-720-h264.mp4?hash=456","name":"720p"}];
            </script>
            </html>
        """.trimIndent()
        val mediaResult = adapter.parsePlayableMediaHtml(detailHtml, "https://www.youjizz.com/videos/family-tradition-44240591.html")
        assertTrue("Media parsing should succeed", mediaResult.isSuccess)
        val media = mediaResult.getOrThrow()
        assertTrue("Must extract at least one media source", media.isNotEmpty())
        assertEquals("https://cdne-mobile.youjizz.com/videos/test-720-h264.mp4?hash=456", media[0].url)
        assertEquals(MediaSourceType.PROGRESSIVE_MP4, media[0].type)
    }

    @Test
    fun testTNAFlixListingAndDirectStream() = runBlocking {
        val adapter = createAdapter(
            id = "tnaflix",
            baseUrl = "https://www.tnaflix.com",
            nav = NavigationConfig(home = "/", page = "/featured/{page}", search = "/search.php?what={query}", categories = "/categories"),
            selectors = SelectorConfig(
                item = "div.video-list > div, div[data-vid], a.video-thumb",
                title = "a[title], img[alt], a.video-thumb",
                thumbnail = "img[data-src], img",
                thumbnailAttr = "data-src, src",
                detailUrl = "a.video-thumb, a[href*='/video']",
                duration = "div.video-duration, .thumb-icon",
                detailTitle = "h1",
                videoSource = "video source[src]"
            )
        )

        // 1. Pagination
        val p2 = adapter.buildPagedUrl("https://www.tnaflix.com/featured/1", 2)
        assertEquals("https://www.tnaflix.com/featured/2/", p2)

        // 2. Listing parsing
        val sampleHtml = """
            <div class="row video-list">
                <div class="col-xs-6" data-vid="7140753">
                    <a class="thumb video-thumb" href="https://www.tnaflix.com/amateur-porn/Escalator-Fun/video7140753" title="Escalator Fun">
                        <img data-src="https://pics.tnaflix.com/thumb.jpg" alt="Escalator Fun" />
                        <div class="thumb-icon video-duration">08:45</div>
                    </a>
                </div>
            </div>
        """.trimIndent()
        val feed = adapter.parseListingHtml(sampleHtml, 1).getOrThrow()
        assertEquals(1, feed.items.size)
        val item = feed.items[0]
        assertEquals("Escalator Fun", item.title)

        // 3. Direct HTML5 video source extraction
        val detailHtml = """
            <html>
            <h1>Escalator Fun</h1>
            <video id="player">
                <source src="https://sl196.tnaflix.com/vid-720p.mp4" label="720p" type="video/mp4" />
                <source src="https://sl196.tnaflix.com/vid-480p.mp4" label="480p" type="video/mp4" />
            </video>
            </html>
        """.trimIndent()
        val mediaResult = adapter.parsePlayableMediaHtml(detailHtml, "https://www.tnaflix.com/video7140753")
        assertTrue(mediaResult.isSuccess)
        val media = mediaResult.getOrThrow()
        assertTrue(media.isNotEmpty())
        assertEquals("https://sl196.tnaflix.com/vid-720p.mp4", media[0].url)
        assertEquals(MediaSourceType.PROGRESSIVE_MP4, media[0].type)
    }

    @Test
    fun testEmpFlixPlatformIntegration() = runBlocking {
        val adapter = createAdapter(
            id = "empflix",
            baseUrl = "https://www.empflix.com",
            nav = NavigationConfig(home = "/", page = "/featured/{page}", search = "/search.php?what={query}", categories = "/categories"),
            selectors = SelectorConfig(
                item = "div.video-list > div, div[data-vid], a.video-thumb",
                title = "a[title], img[alt]",
                thumbnail = "img[data-src], img",
                thumbnailAttr = "data-src, src",
                detailUrl = "a.video-thumb, a[href*='/video']",
                duration = "div.video-duration",
                videoSource = "video source[src]"
            )
        )

        val p3 = adapter.buildPagedUrl("https://www.empflix.com/featured/1", 3)
        assertEquals("https://www.empflix.com/featured/3/", p3)
    }

    @Test
    fun testEpornerListingAndDirectStream() = runBlocking {
        val adapter = createAdapter(
            id = "eporner",
            baseUrl = "https://www.eporner.com",
            nav = NavigationConfig(home = "/", page = "/0/{page}/", search = "/search/{query}/{page}/", categories = "/categories/"),
            selectors = SelectorConfig(
                item = "div.mb, div.video-box, div[data-id]",
                title = "p.mbtitle a, a[title]",
                thumbnail = "img[data-src], img",
                thumbnailAttr = "data-src, src",
                detailUrl = "a[href^='/video-'], p.mbtitle a",
                duration = "span.mblength",
                detailTitle = "h1",
                videoSource = "video source[src], a[href*='/dload/'], meta[property='og:video:url']"
            )
        )

        // 1. Pagination
        val p2 = adapter.buildPagedUrl("https://www.eporner.com/0/1/", 2)
        assertEquals("https://www.eporner.com/0/2/", p2)

        // 2. Listing parsing
        val sampleHtml = """
            <div class="mb" data-id="12345">
                <a href="/video-9CoW1hoehsU/li-rongrong-md0276/">
                    <img data-src="https://static.eporner.com/thumb.jpg" alt="Li Rongrong" />
                </a>
                <p class="mbtitle"><a href="/video-9CoW1hoehsU/li-rongrong-md0276/">Li Rongrong</a></p>
                <span class="mblength">24:10</span>
            </div>
        """.trimIndent()
        val feed = adapter.parseListingHtml(sampleHtml, 1).getOrThrow()
        assertEquals(1, feed.items.size)
        val item = feed.items[0]
        assertEquals("Li Rongrong", item.title)

        // 3. Direct Gvideo stream
        val detailHtml = """
            <html>
            <h1>Li Rongrong</h1>
            <meta property="og:video:url" content="https://gvideo.eporner.com/9CoW1hoehsU/9CoW1hoehsU.mp4" />
            <a href="/dload/9CoW1hoehsU/720/12584381-720p.mp4">Download 720p</a>
            </html>
        """.trimIndent()
        val mediaResult = adapter.parsePlayableMediaHtml(detailHtml, "https://www.eporner.com/video-9CoW1hoehsU/")
        assertTrue(mediaResult.isSuccess)
        val media = mediaResult.getOrThrow()
        assertTrue(media.isNotEmpty())
        assertTrue(media.any { it.url.contains("gvideo.eporner.com") || it.url.contains("dload") })
    }

    @Test
    fun testDrTuberAndNuVidPosterToStreamReconstruction() = runBlocking {
        val adapter = createAdapter(
            id = "drtuber",
            baseUrl = "https://www.drtuber.com",
            nav = NavigationConfig(home = "/", page = "/latest-updates/{page}/", search = "/search/videos/{query}/{page}/", categories = "/categories"),
            selectors = SelectorConfig(
                item = "div.thumbs_box > div, div.th",
                title = "a.title, a[title]",
                thumbnail = "img[data-src], img",
                thumbnailAttr = "data-src, src",
                detailUrl = "a[href*='/video/']",
                duration = "span.duration",
                videoSource = "video source[src]"
            )
        )

        // 1. Pagination
        val p2 = adapter.buildPagedUrl("https://www.drtuber.com/latest-updates/1/", 2)
        assertEquals("https://www.drtuber.com/latest-updates/2/", p2)

        // 2. Media reconstruction from CDN poster
        val detailHtml = """
            <html>
            <video poster="https://g7.drtst.com/media/videos/tmb/9294590/player/16.jpg"></video>
            </html>
        """.trimIndent()
        val mediaResult = adapter.parsePlayableMediaHtml(detailHtml, "https://www.drtuber.com/video/9294590/test")
        assertTrue(mediaResult.isSuccess)
        val media = mediaResult.getOrThrow()
        assertTrue(media.isNotEmpty())
        assertEquals("https://g7.drtst.com/media/videos/tmb/9294590/9294590.mp4", media[0].url)
        assertEquals(MediaSourceType.PROGRESSIVE_MP4, media[0].type)
    }

    @Test
    fun testSpankBangAndPornOneConfigIntegrity() {
        val sbAdapter = createAdapter(
            id = "spankbang",
            baseUrl = "https://spankbang.com",
            nav = NavigationConfig(home = "/trending_videos", page = "/trending_videos/{page}/", search = "/s/{query}/", categories = "/categories"),
            selectors = SelectorConfig(
                item = "div.video-item, div.item",
                title = "a.title, .n a",
                thumbnail = "img[data-src], img",
                thumbnailAttr = "data-src, src",
                detailUrl = "a[href*='/video/']"
            )
        )
        val sbPage = sbAdapter.buildPagedUrl("https://spankbang.com/trending_videos/1/", 2)
        assertEquals("https://spankbang.com/trending_videos/2/", sbPage)

        val poAdapter = createAdapter(
            id = "pornone",
            baseUrl = "https://pornone.com",
            nav = NavigationConfig(home = "/videos/", page = "/videos/page/{page}/", search = "/search/{query}/page/{page}/", categories = "/categories/"),
            selectors = SelectorConfig(
                item = "div.thumb-block, div.video-box",
                title = "a.title",
                thumbnail = "img.thumb, img",
                thumbnailAttr = "data-src, src",
                detailUrl = "a[href*='/video/']"
            )
        )
        val poPage = poAdapter.buildPagedUrl("https://pornone.com/videos/page/1/", 2)
        assertEquals("https://pornone.com/videos/page/2/", poPage)
    }
}
