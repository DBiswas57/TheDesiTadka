package com.thedesitadka.core.security

import java.net.InetAddress
import java.net.URI

object UrlSecurityValidator {

    private val BLOCKED_HOST_PATTERNS = listOf(
        "localhost",
        "127.0.0.1",
        "0.0.0.0",
        "169.254.169.254", // Cloud metadata (AWS, GCP, Azure)
        "metadata.google.internal",
        "instance-data",
        "::1"
    )

    /**
     * Validates that the provided URL string is a safe, authorized public endpoint.
     * Enforces:
     * 1. HTTPS scheme only (HTTP is rejected).
     * 2. Host presence.
     * 3. SSRF checks against local, loopback, link-local, carrier-grade NAT, private RFC1918, and cloud metadata IPs.
     */
    fun isUrlSafe(urlString: String): Boolean {
        return try {
            val uri = URI(urlString.trim())
            val scheme = uri.scheme?.lowercase() ?: return false
            if (scheme != "https") {
                StreamHubLogger.w("UrlSecurityValidator", "Rejected non-HTTPS scheme: $scheme in $urlString")
                return false
            }

            val host = uri.host?.lowercase() ?: return false

            // Direct check on host string
            for (blocked in BLOCKED_HOST_PATTERNS) {
                if (host == blocked || host.endsWith(".$blocked")) {
                    StreamHubLogger.e("UrlSecurityValidator", "SSRF attempt detected on blocked host: $host")
                    return false
                }
            }

            // Reject local TLDs
            if (host.endsWith(".local") || host.endsWith(".internal") || host.endsWith(".lan") || host.endsWith(".home")) {
                StreamHubLogger.e("UrlSecurityValidator", "SSRF attempt detected on local domain: $host")
                return false
            }

            // If the host is an IP literal or resolves to private/link-local/loopback IP
            if (isHostPrivateOrRestricted(host)) {
                StreamHubLogger.e("UrlSecurityValidator", "SSRF attempt detected on private IP: $host")
                return false
            }

            true
        } catch (e: Exception) {
            StreamHubLogger.e("UrlSecurityValidator", "Malformed URL: $urlString (${e.message})")
            false
        }
    }

    private fun isHostPrivateOrRestricted(host: String): Boolean {
        return try {
            // Check IP literal or resolve address
            val address = InetAddress.getByName(host)

            if (address.isLoopbackAddress) return true
            if (address.isAnyLocalAddress) return true
            if (address.isLinkLocalAddress) return true
            if (address.isSiteLocalAddress) return true // RFC 1918 (10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16)
            if (address.isMulticastAddress) return true

            // Carrier-grade NAT: 100.64.0.0/10
            val bytes = address.address
            if (bytes.size == 4) {
                val b0 = bytes[0].toInt() and 0xFF
                val b1 = bytes[1].toInt() and 0xFF
                if (b0 == 100 && (b1 in 64..127)) {
                    return true
                }
                // Cloud metadata: 169.254.169.254
                if (b0 == 169 && b1 == 254) {
                    return true
                }
            }

            false
        } catch (e: Exception) {
            // If hostname resolution fails or is invalid, do not allow it
            false
        }
    }
}
