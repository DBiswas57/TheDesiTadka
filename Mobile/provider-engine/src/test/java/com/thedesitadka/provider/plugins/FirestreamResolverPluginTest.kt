package com.thedesitadka.provider.plugins

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FirestreamResolverPluginTest {

    private val plugin = FirestreamResolverPlugin()

    @Test
    fun testCanHandle() {
        assertTrue(plugin.canHandle("https://firestream.site/e/WAop-hsu"))
        assertTrue(plugin.canHandle("https://firestream.to/e/nhbUOAaQ"))
        assertTrue(plugin.canHandle("https://www.firestream.site/e/12345"))
        assertTrue(plugin.canHandle("https://firestream.me/v/abc-def"))
        assertTrue(plugin.canHandle("https://firestream.cc/embed/xyz789"))
        assertTrue(!plugin.canHandle("https://vixeo.io/e/EQkJKzs7LwIf"))
        assertTrue(!plugin.canHandle("https://watchxxxfree.xyz/some-video/"))
        assertTrue(!plugin.canHandle("https://google.com"))
    }

    @Test
    fun testExtractSlug() {
        assertEquals("WAop-hsu", plugin.extractSlug("https://firestream.site/e/WAop-hsu"))
        assertEquals("nhbUOAaQ", plugin.extractSlug("https://firestream.to/v/nhbUOAaQ"))
        assertEquals("sample-slug-123", plugin.extractSlug("https://firestream.org/embed/sample-slug-123"))
        assertEquals("direct123", plugin.extractSlug("https://firestream.site/direct123"))
    }

    @Test
    fun testExtractTokenBlobFromHtml() {
        val pluginDir = File("c:/Users/LearnersYT/source/TheDesiTadka/Plugins/firestream.to")
        val sampleFile = File(pluginDir, "firestream.site.html")
        assertTrue("Sample HTML must exist", sampleFile.exists())

        val html = sampleFile.readText(Charsets.UTF_8)
        val tokenBlob = plugin.extractTokenBlob(html)

        assertNotNull(tokenBlob)
        assertTrue("Token blob must be non-empty", tokenBlob!!.isNotBlank())
        assertTrue("Token blob should be valid base64 payload", tokenBlob.length > 20)
    }

    @Test
    fun testParseResolveResponse() {
        val json = """
            {
              "signedVideoUrl": "https://fr-cdn-1.firestream.to/encodings/d2f768ec/50a1ed4c/video.mp4/video.m3u8?md5=GoM0p5a6ZYTR2gVrlxgISw&expires=1789824028",
              "signedVideoSdUrl": null
            }
        """.trimIndent()

        val streamUrl = plugin.parseResolveResponse(json)
        assertNotNull(streamUrl)
        assertEquals(
            "https://fr-cdn-1.firestream.to/encodings/d2f768ec/50a1ed4c/video.mp4/video.m3u8?md5=GoM0p5a6ZYTR2gVrlxgISw&expires=1789824028",
            streamUrl
        )
    }

    @Test
    fun testHostResolverEngineIntegration() {
        val pluginForUrl = HostResolverEngine.findPluginForUrl("https://firestream.site/e/WAop-hsu")
        assertNotNull("HostResolverEngine must find plugin for firestream.site", pluginForUrl)
        assertTrue("Plugin must be FirestreamResolverPlugin", pluginForUrl is FirestreamResolverPlugin)

        val html = """
            <html>
            <body>
                <div class="video-container">
                    <iframe src="https://vixeo.io/e/Dr6nHyWd3pM9" width="100%" height="100%"></iframe>
                    <iframe src="https://firestream.site/e/WAop-hsu" width="100%" height="100%"></iframe>
                    <iframe src="https://unrelated-ad.com/banner.html"></iframe>
                </div>
            </body>
            </html>
        """.trimIndent()

        val doc = Jsoup.parse(html, "https://watchxxxfree.xyz")
        val candidates = HostResolverEngine.extractCandidateUrls(doc)

        assertEquals(2, candidates.size)
        assertTrue(candidates.contains("https://vixeo.io/e/Dr6nHyWd3pM9"))
        assertTrue(candidates.contains("https://firestream.site/e/WAop-hsu"))
    }
}
