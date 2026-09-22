package com.thedesitadka.provider.plugins

import com.thedesitadka.core.model.MediaSourceType
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LuluvdoResolverPluginTest {

    private val plugin = LuluvdoResolverPlugin()

    @Test
    fun testCanHandle() {
        assertTrue(plugin.canHandle("https://luluvdo.com/e/aft5mblywcq6"))
        assertTrue(plugin.canHandle("https://lulustream.com/e/aft5mblywcq6"))
        assertTrue(plugin.canHandle("https://luluvid.com/e/aft5mblywcq6"))
        assertTrue(plugin.canHandle("https://playmogo.com/e/nitrrehbaw9m"))
        assertTrue(plugin.canHandle("https://luluvdo.com/d/aft5mblywcq6"))
        assertTrue(plugin.canHandle("https://cdn1.site/e/uhxj1topnqk3"))
        assertTrue(!plugin.canHandle("https://firestream.site/e/WAop-hsu"))
        assertTrue(!plugin.canHandle("https://vixeo.io/e/EQkJKzs7LwIf"))
        assertTrue(!plugin.canHandle("https://google.com"))
    }

    @Test
    fun testExtractSlug() {
        assertEquals("aft5mblywcq6", plugin.extractSlug("https://luluvdo.com/e/aft5mblywcq6"))
        assertEquals("aft5mblywcq6", plugin.extractSlug("https://lulustream.com/d/aft5mblywcq6"))
        assertEquals("nitrrehbaw9m", plugin.extractSlug("https://playmogo.com/e/nitrrehbaw9m"))
        assertEquals("uhxj1topnqk3", plugin.extractSlug("https://cdn1.site/e/uhxj1topnqk3"))
    }

    @Test
    fun testUnpackAndExtractStreamFromLocalHtml() {
        val pluginDir = File("c:/Users/LearnersYT/source/TheDesiTadka/Plugins/luluvdo.com")
        val sampleFile = File(pluginDir, "luluvdo.com (1).html")
        assertTrue("Sample HTML must exist", sampleFile.exists())

        val html = sampleFile.readText(Charsets.UTF_8)
        val result = plugin.resolveFromHtml(html, "https://luluvdo.com/e/aft5mblywcq6")

        assertTrue("Resolution must succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        val source = result.getOrThrow()

        assertEquals(MediaSourceType.HLS, source.type)
        assertEquals("application/x-mpegURL", source.mimeType)
        assertTrue(source.url.contains("master.m3u8"))
        assertTrue(source.url.contains("aft5mblywcq6"))
        assertTrue(source.url.contains("tnmr.org"))

        assertNotNull(source.headersRequired)
        assertEquals("https://luluvdo.com/", source.headersRequired?.get("Referer"))
        assertEquals("https://luluvdo.com", source.headersRequired?.get("Origin"))
    }

    @Test
    fun testHostResolverEngineIntegration() {
        val pluginForUrl = HostResolverEngine.findPluginForUrl("https://luluvdo.com/e/aft5mblywcq6")
        assertNotNull("HostResolverEngine must find plugin for luluvdo.com", pluginForUrl)
        assertTrue("Plugin must be LuluvdoResolverPlugin", pluginForUrl is LuluvdoResolverPlugin)

        val html = """
            <html>
            <body>
                <div class="video-container">
                    <iframe src="https://luluvdo.com/e/aft5mblywcq6" width="100%" height="100%"></iframe>
                    <iframe src="https://vixeo.io/e/Dr6nHyWd3pM9" width="100%" height="100%"></iframe>
                    <iframe src="https://firestream.site/e/WAop-hsu" width="100%" height="100%"></iframe>
                    <iframe src="https://unrelated-ad.com/banner.html"></iframe>
                </div>
            </body>
            </html>
        """.trimIndent()

        val doc = Jsoup.parse(html, "https://watchxxxfree.xyz")
        val candidates = HostResolverEngine.extractCandidateUrls(doc)

        assertEquals(3, candidates.size)
        assertTrue(candidates.contains("https://luluvdo.com/e/aft5mblywcq6"))
        assertTrue(candidates.contains("https://vixeo.io/e/Dr6nHyWd3pM9"))
        assertTrue(candidates.contains("https://firestream.site/e/WAop-hsu"))
    }

    @Test
    fun testLiveResolution() = kotlinx.coroutines.runBlocking {
        val res = plugin.resolve("https://luluvdo.com/e/aft5mblywcq6", "https://watchxxxfree.xyz/evil-angel-misha-maver-anuskatzz/")
        println("LIVE RESOLUTION RESULT: $res")
        if (res.isFailure) {
            res.exceptionOrNull()?.printStackTrace()
        }
        assertTrue("Live resolution must succeed: ${res.exceptionOrNull()?.message}", res.isSuccess)

        val mediaSource = res.getOrThrow()
        println("Testing NetworkClient to stream URL: ${mediaSource.url}")
        val okHttpResult = com.thedesitadka.core.network.NetworkClient.fetchString(mediaSource.url, headers = mediaSource.headersRequired ?: emptyMap())
        assertTrue("HLS playlist must contain EXTM3U", okHttpResult.contains("#EXTM3U"))
    }
}
