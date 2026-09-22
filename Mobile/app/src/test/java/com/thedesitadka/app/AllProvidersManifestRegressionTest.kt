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

    private val english17ProviderIds = listOf(
        "brazzpw", "fpo", "hello", "hqporner",
        "max", "netfapx", "ok_porn", "ok_xxx", "perfectgirls",
        "porn4days", "pornhat", "pornhd4k", "pornhouse", "pornmz",
        "pornstars_tube", "sxyprn", "watchxxxfree"
    )

    private val additional4ProviderIds = listOf(
        "lalamasa", "watchoerotic", "prmovies", "prmovies_church"
    )

    private val recommended8ProviderIds = listOf(
        "xnxx", "xvideos", "pornhub", "xhamster", "redtube", "youporn", "tube8", "freeonestube"
    )

    private val recommendedSites9to17ProviderIds = listOf(
        "spankbang", "tnaflix", "empflix", "beeg", "youjizz", "eporner", "drtuber", "nuvid", "pornone"
    )

    private val recommendedSites18toLastProviderIds = listOf(
        "definebabe", "three_movs", "txxx", "upornia", "hdzog"
    )

    private val movieAndSeriesProviderIds = listOf(
        "movienerds", "cineapse"
    )

    @Test
    fun testManifestIntegrityAndRegression() {
        val manifest = AppContainer.createDefaultManifest()

        // 1. Total count must be 72 (70 existing + 2 new movie/series sites: movienerds, cineapse)
        assertEquals("Total registered providers must be 72", 72, manifest.providers.size)

        val providerMap = manifest.providers.associateBy { it.id }

        // 2. Zero duplicate IDs
        assertEquals("Provider IDs must be completely unique", manifest.providers.size, providerMap.size)

        // 2.1 Assert brazzers is completely removed
        assertFalse("brazzers must be completely removed from manifest", providerMap.containsKey("brazzers"))

        // 3. Regression test: all 29 original providers must exist without alteration of IDs
        for (origId in original29ProviderIds) {
            val provider = providerMap[origId]
            assertNotNull("Original provider '$origId' must exist to prevent regressions", provider)
            assertFalse("Original provider '$origId' name must not be blank", provider!!.name.isBlank())
            assertFalse("Original provider '$origId' baseUrl must not be blank", provider.baseUrl.isBlank())
            assertTrue("Original provider '$origId' must have HOME capability", provider.capabilities.contains(ProviderCapability.HOME))
        }

        // 4. English providers test (17 providers)
        for (engId in english17ProviderIds) {
            val provider = providerMap[engId]
            assertNotNull("English provider '$engId' must exist", provider)
            assertFalse("English provider '$engId' name must not be blank", provider!!.name.isBlank())
            assertFalse("English provider '$engId' baseUrl must not be blank", provider.baseUrl.isBlank())
            assertTrue("English provider '$engId' must have HOME capability", provider.capabilities.contains(ProviderCapability.HOME))
            assertTrue("English provider '$engId' must have STREAM capability", provider.capabilities.contains(ProviderCapability.STREAM))
        }

        // 4.5 Additional providers test: lalamasa, watchoerotic, prmovies, prmovies_church
        for (newId in additional4ProviderIds) {
            val provider = providerMap[newId]
            assertNotNull("Additional provider '$newId' must exist", provider)
            assertFalse("Additional provider '$newId' name must not be blank", provider!!.name.isBlank())
            assertFalse("Additional provider '$newId' baseUrl must not be blank", provider.baseUrl.isBlank())
            assertTrue("Additional provider '$newId' must have HOME capability", provider.capabilities.contains(ProviderCapability.HOME))
            assertTrue("Additional provider '$newId' must have STREAM capability", provider.capabilities.contains(ProviderCapability.STREAM))
            assertTrue("Additional provider '$newId' must have CATEGORY capability", provider.capabilities.contains(ProviderCapability.CATEGORY))
            assertTrue("Additional provider '$newId' must have SEARCH capability", provider.capabilities.contains(ProviderCapability.SEARCH))
        }

        // 4.55 Recommended 8 tube providers test: xnxx, xvideos, pornhub, xhamster, redtube, youporn, tube8, freeonestube
        for (recId in recommended8ProviderIds) {
            val provider = providerMap[recId]
            assertNotNull("Recommended provider '$recId' must exist", provider)
            assertFalse("Recommended provider '$recId' name must not be blank", provider!!.name.isBlank())
            assertFalse("Recommended provider '$recId' baseUrl must not be blank", provider.baseUrl.isBlank())
            assertTrue("Recommended provider '$recId' must have HOME capability", provider.capabilities.contains(ProviderCapability.HOME))
            assertTrue("Recommended provider '$recId' must have STREAM capability", provider.capabilities.contains(ProviderCapability.STREAM))
            assertTrue("Recommended provider '$recId' must have CATEGORY capability", provider.capabilities.contains(ProviderCapability.CATEGORY))
            assertTrue("Recommended provider '$recId' must have SEARCH capability", provider.capabilities.contains(ProviderCapability.SEARCH))
            assertTrue("Recommended provider '$recId' must have DETAILS capability", provider.capabilities.contains(ProviderCapability.DETAILS))
            assertTrue("Recommended provider '$recId' must have DOWNLOAD capability", provider.capabilities.contains(ProviderCapability.DOWNLOAD))
        }

        // 4.56 Recommended 9 tube providers test (sites 9-17: spankbang, tnaflix, empflix, beeg, youjizz, eporner, drtuber, nuvid, pornone)
        for (recId in recommendedSites9to17ProviderIds) {
            val provider = providerMap[recId]
            assertNotNull("Recommended provider '$recId' must exist", provider)
            assertFalse("Recommended provider '$recId' name must not be blank", provider!!.name.isBlank())
            assertFalse("Recommended provider '$recId' baseUrl must not be blank", provider.baseUrl.isBlank())
            assertTrue("Recommended provider '$recId' must have HOME capability", provider.capabilities.contains(ProviderCapability.HOME))
            assertTrue("Recommended provider '$recId' must have STREAM capability", provider.capabilities.contains(ProviderCapability.STREAM))
            assertTrue("Recommended provider '$recId' must have CATEGORY capability", provider.capabilities.contains(ProviderCapability.CATEGORY))
            assertTrue("Recommended provider '$recId' must have SEARCH capability", provider.capabilities.contains(ProviderCapability.SEARCH))
            assertTrue("Recommended provider '$recId' must have DETAILS capability", provider.capabilities.contains(ProviderCapability.DETAILS))
            assertTrue("Recommended provider '$recId' must have DOWNLOAD capability", provider.capabilities.contains(ProviderCapability.DOWNLOAD))
        }

        // 4.57 Recommended tube providers test (sites 18 to last: definebabe, three_movs, txxx, upornia, hdzog)
        for (recId in recommendedSites18toLastProviderIds) {
            val provider = providerMap[recId]
            assertNotNull("Recommended provider '$recId' must exist", provider)
            assertFalse("Recommended provider '$recId' name must not be blank", provider!!.name.isBlank())
            assertFalse("Recommended provider '$recId' baseUrl must not be blank", provider.baseUrl.isBlank())
            assertTrue("Recommended provider '$recId' must have HOME capability", provider.capabilities.contains(ProviderCapability.HOME))
            assertTrue("Recommended provider '$recId' must have STREAM capability", provider.capabilities.contains(ProviderCapability.STREAM))
            assertTrue("Recommended provider '$recId' must have CATEGORY capability", provider.capabilities.contains(ProviderCapability.CATEGORY))
            assertTrue("Recommended provider '$recId' must have SEARCH capability", provider.capabilities.contains(ProviderCapability.SEARCH))
            assertTrue("Recommended provider '$recId' must have DETAILS capability", provider.capabilities.contains(ProviderCapability.DETAILS))
            assertTrue("Recommended provider '$recId' must have DOWNLOAD capability", provider.capabilities.contains(ProviderCapability.DOWNLOAD))
        }

        // 4.58 Movie & TV Series providers test: movienerds, cineapse
        for (mId in movieAndSeriesProviderIds) {
            val provider = providerMap[mId]
            assertNotNull("Movie/Series provider '$mId' must exist", provider)
            assertFalse("Movie/Series provider '$mId' name must not be blank", provider!!.name.isBlank())
            assertFalse("Movie/Series provider '$mId' baseUrl must not be blank", provider.baseUrl.isBlank())
            assertTrue("Movie/Series provider '$mId' must have HOME capability", provider.capabilities.contains(ProviderCapability.HOME))
            assertTrue("Movie/Series provider '$mId' must have STREAM capability", provider.capabilities.contains(ProviderCapability.STREAM))
            assertTrue("Movie/Series provider '$mId' must have CATEGORY capability", provider.capabilities.contains(ProviderCapability.CATEGORY))
            assertTrue("Movie/Series provider '$mId' must have SEARCH capability", provider.capabilities.contains(ProviderCapability.SEARCH))
            assertTrue("Movie/Series provider '$mId' must have DETAILS capability", provider.capabilities.contains(ProviderCapability.DETAILS))
            assertTrue("Movie/Series provider '$mId' must have DOWNLOAD capability", provider.capabilities.contains(ProviderCapability.DOWNLOAD))
        }

        // 4.6 Verify PRMovies and PRMovies Church domain mirrors
        val prmovies = providerMap["prmovies"]
        assertNotNull(prmovies)
        assertEquals("https://prmovies.com", prmovies!!.baseUrl)
        assertEquals(listOf("https://prmovies.com"), prmovies.domains)
        assertEquals("/account/", prmovies.navigation?.home)
        assertEquals("/account/page/{page}/", prmovies.navigation?.page)

        val prmoviesChurch = providerMap["prmovies_church"]
        assertNotNull(prmoviesChurch)
        assertEquals("https://prmovies.church", prmoviesChurch!!.baseUrl)
        assertTrue(prmoviesChurch.domains.contains("https://prmovies.church"))
        assertTrue(prmoviesChurch.domains.contains("https://prmovies.energy"))
        assertEquals("/bollywood-movies-on-prmovies/", prmoviesChurch.navigation?.home)
        assertEquals("/bollywood-movies-on-prmovies/page/{page}/", prmoviesChurch.navigation?.page)

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

        // 7. Verify aagmaal and aagmaal_com strict isolation
        val aagmaal = providerMap["aagmaal"]
        val aagmaalCom = providerMap["aagmaal_com"]
        assertNotNull(aagmaal)
        assertNotNull(aagmaalCom)
        assertTrue("aagmaal baseUrl must be aagmaal.date", aagmaal!!.baseUrl.contains("aagmaal.date"))
        assertTrue("aagmaal_com baseUrl must be aagmaal.com", aagmaalCom!!.baseUrl.contains("aagmaal.com"))
        assertEquals("vp-card", aagmaalCom.validationMarker)
        assertTrue("aagmaal_com must contain xxviu mirrors", aagmaalCom.domains.contains("https://govmaal.com"))
        assertTrue("aagmaal_com must contain xxviu mirrors", aagmaalCom.domains.contains("https://kambihub.com"))
        assertTrue("aagmaal_com must contain xxviu mirrors", aagmaalCom.domains.contains("https://aagmaal.bz"))
    }

    @Test
    fun exportManifestToJson() {
        val manifest = AppContainer.createDefaultManifest()
        val json = kotlinx.serialization.json.Json {
            prettyPrint = true
            encodeDefaults = true
            explicitNulls = false
        }
        val encoded = json.encodeToString(com.thedesitadka.core.model.ProviderManifest.serializer(), manifest)
        val targetFile = java.io.File("c:/Users/LearnersYT/source/TheDesiTadka/config-tools/sample-manifest.json")
        targetFile.writeText(encoded)
        assertTrue("Exported manifest must exist", targetFile.exists())
    }

    @Test
    fun testPrmoviesMenuCategoriesAndFilterData() {
        val churchCats = com.thedesitadka.provider.plugins.PrmoviesMenuCategories.getCategories("prmovies_church", "https://prmovies.church")
        assertEquals(94, churchCats.size)
        assertTrue(churchCats.any { it.name == "Dual Audio" })
        assertTrue(churchCats.any { it.name == "Bollywood (2026)" })
        assertTrue(churchCats.any { it.name == "Hollywood (2026)" })
        assertTrue(churchCats.any { it.name == "English Series" })
        assertTrue(churchCats.any { it.name == "Hindi Series" })
        assertTrue(churchCats.any { it.name == "Ullu Originals" })

        val comCats = com.thedesitadka.provider.plugins.PrmoviesMenuCategories.getCategories("prmovies", "https://prmovies.com")
        assertEquals(43, comCats.size)
        assertTrue(comCats.any { it.name == "Ullu" })
        assertTrue(comCats.any { it.name == "Hotshots" })
        assertTrue(comCats.any { it.name == "Fliz" })
        assertTrue(comCats.any { it.name == "App Video" || it.name == "I Entertainment" })
        assertTrue(comCats.any { it.name == "Models" || it.name == "Poonam Pandey" })

        assertEquals(14, com.thedesitadka.app.ui.screens.PrmoviesFilterData.QUALITIES.size)
        assertEquals(60, com.thedesitadka.app.ui.screens.PrmoviesFilterData.GENRES.size)
        assertEquals(188, com.thedesitadka.app.ui.screens.PrmoviesFilterData.COUNTRIES.size)
        assertTrue(com.thedesitadka.app.ui.screens.PrmoviesFilterData.YEARS.size >= 100)
    }
}
