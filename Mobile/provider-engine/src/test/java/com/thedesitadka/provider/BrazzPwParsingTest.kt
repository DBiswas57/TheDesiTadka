package com.thedesitadka.provider

import com.thedesitadka.core.model.ContentPolicy
import com.thedesitadka.core.model.NavigationConfig
import com.thedesitadka.core.model.ProviderCapability
import com.thedesitadka.core.model.ProviderConfig
import com.thedesitadka.core.model.SelectorConfig
import com.thedesitadka.core.model.isCategoryUrl
import com.thedesitadka.provider.adapters.HtmlSelectorAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class BrazzPwParsingTest {

    private lateinit var config: ProviderConfig
    private lateinit var adapter: HtmlSelectorAdapter

    private fun findDirectory(folderName: String): File? {
        val candidates = listOf(
            File(folderName),
            File("../$folderName"),
            File("../../$folderName"),
            File("c:/Users/LearnersYT/source/TheDesiTadka/$folderName")
        )
        return candidates.firstOrNull { it.exists() && it.isDirectory }
    }

    @Before
    fun setUp() {
        config = ProviderConfig(
            id = "brazzpw",
            familyId = "clean_tube_family",
            domains = listOf("https://brazzpw.xyz", "https://brazzpw.com"),
            validationMarker = "brazzpw",
            name = "BrazzPW",
            enabled = true,
            baseUrl = "https://brazzpw.xyz",
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
                home = "/videos/free-brazz-premium-full-new-2026/",
                search = "/search/free-brazz-premium-full-new-2026/?s={query}",
                page = "/videos/page/{page}/free-brazz-premium-full-new-2026/",
                categories = "/categories/free-brazz-premium-full-new-2026/"
            ),
            selectors = SelectorConfig(
                item = "div.videos-list article.thumb-block:not(.slide):not(.bx-clone), div.videos-list .loop-video:not(.slide):not(.bx-clone), article.thumb-block:not(.slide):not(.bx-clone), div.content-area article",
                title = "a[title], h2.entry-title a, h2 a, a.title, .entry-title",
                thumbnail = "img.video-main-thumb, img",
                thumbnailAttr = "data-src, src",
                detailUrl = "a[href*='/video/'], a[href*='/videos/'], a[href*='brazzpw.xyz/'], a[href*='brazzpw.com/'], a",
                duration = ".duration",
                detailTitle = "h1.entry-title, h1",
                detailDescription = ".entry-content p, p",
                detailThumbnail = "meta[property='og:image'], img",
                player = "iframe[src*='player'], video",
                videoSource = "video source[src], video[src], source[type='video/mp4']",
                videoSourceAttr = "src"
            ),
            contentPolicy = ContentPolicy(
                rightsStatus = "Public Aggregation Index",
                disclaimer = "Aggregated public media feed."
            )
        )
        adapter = HtmlSelectorAdapter(config)
    }

    @Test
    fun testBrazzPwHomePageNoCarouselDuplicates() {
        val siteFolder = findDirectory("EnglishSites/brazzpw.com")
        assertNotNull("EnglishSites/brazzpw.com directory must exist", siteFolder)
        val homeHtmlFile = File(siteFolder!!, "brazzpw.xyz (1).html")
        assertTrue("brazzpw.xyz (1).html must exist", homeHtmlFile.exists())

        val html = homeHtmlFile.readText(Charsets.UTF_8)
        val feedResult = adapter.parseListingHtml(html, page = 1)
        assertTrue("Home page parse result must be success", feedResult.isSuccess)

        val feedPage = feedResult.getOrThrow()
        val items = feedPage.items
        assertTrue("Home page must extract items (got ${items.size})", items.isNotEmpty())

        // Verify items have titles and valid detail URLs
        for (item in items) {
            assertFalse("Item title must not be blank", item.title.isBlank())
            assertFalse("Item detailUrl must not be blank", item.detailUrl.isBlank())
            assertTrue("Item detailUrl must be absolute or valid path", item.detailUrl.startsWith("http"))
        }

        // Verify carousel items with .slide or .bx-clone are excluded and not polluting the list
        val uniqueUrls = items.map { it.detailUrl }.toSet()
        assertTrue("Extracted items should be substantially unique", uniqueUrls.size >= items.size - 2)
    }

    @Test
    fun testBrazzPwVideosCatalogAndPagination() {
        val siteFolder = findDirectory("EnglishSites/brazzpw.com")
        assertNotNull(siteFolder)
        val videosHtmlFile = File(siteFolder!!, "brazzpw.xyz (2).html")
        assertTrue("brazzpw.xyz (2).html must exist", videosHtmlFile.exists())

        val html = videosHtmlFile.readText(Charsets.UTF_8)
        val feedResult = adapter.parseListingHtml(html, page = 2)
        assertTrue("Videos catalog parse result must be success", feedResult.isSuccess)

        val feedPage = feedResult.getOrThrow()
        assertEquals("Must extract exactly 20 videos from page 2 catalog", 20, feedPage.items.size)

        // Verify pagination URL construction with trailing theme slug
        val homeVideosUrl = "https://brazzpw.xyz/videos/free-brazz-premium-full-new-2026/"
        val pagedUrl2 = adapter.buildPagedUrl(homeVideosUrl, 2)
        assertEquals(
            "Page 2 URL must insert /page/2/ before the theme slug",
            "https://brazzpw.xyz/videos/page/2/free-brazz-premium-full-new-2026/",
            pagedUrl2
        )

        val pagedUrl3 = adapter.buildPagedUrl(homeVideosUrl, 3)
        assertEquals(
            "Page 3 URL must insert /page/3/ before the theme slug",
            "https://brazzpw.xyz/videos/page/3/free-brazz-premium-full-new-2026/",
            pagedUrl3
        )

        // Verify category-specific pagination preserves the tag/category path and inserts page before slug
        val tagUrl = "https://brazzpw.xyz/videos/tags/79/milf/free-brazz-premium-full-new-2026/"
        val catPagedUrl2 = adapter.buildPagedUrl(tagUrl, 2)
        assertEquals(
            "Category pagination URL must insert /page/2/ before the theme slug",
            "https://brazzpw.xyz/videos/tags/79/milf/page/2/free-brazz-premium-full-new-2026/",
            catPagedUrl2
        )
    }

    @Test
    fun testBrazzPwCategoriesPageNoPaginationNumbers() {
        val siteFolder = findDirectory("EnglishSites/brazzpw.com")
        assertNotNull(siteFolder)
        val categoriesHtmlFile = File(siteFolder!!, "brazzpw.xyz (6).html")
        assertTrue("brazzpw.xyz (6).html must exist", categoriesHtmlFile.exists())

        val html = categoriesHtmlFile.readText(Charsets.UTF_8)
        val categories = adapter.parseCategoriesHtml(html)

        assertTrue("Categories must be extracted from categories page", categories.isNotEmpty())

        // Verify that NO category name is a pagination number or pagination keyword
        val paginationKeywords = setOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "Next", "End", "Prev", "Previous", "»", "«")
        for (cat in categories) {
            assertFalse(
                "Category name '${cat.name}' must not be a pagination digit or keyword",
                cat.name in paginationKeywords || cat.name.toIntOrNull() != null
            )
            assertFalse("Category name must not be blank", cat.name.isBlank())
            assertFalse("Category url must not be blank", cat.url.isBlank())
        }

        // Verify expected categories are present
        val categoryNames = categories.map { it.name }
        assertTrue("Must contain Lesbian", categoryNames.contains("Lesbian"))
        assertTrue("Must contain Black", categoryNames.contains("Black"))
    }

    @Test
    fun testBrazzPwMultiLevelCategoryAndDetailUrls() {
        // Multi-level category/site/channel/model URLs must be detected as categories
        assertTrue(
            "Categories hub URL must be recognized as category",
            isCategoryUrl("https://brazzpw.xyz/categories/free-brazz-premium-full-new-2026/")
        )
        assertTrue(
            "Site list URL must be recognized as category/multi-level",
            isCategoryUrl("https://brazzpw.xyz/videos/site/12/bangbros/free-brazz-premium-full-new-2026/")
        )
        assertTrue(
            "Tag list URL must be recognized as category/multi-level",
            isCategoryUrl("https://brazzpw.xyz/videos/tags/79/milf/free-brazz-premium-full-new-2026/")
        )
        assertTrue(
            "Model list URL must be recognized as category/multi-level",
            isCategoryUrl("https://brazzpw.xyz/videos/models/25/eva-elfie/free-brazz-premium-full-new-2026/")
        )
        assertTrue(
            "Sites directory URL must be recognized as category/multi-level",
            isCategoryUrl("https://brazzpw.xyz/sites/free-brazz-premium-full-new-2026/")
        )

        // Single video URLs must NOT be treated as categories
        assertFalse(
            "Single video detail URL with /video/<id>/ must NOT be recognized as category",
            isCategoryUrl("https://brazzpw.xyz/video/8277401/the-big-oral/")
        )
        assertFalse(
            "Single video detail URL with /videos/<id>/ must NOT be recognized as category",
            isCategoryUrl("https://brazzpw.xyz/videos/8277401/the-big-oral/")
        )
    }

    @Test
    fun testBrazzPwDetailPagePlayerDetection() {
        val siteFolder = findDirectory("EnglishSites/brazzpw.com")
        assertNotNull(siteFolder)
        val detailHtmlFile = File(siteFolder!!, "brazzpw.xyz (11).html")
        assertTrue("brazzpw.xyz (11).html must exist", detailHtmlFile.exists())

        val html = detailHtmlFile.readText(Charsets.UTF_8)
        val doc = org.jsoup.Jsoup.parse(html, config.baseUrl)

        // Verify player iframe exists and contains brazzpw.xyz/player/?id=
        val playerIframe = doc.selectFirst("iframe[src*='player'], iframe")
        assertNotNull("Player iframe must be found in detail page", playerIframe)
        val iframeSrc = playerIframe?.attr("src") ?: ""
        assertTrue("Iframe src must contain player query", iframeSrc.contains("/player/"))
    }

    @Test
    fun testBrazzPwPlayerIframeStreamExtraction() {
        val scratchPlayerFile = File("c:/Users/LearnersYT/source/TheDesiTadka/scratch/brazzpw_player.html")
        val iframeHtml = if (scratchPlayerFile.exists()) {
            scratchPlayerFile.readText(Charsets.UTF_8)
        } else {
            """
            <video id="my-video" class="video-js vjs-fluid" controls preload="auto"></video>
            <script>
            var player = videojs('my-video');
            player.src({type: "application/x-mpegURL", selected: "true", label:"auto", res: "auto", src: "m3u8_11522431.m3u8?hash=178983&time=178983" });
            </script>
            """.trimIndent()
        }

        val iframeSrc = "https://brazzpw.xyz/player/?id=11522431&p=aHR0cHM6Ly9tZWRpYS..."
        val fullUrl = "https://brazzpw.xyz/video/11522431/unmasking-a-threesome/free-brazz-premium-full-new-2026/"
        val embedHeaders = mapOf(
            "User-Agent" to "Mozilla/5.0",
            "Referer" to iframeSrc,
            "Origin" to "https://brazzpw.xyz"
        )

        val sources = adapter.parseIframePlayerMedia(iframeHtml, iframeSrc, fullUrl, embedHeaders)
        assertFalse("Extracted media sources must not be empty", sources.isEmpty())

        val primary = sources.first()
        assertEquals("Must be HLS stream", com.thedesitadka.core.model.MediaSourceType.HLS, primary.type)
        assertEquals("MIME type must be application/x-mpegURL", "application/x-mpegURL", primary.mimeType)
        assertEquals(
            "URL must be resolved relative to iframeSrc (/player/)",
            "https://brazzpw.xyz/player/m3u8_11522431.m3u8?hash=178983&time=178983",
            primary.url
        )
        assertEquals("Referer header must be iframeSrc", iframeSrc, primary.headersRequired["Referer"])
    }

    @Test
    fun testBrazzPwCloudflareChallengeDetectedInListing() {
        val challengeHtml = "<html><head><title>Just a moment...</title></head><body>cf-browser-verification</body></html>"
        val result = adapter.parseListingHtml(challengeHtml, page = 1)
        assertTrue("Listing parsing must fail on Cloudflare challenge", result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue("Exception must be CloudflareChallengeException", exception is com.thedesitadka.core.network.CloudflareChallengeException)
    }
}
