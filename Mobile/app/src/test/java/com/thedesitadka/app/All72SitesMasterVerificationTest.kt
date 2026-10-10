package com.thedesitadka.app

import com.thedesitadka.core.model.ProviderCapability
import com.thedesitadka.core.security.FileNameSanitizer
import com.thedesitadka.core.security.UrlSecurityValidator
import com.thedesitadka.provider.adapters.HtmlSelectorAdapter
import org.jsoup.Jsoup
import org.jsoup.select.Selector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class All72SitesMasterVerificationTest {

    @Test
    fun testAll72SitesDeepForensicVerification() {
        val manifest = AppContainer.createDefaultManifest()

        assertEquals("Manifest must register exactly 74 sites", 74, manifest.providers.size)

        val seenIds = mutableSetOf<String>()
        val seenNames = mutableSetOf<String>()

        for ((index, provider) in manifest.providers.withIndex()) {
            val siteNumber = index + 1
            val siteTag = "SITE-%03d [%s]".format(siteNumber, provider.id)

            // 1. Identity & Naming verification
            assertFalse("$siteTag ID must not be blank", provider.id.isBlank())
            assertFalse("$siteTag Name must not be blank", provider.name.isBlank())
            assertTrue("$siteTag ID must be unique", seenIds.add(provider.id))

            // 2. Base URL & Security (SSRF & Strict HTTPS)
            assertFalse("$siteTag Base URL must not be blank", provider.baseUrl.isBlank())
            assertTrue(
                "$siteTag Base URL '${provider.baseUrl}' must be strictly HTTPS",
                provider.baseUrl.startsWith("https://")
            )
            assertTrue(
                "$siteTag Base URL '${provider.baseUrl}' must pass SSRF safety validation",
                UrlSecurityValidator.isUrlSafe(provider.baseUrl)
            )

            // 3. Alternative Domains Security
            for (domain in provider.domains) {
                assertTrue(
                    "$siteTag Domain '$domain' must be strictly HTTPS",
                    domain.startsWith("https://")
                )
                assertTrue(
                    "$siteTag Domain '$domain' must pass SSRF safety validation",
                    UrlSecurityValidator.isUrlSafe(domain)
                )
            }

            // 4. Core Capabilities
            assertTrue(
                "$siteTag must have HOME capability",
                provider.capabilities.contains(ProviderCapability.HOME)
            )
            assertTrue(
                "$siteTag must have STREAM capability",
                provider.capabilities.contains(ProviderCapability.STREAM)
            )

            // 5. Navigation Architecture & URL formatting
            val nav = provider.navigation
            assertNotNull("$siteTag Navigation configuration must not be null", nav)
            assertFalse("$siteTag Home navigation path must not be blank", nav!!.home.isBlank())
            assertFalse("$siteTag Search navigation path must not be blank", nav.search.isBlank())
            assertFalse("$siteTag Page navigation path must not be blank", nav.page.isBlank())

            // Test search query template
            assertTrue(
                "$siteTag Search query must contain {query} placeholder",
                nav.search.contains("{query}")
            )

            // 6. Selector DSL Verification
            val selectors = provider.selectors
            assertNotNull("$siteTag Selectors configuration must not be null", selectors)
            assertFalse("$siteTag Item selector must not be blank", selectors!!.item.isBlank())
            assertFalse("$siteTag Title selector must not be blank", selectors.title.isBlank())
            assertFalse("$siteTag Thumbnail selector must not be blank", selectors.thumbnail.isBlank())
            assertFalse("$siteTag DetailUrl selector must not be blank", selectors.detailUrl.isBlank())

            // Verify Jsoup CSS selector syntax validity (anti-crash)
            val dummyDoc = Jsoup.parse("<html><body><div></div></body></html>")
            try {
                dummyDoc.select(selectors.item)
            } catch (e: Exception) {
                throw AssertionError("$siteTag Item selector syntax invalid: '${selectors.item}'", e)
            }
            try {
                dummyDoc.select(selectors.title)
            } catch (e: Exception) {
                throw AssertionError("$siteTag Title selector syntax invalid: '${selectors.title}'", e)
            }
            try {
                dummyDoc.select(selectors.thumbnail)
            } catch (e: Exception) {
                throw AssertionError("$siteTag Thumbnail selector syntax invalid: '${selectors.thumbnail}'", e)
            }
            try {
                if (selectors.detailUrl != "this") {
                    dummyDoc.select(selectors.detailUrl)
                }
            } catch (e: Exception) {
                throw AssertionError("$siteTag DetailUrl selector syntax invalid: '${selectors.detailUrl}'", e)
            }

            // 7. Adapter Pagination & Navigation State Engine
            val adapter = HtmlSelectorAdapter(provider)
            val p1Url = adapter.buildPagedUrl(provider.baseUrl, 1)
            val p2Url = adapter.buildPagedUrl(provider.baseUrl, 2)
            assertNotNull("$siteTag Page 1 URL must be generated", p1Url)
            assertNotNull("$siteTag Page 2 URL must be generated", p2Url)
            assertFalse("$siteTag Page 2 URL must not be blank", p2Url.isBlank())

            // 8. Download Filename Sanitization Flow
            val safeFileName = FileNameSanitizer.buildSafeFileName("Sample Video: Special /\\ ? * < > | Name", provider.id, "mp4")
            assertFalse("$siteTag Download filename must not be blank", safeFileName.isBlank())
            assertTrue("$siteTag Download filename must end with .mp4", safeFileName.endsWith(".mp4"))
            assertFalse("$siteTag Download filename must not contain illegal chars", safeFileName.contains(":") || safeFileName.contains("/") || safeFileName.contains("\\"))
        }
    }
}
