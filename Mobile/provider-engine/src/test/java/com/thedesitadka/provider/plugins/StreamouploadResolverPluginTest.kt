package com.thedesitadka.provider.plugins

import com.thedesitadka.core.model.MediaSourceType
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StreamouploadResolverPluginTest {

    private val plugin = StreamouploadResolverPlugin()

    @Test
    fun testCanHandle() {
        assertTrue(plugin.canHandle("https://streamoupload.xyz/embed-6nvkbhqn7ex1.html"))
        assertTrue(plugin.canHandle("https://streamoupload.com/embed-typo60w00b8t.html"))
        assertTrue(plugin.canHandle("https://streamoupload.net/e/6nvkbhqn7ex1"))
        assertTrue(plugin.canHandle("https://pnam.streamoupload.xyz/embed-6nvkbhqn7ex1.html"))
        assertTrue(!plugin.canHandle("https://luluvdo.com/e/aft5mblywcq6"))
        assertTrue(!plugin.canHandle("https://firestream.site/e/WAop-hsu"))
        assertTrue(!plugin.canHandle("https://vixeo.io/e/EQkJKzs7LwIf"))
        assertTrue(!plugin.canHandle("https://google.com"))
    }

    @Test
    fun testUnpackAndExtractStreamFromLocalHtml() {
        val sampleFile = File("c:/Users/LearnersYT/source/TheDesiTadka/scratch/streamoupload.html")
        assertTrue("Sample streamoupload HTML must exist", sampleFile.exists())

        val html = sampleFile.readText(Charsets.UTF_8)
        val result = plugin.resolveFromHtml(html, "https://streamoupload.xyz/embed-6nvkbhqn7ex1.html")

        assertTrue("Resolution must succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        val source = result.getOrThrow()

        assertEquals(MediaSourceType.HLS, source.type)
        assertEquals("application/vnd.apple.mpegurl", source.mimeType)
        assertTrue(source.url.contains("master.m3u8"))
        assertTrue(source.url.contains("streamoupload.xyz"))

        assertNotNull(source.headersRequired)
        assertEquals("https://streamoupload.xyz/", source.headersRequired?.get("Referer"))
        assertEquals("https://streamoupload.xyz", source.headersRequired?.get("Origin"))
    }

    @Test
    fun testHostResolverEngineIntegration() {
        val pluginForUrl = HostResolverEngine.findPluginForUrl("https://streamoupload.xyz/embed-6nvkbhqn7ex1.html")
        assertNotNull("HostResolverEngine must find plugin for streamoupload.xyz", pluginForUrl)
        assertTrue("Plugin must be StreamouploadResolverPlugin", pluginForUrl is StreamouploadResolverPlugin)

        val html = """
            <html>
            <body>
                <div class="video-container">
                    <iframe data-lazy-src="https://streamoupload.xyz/embed-6nvkbhqn7ex1.html" src="about:blank" width="100%" height="100%"></iframe>
                    <iframe src="https://luluvdo.com/e/aft5mblywcq6" width="100%" height="100%"></iframe>
                    <iframe src="https://vixeo.io/e/Dr6nHyWd3pM9" width="100%" height="100%"></iframe>
                    <iframe src="https://firestream.site/e/WAop-hsu" width="100%" height="100%"></iframe>
                    <iframe src="https://unrelated-ad.com/banner.html"></iframe>
                </div>
            </body>
            </html>
        """.trimIndent()

        val doc = Jsoup.parse(html, "https://watchoerotic.com")
        val candidates = HostResolverEngine.extractCandidateUrls(doc)

        assertEquals(4, candidates.size)
        assertTrue(candidates.contains("https://streamoupload.xyz/embed-6nvkbhqn7ex1.html"))
        assertTrue(candidates.contains("https://luluvdo.com/e/aft5mblywcq6"))
        assertTrue(candidates.contains("https://vixeo.io/e/Dr6nHyWd3pM9"))
        assertTrue(candidates.contains("https://firestream.site/e/WAop-hsu"))
    }
}
