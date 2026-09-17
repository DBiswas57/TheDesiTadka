package com.thedesitadka.core.network

import java.net.URI
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe cache holding pristine HTML captured immediately upon solving
 * a Cloudflare challenge in WebView, preventing redundant and error-prone network requests.
 */
object CloudflareHtmlCache {

    private data class CachedEntry(
        val html: String,
        val timestamp: Long
    )

    private val cache = ConcurrentHashMap<String, CachedEntry>()

    /**
     * Store freshly verified HTML for a given URL.
     */
    fun put(url: String, html: String) {
        if (url.isBlank() || html.isBlank()) return
        val key = normalizeKey(url)
        cache[key] = CachedEntry(html, System.currentTimeMillis())
    }

    /**
     * Consume (read and remove) cached HTML if within [maxAgeMs] validity window.
     */
    fun consume(url: String, maxAgeMs: Long = 120_000): String? {
        if (url.isBlank()) return null
        val key = normalizeKey(url)
        val entry = cache.remove(key) ?: return null
        return if (System.currentTimeMillis() - entry.timestamp <= maxAgeMs) {
            entry.html
        } else {
            null
        }
    }

    /**
     * Retrieve cached HTML if within [maxAgeMs] validity window without removing it.
     */
    fun get(url: String, maxAgeMs: Long = 120_000): String? = peek(url, maxAgeMs)

    /**
     * Peek at cached HTML without removing it.
     */
    fun peek(url: String, maxAgeMs: Long = 120_000): String? {
        if (url.isBlank()) return null
        val key = normalizeKey(url)
        val entry = cache[key] ?: return null
        return if (System.currentTimeMillis() - entry.timestamp <= maxAgeMs) {
            entry.html
        } else {
            null
        }
    }

    /**
     * Clear all cached HTML entries.
     */
    fun clear() {
        cache.clear()
    }

    private fun normalizeKey(url: String): String {
        return try {
            val uri = URI(url.trim())
            val host = (uri.host?.lowercase() ?: "").removePrefix("www.")
            val path = (uri.path ?: "").trimEnd('/')
            "$host$path"
        } catch (e: Exception) {
            url.trim().lowercase().removePrefix("https://").removePrefix("http://").removePrefix("www.").removeSuffix("/")
        }
    }
}
