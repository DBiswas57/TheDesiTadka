package com.thedesitadka.provider

import com.thedesitadka.core.model.ProviderCapability
import com.thedesitadka.core.model.ProviderConfig
import com.thedesitadka.core.model.SelectorConfig
import com.thedesitadka.core.model.NavigationConfig
import com.thedesitadka.provider.adapters.HtmlSelectorAdapter
import com.thedesitadka.provider.plugins.StreamouploadResolverPlugin
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class PrmoviesIntegrationTest {

    private fun findWorkspaceRoot(): File {
        var current: File? = File(".").canonicalFile
        while (current != null) {
            if (File(current, "scratch").exists() || File(current, "Mobile").exists()) {
                return current
            }
            current = current.parentFile
        }
        return File(".")
    }

    private val prmoviesConfig = ProviderConfig(
        id = "prmovies",
        familyId = "erotic_movies_family",
        domains = listOf("https://prmovies.com"),
        validationMarker = "prmovies",
        name = "PRMovies",
        enabled = true,
        baseUrl = "https://prmovies.com",
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
            categories = "/genre/desi/"
        ),
        selectors = SelectorConfig(
            item = ".ml-item, div.ml-item",
            title = ".mli-info h2, h2, a[oldtitle], img[alt]",
            thumbnail = "img.mli-thumb, img",
            thumbnailAttr = "data-original, src",
            detailUrl = "a.ml-mask, a",
            duration = ".mli-quality",
            detailTitle = "[itemprop='name'], h3[itemprop='name'], h1, h2.film-name, .film-info h1, .entry-title",
            detailDescription = "[itemprop='description'], .desc, .film-desc, .film-description, p",
            detailThumbnail = "meta[property='og:image'], [itemprop='image'], [itemprop='thumbnailUrl'], img.thumb, img",
            player = "iframe[src*='streamoupload'], iframe[data-lazy-src*='streamoupload'], iframe, video",
            videoSource = "iframe[src*='streamoupload'], iframe[data-lazy-src*='streamoupload']",
            videoSourceAttr = "src, data-lazy-src"
        )
    )

    private val prmoviesChurchConfig = ProviderConfig(
        id = "prmovies_church",
        familyId = "cinema_movies_family",
        domains = listOf("https://prmovies.church", "https://prmovies.energy"),
        validationMarker = "prmovies",
        name = "PRMovies Church",
        enabled = true,
        baseUrl = "https://prmovies.church",
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
            categories = "/genre/bollywood-movies/"
        ),
        selectors = prmoviesConfig.selectors
    )

    @Test
    fun testPrmoviesHomepageExtractsAll72ItemsAcrossSections() {
        val root = findWorkspaceRoot()
        val htmlFile = File(root, "scratch/prmovies_com.html")
        assertTrue("scratch/prmovies_com.html must exist", htmlFile.exists())

        val html = htmlFile.readText(Charsets.UTF_8)
        val adapter = HtmlSelectorAdapter(prmoviesConfig)

        val feedResult = adapter.parseListingHtml(html, 1)
        assertTrue("Listing parse must succeed", feedResult.isSuccess)

        val items = feedResult.getOrThrow().items
        assertEquals("Home feed must extract all 72 items across the 6 sections", 72, items.size)

        // Verify items contain expected titles and links
        val firstItem = items[0]
        assertNotNull(firstItem.title)
        assertTrue("First item title must match", firstItem.title.contains("Step Sister"))
        assertTrue("First item url must match", firstItem.detailUrl.contains("step-sister-ki-tight-chut-mein-step-brother"))
        assertNotNull(firstItem.thumbnailUrl)
        assertTrue("First item thumb must not be empty", firstItem.thumbnailUrl.isNotEmpty())

        // Verify unique items
        val urls = items.map { it.detailUrl }.toSet()
        assertEquals("All extracted items must have unique URLs", 72, urls.size)
    }

    @Test
    fun testPrmoviesDetailPageParsing() {
        val root = findWorkspaceRoot()
        val detailFile = File(root, "scratch/prmovies_com_detail.html")
        assertTrue("scratch/prmovies_com_detail.html must exist", detailFile.exists())

        val html = detailFile.readText(Charsets.UTF_8)
        val adapter = HtmlSelectorAdapter(prmoviesConfig)

        val detailsResult = adapter.parseDetailsHtml(html, "https://prmovies.com/step-sister-ki-tight-chut-mein-step-brother-watch-online/")
        assertTrue("Details parse must succeed", detailsResult.isSuccess)

        val details = detailsResult.getOrThrow()
        assertEquals("Step Sister Ki Tight Chut Mein Step Brother", details.title)
        assertNotNull(details.description)
        assertTrue("Description must contain excerpt", details.description.contains("Brother Helps Sister"))
        assertNotNull(details.thumbnailUrl)
        assertTrue("Thumbnail must contain flixcdn", details.thumbnailUrl.contains("flixcdn.com"))

        // Verify playable media sources extracts streamoupload embed
        val mediaResult = adapter.parsePlayableMediaHtml(html, "https://prmovies.com/step-sister-ki-tight-chut-mein-step-brother-watch-online/")
        assertTrue("Media source extraction must succeed", mediaResult.isSuccess)
        val sources = mediaResult.getOrThrow()
        assertTrue("Must extract streamoupload media source", sources.isNotEmpty())
        assertTrue("Source URL must point to streamoupload", sources.any { it.url.contains("streamoupload.xyz") })
    }

    @Test
    fun testCategoryPaginationUrlBuilder() {
        val adapter = HtmlSelectorAdapter(prmoviesConfig)
        val page2Url = adapter.buildPagedUrl("https://prmovies.com/genre/desi/", 2)
        assertEquals("https://prmovies.com/genre/desi/page/2/", page2Url)

        val page3Url = adapter.buildPagedUrl("https://prmovies.com/genre/desi/page/2/", 3)
        assertEquals("https://prmovies.com/genre/desi/page/3/", page3Url)

        val churchAdapter = HtmlSelectorAdapter(prmoviesChurchConfig)
        val churchPage2 = churchAdapter.buildPagedUrl("https://prmovies.church/genre/bollywood-movies/", 2)
        assertEquals("https://prmovies.church/genre/bollywood-movies/page/2/", churchPage2)
    }

    @Test
    fun testStreamouploadPluginRecognizesPrmoviesEmbed() {
        val plugin = StreamouploadResolverPlugin()
        val embedUrl = "https://streamoupload.xyz/embed-6nvkbhqn7ex1.html"
        assertTrue("Streamoupload plugin must handle embed", plugin.canHandle(embedUrl))
    }
}
