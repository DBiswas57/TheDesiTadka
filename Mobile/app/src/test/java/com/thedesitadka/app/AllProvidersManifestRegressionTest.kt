package com.thedesitadka.app

import com.thedesitadka.core.model.ProviderCapability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AllProvidersManifestRegressionTest {

    private val original29ProviderIds = listOf(
        "kamababa1", "masa49", "fsiblogxx", "masahub2", "aagmaal",
        "fry99", "hitmaal", "webxseries", "desibf", "antarvasnabf",
        "desisex", "ixiporn", "masafun", "uncutmaza", "wowuncut",
        "desikahani2", "desitales2", "indiansexstories3", "xxxindianstories", "xmaza",
        "xnxx", "xvideos", "aagmaal_com", "bmaal", "chiggywiggy",
        "desibabe", "desigirlxx", "desimaals", "desivideo"
    )

    private val new18EnglishProviderIds = listOf(
        "brazzers", "brazzpw", "fpo", "hello", "hqporner",
        "max", "netfapx", "ok_porn", "ok_xxx", "perfectgirls",
        "porn4days", "pornhat", "pornhd4k", "pornhouse", "pornmz",
        "pornstars_tube", "sxyprn", "watchxxxfree"
    )

    @Test
    fun testManifestIntegrityAndRegression() {
        val manifest = AppContainer.createDefaultManifest()

        // 1. Total count must be 47
        assertEquals("Total registered providers must be 47 (29 original + 18 English)", 47, manifest.providers.size)

        val providerMap = manifest.providers.associateBy { it.id }

        // 2. Zero duplicate IDs
        assertEquals("Provider IDs must be completely unique", manifest.providers.size, providerMap.size)

        // 3. Regression test: all 29 original providers must exist without alteration of IDs
        for (origId in original29ProviderIds) {
            val provider = providerMap[origId]
            assertNotNull("Original provider '$origId' must exist to prevent regressions", provider)
            assertFalse("Original provider '$origId' name must not be blank", provider!!.name.isBlank())
            assertFalse("Original provider '$origId' baseUrl must not be blank", provider.baseUrl.isBlank())
            assertTrue("Original provider '$origId' must have HOME capability", provider.capabilities.contains(ProviderCapability.HOME))
        }

        // 4. Phase 1 test: all 18 new English providers must exist
        for (engId in new18EnglishProviderIds) {
            val provider = providerMap[engId]
            assertNotNull("New English provider '$engId' must exist", provider)
            assertFalse("New English provider '$engId' name must not be blank", provider!!.name.isBlank())
            assertFalse("New English provider '$engId' baseUrl must not be blank", provider.baseUrl.isBlank())
            assertTrue("New English provider '$engId' must have HOME capability", provider.capabilities.contains(ProviderCapability.HOME))
            assertTrue("New English provider '$engId' must have STREAM capability", provider.capabilities.contains(ProviderCapability.STREAM))
        }

        // 5. Phase 2 test: Verify kamababa1 fix
        val kamababa = providerMap["kamababa1"]
        assertNotNull(kamababa)
        assertEquals("https://www.mykamababa.com", kamababa!!.baseUrl)
        assertTrue("Domains must include mykamababa.com", kamababa.domains.any { it.contains("mykamababa.com") })
        assertTrue("DetailUrl must match mykamababa pattern", kamababa.selectors?.detailUrl?.contains("kamababa") == true)

        // 6. Phase 2 test: Verify xnxx fix
        val xnxx = providerMap["xnxx"]
        assertNotNull(xnxx)
        assertTrue(xnxx!!.baseUrl.contains("xnxx"))
        assertTrue(xnxx.domains.any { it.contains("xnxx.com") })
        assertEquals("/todays-selection", xnxx.navigation?.home)
        assertEquals("/todays-selection/{page}", xnxx.navigation?.page)
        assertTrue("Selectors thumbnailAttr must include data-mzl", xnxx.selectors?.thumbnailAttr?.contains("data-mzl") == true)
        assertTrue("Selectors detailUrl must include /video-", xnxx.selectors?.detailUrl?.contains("/video-") == true)
    }
}
