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

class SevenSitesListingAndNavTest {

    private fun findEnglishSitesDir(): File {
        val candidates = listOf(
            File("EnglishSites"),
            File("../EnglishSites"),
            File("../../EnglishSites")
        )
        return candidates.firstOrNull { it.exists() && it.isDirectory }
            ?: throw IllegalStateException("Cannot find EnglishSites directory")
    }

    private data class SiteSpec(
        val id: String,
        val folder: String,
        val baseUrl: String,
        val item: String,
        val title: String,
        val thumbnail: String,
        val thumbnailAttr: String,
        val detailUrl: String
    )

    private val sevenSites = listOf(
        SiteSpec(
            id = "fpo",
            folder = "fpo.xxx",
            baseUrl = "https://www.fpo.xxx",
            item = "div.item:not(.pfb-slider-group *), a.item:not(.pfb-slider-group *)",
            title = "strong.title, a.title, [title]",
            thumbnail = "img",
            thumbnailAttr = "data-src, src",
            detailUrl = "a[href*='/video/'], a[href*='/sites/'], a[href*='/models/'], a"
        ),
        SiteSpec(
            id = "hello",
            folder = "hello.porn",
            baseUrl = "https://hello.porn",
            item = "div.items-videos div.item, div.item:not(.navigation *):not(.items-categories *):not(.slider-cat *):not(.pfb-slider-group *)",
            title = "a.title, strong.title, [title], a",
            thumbnail = "img",
            thumbnailAttr = "data-src, src",
            detailUrl = "a[href*='/videos/'], a[href*='/video/'], a[href*='/pornstar/'], a[href*='/channels/'], a"
        ),
        SiteSpec(
            id = "hqporner",
            folder = "hqporner.com",
            baseUrl = "https://hqporner.com",
            item = "section.box.feature:has(h3), section.feature:has(h3), div.video-item",
            title = "h3.meta-data-title a, a.title, h3 a, [title], a",
            thumbnail = "img",
            thumbnailAttr = "data-src, src",
            detailUrl = "a[href*='/hdporn/'], a[href*='/category/'], a[href*='/actress/'], h3 a, a"
        ),
        SiteSpec(
            id = "max",
            folder = "max.porn",
            baseUrl = "https://max.porn",
            item = "div.item:not(.swiper-slide):not(.pfb-slider-group *):not(.page):not(.jump):not(.last):not(.next):not(.prev)",
            title = "a.title, [title], a",
            thumbnail = "img",
            thumbnailAttr = "data-src, src",
            detailUrl = "a[href*='/videos/'], a[href*='/channels/'], a"
        ),
        SiteSpec(
            id = "netfapx",
            folder = "netfapx.com",
            baseUrl = "https://netfapx.com",
            item = "article.pinbox, div.pinbox, article.post, article:not(.pfb-slider-group *)",
            title = "h2.entry-title a, h2 a, a[title]",
            thumbnail = "img",
            thumbnailAttr = "data-src, src",
            detailUrl = "h2.entry-title a, a[href*='netfapx.com/20'], a[href*='?cat='], a"
        ),
        SiteSpec(
            id = "ok_porn",
            folder = "ok.porn",
            baseUrl = "https://ok.porn",
            item = "div.item:not(.pfb-slider-group *), div.thumb-bl:not(.item):not(.item *):not(.pfb-slider-group *)",
            title = "a.title, [title], a",
            thumbnail = "img",
            thumbnailAttr = "data-src, src",
            detailUrl = "a[href*='/video/'], a[href*='/sites/'], a[href*='/models/'], a"
        ),
        SiteSpec(
            id = "ok_xxx",
            folder = "ok.xxx",
            baseUrl = "https://ok.xxx",
            item = "div.item:not(.pfb-slider-group *), div.thumb-bl:not(.item):not(.item *):not(.pfb-slider-group *)",
            title = "a.title, [title], a",
            thumbnail = "img",
            thumbnailAttr = "data-src, src",
            detailUrl = "a[href*='/video/'], a[href*='/sites/'], a[href*='/models/'], a"
        )
    )

    @Test
    fun testSevenSitesParsingCorrectnessAndZeroDuplicates() {
        val baseDir = findEnglishSitesDir()

        for (spec in sevenSites) {
            val siteDir = File(baseDir, spec.folder)
            assertTrue("Directory for ${spec.id} must exist at ${siteDir.absolutePath}", siteDir.exists())

            val htmlFiles = siteDir.listFiles()?.filter { it.name.endsWith(".html") } ?: emptyList()
            assertTrue("${spec.id} must contain HTML files", htmlFiles.isNotEmpty())

            val config = ProviderConfig(
                id = spec.id,
                name = spec.id,
                baseUrl = spec.baseUrl,
                adapter = "html_selector",
                selectors = SelectorConfig(
                    item = spec.item,
                    title = spec.title,
                    thumbnail = spec.thumbnail,
                    thumbnailAttr = spec.thumbnailAttr,
                    detailUrl = spec.detailUrl
                )
            )
            val adapter = HtmlSelectorAdapter(config)

            var totalParsedItemsAcrossFiles = 0
            for (htmlFile in htmlFiles) {
                val html = htmlFile.readText(Charsets.UTF_8)
                val feedResult = adapter.parseListingHtml(html, 1)
                if (feedResult.isSuccess) {
                    val feed = feedResult.getOrThrow()
                    val items = feed.items
                    totalParsedItemsAcrossFiles += items.size
                    println("TEST_DEBUG: ${spec.id} - ${htmlFile.name} -> ${items.size} items")

                    // Verify zero duplicate detail URLs within the page
                    val urls = items.map { it.detailUrl }
                    val uniqueUrls = urls.distinct()
                    assertEquals("Site ${spec.id} in file ${htmlFile.name} must have 0 duplicate URLs", uniqueUrls.size, urls.size)

                    // Verify no carousel or top shortcut links in video listings
                    for (item in items) {
                        val lower = item.detailUrl.lowercase()
                        assertFalse("Item detailUrl in ${spec.id} must not be top shortcut '/top': ${item.detailUrl}", lower.endsWith("/top"))
                        assertFalse("Item detailUrl in ${spec.id} must not be top shortcut '/new': ${item.detailUrl}", lower.endsWith("/new") || lower.endsWith("/new/"))
                        assertFalse("Item detailUrl in ${spec.id} must not be top shortcut '/history': ${item.detailUrl}", lower.endsWith("/history") || lower.endsWith("/history/"))
                        assertFalse("Item detailUrl in ${spec.id} must not be ad/spam: ${item.detailUrl}", lower.contains("theporndude") || lower.contains("homo.xxx") || lower.contains("xlivrdr"))
                    }
                }
            }
            assertTrue("Site ${spec.id} must extract items across its captured pages (got $totalParsedItemsAcrossFiles)", totalParsedItemsAcrossFiles > 0)
        }
    }

    @Test
    fun testSevenSitesCategoryVsVideoClassification() {
        // Multi-level category/channel/model/site URLs must be classified as categories (isCategory = true)
        val categoryUrls = listOf(
            "https://www.fpo.xxx/categories/",
            "https://www.fpo.xxx/models-2/",
            "https://www.fpo.xxx/models/ricky-spanish/",
            "https://www.fpo.xxx/sites/nik-porn/",
            "https://hello.porn/categories/4k-video/",
            "https://hello.porn/channels/brazzers/",
            "https://hello.porn/pornstars/videos/W/",
            "https://hqporner.com/category/deepthroat",
            "https://hqporner.com/girls",
            "https://hqporner.com/actress/line-heat",
            "https://max.porn/channels/brazzers/",
            "https://max.porn/channels/jav-hd/",
            "https://netfapx.com/category/asian/",
            "https://netfapx.com/?cat=4",
            "https://ok.porn/channels/",
            "https://ok.porn/sites/jav-hd/",
            "https://ok.porn/models/mike-adriano/",
            "https://ok.xxx/channels/videos/",
            "https://ok.xxx/sites/brazzers/",
            "https://ok.xxx/models/angela-white/"
        )

        for (url in categoryUrls) {
            assertTrue("URL should be identified as category/sub-list: $url", isCategoryUrl(url))
        }

        // Single playable video URLs must NOT be classified as categories (isCategory = false)
        val videoUrls = listOf(
            "https://www.fpo.xxx/video/1296954/se-deja-follar-en-su-casa-por-cualuiera-con-monique-fuentes/",
            "https://www.fpo.xxx/video/1294156/pain-and-stuff-her-ing-sara-jay/",
            "https://hello.porn/videos/786866/",
            "https://hello.porn/videos/787262/",
            "https://hqporner.com/hdporn/127881-he_can_be_short_if_he_rocks_the_boat.html",
            "https://hqporner.com/hdporn/127576-theres_no_end_to_her_throat.html",
            "https://max.porn/videos/787072/tattooed-brunette-in-ryan-reid-lingerie/",
            "https://max.porn/videos/788637/pink-miniskirt-cutie/",
            "https://netfapx.com/2026/09/stepmom-and-i-share-a-shower/",
            "https://netfapx.com/2026/09/poolside-sneaky-fuck/",
            "https://ok.porn/video/787960/",
            "https://ok.porn/video/23836/",
            "https://ok.xxx/video/783534/",
            "https://ok.xxx/video/781917/"
        )

        for (url in videoUrls) {
            assertFalse("URL should be recognized as single playable video, NOT category: $url", isCategoryUrl(url))
        }
    }

    @Test
    fun testSevenSitesPaginationUrlBuilding() {
        val testCases = listOf(
            // FPO.xxx
            Triple("https://www.fpo.xxx/new-1/", 2, "https://www.fpo.xxx/new-1/2/"),
            Triple("https://www.fpo.xxx/new-1/2/", 3, "https://www.fpo.xxx/new-1/3/"),
            Triple("https://www.fpo.xxx/models-2/", 2, "https://www.fpo.xxx/models-2/2/"),
            Triple("https://www.fpo.xxx/sites/nik-porn/", 2, "https://www.fpo.xxx/sites/nik-porn/2/"),
            // Hello.porn
            Triple("https://hello.porn/trending/", 2, "https://hello.porn/trending/2/"),
            Triple("https://hello.porn/categories/4k-video/", 2, "https://hello.porn/categories/4k-video/2/"),
            Triple("https://hello.porn/channels/brazzers/", 2, "https://hello.porn/channels/brazzers/2/"),
            Triple("https://hello.porn/pornstars/videos/W/", 2, "https://hello.porn/pornstars/videos/W/2/"),
            // HQPorner
            Triple("https://hqporner.com", 2, "https://hqporner.com/hdporn/2"),
            Triple("https://hqporner.com/", 2, "https://hqporner.com/hdporn/2"),
            Triple("https://hqporner.com/hdporn/2", 3, "https://hqporner.com/hdporn/3"),
            Triple("https://hqporner.com/category/deepthroat", 2, "https://hqporner.com/category/deepthroat/2"),
            Triple("https://hqporner.com/category/deepthroat/2", 3, "https://hqporner.com/category/deepthroat/3"),
            Triple("https://hqporner.com/?q=braz", 2, "https://hqporner.com/?q=braz&p=2"),
            Triple("https://hqporner.com/?q=braz&p=2", 3, "https://hqporner.com/?q=braz&p=3"),
            // MAX.porn
            Triple("https://max.porn/", 2, "https://max.porn/2/"),
            Triple("https://max.porn/channels/brazzers/", 2, "https://max.porn/channels/brazzers/2/"),
            Triple("https://max.porn/search/desi/", 2, "https://max.porn/search/desi/2/"),
            // Netfapx
            Triple("https://netfapx.com/", 2, "https://netfapx.com/page/2/"),
            Triple("https://netfapx.com/category/asian/", 2, "https://netfapx.com/category/asian/page/2/"),
            Triple("https://netfapx.com/page/2/", 3, "https://netfapx.com/page/3/"),
            // OK.porn
            Triple("https://ok.porn/", 2, "https://ok.porn/2/"),
            Triple("https://ok.porn/channels/", 2, "https://ok.porn/channels/2/"),
            Triple("https://ok.porn/models/", 2, "https://ok.porn/models/2/"),
            Triple("https://ok.porn/models/F/", 2, "https://ok.porn/models/F/2/"),
            // OK.xxx
            Triple("https://ok.xxx/", 2, "https://ok.xxx/2/"),
            Triple("https://ok.xxx/2/", 3, "https://ok.xxx/3/"),
            Triple("https://ok.xxx/channels/videos/", 2, "https://ok.xxx/channels/videos/2/"),
            Triple("https://ok.xxx/search/desi/", 2, "https://ok.xxx/search/desi/2/")
        )

        for ((url, page, expected) in testCases) {
            val siteId = when {
                url.contains("fpo") -> "fpo"
                url.contains("hello") -> "hello"
                url.contains("hqporner") -> "hqporner"
                url.contains("max") -> "max"
                url.contains("netfapx") -> "netfapx"
                url.contains("ok.porn") -> "ok_porn"
                url.contains("ok.xxx") -> "ok_xxx"
                else -> "general"
            }
            val cfg = ProviderConfig(id = siteId, name = siteId, baseUrl = "https://example.com", adapter = "html_selector")
            val adapter = HtmlSelectorAdapter(cfg)
            val actual = adapter.buildPagedUrl(url, page)
            assertEquals("Pagination for $url on page $page should match expected", expected, actual)
        }
    }

    @Test
    fun testFpoAndHelloMediaSourceExtraction() {
        // 1. FPO.xxx single content video page
        val fpoConfig = ProviderConfig(
            id = "fpo",
            name = "FPO.xxx",
            baseUrl = "https://www.fpo.xxx",
            adapter = "html_selector",
            selectors = SelectorConfig(
                player = "video",
                videoSource = "div.kt-player[data-url], [data-url*='.mp4'], [data-url*='/hls/'], video source[src], video[src]"
            )
        )
        val fpoAdapter = HtmlSelectorAdapter(fpoConfig)

        val fpoHtml = """
            <!DOCTYPE html>
            <html>
            <head><title>FPO video</title></head>
            <body>
                <iframe width="300" height="250" src="https://a.adtng.com/get/10007077?time=1562697453361"></iframe>
                <div class="player">
                    <div id="kt_player"></div>
                    <script type="text/javascript">
                        var flashvars = {
                            video_id: '1296852',
                            video_title: 'blonde bitch',
                            video_url: 'https://www.fpo.xxx/get_file/22/8db9326f9a97ab8abdf9699cd4733471/1296000/1296852/1296852.mp4/?v-acctoken=MTQ3MHwyMzc4ODJ8MHxjNjJiNzU3ZGYxMjk1MzVhODI4ZGQ2Y2MzNWQ3MjE5ZA952b9887894f8609',
                            postfix: '.mp4',
                            video_url_text: 'LQ',
                            video_alt_url: 'https://www.fpo.xxx/get_file/22/d7eb5ef29b3c9446f56381e03e582ebe/1296000/1296852/1296852_720p.mp4/?v-acctoken=MjYyMXwyMzc4ODJ8MHxhZDJiZDAxYmRjODVlOTU3YjkxN2NlZDUwMDhkYzdhZg16447961ca5e01a9',
                            video_alt_url_text: 'HQ'
                        };
                        kt_player('kt_player', 'player.swf', '100%', '100%', flashvars);
                    </script>
                </div>
            </body>
            </html>
        """.trimIndent()

        val fpoResult = fpoAdapter.parsePlayableMediaHtml(fpoHtml, "https://www.fpo.xxx/video/1296852/blonde-bitch2/")
        val fpoSources = fpoResult.getOrThrow()
        assertTrue("FPO sources should not be empty", fpoSources.isNotEmpty())
        // Verify HQ stream is prioritized first
        assertTrue("HQ stream 720p should be primary source", fpoSources[0].url.contains("1296852_720p.mp4"))
        assertEquals("video/mp4", fpoSources[0].mimeType)
        assertEquals(MediaSourceType.PROGRESSIVE_MP4, fpoSources[0].type)
        assertEquals("https://www.fpo.xxx/", fpoSources[0].headersRequired["Referer"])
        assertEquals("https://www.fpo.xxx", fpoSources[0].headersRequired["Origin"])
        // Verify ad iframe is never included
        assertTrue("Ad networks must never be included in media sources", fpoSources.none { it.url.contains("adtng") })

        // 2. Hello.porn single content video page with blob and HLS get_file stream
        val helloConfig = ProviderConfig(
            id = "hello",
            name = "Hello.porn",
            baseUrl = "https://hello.porn",
            adapter = "html_selector",
            selectors = SelectorConfig(
                player = "video",
                videoSource = "div.kt-player[data-url], [data-url*='.mp4'], [data-url*='/hls/'], video source[src], video[src]"
            )
        )
        val helloAdapter = HtmlSelectorAdapter(helloConfig)

        val helloHtml = """
            <!DOCTYPE html>
            <html>
            <head><title>Hello video</title></head>
            <body>
                <div class="fluid_video_wrapper">
                    <video id="my-video" class="video-js" src="blob:https://hello.porn/934c9cfa-dfc5-4927-a068-12e0943ef105">
                        <source src="https://hello.porn/get_file/13/9ea4db168f0f797f15bce263ce05b887/786000/786866/786866_360p.mp4/" type="video/mp4" title="360p" label="360p">
                        <source src="https://hello.porn/get_file/13/32bcbf4f5b5798bc22c8e786652b64ca/786000/786866/786866_720p.mp4/" type="video/mp4" title="720p" label="720p">
                    </video>
                </div>
            </body>
            </html>
        """.trimIndent()

        val helloResult = helloAdapter.parsePlayableMediaHtml(helloHtml, "https://hello.porn/videos/786866/")
        val helloSources = helloResult.getOrThrow()
        assertTrue("Hello.porn sources should not be empty", helloSources.isNotEmpty())
        // Verify primary source is prioritized to 720p HD stream
        assertEquals("https://hello.porn/get_file/13/32bcbf4f5b5798bc22c8e786652b64ca/786000/786866/786866_720p.mp4/", helloSources[0].url)
        assertEquals("720p", helloSources[0].quality)
        // Verify detected as HLS stream despite .mp4 extension
        assertEquals(MediaSourceType.HLS, helloSources[0].type)
        assertEquals("application/x-mpegURL", helloSources[0].mimeType)
        assertEquals("https://hello.porn/", helloSources[0].headersRequired["Referer"])
        // Verify secondary quality exists
        assertEquals("https://hello.porn/get_file/13/9ea4db168f0f797f15bce263ce05b887/786000/786866/786866_360p.mp4/", helloSources[1].url)
        assertEquals("360p", helloSources[1].quality)

        // 3. Check disk hello.porn (9).html if present
        val baseDir = findEnglishSitesDir()
        val helloFile = File(File(baseDir, "hello.porn"), "hello.porn (9).html")
        if (helloFile.exists()) {
            val diskHtml = helloFile.readText(Charsets.UTF_8)
            val diskResult = helloAdapter.parsePlayableMediaHtml(diskHtml, "https://hello.porn/videos/786866/")
            assertTrue("Disk hello.porn (9).html should parse media successfully", diskResult.isSuccess)
            val diskSources = diskResult.getOrThrow()
            assertTrue("Disk hello.porn sources should not be empty", diskSources.isNotEmpty())
            assertTrue("Disk sources must not have blob URLs", diskSources.none { it.url.startsWith("blob:") })
            assertEquals(MediaSourceType.HLS, diskSources[0].type)
        }

        // 4. HQPorner single content video page with mydaddy.cc external host embed
        val hqpornerFile = File(File(baseDir, "hqporner.com"), "hqporner.com (10).html")
        if (hqpornerFile.exists()) {
            val hqHtml = hqpornerFile.readText(Charsets.UTF_8)
            val hqDoc = org.jsoup.Jsoup.parse(hqHtml)

            val candidateEmbeds = com.thedesitadka.provider.plugins.HostResolverEngine.extractCandidateUrls(hqDoc)
            assertTrue("Must extract external host embed from hqporner page", candidateEmbeds.isNotEmpty())
            assertEquals("https://mydaddy.cc/video/27549b5f615036a9ca/", candidateEmbeds[0])

            val mydaddyPlugin = com.thedesitadka.provider.plugins.HostResolverEngine.findPluginForUrl(candidateEmbeds[0])
            assertNotNull("HostResolverEngine must match MydaddyResolverPlugin", mydaddyPlugin)
            assertTrue(mydaddyPlugin is com.thedesitadka.provider.plugins.MydaddyResolverPlugin)

            // Simulate resolved response from mydaddy FluidPlayer markup
            val sampleMydaddyHtml = """
                <html><body><script>
                $("#jw").html("<video id=\"flvv\" preload=\"none\" poster=\"//s8.bigcdn.cc/pubs/6aaf39efe25c48.15363868/main.jpg\"><source src=\"//s8.bigcdn.cc/pubs/6aaf39efe25c48.15363868/360.mp4\" title=\"360p\" type=\"video/mp4\" /><source src=\"//s8.bigcdn.cc/pubs/6aaf39efe25c48.15363868/720.mp4\" title=\"720p HD\" type=\"video/mp4\" /><source src=\"//s8.bigcdn.cc/pubs/6aaf39efe25c48.15363868/1080.mp4\" title=\"1080p Full HD\" type=\"video/mp4\" /></video>");
                </script></body></html>
            """.trimIndent()

            val parentDetailUrl = "https://hqporner.com/hdporn/127881-he_can_be_short_if_he_rocks_the_boat.html"
            val resolveResult = (mydaddyPlugin as com.thedesitadka.provider.plugins.MydaddyResolverPlugin).resolveFromHtml(sampleMydaddyHtml, candidateEmbeds[0], parentDetailUrl)
            assertNotNull("Resolution result must not be null", resolveResult)
            assertTrue("Resolution must be successful", resolveResult!!.isSuccess)

            val source = resolveResult.getOrThrow()
            assertEquals("https://s8.bigcdn.cc/pubs/6aaf39efe25c48.15363868/1080.mp4", source.url)
            assertEquals("1080p Full HD", source.quality)
            assertEquals(MediaSourceType.PROGRESSIVE_MP4, source.type)
            assertEquals("video/mp4", source.mimeType)
            assertEquals(parentDetailUrl, source.headersRequired["Referer"])
            assertEquals("https://hqporner.com", source.headersRequired["Origin"])
        }

        // 5. MAX.porn single content video page with direct HLS stream & quality prioritization
        val maxFile = File(File(baseDir, "max.porn"), "max.porn (8).html")
        if (maxFile.exists()) {
            val maxConfig = ProviderConfig(
                id = "max",
                name = "MAX.porn",
                baseUrl = "https://max.porn",
                adapter = "html_selector",
                selectors = SelectorConfig(
                    player = "video",
                    videoSource = "video source[src], video[src]"
                )
            )
            val maxAdapter = HtmlSelectorAdapter(maxConfig)
            val maxHtml = maxFile.readText(Charsets.UTF_8)
            val maxDetailUrl = "https://max.porn/videos/789090/white-shirt-pulled-up-br/"
            val maxResult = maxAdapter.parsePlayableMediaHtml(maxHtml, maxDetailUrl)
            assertTrue("max.porn (8).html must parse media successfully", maxResult.isSuccess)
            val maxSources = maxResult.getOrThrow()
            assertTrue("max.porn sources should not be empty", maxSources.isNotEmpty())

            // Verify blob is filtered out
            assertTrue("Blob URLs must be filtered out", maxSources.none { it.url.startsWith("blob:") })

            // Verify primary source is prioritized to 720p HD stream
            assertEquals("https://max.porn/get_file/13/b4fedf0ddbf069ad817be3a9ed50aaa1/789000/789090/789090_720p.mp4/", maxSources[0].url)
            assertEquals("720p", maxSources[0].quality)
            assertEquals(MediaSourceType.HLS, maxSources[0].type)
            assertEquals("application/x-mpegURL", maxSources[0].mimeType)
            assertEquals("https://max.porn/", maxSources[0].headersRequired["Referer"])
            assertEquals("https://max.porn", maxSources[0].headersRequired["Origin"])

            // Verify secondary qualities exist and are deduplicated
            assertTrue("Must contain multiple distinct qualities", maxSources.size >= 3)
            val qualities = maxSources.map { it.quality }
            assertTrue("Must contain 720p, 480p, 360p", qualities.contains("720p") && qualities.contains("480p") && qualities.contains("360p"))
        }

        // 6. MAX.porn multi-level navigation and channel vs video classification
        val maxHomeFile = File(File(baseDir, "max.porn"), "max.porn.html")
        val maxChannelFile = File(File(baseDir, "max.porn"), "max.porn (2).html")
        if (maxHomeFile.exists() && maxChannelFile.exists()) {
            val maxNavConfig = ProviderConfig(
                id = "max",
                name = "MAX.porn",
                baseUrl = "https://max.porn",
                adapter = "html_selector",
                selectors = SelectorConfig(
                    item = "div.item:not(.swiper-slide):not(.pfb-slider-group *):not(.page):not(.jump):not(.last):not(.next):not(.prev)",
                    title = "a.title, [title], a",
                    thumbnail = "img",
                    thumbnailAttr = "data-src, src",
                    detailUrl = "a[href*='/videos/'], a[href*='/channels/'], a",
                    duration = ".duration_item, .duration"
                )
            )
            val navAdapter = HtmlSelectorAdapter(maxNavConfig)

            // Home: All items must be channels (isCategory = true)
            val homeFeed = navAdapter.parseListingHtml(maxHomeFile.readText(Charsets.UTF_8), 1).getOrThrow()
            assertEquals("Home page must extract 50 channels", 50, homeFeed.items.size)
            assertTrue("All home page items must be classified as categories/channels", homeFeed.items.all { it.isCategory })
            assertTrue("No slider elements should be extracted", homeFeed.items.none { it.detailUrl.contains("/label/") })

            // Channel: All items must be videos (isCategory = false)
            val chanFeed = navAdapter.parseListingHtml(maxChannelFile.readText(Charsets.UTF_8), 1).getOrThrow()
            assertEquals("Channel page must extract 25 videos", 25, chanFeed.items.size)
            assertTrue("All channel page items must be classified as playable videos", chanFeed.items.none { it.isCategory })
            assertTrue("Channel videos must have durationSeconds and non-blank titles", chanFeed.items.all { it.durationSeconds != null && it.title.isNotBlank() })
        }
    }
}
