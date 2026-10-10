package com.thedesitadka.provider.plugins

import com.thedesitadka.core.model.MediaSourceType
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UqloadResolverPluginTest {

    private val plugin = UqloadResolverPlugin()

    @Test
    fun testCanHandle() {
        assertTrue(plugin.canHandle("https://uqload.vc/embed-vzwobtd0gp22.html"))
        assertTrue(plugin.canHandle("https://uqload.com/embed-kefcpyd47lp2.html"))
        assertTrue(plugin.canHandle("https://uqload.to/embed-xkyb5ndlw1lr.html"))
        assertTrue(plugin.canHandle("https://strm8.uqload.vc/embed-test.html"))
        assertTrue(plugin.canHandle("https://abstream.to/embed-9cr9qqz7kyt4.html"))
        assertTrue(plugin.canHandle("https://happyams1.streambucket.xyz/embed-test.html"))
        assertTrue(!plugin.canHandle("https://google.com"))
        assertTrue(!plugin.canHandle("https://luluvdo.com/e/123"))
    }

    @Test
    fun testUqloadPackedHtmlResolution() {
        // Sample HTML from uqload.vc with Dean Edwards packing
        val html = """
            <html>
            <head>
            <script type='text/javascript'>
            eval(function(p,a,c,k,e,d){while(c--)if(k[c])p=p.replace(new RegExp('\\b'+c+'\\b','g'),k[c]);return p}('jwplayer("vplayer").setup({sources:[{file:"https://strm8.uqload.vc/hls2/01/05836/vzwobtd0gp22_h/master.m3u8?t=token&s=123"}]});',1,1,'||'.split('|'),0,{}))
            </script>
            </head>
            <body><div id="vplayer"></div></body>
            </html>
        """.trimIndent()

        val result = plugin.resolveFromHtml(html, "https://uqload.vc/embed-vzwobtd0gp22.html")
        assertTrue("Resolution must succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        val source = result.getOrThrow()

        assertEquals(MediaSourceType.HLS, source.type)
        assertEquals("application/vnd.apple.mpegurl", source.mimeType)
        assertTrue(source.url.contains("master.m3u8"))
        assertTrue(source.url.contains("strm8.uqload.vc"))

        assertNotNull(source.headersRequired)
        assertEquals("https://uqload.vc/", source.headersRequired?.get("Referer"))
        assertEquals("https://uqload.vc", source.headersRequired?.get("Origin"))
    }

    @Test
    fun testAbstreamHtmlResolution() {
        // Sample HTML from abstream.to
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
            <script type="text/javascript">
            jwplayer("vplayer").setup({
                sources: [
                    {file: "https://happyams1.streambucket.xyz/hls2/01/00546/,9cr9qqz7kyt4_l,.urlset/master.m3u8?t=token123"}
                ]
            });
            </script>
            </head>
            <body><div id="vplayer"></div></body>
            </html>
        """.trimIndent()

        val result = plugin.resolveFromHtml(html, "https://abstream.to/embed-9cr9qqz7kyt4.html")
        assertTrue("Resolution must succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        val source = result.getOrThrow()

        assertEquals(MediaSourceType.HLS, source.type)
        assertEquals("application/vnd.apple.mpegurl", source.mimeType)
        assertTrue(source.url.contains("master.m3u8"))
        assertTrue(source.url.contains("streambucket.xyz"))

        assertNotNull(source.headersRequired)
        assertEquals("https://abstream.to/", source.headersRequired?.get("Referer"))
        assertEquals("https://abstream.to", source.headersRequired?.get("Origin"))
    }

    @Test
    fun testHostResolverEngineIntegration() {
        val uqloadPlugin = HostResolverEngine.findPluginForUrl("https://uqload.vc/embed-vzwobtd0gp22.html")
        assertNotNull("HostResolverEngine must find plugin for uqload.vc", uqloadPlugin)
        assertTrue("Plugin must be UqloadResolverPlugin", uqloadPlugin is UqloadResolverPlugin)

        val abstreamPlugin = HostResolverEngine.findPluginForUrl("https://abstream.to/embed-9cr9qqz7kyt4.html")
        assertNotNull("HostResolverEngine must find plugin for abstream.to", abstreamPlugin)
        assertTrue("Plugin must be UqloadResolverPlugin", abstreamPlugin is UqloadResolverPlugin)

        val html = """
            <html>
            <body>
                <iframe src="https://uqload.vc/embed-vzwobtd0gp22.html"></iframe>
                <iframe src="https://abstream.to/embed-9cr9qqz7kyt4.html"></iframe>
            </body>
            </html>
        """.trimIndent()

        val doc = Jsoup.parse(html, "https://livecamrips.to")
        val candidates = HostResolverEngine.extractCandidateUrls(doc)
        assertEquals(2, candidates.size)
        assertTrue(candidates.contains("https://uqload.vc/embed-vzwobtd0gp22.html"))
        assertTrue(candidates.contains("https://abstream.to/embed-9cr9qqz7kyt4.html"))
    }
}
