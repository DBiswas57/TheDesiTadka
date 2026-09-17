package com.thedesitadka.core.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlSecurityValidatorTest {

    @Test
    fun testValidPublicHttpsUrls() {
        assertTrue(UrlSecurityValidator.isUrlSafe("https://example.com/manifest.json"))
        assertTrue(UrlSecurityValidator.isUrlSafe("https://cdn.streaming.org/video/manifest.m3u8"))
        assertTrue(UrlSecurityValidator.isUrlSafe("https://api.wordpress.org/wp-json/wp/v2/posts"))
    }

    @Test
    fun testRejectInsecureHttp() {
        assertFalse("Plain HTTP must be rejected", UrlSecurityValidator.isUrlSafe("http://example.com/manifest.json"))
        assertFalse("FTP must be rejected", UrlSecurityValidator.isUrlSafe("ftp://example.com/file"))
        assertFalse("File scheme must be rejected", UrlSecurityValidator.isUrlSafe("file:///etc/passwd"))
    }

    @Test
    fun testRejectLoopbackAndLocalhost() {
        assertFalse("localhost must be blocked", UrlSecurityValidator.isUrlSafe("https://localhost/api"))
        assertFalse("127.0.0.1 must be blocked", UrlSecurityValidator.isUrlSafe("https://127.0.0.1:8080/api"))
        assertFalse("0.0.0.0 must be blocked", UrlSecurityValidator.isUrlSafe("https://0.0.0.0:80/"))
    }

    @Test
    fun testRejectPrivateRfc1918AndLinkLocal() {
        assertFalse("10.0.0.1 must be blocked", UrlSecurityValidator.isUrlSafe("https://10.0.0.1/admin"))
        assertFalse("192.168.1.1 must be blocked", UrlSecurityValidator.isUrlSafe("https://192.168.1.1/config"))
        assertFalse("172.16.0.1 must be blocked", UrlSecurityValidator.isUrlSafe("https://172.16.0.1/status"))
        assertFalse("Cloud metadata IP must be blocked", UrlSecurityValidator.isUrlSafe("https://169.254.169.254/latest/meta-data"))
    }

    @Test
    fun testRejectLocalTlds() {
        assertFalse(".local must be blocked", UrlSecurityValidator.isUrlSafe("https://gateway.local/api"))
        assertFalse(".lan must be blocked", UrlSecurityValidator.isUrlSafe("https://server.lan/api"))
        assertFalse(".internal must be blocked", UrlSecurityValidator.isUrlSafe("https://metadata.internal/api"))
    }
}
