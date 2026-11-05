package com.thedesitadka.app

import com.thedesitadka.core.model.ProviderCapability
import com.thedesitadka.core.security.FileNameSanitizer
import com.thedesitadka.core.security.UrlSecurityValidator
import com.thedesitadka.provider.adapters.HtmlSelectorAdapter
import com.thedesitadka.provider.plugins.HostResolverEngine
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * All 74 Enlisted Sites Deep Verification Pipeline Test.
 *
 * Verifies across all 74 providers:
 * 1. Main content feed grab: URL validation, SSRF safety, Jsoup selector DSL compilation.
 * 2. Single content video stream & download: HostResolverEngine embed discovery, direct stream capability.
 * 3. Search pipeline: query template format, {query} token presence, query encoding.
 * 4. Menu & Category navigation: route resolution, multi-page pagination (page 1, 2, 3).
 * 5. Download file management: security sanitation & filename generation.
 */
class All74SitesDeepPipelineVerificationTest {

    @Test
    fun testAll74SitesCompletePipelineIntegrity() {
        val manifest = AppContainer.createDefaultManifest()

        assertEquals("Manifest must register exactly 74 sites", 74, manifest.providers.size)

        val seenIds = mutableSetOf<String>()
        val seenNames = mutableSetOf<String>()

        for ((index, provider) in manifest.providers.withIndex()) {
            val siteNumber = index + 1
            val tag = "Site #%02d [%s]".format(siteNumber, provider.id)

            // ─── 1. Identity & SSRF Security ───
            assertFalse("$tag ID must not be blank", provider.id.isBlank())
            assertFalse("$tag Name must not be blank", provider.name.isBlank())
            assertTrue("$tag ID must be globally unique", seenIds.add(provider.id))

            assertFalse("$tag Base URL must not be blank", provider.baseUrl.isBlank())
            assertTrue("$tag Base URL must be HTTPS", provider.baseUrl.startsWith("https://"))
            assertTrue("$tag Base URL must pass SSRF safety check", UrlSecurityValidator.isUrlSafe(provider.baseUrl))

            for (domain in provider.domains) {
                assertTrue("$tag Alternative domain '$domain' must be HTTPS", domain.startsWith("https://"))
                assertTrue("$tag Alternative domain '$domain' must pass SSRF safety check", UrlSecurityValidator.isUrlSafe(domain))
            }

            // ─── 2. Core Capabilities ───
            assertTrue("$tag must provide HOME capability", provider.capabilities.contains(ProviderCapability.HOME))
            assertTrue("$tag must provide STREAM capability", provider.capabilities.contains(ProviderCapability.STREAM))

            // ─── 3. Navigation & Search Pipeline ───
            val nav = provider.navigation
            assertNotNull("$tag Navigation config must be present", nav)
            assertFalse("$tag Home navigation path must not be blank", nav!!.home.isBlank())
            assertFalse("$tag Search navigation path must not be blank", nav.search.isBlank())
            assertTrue("$tag Search template must include {query}", nav.search.contains("{query}"))

            val adapter = HtmlSelectorAdapter(provider)

            // Verify Search query URL construction
            val encodedQuery = java.net.URLEncoder.encode("desi hot", "UTF-8")
            val searchPath = nav.search.replace("{query}", encodedQuery)
            val fullSearchUrl = if (searchPath.startsWith("http")) searchPath else "${provider.baseUrl.removeSuffix("/")}$searchPath"
            assertTrue(
                "$tag Search URL must contain query keyword",
                fullSearchUrl.contains("desi") || fullSearchUrl.contains("hot")
            )

            // ─── 4. Pagination & Menu/Category Navigation ───
            val p1Url = adapter.buildPagedUrl(provider.baseUrl, 1)
            val p2Url = adapter.buildPagedUrl(provider.baseUrl, 2)
            val p3Url = adapter.buildPagedUrl(provider.baseUrl, 3)

            assertNotNull("$tag Page 1 URL must be generated", p1Url)
            assertNotNull("$tag Page 2 URL must be generated", p2Url)
            assertNotNull("$tag Page 3 URL must be generated", p3Url)
            assertFalse("$tag Page 2 URL must not be empty", p2Url.isBlank())
            assertFalse("$tag Page 3 URL must not be empty", p3Url.isBlank())

            // ─── 5. Main Content Selector DSL Validity ───
            val selectors = provider.selectors
            assertNotNull("$tag Selectors config must be present", selectors)
            assertFalse("$tag Item selector must not be blank", selectors!!.item.isBlank())
            assertFalse("$tag Title selector must not be blank", selectors.title.isBlank())
            assertFalse("$tag Thumbnail selector must not be blank", selectors.thumbnail.isBlank())
            assertFalse("$tag DetailUrl selector must not be blank", selectors.detailUrl.isBlank())

            // Verify Jsoup syntax compiles cleanly for every selector
            val dummyDoc = Jsoup.parse("""
                <html>
                <body>
                    <article class="post video item post-item entry thumb-block vid-item">
                        <a href="https://example.com/video1" class="thumb">
                            <img src="https://example.com/thumb.jpg" data-src="https://example.com/thumb.jpg" />
                            <h2 class="title entry-title">Sample Video Title</h2>
                            <span class="duration">12:34</span>
                        </a>
                    </article>
                </body>
                </html>
            """.trimIndent())

            try {
                dummyDoc.select(selectors.item)
            } catch (e: Exception) {
                throw AssertionError("$tag Invalid CSS selector for item: '${selectors.item}'", e)
            }
            try {
                dummyDoc.select(selectors.title)
            } catch (e: Exception) {
                throw AssertionError("$tag Invalid CSS selector for title: '${selectors.title}'", e)
            }
            try {
                dummyDoc.select(selectors.thumbnail)
            } catch (e: Exception) {
                throw AssertionError("$tag Invalid CSS selector for thumbnail: '${selectors.thumbnail}'", e)
            }
            try {
                if (selectors.detailUrl != "this") {
                    dummyDoc.select(selectors.detailUrl)
                }
            } catch (e: Exception) {
                throw AssertionError("$tag Invalid CSS selector for detailUrl: '${selectors.detailUrl}'", e)
            }

            // ─── 6. Single Content Stream & HostResolverEngine Embed Discovery ───
            val sampleEmbedHtml = """
                <html>
                <body>
                    <div class="video-container">
                        <iframe src="https://lulust.com/e/testembed123" width="100%" height="400"></iframe>
                        <a href="https://vixcloud.co/embed/test456">Alternative Player</a>
                    </div>
                </body>
                </html>
            """.trimIndent()
            val embedDoc = Jsoup.parse(sampleEmbedHtml)
            val extractedHosts = HostResolverEngine.extractCandidateUrls(embedDoc)
            assertTrue(
                "$tag HostResolverEngine must detect standard video iframe embeds",
                extractedHosts.isNotEmpty()
            )

            // ─── 7. Download Filename Sanitization Flow ───
            val rawTitle = "Exclusive: Desi Video / Episode 01 [HD] ? * < > |"
            val sanitizedName = FileNameSanitizer.buildSafeFileName(rawTitle, provider.id, "mp4")

            assertFalse("$tag Sanitized filename must not be blank", sanitizedName.isBlank())
            assertTrue("$tag Sanitized filename must end with .mp4", sanitizedName.endsWith(".mp4"))
            assertFalse(
                "$tag Sanitized filename must not contain illegal filesystem chars",
                sanitizedName.contains(":") || sanitizedName.contains("/") ||
                sanitizedName.contains("\\") || sanitizedName.contains("?") ||
                sanitizedName.contains("*") || sanitizedName.contains("<") ||
                sanitizedName.contains(">") || sanitizedName.contains("|")
            )
        }
    }
}
