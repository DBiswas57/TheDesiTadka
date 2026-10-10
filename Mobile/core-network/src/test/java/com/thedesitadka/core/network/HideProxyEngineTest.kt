package com.thedesitadka.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HideProxyEngineTest {

    @Test
    fun testBuildProxiedUrl() {
        val target = "https://www.example.com/videos/desi-clip?page=2"
        val proxied = HideProxyEngine.buildProxiedUrl(target)

        assertTrue(proxied.startsWith("https://nl.hideproxy.me/go.php?u="))
        assertTrue(proxied.contains("example.com"))
        assertTrue(proxied.endsWith("&b=0&f=norefer"))
    }

    @Test
    fun testUnwrapProxiedUrl() {
        val target = "https://www.example.com/videos/desi-clip?page=2"
        val proxied = HideProxyEngine.buildProxiedUrl(target)

        val unwrapped = HideProxyEngine.unwrapProxiedUrl(proxied)
        assertEquals(target, unwrapped)
    }

    @Test
    fun testIsProxiedUrl() {
        assertTrue(HideProxyEngine.isProxiedUrl("https://nl.hideproxy.me/go.php?u=xyz"))
        assertTrue(HideProxyEngine.isProxiedUrl("/go.php?u=xyz"))
        assertFalse(HideProxyEngine.isProxiedUrl("https://www.google.com/search"))
    }

    @Test
    fun testEnsureAbsoluteProxyLinks() {
        val htmlWithRelative = """
            <div class="video">
                <a href="/go.php?u=https%3A%2F%2Fsite.com%2Fv1&b=0">Watch</a>
                <img src="/go.php?u=https%3A%2F%2Fsite.com%2Fthumb.jpg&b=0" />
            </div>
        """.trimIndent()

        val normalized = HideProxyEngine.ensureAbsoluteProxyLinks(htmlWithRelative)
        assertTrue(normalized.contains("href=\"https://nl.hideproxy.me/go.php?u="))
        assertTrue(normalized.contains("src=\"https://nl.hideproxy.me/go.php?u="))
        assertFalse(normalized.contains("href=\"/go.php?"))
        assertFalse(normalized.contains("src=\"/go.php?"))
    }
}
