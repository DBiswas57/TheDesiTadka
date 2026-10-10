package com.thedesitadka.core.network

import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Smart DNS resolver that automatically detects ISP DNS sinkholes (such as Indian ISP sinkhole 49.44.79.236)
 * and NXDOMAIN / DNS resolution failures on mobile devices, seamlessly falling back to DNS-over-HTTPS (DoH)
 * via Cloudflare (1.1.1.1) and Google (8.8.8.8).
 */
class SmartBypassDns(
    private val enableDohFallback: Boolean = true
) : Dns {

    private val cache = ConcurrentHashMap<String, CachedDnsEntry>()

    // Dedicated lightweight OkHttpClient for DoH queries with short timeouts and no circular DNS
    private val dohClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> {
                    return when (hostname) {
                        "dns.adguard-dns.com" -> {
                            try {
                                val resolved = Dns.SYSTEM.lookup(hostname).filter { !isSinkholeIp(it.hostAddress) }
                                if (resolved.isNotEmpty()) resolved
                                else listOf(
                                    InetAddress.getByAddress("dns.adguard-dns.com", byteArrayOf(94.toByte(), 140.toByte(), 14.toByte(), 14.toByte())),
                                    InetAddress.getByAddress("dns.adguard-dns.com", byteArrayOf(94.toByte(), 140.toByte(), 15.toByte(), 15.toByte()))
                                )
                            } catch (e: Exception) {
                                listOf(
                                    InetAddress.getByAddress("dns.adguard-dns.com", byteArrayOf(94.toByte(), 140.toByte(), 14.toByte(), 14.toByte())),
                                    InetAddress.getByAddress("dns.adguard-dns.com", byteArrayOf(94.toByte(), 140.toByte(), 15.toByte(), 15.toByte()))
                                )
                            }
                        }
                        "dns.alidns.com" -> {
                            try {
                                val resolved = Dns.SYSTEM.lookup(hostname).filter { !isSinkholeIp(it.hostAddress) }
                                if (resolved.isNotEmpty()) resolved
                                else listOf(
                                    InetAddress.getByAddress("dns.alidns.com", byteArrayOf(223.toByte(), 5.toByte(), 5.toByte(), 5.toByte())),
                                    InetAddress.getByAddress("dns.alidns.com", byteArrayOf(223.toByte(), 6.toByte(), 6.toByte(), 6.toByte()))
                                )
                            } catch (e: Exception) {
                                listOf(
                                    InetAddress.getByAddress("dns.alidns.com", byteArrayOf(223.toByte(), 5.toByte(), 5.toByte(), 5.toByte())),
                                    InetAddress.getByAddress("dns.alidns.com", byteArrayOf(223.toByte(), 6.toByte(), 6.toByte(), 6.toByte()))
                                )
                            }
                        }
                        "cloudflare-dns.com" -> {
                            try {
                                val resolved = Dns.SYSTEM.lookup(hostname).filter { !isSinkholeIp(it.hostAddress) }
                                if (resolved.isNotEmpty()) resolved
                                else listOf(
                                    InetAddress.getByAddress("cloudflare-dns.com", byteArrayOf(104.toByte(), 16.toByte(), 248.toByte(), 249.toByte())),
                                    InetAddress.getByAddress("cloudflare-dns.com", byteArrayOf(104.toByte(), 16.toByte(), 249.toByte(), 249.toByte()))
                                )
                            } catch (e: Exception) {
                                listOf(
                                    InetAddress.getByAddress("cloudflare-dns.com", byteArrayOf(104.toByte(), 16.toByte(), 248.toByte(), 249.toByte())),
                                    InetAddress.getByAddress("cloudflare-dns.com", byteArrayOf(104.toByte(), 16.toByte(), 249.toByte(), 249.toByte()))
                                )
                            }
                        }
                        "dns.google" -> {
                            try {
                                val resolved = Dns.SYSTEM.lookup(hostname).filter { !isSinkholeIp(it.hostAddress) }
                                if (resolved.isNotEmpty()) resolved
                                else listOf(
                                    InetAddress.getByAddress("dns.google", byteArrayOf(8.toByte(), 8.toByte(), 8.toByte(), 8.toByte())),
                                    InetAddress.getByAddress("dns.google", byteArrayOf(8.toByte(), 8.toByte(), 4.toByte(), 4.toByte()))
                                )
                            } catch (e: Exception) {
                                listOf(
                                    InetAddress.getByAddress("dns.google", byteArrayOf(8.toByte(), 8.toByte(), 8.toByte(), 8.toByte())),
                                    InetAddress.getByAddress("dns.google", byteArrayOf(8.toByte(), 8.toByte(), 4.toByte(), 4.toByte()))
                                )
                            }
                        }
                        "1.1.1.1" -> listOf(InetAddress.getByAddress("1.1.1.1", byteArrayOf(1, 1, 1, 1)))
                        "1.0.0.1" -> listOf(InetAddress.getByAddress("1.0.0.1", byteArrayOf(1, 0, 0, 1)))
                        "8.8.8.8" -> listOf(InetAddress.getByAddress("8.8.8.8", byteArrayOf(8, 8, 8, 8.toByte())))
                        "8.8.4.4" -> listOf(InetAddress.getByAddress("8.8.4.4", byteArrayOf(8, 8, 4, 4)))
                        else -> Dns.SYSTEM.lookup(hostname)
                    }
                }
            })
            .hostnameVerifier { hostname, _ ->
                hostname == "1.1.1.1" || hostname == "1.0.0.1" || hostname == "8.8.8.8" || hostname == "8.8.4.4" ||
                hostname == "dns.adguard-dns.com" || hostname == "dns.alidns.com" ||
                hostname == "cloudflare-dns.com" || hostname == "dns.google"
            }
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .callTimeout(3, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    override fun lookup(hostname: String): List<InetAddress> {
        val cleanHost = hostname.trim().lowercase()

        // 1. IP literal check
        if (IPV4_PATTERN.matcher(cleanHost).matches()) {
            return listOf(InetAddress.getByName(cleanHost))
        }

        // 2. Cache lookup with TTL check (5 minutes)
        val cached = cache[cleanHost]
        if (cached != null && System.currentTimeMillis() - cached.timestamp < CACHE_TTL_MS) {
            return cached.addresses
        }

        // 3. Fast path: Query System DNS
        var systemFailed = false
        var sinkholeDetected = false
        try {
            val systemAddresses = Dns.SYSTEM.lookup(cleanHost)
            if (systemAddresses.isNotEmpty()) {
                val isSinkholed = systemAddresses.any { isSinkholeIp(it.hostAddress) }
                if (!isSinkholed) {
                    cache[cleanHost] = CachedDnsEntry(systemAddresses, System.currentTimeMillis())
                    return systemAddresses
                }
                sinkholeDetected = true
            }
        } catch (e: Exception) {
            systemFailed = true
        }

        // 4. DoH Fallback path if sinkhole or DNS failure detected
        if (enableDohFallback) {
            val dohResult = resolveViaDoh(cleanHost)
            if (dohResult.isNotEmpty()) {
                cache[cleanHost] = CachedDnsEntry(dohResult, System.currentTimeMillis())
                return dohResult
            }
        }

        // 5. Hardcoded emergency fallbacks for known critical adult & streaming hosts
        val emergencyIps = getEmergencyIps(cleanHost)
        if (emergencyIps.isNotEmpty()) {
            cache[cleanHost] = CachedDnsEntry(emergencyIps, System.currentTimeMillis())
            return emergencyIps
        }

        throw UnknownHostException("Unable to resolve $cleanHost via System DNS or DoH fallback (sinkholed: $sinkholeDetected, systemFailed: $systemFailed)")
    }

    private fun resolveViaDoh(hostname: String): List<InetAddress> {
        // 1. Try AdGuard DoH (highly reliable across mobile networks in India)
        val adguardAddresses = queryDohEndpoint("https://dns.adguard-dns.com/resolve?name=$hostname&type=A", hostname)
        if (adguardAddresses.isNotEmpty()) {
            return adguardAddresses
        }

        // 2. Try AliDNS DoH (rapid response, unblocked)
        val alidnsAddresses = queryDohEndpoint("https://dns.alidns.com/resolve?name=$hostname&type=A", hostname)
        if (alidnsAddresses.isNotEmpty()) {
            return alidnsAddresses
        }

        // 3. Try Google Domain DoH
        val googleDomainAddresses = queryDohEndpoint("https://dns.google/resolve?name=$hostname&type=A", hostname)
        if (googleDomainAddresses.isNotEmpty()) {
            return googleDomainAddresses
        }

        // 4. Try Cloudflare Domain DoH
        val cfDomainAddresses = queryDohEndpoint("https://cloudflare-dns.com/dns-query?name=$hostname&type=A", hostname)
        if (cfDomainAddresses.isNotEmpty()) {
            return cfDomainAddresses
        }

        // 5. Try Cloudflare Primary IP DoH (1.1.1.1)
        val cfAddresses = queryDohEndpoint("https://1.1.1.1/dns-query?name=$hostname&type=A", hostname)
        if (cfAddresses.isNotEmpty()) {
            return cfAddresses
        }

        // 6. Try Google IP DoH (8.8.8.8)
        val googleAddresses = queryDohEndpoint("https://8.8.8.8/resolve?name=$hostname&type=A", hostname)
        if (googleAddresses.isNotEmpty()) {
            return googleAddresses
        }

        return emptyList()
    }

    private fun queryDohEndpoint(url: String, hostname: String): List<InetAddress> {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/dns-json")
                .header("User-Agent", "TheDesiTadka-SmartDns/1.0")
                .build()

            dohClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                parseIpsFromDnsJson(body, hostname)
            }
        } catch (e: Exception) {
            com.thedesitadka.core.security.StreamHubLogger.w("SmartBypassDns", "DoH query failed for $url: ${e.message}")
            emptyList()
        }
    }

    private fun parseIpsFromDnsJson(json: String, hostname: String): List<InetAddress> {
        val matcher = IPV4_DATA_PATTERN.matcher(json)
        val addresses = mutableListOf<InetAddress>()
        while (matcher.find()) {
            val ipStr = matcher.group(1) ?: continue
            if (!isSinkholeIp(ipStr) && IPV4_PATTERN.matcher(ipStr).matches()) {
                try {
                    val parts = ipStr.split(".").map { it.toInt().toByte() }
                    if (parts.size == 4) {
                        addresses.add(InetAddress.getByAddress(hostname, byteArrayOf(parts[0], parts[1], parts[2], parts[3])))
                    }
                } catch (e: Exception) {
                    // ignore invalid address
                }
            }
        }
        return addresses
    }

    private fun isSinkholeIp(ip: String?): Boolean {
        if (ip.isNullOrBlank()) return true
        val trimmed = ip.trim()
        if (SINKHOLE_EXACT_IPS.contains(trimmed)) return true
        for (prefix in SINKHOLE_PREFIXES) {
            if (trimmed.startsWith(prefix)) return true
        }
        return false
    }

    private fun getEmergencyIps(hostname: String): List<InetAddress> {
        val clean = hostname.removePrefix("www.").removePrefix("m.")
        val ips = EMERGENCY_HOST_MAP[clean] ?: EMERGENCY_HOST_MAP[hostname] ?: return emptyList()
        return ips.mapNotNull { ipStr ->
            try {
                val parts = ipStr.split(".").map { it.toInt().toByte() }
                InetAddress.getByAddress(hostname, byteArrayOf(parts[0], parts[1], parts[2], parts[3]))
            } catch (e: Exception) {
                null
            }
        }
    }

    private data class CachedDnsEntry(
        val addresses: List<InetAddress>,
        val timestamp: Long
    )

    companion object {
        private const val CACHE_TTL_MS = 5 * 60 * 1000L // 5 minutes

        private val IPV4_PATTERN = Pattern.compile("""^(?:\d{1,3}\.){3}\d{1,3}$""")
        private val IPV4_DATA_PATTERN = Pattern.compile(""""data"\s*:\s*"(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})"""")

        // Known Indian and international ISP DNS sinkhole prefixes and addresses
        private val SINKHOLE_EXACT_IPS = setOf(
            "49.44.79.236",
            "49.44.79.237",
            "49.44.79.238",
            "49.44.79.239",
            "180.179.211.236",
            "180.179.211.237",
            "103.241.136.236",
            "115.112.8.236",
            "127.0.0.1",
            "0.0.0.0"
        )

        private val SINKHOLE_PREFIXES = listOf(
            "49.44.79.",
            "180.179.211."
        )

        // Verified live IPs for critical hosts as tertiary fallback if network DoH is temporarily blocked
        private val EMERGENCY_HOST_MAP = mapOf(
            "hdzog.com" to listOf("109.206.162.36"),
            "tn.hdzog.com" to listOf("109.206.162.36"),
            "pv1.hdzog.com" to listOf("109.206.162.36"),
            "empflix.com" to listOf("51.158.154.154", "66.163.126.52", "193.111.199.8"),
            "beeg.com" to listOf("104.21.56.86", "172.67.181.179"),
            "store.externulls.com" to listOf("104.21.79.118", "172.67.170.134"),
            "thumbs.externulls.com" to listOf("104.21.79.118", "172.67.170.134"),
            "eporner.com" to listOf("94.75.220.1"),
            "www.eporner.com" to listOf("94.75.220.1"),
            "drtuber.desi" to listOf("78.140.187.220", "188.72.207.212"),
            "m.drtuber.desi" to listOf("78.140.187.220", "188.72.207.212"),
            "www.drtuber.desi" to listOf("78.140.187.220", "188.72.207.212"),
            "drtuber.com" to listOf("78.140.187.214", "188.72.207.210"),
            "3movs.xxx" to listOf("208.122.196.244"),
            "www.3movs.xxx" to listOf("208.122.196.244"),
            "pornone.com" to listOf("104.26.14.226", "172.67.72.49", "104.26.15.226"),
            "www.pornone.com" to listOf("104.26.14.226", "172.67.72.49", "104.26.15.226"),
            "pornhub.com" to listOf("66.254.114.41"),
            "www.pornhub.com" to listOf("66.254.114.41"),
            "prmovies.futbol" to listOf("104.21.25.128", "172.67.134.62"),
            "youjizz.com" to listOf("66.254.114.242"),
            "www.youjizz.com" to listOf("66.254.114.242"),
            "spankbang.com" to listOf("104.19.239.156", "104.18.96.242"),
            "tnaflix.com" to listOf("85.159.232.21", "85.159.232.22"),
            "tube8.es" to listOf("66.254.114.239"),
            "www.tube8.es" to listOf("66.254.114.239"),
            "tube8.com" to listOf("66.254.114.239"),
            "www.tube8.com" to listOf("66.254.114.239"),
            "nuvid.com" to listOf("88.208.18.240", "88.208.18.241"),
            "nuvid.org" to listOf("88.208.18.240", "88.208.18.241"),
            "m.nuvid.org" to listOf("88.208.18.240", "88.208.18.241"),
            "www.nuvid.org" to listOf("88.208.18.240", "88.208.18.241")
        )
    }
}
