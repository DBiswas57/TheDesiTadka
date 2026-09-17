package com.thedesitadka.core.network

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CloudflareHtmlCacheTest {

    @Before
    fun setup() {
        CloudflareHtmlCache.clear()
    }

    @Test
    fun testPutAndConsumeNormalizedUrls() {
        val sampleHtml = "<html><body><h1>PornX11</h1></body></html>"
        CloudflareHtmlCache.put("https://pornx11.com", sampleHtml)

        // Consume with trailing slash should match
        val consumed = CloudflareHtmlCache.consume("https://pornx11.com/")
        assertEquals(sampleHtml, consumed)

        // Second consume should return null (already consumed)
        assertNull(CloudflareHtmlCache.consume("https://pornx11.com/"))
    }

    @Test
    fun testExpiration() {
        val sampleHtml = "<html><body><h1>PornX11 Expired</h1></body></html>"
        CloudflareHtmlCache.put("https://pornx11.com/videos", sampleHtml)

        // maxAgeMs = -1 forces immediate expiration
        val consumed = CloudflareHtmlCache.consume("https://pornx11.com/videos", maxAgeMs = -1L)
        assertNull(consumed)
    }
}
