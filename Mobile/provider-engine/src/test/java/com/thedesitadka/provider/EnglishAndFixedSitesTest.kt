package com.thedesitadka.provider

import com.thedesitadka.core.model.MediaSourceType
import com.thedesitadka.core.model.NavigationConfig
import com.thedesitadka.core.model.ProviderCapability
import com.thedesitadka.core.model.ProviderConfig
import com.thedesitadka.core.model.SelectorConfig
import com.thedesitadka.provider.adapters.HtmlSelectorAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EnglishAndFixedSitesTest {

    private fun findDirectory(folderName: String): File? {
        val candidates = listOf(
            File(folderName),
            File("../$folderName"),
            File("../../$folderName"),
            File("c:/Users/LearnersYT/source/TheDesiTadka/$folderName")
        )
        return candidates.firstOrNull { it.exists() && it.isDirectory }
    }

    // =========================================================================
    // Phase 2 Tests: kamababa1 and xnxx.com Individual Testing
    // =========================================================================

    @Test
    fun testKamababa1IndividualSiteParsing() {
        val config = ProviderConfig(
            id = "kamababa1",
            familyId = "desi_family",
            name = "Kamababa",
            baseUrl = "https://www.mykamababa.com",
            adapter = "html_selector",
            domains = listOf("kamababa1.com", "mykamababa.com"),
            capabilities = listOf(
                ProviderCapability.HOME,
                ProviderCapability.CATEGORY,
                ProviderCapability.SEARCH,
                ProviderCapability.DETAILS,
                ProviderCapability.STREAM
            ),
            navigation = NavigationConfig(
                home = "/",
                search = "/search/{query}/",
                page = "/page/{page}/",
                categories = "/categories/"
            ),
            selectors = SelectorConfig(
                item = "article.thumb-block, article.video-preview-item, article",
                title = "a[title], h2.entry-title a, h2 a",
                thumbnail = "img.video-main-thumb, img",
                thumbnailAttr = "src",
                detailUrl = "a.video-preview-link, a[href*='kamababa'], a",
                duration = "span.duration",
                videoSource = "video source[src], video[src], meta[itemprop*='embedUrl'], iframe[src*='embed']"
            )
        )
        val adapter = HtmlSelectorAdapter(config)

        // 1. Test against real captured HTML if present
        val kamaDir = findDirectory("01. SiteReferrence/kamababa1")
        if (kamaDir != null) {
            val htmlFile = kamaDir.listFiles()?.firstOrNull { it.name.endsWith(".html") || it.name.endsWith(".htm") }
            if (htmlFile != null) {
                val html = htmlFile.readText(Charsets.UTF_8)
                val feedResult = adapter.parseListingHtml(html, 1)
                assertTrue("Kamababa parse result must be success", feedResult.isSuccess)
                val items = feedResult.getOrThrow().items
                assertTrue("Kamababa must extract items from captured HTML", items.isNotEmpty())
                for (item in items) {
                    assertFalse("Title must not be blank", item.title.isBlank())
                    assertFalse("Detail URL must not be blank", item.detailUrl.isBlank())
                }
            }
        }

        // 2. Test synthetic fixture to guarantee test execution in any environment
        val sampleKamaHtml = """
            <div class="video-list">
                <article class="thumb-block video-preview-item">
                    <div class="thumb-inside">
                        <a href="https://www.mykamababa.com/desi-bhabhi-romantic-clip/" class="video-preview-link" title="Desi Bhabhi Romantic MMS">
                            <img class="video-main-thumb" src="https://www.mykamababa.com/thumbs/1.jpg" alt="Thumb" />
                            <span class="duration">12:30</span>
                        </a>
                        <h2 class="entry-title"><a href="https://www.mykamababa.com/desi-bhabhi-romantic-clip/">Desi Bhabhi Romantic MMS</a></h2>
                    </div>
                </article>
            </div>
        """.trimIndent()

        val parsed = adapter.parseListingHtml(sampleKamaHtml, 1).getOrThrow()
        assertEquals(1, parsed.items.size)
        assertEquals("Desi Bhabhi Romantic MMS", parsed.items[0].title)
        assertEquals("https://www.mykamababa.com/desi-bhabhi-romantic-clip/", parsed.items[0].detailUrl)
        assertEquals("https://www.mykamababa.com/thumbs/1.jpg", parsed.items[0].thumbnailUrl)
    }

    @Test
    fun testXnxxIndividualSiteParsing() {
        val config = ProviderConfig(
            id = "xnxx",
            familyId = "global_family",
            name = "XNXX",
            baseUrl = "https://www.xnxx.com",
            adapter = "html_selector",
            domains = listOf("xnxx.com", "xnxx.health"),
            capabilities = listOf(
                ProviderCapability.HOME,
                ProviderCapability.CATEGORY,
                ProviderCapability.SEARCH,
                ProviderCapability.DETAILS,
                ProviderCapability.STREAM
            ),
            navigation = NavigationConfig(
                home = "/todays-selection",
                search = "/search/{query}",
                page = "/todays-selection/{page}",
                categories = "/"
            ),
            selectors = SelectorConfig(
                item = "div.thumb-block:not(.thumb-cat), div.thumb-block, div.mozaique > div",
                title = "p.title a, .title a, a[title]",
                thumbnail = "img",
                thumbnailAttr = "data-mzl, src, data-src",
                detailUrl = "p.title a, .thumb a, a[href*='/video-'], a[href*='/video'], a",
                duration = "span.metadata",
                videoSource = "script"
            )
        )
        val adapter = HtmlSelectorAdapter(config)

        // 1. Test against real captured HTML if present
        val xnxxDir = findDirectory("01. SiteReferrence/xnxx.com")
        if (xnxxDir != null) {
            val htmlFiles = xnxxDir.listFiles()?.filter { it.name.endsWith(".html") } ?: emptyList()
            var foundVideos = false
            for (htmlFile in htmlFiles) {
                val html = htmlFile.readText(Charsets.UTF_8)
                val feedResult = adapter.parseListingHtml(html, 1)
                if (feedResult.isSuccess) {
                    val items = feedResult.getOrThrow().items
                    val videoItems = items.filter { it.detailUrl.contains("/video-") || it.detailUrl.contains("/video") }
                    if (videoItems.isNotEmpty()) {
                        foundVideos = true
                        for (item in videoItems) {
                            assertFalse("Title must not be blank", item.title.isBlank())
                            assertTrue("Detail URL must point to video", item.detailUrl.contains("/video"))
                        }
                        break
                    }
                }
            }
            assertTrue("XNXX must extract video items from captured HTML", foundVideos)
        }

        // 2. Test synthetic fixture with data-mzl and /video- url
        val sampleXnxxHtml = """
            <div class="mozaique">
                <div class="thumb-block" id="video_1001">
                    <div class="thumb-inside">
                        <div class="thumb">
                            <a href="/video-ab12cd/hot_indian_college_girl_fun">
                                <img src="https://static.xnxx.com/clear.gif" data-mzl="https://img-l3.xnxx-cdn.com/videos/thumbs169ll/ab/12/cd/ab12cd/ab12cd.1.jpg" />
                            </a>
                        </div>
                        <p class="title"><a href="/video-ab12cd/hot_indian_college_girl_fun" title="Hot Indian College Girl Fun">Hot Indian College Girl Fun</a></p>
                    </div>
                </div>
            </div>
        """.trimIndent()

        val parsed = adapter.parseListingHtml(sampleXnxxHtml, 1).getOrThrow()
        assertEquals(1, parsed.items.size)
        assertEquals("Hot Indian College Girl Fun", parsed.items[0].title)
        assertEquals("https://www.xnxx.com/video-ab12cd/hot_indian_college_girl_fun", parsed.items[0].detailUrl)
        assertEquals("https://img-l3.xnxx-cdn.com/videos/thumbs169ll/ab/12/cd/ab12cd/ab12cd.1.jpg", parsed.items[0].thumbnailUrl)

        // 3. Test script stream player parsing for XNXX
        val samplePlayerScriptHtml = """
            <!DOCTYPE html>
            <html>
            <head><title>Hot Indian College Girl Fun - XNXX.COM</title></head>
            <body>
                <div id="html5video">
                    <script type="text/javascript">
                        html5player.setVideoTitle('Hot Indian College Girl Fun');
                        html5player.setVideoUrlHigh('https://video.xnxx-cdn.com/hls/test_high.m3u8');
                        html5player.setVideoUrlLow('https://video.xnxx-cdn.com/mp4/test_low.mp4');
                    </script>
                </div>
            </body>
            </html>
        """.trimIndent()

        val mediaResult = adapter.parsePlayableMediaHtml(samplePlayerScriptHtml, "https://www.xnxx.com/video-ab12cd/test")
        assertTrue(mediaResult.isSuccess)
        val sources = mediaResult.getOrThrow()
        assertTrue("Must extract streams from html5player script", sources.isNotEmpty())
        assertEquals("https://video.xnxx-cdn.com/hls/test_high.m3u8", sources[0].url)
        assertEquals(MediaSourceType.HLS, sources[0].type)
    }

    // =========================================================================
    // Phase 1 Tests: All 18 English Sites Testing
    // =========================================================================

    private data class EnglishSiteConfigEntry(
        val id: String,
        val folder: String,
        val baseUrl: String,
        val item: String,
        val title: String,
        val thumbnail: String,
        val thumbnailAttr: String,
        val detailUrl: String
    )

    @Test
    fun testAll18EnglishSitesParsingIndividually() {
        val englishDir = findDirectory("EnglishSites")
        assertNotNull("EnglishSites directory must exist", englishDir)

        val sites = listOf(
            EnglishSiteConfigEntry("brazzers", "brazzers.com", "https://www.brazzers.com", "div[class*='scene-card'], div[class*='e1qkfw3j2'], div.scene-card, a[href*='/video/']", "a[title], [title], img[alt]", "img", "data-src, src", "a[href*='/video/'], a"),
            EnglishSiteConfigEntry("brazzpw", "brazzpw.com", "https://brazzpw.xyz", "article.thumb-block, article.video-preview-item, article, div.item", "a[title], h2.entry-title a, h2 a, a.title", "img.video-main-thumb, img", "src", "a[href*='/video/'], a[href*='brazzpw'], a"),
            EnglishSiteConfigEntry("fpo", "fpo.xxx", "https://www.fpo.xxx", "div.item:not(.pfb-slider-group *), a.item:not(.pfb-slider-group *)", "strong.title, a.title, [title]", "img", "data-src, src", "a[href*='/video/'], a[href*='/sites/'], a[href*='/models/'], a"),
            EnglishSiteConfigEntry("hello", "hello.porn", "https://hello.porn", "div.items-videos div.item, div.item:not(.navigation *):not(.items-categories *):not(.slider-cat *):not(.pfb-slider-group *)", "a.title, strong.title, [title], a", "img", "data-src, src", "a[href*='/videos/'], a[href*='/video/'], a[href*='/pornstar/'], a[href*='/channels/'], a"),
            EnglishSiteConfigEntry("hqporner", "hqporner.com", "https://hqporner.com", "section.box.feature:has(h3), section.feature:has(h3), div.video-item", "h3.meta-data-title a, a.title, h3 a, [title], a", "img", "data-src, src", "a[href*='/hdporn/'], a[href*='/category/'], a[href*='/actress/'], h3 a, a"),
            EnglishSiteConfigEntry("max", "max.porn", "https://max.porn", "div.item:not(.swiper-slide):not(.pfb-slider-group *):not(.page):not(.jump):not(.last):not(.next):not(.prev)", "a.title, [title], a", "img", "data-src, src", "a[href*='/videos/'], a[href*='/channels/'], a"),
            EnglishSiteConfigEntry("netfapx", "netfapx.com", "https://netfapx.com", "article.pinbox, div.pinbox, article.post, article:not(.pfb-slider-group *)", "h2.entry-title a, h2 a, a[title]", "img", "data-src, src", "h2.entry-title a, a[href*='netfapx.com/20'], a[href*='?cat='], a"),
            EnglishSiteConfigEntry("ok_porn", "ok.porn", "https://ok.porn", "div.item:not(.pfb-slider-group *), div.thumb-bl:not(.item):not(.item *):not(.pfb-slider-group *)", "a.title, [title], a", "img", "data-src, src", "a[href*='/video/'], a[href*='/sites/'], a[href*='/models/'], a"),
            EnglishSiteConfigEntry("ok_xxx", "ok.xxx", "https://ok.xxx", "div.item:not(.pfb-slider-group *), div.thumb-bl:not(.item):not(.item *):not(.pfb-slider-group *)", "a.title, [title], a", "img", "data-src, src", "a[href*='/video/'], a[href*='/sites/'], a[href*='/models/'], a"),
            EnglishSiteConfigEntry("perfectgirls", "perfectgirls.xxx", "https://www.perfectgirls.xxx", "div.item, div.video-item, div.card", "a.title, [title], a", "img", "data-src, src", "a[href*='/video/'], a[href*='/videos/'], a"),
            EnglishSiteConfigEntry("porn4days", "porn4days.pw", "https://porn4days.pw", "div.card, div.video-item, div.col-6, div.col-md-3", "a[title], img[alt], h5, .card-title, a", "img.card-img-top, img", "src, data-src", "a[href*='video/'], a"),
            EnglishSiteConfigEntry("pornhat", "pornhat.com", "https://www.pornhat.com", "div.item, div.thumb, div.video-item", "a.title, [title], a", "img", "data-src, src", "a[href*='/video/'], a[href*='/videos/'], a"),
            EnglishSiteConfigEntry("pornhd4k", "pornhd4k.net", "https://pornhd4k.net", "div.item, div.film-poster, div.video-item", "a.title, h3 a, [title], a", "img", "data-src, src", "a[href*='/movies/'], a"),
            EnglishSiteConfigEntry("pornhouse", "pornhouse.me", "https://pornhouse.me", "div.item, div.film-poster, div.video-item", "a.title, h3 a, [title], a", "img", "data-src, src", "a[href*='/movies/'], a"),
            EnglishSiteConfigEntry("pornmz", "pornmz.com", "https://pornmz.com", "article.thumb-block, article.video-preview-item, article, div.item", "a[title], h2.entry-title a, h2 a, a.title", "img.video-main-thumb, img", "src", "a[href*='/video/'], a[href*='pornmz.com/video/'], a"),
            EnglishSiteConfigEntry("pornstars_tube", "pornstars.tube", "https://pornstars.tube", "div.item, div.video-card, div.thumb", "a.title, [title], a", "img", "data-src, src", "a[href*='/videos/'], a"),
            EnglishSiteConfigEntry("sxyprn", "sxyprn.com", "https://sxyprn.com", "div.post_el_small, div.post_el", "div.post_text, a.ps_link, a", "img.mini_post_vid_thumb, img", "src, data-src", "a[href*='/post/'], a"),
            EnglishSiteConfigEntry("watchxxxfree", "watchxxxfree.xyz", "https://watchxxxfree.xyz", "div.videos-list article, div.videos-list .loop-video, article.thumb-block:not(.slide):not(.bx-clone)", "a[title], img[alt], h2.entry-title a, h2 a, a", "img.video-main-thumb, img", "src", "a[href*='watchxxxfree.xyz/'], a[href*='/'], a")
        )

        assertEquals("Must verify all 18 English sites", 18, sites.size)

        for (site in sites) {
            val siteFolder = File(englishDir, site.folder)
            assertTrue("Folder for ${site.id} must exist at ${siteFolder.absolutePath}", siteFolder.exists() && siteFolder.isDirectory)

            val htmlFiles = siteFolder.listFiles()?.filter { it.name.endsWith(".html") || it.name.endsWith(".htm") } ?: emptyList()
            assertTrue("Site ${site.id} must contain HTML capture files", htmlFiles.isNotEmpty())

            val providerConfig = ProviderConfig(
                id = site.id,
                familyId = "english_family",
                name = site.id,
                baseUrl = site.baseUrl,
                adapter = "html_selector",
                selectors = SelectorConfig(
                    item = site.item,
                    title = site.title,
                    thumbnail = site.thumbnail,
                    thumbnailAttr = site.thumbnailAttr,
                    detailUrl = site.detailUrl
                )
            )
            val adapter = HtmlSelectorAdapter(providerConfig)

            var extractedCount = 0
            for (htmlFile in htmlFiles) {
                val html = htmlFile.readText(Charsets.UTF_8)
                val feedResult = adapter.parseListingHtml(html, 1)
                if (feedResult.isSuccess) {
                    val count = feedResult.getOrThrow().items.size
                    if (count > 0) {
                        extractedCount = count
                        break
                    }
                }
            }

            assertTrue("Site ${site.id} must extract items from captured HTML (got $extractedCount)", extractedCount > 0)
        }
    }

    @Test
    fun testParseCategoriesExtractionTaxonomy() {
        val config = ProviderConfig(
            id = "brazzers",
            name = "Brazzers",
            baseUrl = "https://www.brazzers.com",
            adapter = "html_selector"
        )
        val adapter = HtmlSelectorAdapter(config)

        val sampleCategoryHtml = """
            <div class="categories-container">
                <a href="/categories/milf" class="cat-item">
                    <span class="taxonomy-name">MILF</span>
                </a>
                <a href="/studio/bangbros" class="cat-item">
                    <span class="taxonomy-name">BangBros</span>
                </a>
                <a href="/tag/blowjob" class="cat-item">
                    <span class="taxonomy-name">Blowjob</span>
                </a>
            </div>
        """.trimIndent()

        val categories = adapter.parseCategoriesHtml(sampleCategoryHtml)
        assertEquals(3, categories.size)
        assertEquals("MILF", categories[0].name)
        assertEquals("BangBros", categories[1].name)
        assertEquals("Blowjob", categories[2].name)
    }

    @Test
    fun testWatchXxxFreeOmitCarouselAndResolveIframe() {
        val englishDir = findDirectory("EnglishSites")
        assertNotNull("EnglishSites directory must exist", englishDir)
        val watchFolder = File(englishDir!!, "watchxxxfree.xyz")
        assertTrue("watchxxxfree.xyz folder must exist", watchFolder.exists() && watchFolder.isDirectory)

        val config = ProviderConfig(
            id = "watchxxxfree",
            familyId = "clean_tube_family",
            name = "WatchXXXFree",
            baseUrl = "https://watchxxxfree.xyz",
            adapter = "html_selector",
            navigation = NavigationConfig(
                home = "/?filter=latest",
                search = "/?s={query}",
                page = "/page/{page}/?filter=latest",
                categories = "/categories/"
            ),
            selectors = SelectorConfig(
                item = "div.videos-list article, div.videos-list .loop-video, article.thumb-block:not(.slide):not(.bx-clone)",
                title = "a[title], img[alt], h2.entry-title a, h2 a, a",
                thumbnail = "img.video-main-thumb, img",
                thumbnailAttr = "src",
                detailUrl = "a[href*='watchxxxfree.xyz/'], a[href*='/'], a",
                player = "iframe[src*='vixeo.io'], iframe, video",
                videoSource = "iframe[src*='vixeo.io'], video source[src], video[src]"
            )
        )
        val adapter = HtmlSelectorAdapter(config)

        // 1. Test listing parsing omits carousel slides
        val listHtmlFile: File = File(watchFolder, "watchxxxfree.xyz (1).html")
        if (listHtmlFile.exists()) {
            val listHtml = listHtmlFile.readText(Charsets.UTF_8)
            val feedResult = adapter.parseListingHtml(listHtml, page = 1)
            assertTrue("Listing parsing must succeed", feedResult.isSuccess)
            val feedPage = feedResult.getOrThrow()
            // 51 total articles exist in DOM (30 in top carousel, 21 in video list).
            // Carousel slides MUST be omitted, returning exactly the 21 unique video grid items.
            assertEquals("Must return exactly 21 non-carousel items", 21, feedPage.items.size)
            for (item in feedPage.items) {
                assertTrue("Item title must not be blank", item.title.isNotBlank())
                assertTrue("Item detailUrl must be valid", item.detailUrl.startsWith("https://watchxxxfree.xyz/"))
            }
        }

        // 2. Test detail iframe detection on detail page (5).html
        val detailHtmlFile: File = File(watchFolder, "watchxxxfree.xyz (5).html")
        if (detailHtmlFile.exists()) {
            val detailHtml = detailHtmlFile.readText(Charsets.UTF_8)
            val doc = org.jsoup.Jsoup.parse(detailHtml, config.baseUrl)
            val candidateEmbeds = com.thedesitadka.provider.plugins.HostResolverEngine.extractCandidateUrls(doc)
            assertTrue("Must detect vixeo.io embed iframe in detail page", candidateEmbeds.any { it.contains("vixeo.io/e/") })
        }
    }
}
