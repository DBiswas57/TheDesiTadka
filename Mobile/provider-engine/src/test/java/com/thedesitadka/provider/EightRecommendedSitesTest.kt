package com.thedesitadka.provider

import com.thedesitadka.core.model.*
import com.thedesitadka.provider.adapters.HtmlSelectorAdapter
import org.junit.Assert.*
import org.junit.Test

class EightRecommendedSitesTest {

    private fun createAdapter(
        id: String,
        baseUrl: String,
        itemSelector: String,
        titleSelector: String,
        detailUrlSelector: String,
        thumbnailSelector: String,
        thumbnailAttr: String = "src",
        homeNav: String = "/",
        pageNav: String = "/page/{page}/"
    ): HtmlSelectorAdapter {
        val config = ProviderConfig(
            id = id,
            familyId = "clean_tube_family",
            domains = listOf(baseUrl),
            validationMarker = id,
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
            navigation = NavigationConfig(
                home = homeNav,
                search = "/search?q={query}",
                page = pageNav
            ),
            selectors = SelectorConfig(
                item = itemSelector,
                title = titleSelector,
                detailUrl = detailUrlSelector,
                thumbnail = thumbnailSelector,
                thumbnailAttr = thumbnailAttr,
                detailTitle = "h1",
                detailDescription = "p",
                detailThumbnail = "meta[property='og:image']",
                videoSource = "video source",
                videoSourceAttr = "src"
            ),
            contentPolicy = ContentPolicy("Public Web Index", "Public media")
        )
        return HtmlSelectorAdapter(config)
    }

    @Test
    fun testXnxxListingAndPagination() {
        val adapter = createAdapter(
            id = "xnxx",
            baseUrl = "https://www.xnxx.com",
            itemSelector = "div.thumb-block",
            titleSelector = ".thumb-under p a[title], .thumb-under a[title], p.title a, a[title]",
            detailUrlSelector = ".thumb-inside a[href*='/video-'], a[href*='/video-']",
            thumbnailSelector = "img.thumb, img[data-src], img[src]",
            thumbnailAttr = "data-src, src",
            homeNav = "/best",
            pageNav = "/best/{page}"
        )

        val sampleHtml = """
            <html><body>
                <div class="mozaique">
                    <div class="thumb-block" id="video_123">
                        <div class="thumb-inside">
                            <a href="/video-12345/desi_bhabhi_hot_scene"><img class="thumb" data-src="https://thumb-cdn.xnxx.com/123.jpg" /></a>
                        </div>
                        <div class="thumb-under">
                            <p class="title"><a href="/video-12345/desi_bhabhi_hot_scene" title="Desi Bhabhi Hot Scene">Desi Bhabhi Hot Scene</a></p>
                            <span class="duration">12 min</span>
                        </div>
                    </div>
                </div>
            </body></html>
        """.trimIndent()

        val parsed = adapter.parseListingHtml(sampleHtml, 1)
        assertTrue(parsed.isSuccess)
        val feed = parsed.getOrThrow()
        assertEquals(1, feed.items.size)
        assertEquals("Desi Bhabhi Hot Scene", feed.items[0].title)
        assertEquals("https://www.xnxx.com/video-12345/desi_bhabhi_hot_scene", feed.items[0].detailUrl)
        assertEquals("https://thumb-cdn.xnxx.com/123.jpg", feed.items[0].thumbnailUrl)

        // Pagination
        assertEquals("https://www.xnxx.com/best/2", adapter.buildPagedUrl("https://www.xnxx.com/best", 2))
        assertEquals("https://www.xnxx.com/search/indian/3", adapter.buildPagedUrl("https://www.xnxx.com/search/indian", 3))
    }

    @Test
    fun testPornHubListingAyloStreamAndPagination() {
        val adapter = createAdapter(
            id = "pornhub",
            baseUrl = "https://www.pornhub.org",
            itemSelector = "li.videoBox, .pcVideoListItem",
            titleSelector = "span.title a[title], .title a, a[title]",
            detailUrlSelector = "a[href*='view_video.php'], a.linkVideoThumb",
            thumbnailSelector = "img[data-src], img[src]",
            thumbnailAttr = "data-src, src",
            homeNav = "/video?o=mr",
            pageNav = "/video?o=mr&page={page}"
        )

        val sampleHtml = """
            <html><body>
                <ul class="videos">
                    <li class="videoBox pcVideoListItem" id="v123">
                        <div class="phimage">
                            <a href="/view_video.php?viewkey=ph123" class="linkVideoThumb">
                                <img data-src="https://pix.phncdn.com/thumb123.jpg" src="blank.gif" />
                            </a>
                        </div>
                        <span class="title"><a href="/view_video.php?viewkey=ph123" title="Exciting Tube Video">Exciting Tube Video</a></span>
                    </li>
                </ul>
            </body></html>
        """.trimIndent()

        val parsed = adapter.parseListingHtml(sampleHtml, 1)
        assertTrue(parsed.isSuccess)
        val feed = parsed.getOrThrow()
        assertEquals(1, feed.items.size)
        assertEquals("Exciting Tube Video", feed.items[0].title)
        assertEquals("https://www.pornhub.org/view_video.php?viewkey=ph123", feed.items[0].detailUrl)

        // Pagination
        assertEquals("https://www.pornhub.org/video?o=mr&page=2", adapter.buildPagedUrl("https://www.pornhub.org/video?o=mr", 2))
        assertEquals("https://www.pornhub.org/video/search?search=indian&page=3", adapter.buildPagedUrl("https://www.pornhub.org/video/search?search=indian", 3))

        // Aylo mediaDefinitions stream resolution
        val detailHtml = """
            <html><head><script>
                var flashvars_123 = {
                    "mediaDefinitions": [
                        {"format":"hls","videoUrl":"https:\/\/hv-h.phncdn.com\/hls\/1080P_4000K.mp4\/master.m3u8?h=token","quality":"1080"},
                        {"format":"hls","videoUrl":"https:\/\/hv-h.phncdn.com\/hls\/720P_2000K.mp4\/master.m3u8?h=token","quality":"720"}
                    ]
                };
            </script></head><body><h1>Pornhub Video</h1></body></html>
        """.trimIndent()

        val mediaResult = adapter.parsePlayableMediaHtml(detailHtml, "https://www.pornhub.org/view_video.php?viewkey=ph123")
        assertTrue(mediaResult.isSuccess)
        val media = mediaResult.getOrThrow()
        assertEquals(2, media.size)
        assertEquals(MediaSourceType.HLS, media[0].type)
        assertEquals("https://hv-h.phncdn.com/hls/1080P_4000K.mp4/master.m3u8?h=token", media[0].url)
        assertEquals("1080p", media[0].quality)
    }

    @Test
    fun testFreeOnesVideoJsSourcesExtraction() {
        val adapter = createAdapter(
            id = "freeonestube",
            baseUrl = "https://freeonestube.com",
            itemSelector = "a.thumb, div.thumb",
            titleSelector = "a[title], img[alt]",
            detailUrlSelector = "a.thumb, a[href*='/video/']",
            thumbnailSelector = "img.video-img, img",
            thumbnailAttr = "src, data-src"
        )

        val detailHtml = """
            <html><body>
                <script>
                    window.addEventListener('videoJsReady', function () {
                        const playerOptions = {
                            sources: [
                                {"label":"1080p","res":1080,"src":"https:\/\/media.freeones.com\/generated\/1080p.mp4","type":"video\/mp4"},
                                {"label":"720p","res":720,"src":"https:\/\/media.freeones.com\/generated\/720p.mp4","type":"video\/mp4"}
                            ]
                        };
                    });
                </script>
            </body></html>
        """.trimIndent()

        val mediaResult = adapter.parsePlayableMediaHtml(detailHtml, "https://freeonestube.com/video/sample-video/")
        assertTrue(mediaResult.isSuccess)
        val sources = mediaResult.getOrThrow()
        assertEquals(2, sources.size)
        assertEquals("https://media.freeones.com/generated/1080p.mp4", sources[0].url)
        assertEquals("1080p", sources[0].quality)
        assertEquals(MediaSourceType.PROGRESSIVE_MP4, sources[0].type)
    }

    @Test
    fun testXhamsterDirectHlsStreamResolution() {
        val adapter = createAdapter(
            id = "xhamster",
            baseUrl = "https://xhamster.com",
            itemSelector = "[data-video-id], .thumb-list__item.video-thumb",
            titleSelector = "a[data-video-title], a.video-thumb__image-container[aria-label], .video-thumb-info__name, a[title]",
            detailUrlSelector = "a[href*='/videos/']",
            thumbnailSelector = "img.thumb-image-container__image, img[src]"
        )

        val detailHtml = """
            <html><body>
                <script>
                    var xhVideo = {
                        "hls": "https://video-am.xhpingcdn.com/stream-token/media=hls4/1080p.av1.mp4.m3u8"
                    };
                </script>
            </body></html>
        """.trimIndent()

        val mediaResult = adapter.parsePlayableMediaHtml(detailHtml, "https://xhamster.com/videos/sample-video-123")
        assertTrue(mediaResult.isSuccess)
        val sources = mediaResult.getOrThrow()
        assertEquals(1, sources.size)
        assertEquals("https://video-am.xhpingcdn.com/stream-token/media=hls4/1080p.av1.mp4.m3u8", sources[0].url)
        assertEquals(MediaSourceType.HLS, sources[0].type)
    }
}
