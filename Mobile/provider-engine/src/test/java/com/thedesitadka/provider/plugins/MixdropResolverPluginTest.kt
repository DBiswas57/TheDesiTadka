package com.thedesitadka.provider.plugins

import com.thedesitadka.core.model.MediaSourceType
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MixdropResolverPluginTest {

    private val plugin = MixdropResolverPlugin()

    @Test
    fun testCanHandle() {
        assertTrue(plugin.canHandle("https://mixdrop.co/e/vknjxqmlhql3oq"))
        assertTrue(plugin.canHandle("https://mixdrop.ag/e/r6w7wlxgave3jew"))
        assertTrue(plugin.canHandle("https://mdzsmutpcvykb.net/e/r6w7wlxgave3jew"))
        assertTrue(plugin.canHandle("https://ebij8ni1d.mxcontent.net/v2/r6w7wlxgave3jew.mp4"))
        assertTrue(!plugin.canHandle("https://uqload.vc/embed-123.html"))
        assertTrue(!plugin.canHandle("https://google.com"))
    }

    @Test
    fun testMixdropPackedHtmlResolution() {
        // Sample HTML from mdzsmutpcvykb.net with MDCore Dean Edwards packing
        val html = """
            <html>
            <head>
            <script type='text/javascript'>
            eval(function(p,a,c,k,e,d){while(c--)if(k[c])p=p.replace(new RegExp('\\b'+c+'\\b','g'),k[c]);return p}('MDCore.wurl="//81bfqpx79.mxcontent.net/v2/testcode.mp4?s=token&e=123";MDCore.title="Sample Video";',1,1,'||'.split('|'),0,{}))
            </script>
            </head>
            <body></body>
            </html>
        """.trimIndent()

        val result = plugin.resolveFromHtml(html, "https://mdzsmutpcvykb.net/e/testcode")
        assertTrue("Resolution must succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        val source = result.getOrThrow()

        assertEquals(MediaSourceType.PROGRESSIVE_MP4, source.type)
        assertEquals("video/mp4", source.mimeType)
        assertTrue(source.url.startsWith("https://81bfqpx79.mxcontent.net/v2/testcode.mp4"))

        assertNotNull(source.headersRequired)
        assertEquals("https://mdzsmutpcvykb.net/", source.headersRequired?.get("Referer"))
    }

    @Test
    fun testHostResolverEngineIntegration() {
        val pluginForUrl = HostResolverEngine.findPluginForUrl("https://mdzsmutpcvykb.net/e/r6w7wlxgave3jew")
        assertNotNull("HostResolverEngine must find plugin for mdzsmutpcvykb.net", pluginForUrl)
        assertTrue("Plugin must be MixdropResolverPlugin", pluginForUrl is MixdropResolverPlugin)

        val html = """
            <html>
            <body>
                <iframe src="https://mdzsmutpcvykb.net/e/r6w7wlxgave3jew"></iframe>
            </body>
            </html>
        """.trimIndent()

        val doc = Jsoup.parse(html, "https://livecamrips.to")
        val candidates = HostResolverEngine.extractCandidateUrls(doc)
        assertEquals(1, candidates.size)
        assertTrue(candidates.contains("https://mdzsmutpcvykb.net/e/r6w7wlxgave3jew"))
    }
}
