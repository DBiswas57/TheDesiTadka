package com.thedesitadka.core.network

import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

object NetworkClient {

    const val MAX_RESPONSE_BYTES: Long = 5 * 1024 * 1024 // 5 MB ceiling to prevent memory exhaustion

    const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
    const val DESKTOP_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

    /**
     * Pluggable cookie bridge. On Android, this delegates to android.webkit.CookieManager.
     */
    @Volatile
    var cookieProvider: CookieProvider? = null

    private val inMemoryCookies = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.ConcurrentHashMap<String, String>>()

    fun setSessionCookie(host: String, name: String, value: String) {
        val cleanHost = host.lowercase().removePrefix("www.").substringBefore(":")
        inMemoryCookies.computeIfAbsent(cleanHost) { java.util.concurrent.ConcurrentHashMap() }[name] = value
    }

    fun getSessionCookies(host: String): Map<String, String> {
        val cleanHost = host.lowercase().removePrefix("www.").substringBefore(":")
        val result = mutableMapOf<String, String>()
        inMemoryCookies[cleanHost]?.let { result.putAll(it) }
        return result
    }

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val orig = chain.request()
                val reqBuilder = orig.newBuilder()
                val urlString = orig.url.toString()
                val cleanHost = orig.url.host.lowercase().removePrefix("www.")
                val isDesktopHost = cleanHost.contains("hqporner") || cleanHost.contains("sxyprn.com")

                val isMediaRequest = orig.header("Range") != null ||
                        urlString.contains(".mp4") || urlString.contains(".m3u8") ||
                        urlString.contains("/hls/") || urlString.contains("get_file") ||
                        urlString.contains(".mpd") || orig.header("Sec-Fetch-Dest") == "video"

                if (orig.header("User-Agent") == null) {
                    val ua = if (isDesktopHost) DESKTOP_USER_AGENT else DEFAULT_USER_AGENT
                    reqBuilder.header("User-Agent", ua)
                }
                if (orig.header("Accept") == null) {
                    if (isMediaRequest) {
                        reqBuilder.header("Accept", "*/*")
                    } else {
                        reqBuilder.header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                    }
                }
                if (orig.header("Accept-Language") == null) {
                    reqBuilder.header("Accept-Language", "en-US,en;q=0.9")
                }
                if (orig.header("Sec-CH-UA") == null) {
                    reqBuilder.header("Sec-CH-UA", "\"Chromium\";v=\"128\", \"Not;A=Brand\";v=\"24\", \"Google Chrome\";v=\"128\"")
                }
                if (orig.header("Sec-CH-UA-Mobile") == null) {
                    reqBuilder.header("Sec-CH-UA-Mobile", if (isDesktopHost) "?0" else "?1")
                }
                if (orig.header("Sec-CH-UA-Platform") == null) {
                    reqBuilder.header("Sec-CH-UA-Platform", if (isDesktopHost) "\"Windows\"" else "\"Android\"")
                }
                if (orig.header("Sec-Fetch-Dest") == null) {
                    reqBuilder.header("Sec-Fetch-Dest", if (isMediaRequest) "video" else "document")
                }
                if (orig.header("Sec-Fetch-Mode") == null) {
                    reqBuilder.header("Sec-Fetch-Mode", if (isMediaRequest) "no-cors" else "navigate")
                }
                if (orig.header("Sec-Fetch-Site") == null) {
                    reqBuilder.header("Sec-Fetch-Site", if (isMediaRequest) "same-origin" else "none")
                }
                if (!isMediaRequest) {
                    if (orig.header("Sec-Fetch-User") == null) {
                        reqBuilder.header("Sec-Fetch-User", "?1")
                    }
                    if (orig.header("Upgrade-Insecure-Requests") == null) {
                        reqBuilder.header("Upgrade-Insecure-Requests", "1")
                    }
                }

                // Inject cookies: combine inMemoryCookies, CookieProvider cookies, and explicit header
                val combinedCookies = mutableMapOf<String, String>()
                inMemoryCookies[cleanHost]?.let { combinedCookies.putAll(it) }

                val providerCookies = cookieProvider?.getCookies(urlString)
                if (!providerCookies.isNullOrBlank()) {
                    providerCookies.split(";").forEach { c ->
                        val parts = c.trim().split("=", limit = 2)
                        if (parts.size == 2) combinedCookies[parts[0].trim()] = parts[1].trim()
                    }
                }

                orig.header("Cookie")?.let { existing ->
                    existing.split(";").forEach { c ->
                        val parts = c.trim().split("=", limit = 2)
                        if (parts.size == 2) combinedCookies[parts[0].trim()] = parts[1].trim()
                    }
                }

                if (cleanHost.contains("sxyprn.com") && !combinedCookies.containsKey("aavvcc")) {
                    combinedCookies["aavvcc"] = "1"
                }

                if (combinedCookies.isNotEmpty()) {
                    val cookieStr = combinedCookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
                    reqBuilder.header("Cookie", cookieStr)
                }

                val response = chain.proceed(reqBuilder.build())

                // Capture any Set-Cookie headers from server and store in both inMemoryCookies and CookieProvider
                val setCookies = response.headers("Set-Cookie")
                for (sc in setCookies) {
                    val cookiePair = sc.substringBefore(";")
                    val parts = cookiePair.split("=", limit = 2)
                    if (parts.size == 2) {
                        setSessionCookie(orig.url.host, parts[0].trim(), parts[1].trim())
                    }
                    cookieProvider?.setCookies(urlString, sc)
                }

                response
            }
            .addInterceptor(SecurityInterceptor())
            .addInterceptor(SafeLoggingInterceptor())
            .build()
    }

    /**
     * Executes an HTTP request with bounded response size and returns the response body as a string.
     * Throws [CloudflareChallengeException] if Cloudflare Turnstile/Managed challenge is detected.
     * Throws an [IOException] if response size exceeds [MAX_RESPONSE_BYTES] or network fails.
     */
    @Throws(IOException::class)
    fun fetchString(url: String, headers: Map<String, String> = emptyMap()): String {
        return fetchStringInternal(url, headers, retryAntibot = true)
    }

    private fun fetchStringInternal(url: String, headers: Map<String, String>, retryAntibot: Boolean): String {
        // First check if freshly solved Cloudflare HTML is available for this URL
        val cachedHtml = CloudflareHtmlCache.get(url)
        if (!cachedHtml.isNullOrBlank()) {
            return cachedHtml
        }

        val requestBuilder = Request.Builder().url(url)
        headers.forEach { (k, v) -> requestBuilder.header(k, v) }
        val request = requestBuilder.build()

        okHttpClient.newCall(request).execute().use { response ->
            val code = response.code
            val isChallengeCode = code == 403 || code == 503
            val cfMitigated = response.header("cf-mitigated")
            val serverHeader = response.header("server") ?: ""

            if (!response.isSuccessful) {
                val errorBody = try {
                    response.body?.string() ?: ""
                } catch (e: Exception) {
                    ""
                }

                if (retryAntibot && isAntibotChallenge(errorBody)) {
                    val token = extractAntibotToken(errorBody)
                    if (!token.isNullOrBlank()) {
                        val host = request.url.host
                        setSessionCookie(host, "antibot", token)
                        cookieProvider?.setCookies(url, "antibot=$token; path=/")
                        try {
                            cookieProvider?.setCookies("https://$host/", "antibot=$token; path=/")
                        } catch (_: Exception) {}

                        val newHeaders = headers.toMutableMap()
                        val existing = newHeaders["Cookie"]
                        newHeaders["Cookie"] = if (existing.isNullOrBlank()) "antibot=$token" else "$existing; antibot=$token"
                        return fetchStringInternal(url, newHeaders, retryAntibot = false)
                    }
                }

                val isCloudflareChallenge = isChallengeCode && (
                    cfMitigated?.contains("challenge", ignoreCase = true) == true ||
                    serverHeader.contains("cloudflare", ignoreCase = true) ||
                    errorBody.contains("challenges.cloudflare.com", ignoreCase = true) ||
                    errorBody.contains("cf-turnstile", ignoreCase = true) ||
                    errorBody.contains("cf-challenge", ignoreCase = true) ||
                    errorBody.contains("Just a moment...", ignoreCase = true) ||
                    errorBody.contains("Attention Required! | Cloudflare", ignoreCase = true)
                )

                if (isCloudflareChallenge) {
                    throw CloudflareChallengeException(
                        url = url,
                        host = request.url.host,
                        statusCode = code,
                        message = "Cloudflare security challenge detected on ${request.url.host} (HTTP $code)"
                    )
                }

                throw IOException("Unexpected HTTP response code: $code for URL: $url")
            }

            val body = response.body ?: throw IOException("Empty response body for URL: $url")
            val source = body.source()
            source.request(MAX_RESPONSE_BYTES + 1)
            val buffer = source.buffer
            if (buffer.size > MAX_RESPONSE_BYTES) {
                throw IOException("Response exceeded maximum safe ceiling of $MAX_RESPONSE_BYTES bytes")
            }
            val responseString = body.string()

            // Check if response is an antibot challenge returning HTTP 200 OK
            if (retryAntibot && isAntibotChallenge(responseString)) {
                val token = extractAntibotToken(responseString)
                if (!token.isNullOrBlank()) {
                    val host = request.url.host
                    setSessionCookie(host, "antibot", token)
                    cookieProvider?.setCookies(url, "antibot=$token; path=/")
                    try {
                        cookieProvider?.setCookies("https://$host/", "antibot=$token; path=/")
                    } catch (_: Exception) {}

                    val newHeaders = headers.toMutableMap()
                    val existing = newHeaders["Cookie"]
                    newHeaders["Cookie"] = if (existing.isNullOrBlank()) "antibot=$token" else "$existing; antibot=$token"
                    return fetchStringInternal(url, newHeaders, retryAntibot = false)
                }
            }

            val isCloudflareChallengeHtml = responseString.contains("<title>Just a moment...</title>", ignoreCase = true) ||
                (responseString.contains("Just a moment...", ignoreCase = true) && (responseString.contains("cloudflare", ignoreCase = true) || responseString.contains("turnstile", ignoreCase = true))) ||
                responseString.contains("Attention Required! | Cloudflare", ignoreCase = true) ||
                (responseString.contains("cf-browser-verification", ignoreCase = true) && responseString.contains("cloudflare", ignoreCase = true)) ||
                responseString.contains("id=\"challenge-stage\"", ignoreCase = true)

            if (isCloudflareChallengeHtml) {
                throw CloudflareChallengeException(
                    url = url,
                    host = request.url.host,
                    statusCode = code,
                    message = "Cloudflare security challenge detected on ${request.url.host} (HTTP $code)"
                )
            }

            return responseString
        }
    }

    /**
     * Checks if HTML represents an inline PHP antibot challenge page.
     */
    fun isAntibotChallenge(body: String): Boolean {
        if (!body.contains("antibot", ignoreCase = true)) return false
        return body.contains("Checking your browser", ignoreCase = true) ||
                body.contains("Just a moment...", ignoreCase = true) ||
                body.contains("Please turn JavaScript on", ignoreCase = true) ||
                body.contains("cf-im-under-attack", ignoreCase = true)
    }

    /**
     * Extracts antibot verification token from challenge HTML.
     */
    fun extractAntibotToken(html: String): String? {
        // 1. Direct window.atob check with antibot
        val atobRegex = Regex("""antibot\s*==\s*window\.atob\(["']([^"']+)["']\)""")
        val atobMatch = atobRegex.find(html)
        if (atobMatch != null) {
            val b64 = atobMatch.groupValues[1]
            val decoded = decodeBase64Safe(b64)
            if (decoded.isNotBlank()) return decoded
        }

        // 2. Hidden input inside HTML
        val inputRegex = Regex("""name=["']antibot["']\s+type=["']hidden["']\s+value=["']([^"']+)["']|value=["']([^"']+)["']\s+type=["']hidden["']\s+name=["']antibot["']""")
        val inputMatch = inputRegex.find(html)
        if (inputMatch != null) {
            val v = inputMatch.groupValues[1].ifEmpty { inputMatch.groupValues[2] }.trim()
            if (v.isNotBlank()) return v
        }

        // 3. Scan all window.atob literals in the HTML
        val anyAtobRegex = Regex("""window\.atob\(["']([^"']+)["']\)""")
        for (match in anyAtobRegex.findAll(html)) {
            val b64 = match.groupValues[1]
            val decoded = decodeBase64Safe(b64)
            if (decoded.matches(Regex("""^[a-f0-9]{32}$"""))) {
                return decoded
            }
            val innerMatch = inputRegex.find(decoded)
            if (innerMatch != null) {
                val v = innerMatch.groupValues[1].ifEmpty { innerMatch.groupValues[2] }.trim()
                if (v.isNotBlank()) return v
            }
        }

        return null
    }

    private fun decodeBase64Safe(input: String): String {
        return try {
            String(java.util.Base64.getDecoder().decode(input.trim()), Charsets.UTF_8).trim()
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Executes an HTTP POST request sending JSON payload and returns the response body as a string.
     */
    @Throws(IOException::class)
    fun postJson(url: String, jsonBody: String, headers: Map<String, String> = emptyMap()): String {
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = jsonBody.toRequestBody(mediaType)
        val requestBuilder = Request.Builder().url(url).post(body)
        headers.forEach { (k, v) -> requestBuilder.header(k, v) }
        val request = requestBuilder.build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Unexpected HTTP POST response code: ${response.code} for URL: $url")
            }
            val respBody = response.body ?: throw IOException("Empty response body for POST URL: $url")
            return respBody.string()
        }
    }

    /**
     * Executes an HTTP POST request sending form URL-encoded payload and returns the response body as a string.
     */
    @Throws(IOException::class)
    fun postForm(url: String, formParams: Map<String, String>, headers: Map<String, String> = emptyMap()): String {
        val formBuilder = FormBody.Builder()
        formParams.forEach { (k, v) -> formBuilder.add(k, v) }
        val body = formBuilder.build()
        val requestBuilder = Request.Builder().url(url).post(body)
        headers.forEach { (k, v) -> requestBuilder.header(k, v) }
        val request = requestBuilder.build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Unexpected HTTP POST response code: ${response.code} for URL: $url")
            }
            val respBody = response.body ?: throw IOException("Empty response body for POST URL: $url")
            return respBody.string()
        }
    }
}
