package com.thedesitadka.provider

import com.thedesitadka.core.model.ContentCategoryDefinition
import com.thedesitadka.core.model.MediaSourceType
import com.thedesitadka.core.model.NavigationConfig
import com.thedesitadka.core.model.ProviderCapability
import com.thedesitadka.core.model.ProviderConfig
import com.thedesitadka.core.model.SelectorConfig
import com.thedesitadka.provider.adapters.HtmlSelectorAdapter
import com.thedesitadka.provider.plugins.HostResolverEngine
import com.thedesitadka.provider.plugins.UpnsResolverPlugin
import com.thedesitadka.provider.plugins.VidaraResolverPlugin
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LiveCamRipsIntegrationTest {

    private lateinit var providerConfig: ProviderConfig
    private lateinit var adapter: HtmlSelectorAdapter

    @Before
    fun setUp() {
        providerConfig = ProviderConfig(
            id = "livecamrips",
            familyId = "desi_family",
            domains = listOf("https://livecamrips.to", "livecamrips.to"),
            validationMarker = "livecamrips",
            name = "LiveCamRips",
            enabled = true,
            baseUrl = "https://livecamrips.to",
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
                home = "/model/492550/V_Hot_Shila",
                search = "/search/{query}",
                page = "/model/492550/V_Hot_Shila?page={page}",
                categories = "/models"
            ),
            selectors = SelectorConfig(
                item = "div.jc-caption",
                title = "div.tm-text-gray, figcaption, a[href*='/model/'], a[href*='/video/'], a",
                thumbnail = "img[data-src], img[src]",
                thumbnailAttr = "src",
                detailUrl = "figure a[href*='/video/'], figure a[href*='/model/'], a[href*='/video/'], a[href*='/model/'], a",
                duration = "figcaption span, span.tm-bg-dark, .tm-text-gray",
                detailTitle = "h1, h2, title",
                detailDescription = "div.tm-text-primary, p, .description",
                detailThumbnail = "meta[property='og:image'], img",
                player = "iframe[src*='uqload'], iframe[src*='abstream'], iframe[src*='mixdrop'], iframe[src*='mdzsmutpcvykb'], iframe",
                videoSource = "iframe[src*='uqload'], iframe[src*='abstream'], iframe[src*='mixdrop'], iframe[src*='mdzsmutpcvykb'], iframe",
                videoSourceAttr = "src",
                relatedItems = "div.jc-caption"
            )
        )
        adapter = HtmlSelectorAdapter(providerConfig)
    }

    @Test
    fun testDesiCategoryInclusion() {
        val desiCat = ContentCategoryDefinition.DEFAULT_CATEGORIES.find { it.id == "desi" }
        assertNotNull("Desi category must exist", desiCat)
        assertTrue("LiveCamRips must be included in Desi category providerIds", desiCat!!.providerIds.contains("livecamrips"))
    }

    @Test
    fun testModelPageFeedParsing() {
        val modelPageHtml = """
            <!DOCTYPE html>
            <html>
            <head><title>livecamrips.to V_Hot_Shila</title></head>
            <body>
            <div class="row">
                <div class="col-xl-3 col-lg-3 col-md-6 col-sm-6 col-12 mb-5 jc-caption">
                    <figure class="tm-video-item" video-id="16129364">
                        <a href="https://livecamrips.to/video/16129364">
                            <img alt="Image" class="img-fluid" src="https://e17.uqload.vc/i/02/05836/vzwobtd0gp22.jpg"/>
                            <figcaption class="d-flex align-items-end justify-content-end">
                                <span class="tm-bg-dark">25:36</span>
                            </figcaption>
                        </a>
                    </figure>
                    <div class="d-flex justify-content-between tm-text-gray">
                        <a href="https://livecamrips.to/model/492550/V_Hot_Shila">
                            <span class="tm-text-gray-light">V_Hot_Shila</span>
                        </a>
                        <span>2026-10-04 23:59</span>
                    </div>
                </div>
                <div class="col-xl-3 col-lg-3 col-md-6 col-sm-6 col-12 mb-5 jc-caption">
                    <figure class="tm-video-item" video-id="16128511">
                        <a href="https://livecamrips.to/video/16128511">
                            <img alt="Image" class="img-fluid" src="https://images.livecamrips.to/1612/16128511.png"/>
                            <figcaption class="d-flex align-items-end justify-content-end">
                                <span class="tm-bg-dark">08:04</span>
                            </figcaption>
                        </a>
                    </figure>
                    <div class="d-flex justify-content-between tm-text-gray">
                        <a href="https://livecamrips.to/model/492550/V_Hot_Shila">
                            <span class="tm-text-gray-light">V_Hot_Shila</span>
                        </a>
                        <span>2026-10-04 23:15</span>
                    </div>
                </div>
            </div>
            </body>
            </html>
        """.trimIndent()

        val result = adapter.parseListingHtml(modelPageHtml, 1)
        assertTrue("Parsing model page must succeed", result.isSuccess)
        val feed = result.getOrThrow()

        assertEquals(2, feed.items.size)

        val firstItem = feed.items[0]
        assertEquals("https://livecamrips.to/video/16129364", firstItem.detailUrl)
        assertTrue("Title should contain V_Hot_Shila", firstItem.title.contains("V_Hot_Shila"))
        assertEquals(1536L, firstItem.durationSeconds) // 25:36 = 1536s
        assertEquals("https://e17.uqload.vc/i/02/05836/vzwobtd0gp22.jpg", firstItem.thumbnailUrl)
        assertFalse("Single video page must NOT be category", firstItem.isCategory)

        val secondItem = feed.items[1]
        assertEquals("https://livecamrips.to/video/16128511", secondItem.detailUrl)
        assertEquals(484L, secondItem.durationSeconds) // 08:04 = 484s
        assertFalse(secondItem.isCategory)
    }

    @Test
    fun testSearchPageModelCardsParsing() {
        val searchPageHtml = """
            <!DOCTYPE html>
            <html>
            <head><title>livecamrips.to Search: V_Hot_Shila</title></head>
            <body>
            <div class="row">
                <div class="col-xl-3 col-lg-3 col-md-6 col-sm-6 col-12 mb-5 jc-caption">
                    <figure class="tm-video-item" video-id="497464">
                        <a href="https://livecamrips.to/model/497464/V-Hot-Shilaa">
                            <img alt="Image" class="img-fluid" src="https://images.livecamrips.to/1616/16168829.png"/>
                        </a>
                    </figure>
                    <div class="d-flex justify-content-between tm-text-gray">
                        <a href="https://livecamrips.to/model/497464/V-Hot-Shilaa">
                            <span class="tm-text-gray-light">V-Hot-Shilaa</span>
                        </a>
                    </div>
                </div>
                <div class="col-xl-3 col-lg-3 col-md-6 col-sm-6 col-12 mb-5 jc-caption">
                    <figure class="tm-video-item" video-id="492550">
                        <a href="https://livecamrips.to/model/492550/V_Hot_Shila">
                            <img alt="Image" class="img-fluid" src="https://images.livecamrips.to/1612/16129364.png"/>
                        </a>
                    </figure>
                    <div class="d-flex justify-content-between tm-text-gray">
                        <a href="https://livecamrips.to/model/492550/V_Hot_Shila">
                            <span class="tm-text-gray-light">V_Hot_Shila</span>
                        </a>
                    </div>
                </div>
            </div>
            </body>
            </html>
        """.trimIndent()

        val result = adapter.parseListingHtml(searchPageHtml, 1)
        assertTrue("Parsing search page must succeed", result.isSuccess)
        val feed = result.getOrThrow()

        assertEquals(2, feed.items.size)

        val firstModel = feed.items[0]
        assertEquals("https://livecamrips.to/model/497464/V-Hot-Shilaa", firstModel.detailUrl)
        assertEquals("V-Hot-Shilaa", firstModel.title)
        assertTrue("Model link in search must be marked as category for navigation", firstModel.isCategory)

        val secondModel = feed.items[1]
        assertEquals("https://livecamrips.to/model/492550/V_Hot_Shila", secondModel.detailUrl)
        assertEquals("V_Hot_Shila", secondModel.title)
        assertTrue("V_Hot_Shila model link in search must be marked as category for navigation", secondModel.isCategory)
    }

    @Test
    fun testDetailPageAndHostResolving() {
        val detailPageHtml = """
            <!DOCTYPE html>
            <html>
            <head><title>livecamrips.to V_Hot_Shila - Stripchat</title></head>
            <body>
                <h1 class="tm-text-primary">V_Hot_Shila - Stripchat - 2026-10-04 23:59</h1>
                <iframe src="https://uqload.vc/embed-vzwobtd0gp22.html" width="100%" height="500"></iframe>
            </body>
            </html>
        """.trimIndent()

        val detailResult = adapter.parseDetailsHtml(detailPageHtml, "https://livecamrips.to/video/16129364")
        assertTrue("Details parsing must succeed", detailResult.isSuccess)
        val item = detailResult.getOrThrow()
        assertTrue(item.title.contains("V_Hot_Shila"))

        // Test HostResolverEngine detects the uqload embed
        val doc = Jsoup.parse(detailPageHtml, "https://livecamrips.to/video/16129364")
        val candidates = HostResolverEngine.extractCandidateUrls(doc)
        assertEquals(1, candidates.size)
        assertEquals("https://uqload.vc/embed-vzwobtd0gp22.html", candidates[0])
    }

    @Test
    fun testPaginationUrlBuilding() {
        val pagedUrl = adapter.buildPagedUrl("https://livecamrips.to/model/492550/V_Hot_Shila", 2)
        assertEquals("https://livecamrips.to/model/492550/V_Hot_Shila/2", pagedUrl)

        val pagedWithExistingPage = adapter.buildPagedUrl("https://livecamrips.to/model/492550/V_Hot_Shila/2", 3)
        assertEquals("https://livecamrips.to/model/492550/V_Hot_Shila/3", pagedWithExistingPage)

        val pagedWithQuery = adapter.buildPagedUrl("https://livecamrips.to/model/492550/V_Hot_Shila/2?sort=views", 4)
        assertEquals("https://livecamrips.to/model/492550/V_Hot_Shila/4?sort=views", pagedWithQuery)
    }

    @Test
    fun testHasNextPageDetection() {
        val htmlPageWithNext = """
            <div class="row tm-mb-90">
                <div class="jc-caption">
                    <figure><a href="/video/1"><img src="/1.jpg"/></a></figure>
                    <div class="tm-text-gray">Video 1</div>
                </div>
                <div class="col-12 tm-paging-col">
                    <a href="https://livecamrips.to/model/492550/V_Hot_Shila/2" class="btn btn-primary tm-btn-next ">Next Page</a>
                </div>
            </div>
        """.trimIndent()
        val feedWithNext = adapter.parseListingHtml(htmlPageWithNext, 1).getOrThrow()
        assertTrue("Page with active next button should have hasNextPage = true", feedWithNext.hasNextPage)

        val htmlPageLast = """
            <div class="row tm-mb-90">
                <div class="jc-caption">
                    <figure><a href="/video/2"><img src="/2.jpg"/></a></figure>
                    <div class="tm-text-gray">Video 2</div>
                </div>
                <div class="col-12 tm-paging-col">
                    <a href="#" class="btn btn-primary tm-btn-next disabled">Next Page</a>
                </div>
            </div>
        """.trimIndent()
        val feedLast = adapter.parseListingHtml(htmlPageLast, 5).getOrThrow()
        assertFalse("Page with disabled next button should have hasNextPage = false", feedLast.hasNextPage)
    }

    @Test
    fun testUpnsEmbedDetectionOnLiveCamRips() {
        val detailPageHtml = """
            <!DOCTYPE html>
            <html>
            <head><title>livecamrips.to V_Hot_Shila - Stripchat</title></head>
            <body>
            <div class="row tm-mb-90">
                <div class="col-xl-8 col-lg-7 col-md-6 col-sm-12">
                    <iframe id="iframe-video" class="embed-responsive-item" src="https://videosh.upns.live/#c1nk65&amp;ua=1" scrolling="no" frameborder="0" allowfullscreen="true"></iframe>
                </div>
                <div class="col-xl-4 col-lg-5 col-md-6 col-sm-12 tm-video-description-box">
                    <h2 class="mb-5 tm-video-title">V_Hot_Shila (15678156)</h2>
                </div>
            </div>
            </body>
            </html>
        """.trimIndent()

        val doc = Jsoup.parse(detailPageHtml, "https://livecamrips.to/video/15678156")
        val candidates = HostResolverEngine.extractCandidateUrls(doc)
        assertEquals(1, candidates.size)
        assertTrue("Candidate must be upns embed", candidates[0].contains("videosh.upns.live"))

        val plugin = HostResolverEngine.findPluginForUrl(candidates[0])
        assertNotNull("Plugin must be found for upns", plugin)
        assertTrue("Plugin must be UpnsResolverPlugin", plugin is UpnsResolverPlugin)
    }

    @Test
    fun testVidaraEmbedDetectionOnLiveCamRips() {
        val detailPageHtml = """
            <!DOCTYPE html>
            <html>
            <head><title>livecamrips.to V_Hot_Shila - Stripchat</title></head>
            <body>
            <div class="row tm-mb-90">
                <div class="col-xl-8 col-lg-7 col-md-6 col-sm-12">
                    <iframe id="iframe-video" class="embed-responsive-item" src="https://vidara.so/e/cb04fe306625" scrolling="no" frameborder="0" allowfullscreen="true"></iframe>
                </div>
                <div class="col-xl-4 col-lg-5 col-md-6 col-sm-12 tm-video-description-box">
                    <h2 class="mb-5 tm-video-title">V_Hot_Shila (15499805)</h2>
                </div>
            </div>
            </body>
            </html>
        """.trimIndent()

        val doc = Jsoup.parse(detailPageHtml, "https://livecamrips.to/video/15499805")
        val candidates = HostResolverEngine.extractCandidateUrls(doc)
        assertEquals(1, candidates.size)
        assertTrue("Candidate must be vidara embed", candidates[0].contains("vidara.so"))

        val plugin = HostResolverEngine.findPluginForUrl(candidates[0])
        assertNotNull("Plugin must be found for vidara", plugin)
        assertTrue("Plugin must be VidaraResolverPlugin", plugin is VidaraResolverPlugin)
    }
}
