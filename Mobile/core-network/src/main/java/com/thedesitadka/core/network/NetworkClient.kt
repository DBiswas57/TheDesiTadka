package com.thedesitadka.core.network

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

object NetworkClient {

    const val MAX_RESPONSE_BYTES: Long = 5 * 1024 * 1024 // 5 MB ceiling to prevent memory exhaustion

    const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

    /**
     * Pluggable cookie bridge. On Android, this delegates to android.webkit.CookieManager.
     */
    @Volatile
    var cookieProvider: CookieProvider? = null

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

                if (orig.header("User-Agent") == null) {
                    reqBuilder.header("User-Agent", DEFAULT_USER_AGENT)
                }
                if (orig.header("Accept") == null) {
                    reqBuilder.header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                }
                if (orig.header("Accept-Language") == null) {
                    reqBuilder.header("Accept-Language", "en-US,en;q=0.9")
                }
                if (orig.header("Sec-CH-UA") == null) {
                    reqBuilder.header("Sec-CH-UA", "\"Chromium\";v=\"128\", \"Not;A=Brand\";v=\"24\", \"Google Chrome\";v=\"128\"")
                }
                if (orig.header("Sec-CH-UA-Mobile") == null) {
                    reqBuilder.header("Sec-CH-UA-Mobile", "?1")
                }
                if (orig.header("Sec-CH-UA-Platform") == null) {
                    reqBuilder.header("Sec-CH-UA-Platform", "\"Android\"")
                }
                if (orig.header("Sec-Fetch-Dest") == null) {
                    reqBuilder.header("Sec-Fetch-Dest", "document")
                }
                if (orig.header("Sec-Fetch-Mode") == null) {
                    reqBuilder.header("Sec-Fetch-Mode", "navigate")
                }
                if (orig.header("Sec-Fetch-Site") == null) {
                    reqBuilder.header("Sec-Fetch-Site", "none")
                }
                if (orig.header("Sec-Fetch-User") == null) {
                    reqBuilder.header("Sec-Fetch-User", "?1")
                }
                if (orig.header("Upgrade-Insecure-Requests") == null) {
                    reqBuilder.header("Upgrade-Insecure-Requests", "1")
                }

                // Inject cookies from CookieProvider if not already explicitly specified
                if (orig.header("Cookie") == null) {
                    val cookies = cookieProvider?.getCookies(urlString)
                    if (!cookies.isNullOrBlank()) {
                        reqBuilder.header("Cookie", cookies)
                    }
                }

                val response = chain.proceed(reqBuilder.build())

                // Capture any Set-Cookie headers from server and store in CookieProvider
                cookieProvider?.let { provider ->
                    val setCookies = response.headers("Set-Cookie")
                    for (cookie in setCookies) {
                        provider.setCookies(urlString, cookie)
                    }
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
            return body.string()
        }
    }
}
