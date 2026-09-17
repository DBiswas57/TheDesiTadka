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
        val sampleHtml = "<html><body><h1>TestSite</h1></body></html>"
        CloudflareHtmlCache.put("https://example-cached.com", sampleHtml)

        // Consume with trailing slash should match
        val consumed = CloudflareHtmlCache.consume("https://example-cached.com/")
        assertEquals(sampleHtml, consumed)

        // Second consume should return null (already consumed)
        assertNull(CloudflareHtmlCache.consume("https://example-cached.com/"))
    }

    @Test
    fun testExpiration() {
        val sampleHtml = "<html><body><h1>TestSite Expired</h1></body></html>"
        CloudflareHtmlCache.put("https://example-cached.com/videos", sampleHtml)

        // maxAgeMs = -1 forces immediate expiration
        val consumed = CloudflareHtmlCache.consume("https://example-cached.com/videos", maxAgeMs = -1L)
        assertNull(consumed)
    }

    @Test
    fun testPutAndGetMultipleTimesWithWww() {
        val sampleHtml = "<html><body><h1>TestSite Multi</h1></body></html>"
        CloudflareHtmlCache.put("https://example-cached.com/video-slug/", sampleHtml)

        // Multiple get calls must all succeed (e.g. for getDetails, getPlayableMedia, getRelatedContent)
        val firstRead = CloudflareHtmlCache.get("https://example-cached.com/video-slug")
        assertEquals(sampleHtml, firstRead)

        val secondRead = CloudflareHtmlCache.get("https://www.example-cached.com/video-slug/")
        assertEquals(sampleHtml, secondRead)

        val thirdRead = CloudflareHtmlCache.get("https://example-cached.com/video-slug/")
        assertEquals(sampleHtml, thirdRead)
    }
}
