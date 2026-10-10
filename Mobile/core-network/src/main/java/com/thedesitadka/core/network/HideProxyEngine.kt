package com.thedesitadka.core.network

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Port of the nl.hideproxy.me webproxy engine from HttpHelper.vb.
 * Enables fetching website sources and media endpoints through an isolated webproxy gateway
 * when direct access is affected by ISP DNS sinkholes, TLS handshake resets, or regional filtering.
 */
object HideProxyEngine {

    const val PROXY_HOST = "nl.hideproxy.me"
    const val PROXY_HOME_URL = "https://nl.hideproxy.me/index.php"
    const val PROXY_PROCESS_ENDPOINT = "https://nl.hideproxy.me/includes/process.php?action=update"
    const val PROXY_REFERER = "https://nl.hideproxy.me/"

    private val proxyHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /**
     * Builds the direct navigable GET URL for nl.hideproxy.me.
     * Equivalent to BuildHideProxyInitialUrl in HttpHelper.vb.
     */
    fun buildProxiedUrl(targetUrl: String): String {
        val encoded = URLEncoder.encode(targetUrl, StandardCharsets.UTF_8.name())
        return "https://$PROXY_HOST/go.php?u=$encoded&b=0&f=norefer"
    }

    /**
     * Unwraps a proxied URL back to its original destination URL.
     * Extracts the 'u' query parameter from https://nl.hideproxy.me/go.php?u=...
     */
    fun unwrapProxiedUrl(proxiedUrl: String): String {
        if (!isProxiedUrl(proxiedUrl)) return proxiedUrl
        return try {
            val uri = java.net.URI(proxiedUrl)
            val query = uri.rawQuery ?: return proxiedUrl
            val params = query.split("&")
            for (p in params) {
                val parts = p.split("=", limit = 2)
                if (parts.size == 2 && parts[0] == "u") {
                    return URLDecoder.decode(parts[1], StandardCharsets.UTF_8.name())
                }
            }
            proxiedUrl
        } catch (_: Exception) {
            proxiedUrl
        }
    }

    /**
     * Checks if a given URL is routed through nl.hideproxy.me.
     */
    fun isProxiedUrl(url: String): Boolean {
        return url.contains("nl.hideproxy.me/go.php", ignoreCase = true) ||
                url.startsWith("/go.php?", ignoreCase = true)
    }

    /**
     * Converts relative /go.php? links to absolute https://nl.hideproxy.me/go.php? links.
     * Port of EnsureAbsoluteHideProxyLinks from HttpHelper.vb.
     */
    fun ensureAbsoluteProxyLinks(html: String): String {
        if (html.isBlank()) return html

        var result = html

        // 1. Quoted attributes: (src|href|action|poster|data-src|data-href)="/go.php?..."
        val quotedPattern = Pattern.compile(
            """\b(src|href|action|poster|data-src|data-href)\s*=\s*(["'])/go\.php\?([^"']+)\2""",
            Pattern.CASE_INSENSITIVE
        )
        result = quotedPattern.matcher(result).replaceAll("$1=$2https://$PROXY_HOST/go.php?$3$2")

        // 2. Unquoted attributes
        val unquotedPattern = Pattern.compile(
            """\b(src|href|action|poster|data-src|data-href)\s*=\s*/go\.php\?([^\s>]+)""",
            Pattern.CASE_INSENSITIVE
        )
        result = unquotedPattern.matcher(result).replaceAll("$1=https://$PROXY_HOST/go.php?$2")

        // 3. CSS url(/go.php?...)
        val cssPattern = Pattern.compile(
            """url\((["']?)/go\.php\?([^)'"]+)\1\)""",
            Pattern.CASE_INSENSITIVE
        )
        result = cssPattern.matcher(result).replaceAll("url($1https://$PROXY_HOST/go.php?$2$1)")

        return result
    }

    /**
     * Fetches website content via nl.hideproxy.me webproxy.
     * First attempts direct GET on /go.php?u=... and falls back to POST process.php.
     */
    @Throws(IOException::class)
    fun fetchSourceViaProxy(targetUrl: String, timeoutSeconds: Long = 25): String {
        val proxiedUrl = buildProxiedUrl(targetUrl)

        val getRequest = Request.Builder()
            .url(proxiedUrl)
            .header("User-Agent", NetworkClient.DEFAULT_USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Referer", PROXY_REFERER)
            .build()

        try {
            proxyHttpClient.newCall(getRequest).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    if (body.isNotBlank() && !body.contains("Error: Form not found", ignoreCase = true)) {
                        return ensureAbsoluteProxyLinks(body)
                    }
                }
            }
        } catch (_: Exception) {
            // Fall through to form POST fallback
        }

        // Fallback: POST to process.php
        val formBody = FormBody.Builder()
            .add("u", targetUrl)
            .add("proxy_form[server]", "nl")
            .add("allowCookies", "on")
            .build()

        val postRequest = Request.Builder()
            .url(PROXY_PROCESS_ENDPOINT)
            .post(formBody)
            .header("User-Agent", NetworkClient.DEFAULT_USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Referer", PROXY_HOME_URL)
            .build()

        proxyHttpClient.newCall(postRequest).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HideProxy returned HTTP ${response.code} for target $targetUrl")
            }
            val body = response.body?.string() ?: throw IOException("Empty response from HideProxy")
            return ensureAbsoluteProxyLinks(body)
        }
    }
}
