package com.thedesitadka.provider.plugins

import com.thedesitadka.core.model.MediaSourceType
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MydaddyResolverPluginTest {

    private val plugin = MydaddyResolverPlugin()

    @Test
    fun testCanHandle() {
        assertTrue(plugin.canHandle("https://mydaddy.cc/video/27549b5f615036a9ca/"))
        assertTrue(plugin.canHandle("//mydaddy.cc/video/27549b5f615036a9ca/"))
        assertTrue(plugin.canHandle("https://s36.bigcdn.cc/pubs/6aaf39efe25c48.15363868/1080.mp4"))
        assertTrue(!plugin.canHandle("https://vixeo.io/e/Dr6nHyWd3pM9"))
        assertTrue(!plugin.canHandle("https://luluvdo.com/e/aft5mblywcq6"))
        assertTrue(!plugin.canHandle("https://firestream.site/e/WAop-hsu"))
        assertTrue(!plugin.canHandle("https://google.com"))
    }

    @Test
    fun testResolveFromDynamicHtml() {
        val sampleHtml = """
            <!DOCTYPE html>
            <html>
            <head><title>MyDaddy Player</title></head>
            <body>
            <div id="jw"></div>
            <script>
            function do_pl() {
                $("#jw").html("<video id=\"flvv\" preload=\"none\" poster=\"//s8.bigcdn.cc/pubs/6aaf39efe25c48.15363868/main.jpg\" controls style=\"width:100%; height:100%;\" src=\"\"><source src=\"//s8.bigcdn.cc/pubs/6aaf39efe25c48.15363868/360.mp4\" title=\"360p\" type=\"video/mp4\" /><source src=\"//s8.bigcdn.cc/pubs/6aaf39efe25c48.15363868/720.mp4\" title=\"720p HD\" type=\"video/mp4\" /><source src=\"//s8.bigcdn.cc/pubs/6aaf39efe25c48.15363868/1080.mp4\" title=\"1080p Full HD\" type=\"video/mp4\" /></video>");
            }
            do_pl();
            </script>
            </body>
            </html>
        """.trimIndent()

        val embedUrl = "https://mydaddy.cc/video/27549b5f615036a9ca/"
        val parentUrl = "https://hqporner.com/hdporn/127881-he_can_be_short_if_he_rocks_the_boat.html"

        val result = plugin.resolveFromHtml(sampleHtml, embedUrl, parentUrl)
        assertNotNull("Resolution result must not be null", result)
        assertTrue("Resolution must succeed: ${result?.exceptionOrNull()?.message}", result!!.isSuccess)

        val mediaSource = result.getOrThrow()
        assertEquals(MediaSourceType.PROGRESSIVE_MP4, mediaSource.type)
        assertEquals("video/mp4", mediaSource.mimeType)
        assertEquals("https://s8.bigcdn.cc/pubs/6aaf39efe25c48.15363868/1080.mp4", mediaSource.url)
        assertEquals("1080p Full HD", mediaSource.quality)
        assertTrue("Download availability must be enabled", mediaSource.canDownload)
        assertEquals(mediaSource.url, mediaSource.downloadUrl)

        assertNotNull(mediaSource.headersRequired)
        assertEquals(parentUrl, mediaSource.headersRequired["Referer"])
        assertEquals("https://hqporner.com", mediaSource.headersRequired["Origin"])
    }

    @Test
    fun testHostResolverEngineIntegration() {
        val pluginForUrl = HostResolverEngine.findPluginForUrl("https://mydaddy.cc/video/27549b5f615036a9ca/")
        assertNotNull("HostResolverEngine must find plugin for mydaddy.cc", pluginForUrl)
        assertTrue("Plugin must be MydaddyResolverPlugin", pluginForUrl is MydaddyResolverPlugin)

        val html = """
            <html>
            <body>
                <div class="video-container">
                    <iframe src="//mydaddy.cc/video/27549b5f615036a9ca/" frameborder="0" allowfullscreen=""></iframe>
                    <iframe src="https://go.mavrtracktor.com/smartpop/fca968bd474724a286684fd77b85c8a87eafb79f11f158f54d530de5eabb5ac1"></iframe>
                </div>
            </body>
            </html>
        """.trimIndent()

        val doc = Jsoup.parse(html)
        val candidateUrls = HostResolverEngine.extractCandidateUrls(doc)

        assertEquals("Must extract exactly 1 candidate host URL", 1, candidateUrls.size)
        assertEquals("https://mydaddy.cc/video/27549b5f615036a9ca/", candidateUrls[0])
    }

    @Test
    fun testHqpornerHtmlEmbedDetection() {
        val baseDir = File("c:/Users/LearnersYT/source/TheDesiTadka/EnglishSites/hqporner.com")
        val htmlFile = File(baseDir, "hqporner.com (10).html")
        assertTrue("File hqporner.com (10).html must exist", htmlFile.exists())

        val html = htmlFile.readText(Charsets.UTF_8)
        val doc = Jsoup.parse(html)
        val candidateUrls = HostResolverEngine.extractCandidateUrls(doc)

        assertTrue("Must find mydaddy embed candidate in hqporner page", candidateUrls.isNotEmpty())
        assertEquals("https://mydaddy.cc/video/27549b5f615036a9ca/", candidateUrls[0])
    }
}
