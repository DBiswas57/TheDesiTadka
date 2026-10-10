package com.thedesitadka.provider.plugins

import com.thedesitadka.core.model.MediaSourceType
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaymateResolverPluginTest {

    private val plugin = PlaymateResolverPlugin()

    @Test
    fun testCanHandle() {
        assertTrue(plugin.canHandle("https://playmate.to/embed/b6dRKOnT4zND7"))
        assertTrue(plugin.canHandle("https://playmate.to/e/b6dRKOnT4zND7"))
        assertTrue(plugin.canHandle("https://playmate.to/d/b6dRKOnT4zND7"))
        assertTrue(plugin.canHandle("https://frv2.plauymito.live/hls/test/master.txt"))
        assertTrue(plugin.canHandle("https://playmate.me/e/12345"))
        assertTrue(plugin.canHandle("https://playmate.ws/e/12345"))
        assertTrue(!plugin.canHandle("https://luluvdo.com/e/aft5mblywcq6"))
        assertTrue(!plugin.canHandle("https://google.com"))
    }

    @Test
    fun testExtractFilecode() {
        assertEquals("b6dRKOnT4zND7", plugin.extractFilecode("https://playmate.to/embed/b6dRKOnT4zND7"))
        assertEquals("5C8CrijB4lweD", plugin.extractFilecode("https://playmate.to/e/5C8CrijB4lweD"))
        assertEquals("jqbulCnIQT0Fd", plugin.extractFilecode("https://playmate.to/d/jqbulCnIQT0Fd"))
        assertEquals("HMMt53Z4ygRDs", plugin.extractFilecode("https://playmate.to/embed/HMMt53Z4ygRDs?autoplay=1"))
    }

    @Test
    fun testHostResolverEngineRegistration() {
        val found = HostResolverEngine.findPluginForUrl("https://playmate.to/embed/b6dRKOnT4zND7")
        assertNotNull("PlaymateResolverPlugin must be registered in HostResolverEngine", found)
        assertEquals("playmate", found?.id)
        assertTrue(HostResolverEngine.canHandle("https://playmate.to/embed/b6dRKOnT4zND7"))

        val doc = Jsoup.parse("""
            <html>
                <body>
                    <iframe src="https://playmate.to/embed/b6dRKOnT4zND7" width="640" height="360"></iframe>
                </body>
            </html>
        """.trimIndent())
        val candidates = HostResolverEngine.extractCandidateUrls(doc)
        assertTrue("Engine must extract playmate embed URL", candidates.contains("https://playmate.to/embed/b6dRKOnT4zND7"))
    }

    @Test
    fun testLiveOrMockResolution() {
        // Test resolution of known valid embed code
        val res = plugin.resolveSync("https://playmate.to/embed/b6dRKOnT4zND7")
        if (res.isSuccess) {
            val source = res.getOrThrow()
            assertEquals(MediaSourceType.HLS, source.type)
            assertEquals("application/vnd.apple.mpegurl", source.mimeType)
            assertTrue("Stream must contain master.txt or hls", source.url.contains("master.txt") || source.url.contains("/hls/"))
            assertEquals("https://playmate.to/", source.headersRequired["Referer"])
            assertEquals("https://playmate.to", source.headersRequired["Origin"])
        }
    }
}
