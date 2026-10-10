package com.thedesitadka.core.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class SmartBypassDnsTest {

    @Test
    fun testIpLiteralResolution() {
        val dns = SmartBypassDns()
        val addresses = dns.lookup("1.1.1.1")
        assertNotNull(addresses)
        assertTrue(addresses.isNotEmpty())
        assertTrue(addresses.any { it.hostAddress == "1.1.1.1" })
    }

    @Test
    fun testEmergencyFallbackResolution() {
        val dns = SmartBypassDns(enableDohFallback = false)
        val addresses = dns.lookup("hdzog.com")
        assertNotNull(addresses)
        assertTrue(addresses.isNotEmpty())
        assertFalse(addresses.any { it.hostAddress?.startsWith("49.44.79.") == true })
    }

    @Test
    fun testDohResolutionForKnownHost() {
        val dns = SmartBypassDns(enableDohFallback = true)
        val addresses = dns.lookup("eporner.com")
        assertNotNull(addresses)
        assertTrue(addresses.isNotEmpty())
        assertFalse(addresses.any { it.hostAddress == "49.44.79.236" })
    }
}
