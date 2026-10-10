package com.thedesitadka.provider.plugins

import com.thedesitadka.core.model.MediaSourceType
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VidaraResolverPluginTest {

    private val plugin = VidaraResolverPlugin()

    @Test
    fun testCanHandle() {
        assertTrue(plugin.canHandle("https://vidara.so/e/cb04fe306625"))
        assertTrue(plugin.canHandle("https://vidara.to/e/cb04fe306625"))
        assertTrue(plugin.canHandle("https://vidara.net/e/abcd1234efgh"))
        assertTrue(plugin.canHandle("https://vidara.cc/e/test123"))
        assertTrue(plugin.canHandle("https://gbsagbo.com/e/cb04fe306625"))
        assertFalse(plugin.canHandle("https://google.com"))
        assertFalse(plugin.canHandle("https://uqload.vc/embed-123.html"))
    }

    @Test
    fun testExtractFilecode() {
        assertEquals("cb04fe306625", plugin.extractFilecode("https://vidara.so/e/cb04fe306625"))
        assertEquals("cb04fe306625", plugin.extractFilecode("https://vidara.to/e/cb04fe306625?poster=https%3A%2F%2Fimg.com%2F1.jpg"))
        assertEquals("cb04fe306625", plugin.extractFilecode("https://gbsagbo.com/e/cb04fe306625#t=10"))
        assertEquals("cb04fe306625", plugin.extractFilecode("https://vidara.so/embed/cb04fe306625"))
    }

    @Test
    fun testExtractStreamFromHtml() {
        val html = """
            <html>
            <script>
            let player = jwplayer("jwplayer");
            setupPlayer({streaming_url: "https://s12-t.97bf1.com/hls/test/master.m3u8?token=123"});
            </script>
            </html>
        """.trimIndent()

        val stream = plugin.extractStreamFromHtml(html)
        assertNotNull("Should extract stream URL", stream)
        assertTrue(stream!!.contains("master.m3u8"))
    }

    @Test
    fun testHostResolverEngineIntegration() {
        val vidaraPlugin = HostResolverEngine.findPluginForUrl("https://vidara.so/e/cb04fe306625")
        assertNotNull("HostResolverEngine must find plugin for vidara.so", vidaraPlugin)
        assertTrue("Plugin must be VidaraResolverPlugin", vidaraPlugin is VidaraResolverPlugin)

        val html = """
            <html>
            <body>
                <iframe id="iframe-video" src="https://vidara.so/e/cb04fe306625"></iframe>
            </body>
            </html>
        """.trimIndent()

        val doc = Jsoup.parse(html, "https://livecamrips.to")
        val candidates = HostResolverEngine.extractCandidateUrls(doc)
        assertEquals(1, candidates.size)
        assertEquals("https://vidara.so/e/cb04fe306625", candidates[0])
    }
}
