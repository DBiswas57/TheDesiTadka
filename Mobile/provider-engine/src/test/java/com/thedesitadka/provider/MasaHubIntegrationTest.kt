package com.thedesitadka.provider

import com.thedesitadka.core.model.NavigationConfig
import com.thedesitadka.core.model.ProviderCapability
import com.thedesitadka.core.model.ProviderConfig
import com.thedesitadka.core.model.SelectorConfig
import com.thedesitadka.provider.adapters.HtmlSelectorAdapter
import com.thedesitadka.provider.plugins.HostResolverEngine
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class MasaHubIntegrationTest {

    private lateinit var config: ProviderConfig
    private lateinit var adapter: HtmlSelectorAdapter

    @Before
    fun setUp() {
        config = ProviderConfig(
            id = "masahub",
            familyId = "desi_family",
            domains = listOf("https://masahub.net.im"),
            validationMarker = "masahub",
            name = "MasaHub",
            enabled = true,
            baseUrl = "https://masahub.net.im",
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
                home = "/",
                search = "/?s={query}",
                page = "/page/{page}/",
                categories = "/categories/"
            ),
            selectors = SelectorConfig(
                item = "main.site-main article.loop-video, article.loop-video",
                title = "header.entry-header span, a[title], header.entry-header h2 a",
                thumbnail = "img[data-src], img[src]",
                thumbnailAttr = "data-src, src",
                detailUrl = "a",
                duration = "span.duration, .duration",
                detailTitle = "h1.entry-title, h1, title",
                detailDescription = ".entry-content p, .description",
                detailThumbnail = "meta[property='og:image'], img",
                player = "iframe[src*='playmate'], iframe[src*='lulust'], iframe[src*='lulu'], video, iframe",
                videoSource = "video source[src], video[src]",
                videoSourceAttr = "src",
                relatedItems = "article.loop-video"
            )
        )
        adapter = HtmlSelectorAdapter(config)
    }

    @Test
    fun testProviderCapabilitiesAndConfig() {
        assertEquals("masahub", config.id)
        assertEquals("MasaHub", config.name)
        assertEquals("https://masahub.net.im", config.baseUrl)
        assertTrue(config.capabilities.contains(ProviderCapability.HOME))
        assertTrue(config.capabilities.contains(ProviderCapability.CATEGORY))
        assertTrue(config.capabilities.contains(ProviderCapability.SEARCH))
        assertTrue(config.capabilities.contains(ProviderCapability.DETAILS))
        assertTrue(config.capabilities.contains(ProviderCapability.STREAM))
        assertTrue(config.capabilities.contains(ProviderCapability.DOWNLOAD))
    }

    @Test
    fun testContentListParsingNoCarouselContent() {
        val file = File("c:/Users/LearnersYT/source/TheDesiTadka/scratch/masahub_home.html")
        assertTrue("masahub_home.html must exist", file.exists())
        val html = file.readText(Charsets.UTF_8)

        val feedResult = adapter.parseListingHtml(html, page = 1)
        assertTrue("Listing parse must succeed", feedResult.isSuccess)
        val feed = feedResult.getOrThrow()

        assertEquals(20, feed.items.size)
        assertTrue(feed.hasNextPage)

        val first = feed.items[0]
        assertFalse("First video title must not contain raw duration badge prefix", first.title.startsWith("HD1K07"))
        assertTrue("First video title should contain clean description", first.title.contains("Horny Indian GF", ignoreCase = true))
        assertTrue("Detail url must be resolved", first.detailUrl.startsWith("https://masahub.net.im/"))
        assertTrue("Thumbnail url must be valid", first.thumbnailUrl.startsWith("http"))
        assertEquals("Duration should be parsed to seconds", 420L, first.durationSeconds) // 07:00 = 420s
        assertTrue("Qualities should detect 720p HD", first.qualities.contains("720p HD"))
    }

    @Test
    fun testCategoryListAndMenuItemsParsing() {
        val file = File("c:/Users/LearnersYT/source/TheDesiTadka/scratch/masahub_categories.html")
        assertTrue("masahub_categories.html must exist", file.exists())
        val html = file.readText(Charsets.UTF_8)

        val categories = adapter.parseCategoriesHtml(html)
        assertTrue("Categories must not be empty", categories.isNotEmpty())
        assertTrue("Total categories should be at least 15", categories.size >= 15)

        val categoryNames = categories.map { it.name.lowercase() }
        assertTrue("Must include Desi Porn Video", categoryNames.any { it.contains("desi porn") })
        assertTrue("Must include Model Video", categoryNames.any { it.contains("model video") })
        assertTrue("Must include HitPrime", categoryNames.any { it.contains("hitprime") })

        // Ensure pagination labels are not parsed as categories
        assertFalse("Pagination labels must not be in categories", categoryNames.contains("next"))
        assertFalse("Pagination labels must not be in categories", categoryNames.contains("prev"))
    }

    @Test
    fun testNavigationAndPagination() {
        val pagedHome = adapter.buildPagedUrl("https://masahub.net.im/", 2)
        assertEquals("https://masahub.net.im/page/2/", pagedHome)

        val pagedSearch = adapter.buildPagedUrl("https://masahub.net.im/?s=indian", 3)
        assertTrue("Search pagination must include page parameter", pagedSearch.contains("page=3") || pagedSearch.contains("/page/3/"))
    }

    @Test
    fun testSinglePageContentAndResolutionBadge() {
        val file = File("c:/Users/LearnersYT/source/TheDesiTadka/scratch/masahub_detail1.html")
        assertTrue("masahub_detail1.html must exist", file.exists())
        val html = file.readText(Charsets.UTF_8)
        val detailUrl = "https://masahub.net.im/horny-indian-gf-sucking-dick-riding-fucking-hd-video/"

        val detailResult = adapter.parseDetailsHtml(html, detailUrl)
        assertTrue("Details parse must succeed", detailResult.isSuccess)
        val item = detailResult.getOrThrow()

        assertNotNull(item)
        assertTrue("Detail title should contain video subject", item.title.contains("Horny Indian GF", ignoreCase = true))
        assertTrue("Qualities should detect 720p HD", item.qualities.contains("720p HD"))
        assertTrue("Formats should support HLS and MP4", item.formats.contains("HLS") && item.formats.contains("MP4"))
        assertTrue("Tags should not be empty", item.tags.isNotEmpty())

        // Critical: Masahub is a single playback site, not a series site
        assertFalse("Must NOT be identified as series", item.isSeries)
        assertTrue("Seasons list must be empty for single playback", item.seasons.isEmpty())
    }

    @Test
    fun testRelatedContentExtraction() {
        val file = File("c:/Users/LearnersYT/source/TheDesiTadka/scratch/masahub_detail1.html")
        assertTrue("masahub_detail1.html must exist", file.exists())
        val html = file.readText(Charsets.UTF_8)
        val doc = Jsoup.parse(html, config.baseUrl)

        val relatedSelector = config.selectors?.relatedItems ?: "article.loop-video"
        val elements = doc.select(relatedSelector)

        val items = elements.mapNotNull { el ->
            val linkEl = el.select("a").firstOrNull() ?: return@mapNotNull null
            val href = linkEl.attr("href").trim()
            val resolvedHref = "https://masahub.net.im" + (if (href.startsWith("/")) href else "/$href")
            if (href.isBlank() || href.contains("ottlinks.top") || href.contains("/category/")) return@mapNotNull null

            val rawTitle = el.select("header.entry-header span, a[title], h2, .title, a").text().trim()
            val cleanedTitle = rawTitle.replace(Regex("""^(?:HD|FHD|4K|3K|2K|SD|\d+:\d+|\d+%\s*)+\s*"""), "").trim().ifEmpty { rawTitle }
            if (cleanedTitle.isNotBlank()) cleanedTitle else null
        }

        assertEquals("Must extract 8 valid related videos", 8, items.size)
        assertTrue(items[0].contains("Extremely Young Wife", ignoreCase = true))
        assertTrue(items[1].contains("Beautiful Slim Girl", ignoreCase = true))
    }

    @Test
    fun testEmbedResolutionIntegration() {
        val file = File("c:/Users/LearnersYT/source/TheDesiTadka/scratch/masahub_detail1.html")
        val html = file.readText(Charsets.UTF_8)
        val doc = Jsoup.parse(html, config.baseUrl)

        val candidateHosts = HostResolverEngine.extractCandidateUrls(doc)
        assertTrue("HostResolverEngine must find playmate embed URL", candidateHosts.any { it.contains("playmate.to") })
    }
}
