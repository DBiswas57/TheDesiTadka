package com.thedesitadka.provider.plugins

import com.thedesitadka.core.model.MediaSourceType
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class UpnsResolverPluginTest {

    private val plugin = UpnsResolverPlugin()

    @Test
    fun testCanHandle() {
        assertTrue(plugin.canHandle("https://videosh.upns.live/#c1nk65&ua=1"))
        assertTrue(plugin.canHandle("https://upns.live/#c1nk65"))
        assertTrue(plugin.canHandle("https://upnshare.com/c1nk65"))
        assertTrue(plugin.canHandle("https://upns.co/c1nk65"))
        assertFalse(plugin.canHandle("https://google.com"))
        assertFalse(plugin.canHandle("https://vidara.so/e/123"))
    }

    @Test
    fun testExtractVideoId() {
        assertEquals("c1nk65", plugin.extractVideoId("https://videosh.upns.live/#c1nk65&ua=1"))
        assertEquals("c1nk65", plugin.extractVideoId("https://videosh.upns.live/#c1nk65&amp;ua=1"))
        assertEquals("dt9zf3", plugin.extractVideoId("https://videosh.upns.live/#dt9zf3"))
        assertEquals("mlz86u", plugin.extractVideoId("https://videosh.upns.live/api/v1/video?id=mlz86u"))
        assertEquals("r5l3hf", plugin.extractVideoId("https://upnshare.com/e/r5l3hf"))
    }

    @Test
    fun testDecryptPayload() {
        val sampleJson = """{"cfNative":"https://videosh.upns.live/v4/pl/test/master.m3u8?k=token&kx=123","source":"https://94.131.217.190/v4/master.m3u8","title":"test.mp4"}"""

        val key = "kiemtienmua911ca".toByteArray(Charsets.UTF_8)
        val iv = "1234567890oiuytr".toByteArray(Charsets.UTF_8)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        val encryptedBytes = cipher.doFinal(sampleJson.toByteArray(Charsets.UTF_8))
        val hexString = encryptedBytes.joinToString("") { "%02x".format(it) }

        val decrypted = plugin.decryptPayload(hexString)
        assertEquals(sampleJson, decrypted)
        assertTrue(decrypted.contains("master.m3u8"))
    }

    @Test
    fun testHostResolverEngineIntegration() {
        val upnsPlugin = HostResolverEngine.findPluginForUrl("https://videosh.upns.live/#c1nk65&ua=1")
        assertNotNull("HostResolverEngine must find plugin for videosh.upns.live", upnsPlugin)
        assertTrue("Plugin must be UpnsResolverPlugin", upnsPlugin is UpnsResolverPlugin)

        val html = """
            <html>
            <body>
                <iframe id="iframe-video" src="https://videosh.upns.live/#c1nk65&amp;ua=1"></iframe>
            </body>
            </html>
        """.trimIndent()

        val doc = Jsoup.parse(html, "https://livecamrips.to")
        val candidates = HostResolverEngine.extractCandidateUrls(doc)
        assertEquals(1, candidates.size)
        assertTrue(candidates[0].contains("c1nk65"))
    }
}
