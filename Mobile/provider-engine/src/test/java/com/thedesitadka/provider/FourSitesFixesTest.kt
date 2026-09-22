package com.thedesitadka.provider

import com.thedesitadka.core.model.ProviderCapability
import com.thedesitadka.core.model.ProviderConfig
import com.thedesitadka.core.model.NavigationConfig
import com.thedesitadka.core.model.SelectorConfig
import com.thedesitadka.core.model.isCategoryUrl
import com.thedesitadka.provider.adapters.HtmlSelectorAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FourSitesFixesTest {

    private val baseDir = File("c:/Users/LearnersYT/source/TheDesiTadka/EnglishSites")

    @Test
    fun testPorn4DaysUrlResolutionAndCategoryFix() {
        val config = ProviderConfig(
            id = "porn4days",
            domains = listOf("https://porn4days.pw"),
            validationMarker = "porn4days",
            name = "Porn4Days",
            baseUrl = "https://porn4days.pw",
            adapter = "html_selector",
            capabilities = listOf(ProviderCapability.HOME, ProviderCapability.CATEGORY, ProviderCapability.SEARCH),
            navigation = NavigationConfig(
                home = "/newest",
                categories = "/paysitelist"
            )
        )
        val adapter = HtmlSelectorAdapter(config)

        // 1. Direct resolveUrl unit test
        val resolved = adapter.resolveUrl("search/?s=Daddy4K")
        assertEquals(
            "resolveUrl must resolve against normalized base with slash, never producing porn4days.pwsearch",
            "https://porn4days.pw/search/?s=Daddy4K",
            resolved
        )

        // 2. Paysitelist extraction
        val paysiteFile = File(baseDir, "porn4days.pw/porn4days.pw (7).html")
        if (paysiteFile.exists()) {
            val categories = adapter.parseCategoriesHtml(paysiteFile.readText(Charsets.UTF_8))
            val daddy4k = categories.find { it.name.equals("Daddy4K", ignoreCase = true) }
            assertNotNull("Daddy4K category must be extracted", daddy4k)
            assertEquals(
                "Daddy4K category URL must be valid",
                "https://porn4days.pw/search/?s=Daddy4K",
                daddy4k!!.url
            )
            assertFalse(
                "URL must never contain porn4days.pwsearch",
                daddy4k.url.contains("porn4days.pwsearch")
            )
        }
    }

    @Test
    fun testNetfapxDynamicMediaResolution() {
        val config = ProviderConfig(
            id = "netfapx",
            domains = listOf("https://netfapx.com"),
            validationMarker = "netfapx",
            name = "Netfapx",
            baseUrl = "https://netfapx.com",
            adapter = "html_selector",
            capabilities = listOf(ProviderCapability.HOME, ProviderCapability.STREAM, ProviderCapability.DETAILS),
            selectors = SelectorConfig(
                player = "video",
                videoSource = "video source[src], video[src]",
                videoSourceAttr = "src"
            )
        )
        val adapter = HtmlSelectorAdapter(config)

        // Verify adapter can parse a page that has a direct video source embedded
        val syntheticHtml = """
            <html><body>
            <video id="myVideo">
                <source src="https://videos.netfapx.com/contents/videos/2026/09/88361.mp4" type="video/mp4">
            </video>
            </body></html>
        """.trimIndent()
        val result = adapter.parsePlayableMediaHtml(syntheticHtml, "https://netfapx.com/2026/09/stepsis-and-i-ditch-to-play-school/")
        assertTrue("Netfapx media resolution must succeed for page with direct source", result.isSuccess)
        val sources = result.getOrThrow()
        assertTrue("Sources must not be empty", sources.isNotEmpty())
        val stream = sources[0]
        assertTrue("Stream must point to videos.netfapx.com", stream.url.contains("videos.netfapx.com"))
        assertEquals("https://netfapx.com/", stream.headersRequired["Referer"])

        // The offline saved page has empty <source src=""> which requires AJAX - this will fail in unit test
        // (no network), which is expected behavior. The adapter handles this at runtime.
        val detailFile = File(baseDir, "netfapx.com/netfapx.com (11).html")
        if (detailFile.exists()) {
            val html = detailFile.readText(Charsets.UTF_8)
            val offlineResult = adapter.parsePlayableMediaHtml(html, "https://netfapx.com/2026/09/stepsis-and-i-ditch-to-play-school/")
            // AJAX-dependent pages may return empty sources or failure in offline mode - both are acceptable
            if (offlineResult.isSuccess) {
                val offlineSources = offlineResult.getOrThrow()
                if (offlineSources.isNotEmpty()) {
                    assertTrue("If sources found offline, must point to netfapx", offlineSources[0].url.contains("netfapx"))
                }
            }
            // No assertion on failure - expected for AJAX-dependent pages without network
        }
    }

    @Test
    fun testPornstarsTubeModelsLandingAndQuickJump() {
        val config = ProviderConfig(
            id = "pornstars_tube",
            domains = listOf("https://pornstars.tube"),
            validationMarker = "pornstars.tube",
            name = "Pornstars.tube",
            baseUrl = "https://pornstars.tube",
            adapter = "html_selector",
            capabilities = listOf(ProviderCapability.HOME, ProviderCapability.CATEGORY, ProviderCapability.SEARCH),
            navigation = NavigationConfig(
                home = "/models/",
                page = "/models/{page}/",
                categories = "/models/",
                search = "/search/{query}/"
            )
        )
        val adapter = HtmlSelectorAdapter(config)

        // 1. Pagination verification
        val pagedModels = adapter.buildPagedUrl("https://pornstars.tube/models/", 2)
        assertEquals("https://pornstars.tube/models/2/", pagedModels)

        val pagedLetterA = adapter.buildPagedUrl("https://pornstars.tube/A/", 3)
        assertEquals("https://pornstars.tube/A/3/", pagedLetterA)

        val pagedSearch = adapter.buildPagedUrl("https://pornstars.tube/search/angela/", 2)
        assertEquals("https://pornstars.tube/search/angela/2/", pagedSearch)

        // 2. Category / Quick Jump extraction from models page
        val modelsFile = File(baseDir, "pornstars.tube/pornstars.tube (10).html")
        if (modelsFile.exists()) {
            val categories = adapter.parseCategoriesHtml(modelsFile.readText(Charsets.UTF_8))
            val letterA = categories.find { it.name == "A" }
            assertNotNull("Letter A must be extracted from quick jump", letterA)
            assertEquals("https://pornstars.tube/A/", letterA!!.url)

            val letterZ = categories.find { it.name == "Z" }
            assertNotNull("Letter Z must be extracted from quick jump", letterZ)
            assertEquals("https://pornstars.tube/Z/", letterZ!!.url)

            val allCat = categories.find { it.name.equals("All", ignoreCase = true) }
            assertNotNull("'All' must be extracted", allCat)
            assertEquals("https://pornstars.tube/models/", allCat!!.url)
        }

        // 3. Category URL classification
        assertTrue("https://pornstars.tube/A/ must be recognized as category URL", isCategoryUrl("https://pornstars.tube/A/"))
        assertTrue("https://pornstars.tube/A/2/ must be recognized as category URL", isCategoryUrl("https://pornstars.tube/A/2/"))
        assertTrue("https://pornstars.tube/models/angela-white/ must be recognized as category URL", isCategoryUrl("https://pornstars.tube/models/angela-white/"))
        assertFalse("Single video URL must never be recognized as category URL", isCategoryUrl("https://pornstars.tube/videos/787213/"))
    }
}
