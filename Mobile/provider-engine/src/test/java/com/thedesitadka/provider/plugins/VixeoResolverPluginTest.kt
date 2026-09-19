package com.thedesitadka.provider.plugins

import com.thedesitadka.core.model.MediaSourceType
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VixeoResolverPluginTest {

    private val plugin = VixeoResolverPlugin()

    @Test
    fun testCanHandle() {
        assertTrue(plugin.canHandle("https://vixeo.io/e/EQkJKzs7LwIf"))
        assertTrue(plugin.canHandle("https://www.vixeo.io/e/Dr6nHyWd3pM9"))
        assertTrue(plugin.canHandle("https://vixeo.io/embed/abc123xyz"))
        assertTrue(plugin.canHandle("http://vixeo.io/d/test"))
        assertTrue(!plugin.canHandle("https://watchxxxfree.xyz/some-video/"))
        assertTrue(!plugin.canHandle("https://google.com"))
    }

    @Test
    fun testDecodeHexReversedAlgorithm() {
        // String "https://test.com/stream.m3u8" in reverse hex:
        // characters: '8', 'u', '3', 'm', '.', 'm', 'a', 'e', 'r', 't', 's', '/', 'm', 'o', 'c', '.', 't', 's', 'e', 't', '/', '/', ':', 's', 'p', 't', 't', 'h'
        val original = "https://test.com/stream.m3u8"
        val reversed = original.reversed()
        val hexString = reversed.map { String.format("%02x", it.code) }.joinToString("")
        val pipeSeparated = hexString.chunked(10).joinToString("|")

        val decoded = plugin.decodeHexReversed(pipeSeparated)
        assertEquals(original, decoded)
    }

    @Test
    fun testParseVixeoHtmlFromFile() {
        val pluginDir = File("c:/Users/LearnersYT/source/TheDesiTadka/Plugins/vixeo.io")
        val sampleFile = File(pluginDir, "vixeo.io.html")
        assertTrue("Sample HTML must exist", sampleFile.exists())

        val html = sampleFile.readText(Charsets.UTF_8)
        val result = plugin.parseVixeoHtml(html, "https://vixeo.io/e/EQkJKzs7LwIf")

        assertTrue("Resolution must succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        val source = result.getOrThrow()

        assertEquals(MediaSourceType.HLS, source.type)
        assertEquals("application/x-mpegURL", source.mimeType)
        assertTrue(source.url.startsWith("https://sfy-01-fr.vidsonic.net/secure/"))
        assertTrue(source.url.contains("video.mp4/index.m3u8"))
        assertTrue(source.url.contains("file_id=EQkJKzs7LwIf"))

        assertNotNull(source.headersRequired)
        assertEquals("https://vixeo.io/", source.headersRequired?.get("Referer"))
        assertEquals("https://vixeo.io", source.headersRequired?.get("Origin"))
    }

    @Test
    fun testHostResolverEngineExtraction() {
        val html = """
            <html>
            <body>
                <div class="video-container">
                    <iframe src="https://vixeo.io/e/Dr6nHyWd3pM9" width="100%" height="100%"></iframe>
                    <iframe src="https://unrelated-ad.com/banner.html"></iframe>
                </div>
            </body>
            </html>
        """.trimIndent()

        val doc = Jsoup.parse(html, "https://watchxxxfree.xyz")
        val candidates = HostResolverEngine.extractCandidateUrls(doc)

        assertEquals(1, candidates.size)
        assertEquals("https://vixeo.io/e/Dr6nHyWd3pM9", candidates.first())
    }
}
