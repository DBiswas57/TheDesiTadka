package com.thedesitadka.core.config

import com.thedesitadka.core.model.ProviderConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DomainResolverTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testIsDomainAllowed() {
        val resolver = DomainResolver(tempFolder.root)

        // 1. AagMaal.date config (with mirror aagmaal.run)
        val dateConfig = ProviderConfig(
            id = "aagmaal",
            familyId = "aagmaal_date_family",
            domains = listOf("https://aagmaal.date", "https://aagmaal.run"),
            validationMarker = "aagmaal",
            name = "AagMaal",
            enabled = true,
            baseUrl = "https://aagmaal.date",
            adapter = "html_selector"
        )

        // Allowed: itself and declared mirror
        assertTrue(resolver.isDomainAllowed("https://aagmaal.date", dateConfig))
        assertTrue(resolver.isDomainAllowed("https://aagmaal.run", dateConfig))
        assertTrue(resolver.isDomainAllowed("https://www.aagmaal.date", dateConfig))

        // NOT allowed: different site with same brand name but different TLD (aagmaal.com)
        assertFalse(resolver.isDomainAllowed("https://aagmaal.com", dateConfig))
        assertFalse(resolver.isDomainAllowed("http://aagmaal.bz", dateConfig))

        // 2. AagMaal.com config (with mirror aagmaal.bz)
        val comConfig = ProviderConfig(
            id = "aagmaal_com",
            familyId = "aagmaal_com_family",
            domains = listOf("https://aagmaal.com", "http://aagmaal.bz"),
            validationMarker = "aagmaal",
            name = "AagMaal.com",
            enabled = true,
            baseUrl = "https://aagmaal.com",
            adapter = "html_selector"
        )

        // Allowed: itself and declared mirror
        assertTrue(resolver.isDomainAllowed("https://aagmaal.com", comConfig))
        assertTrue(resolver.isDomainAllowed("http://aagmaal.bz", comConfig))
        assertTrue(resolver.isDomainAllowed("https://www.aagmaal.com", comConfig))

        // NOT allowed: different site with same brand name but different TLD (aagmaal.date, aagmaal.run)
        assertFalse(resolver.isDomainAllowed("https://aagmaal.date", comConfig))
        assertFalse(resolver.isDomainAllowed("https://aagmaal.run", comConfig))
    }

    @Test
    fun testStaleCrossTldCacheIsPurged() = runBlocking {
        val cacheDir = tempFolder.root
        val cacheFile = File(cacheDir, "active_domains.json")

        // Simulate stale cache written previously where aagmaal_com was corrupted with aagmaal.date
        val staleJson = """
            {
              "aagmaal_com": { "domain": "https://aagmaal.date", "lastVerifiedMs": ${System.currentTimeMillis()}, "status": "AVAILABLE" }
            }
        """.trimIndent()
        cacheFile.writeText(staleJson)

        val resolver = DomainResolver(cacheDir)

        val comConfig = ProviderConfig(
            id = "aagmaal_com",
            familyId = "aagmaal_com_family",
            domains = listOf("https://aagmaal.com", "http://aagmaal.bz"),
            validationMarker = "aagmaal",
            name = "AagMaal.com",
            enabled = true,
            baseUrl = "https://aagmaal.com",
            adapter = "html_selector"
        )

        // Resolving aagmaal_com must NOT return the unauthorized cached aagmaal.date!
        val resolved = resolver.resolveActiveDomain(comConfig)
        assertFalse("Resolved domain must NEVER be the cross-TLD collision domain", resolved.contains("aagmaal.date"))
        assertTrue("Resolved domain must be aagmaal.com or aagmaal.bz", resolved.contains("aagmaal.com") || resolved.contains("aagmaal.bz"))

        // Stale entry must have been purged from cache file
        val updatedCacheJson = cacheFile.readText()
        assertFalse("Stale aagmaal.date must be purged from active_domains.json", updatedCacheJson.contains("aagmaal.date"))
    }
}
