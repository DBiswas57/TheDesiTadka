package com.thedesitadka.core.network

import java.io.IOException

/**
 * Thrown when an HTTP request encounters a Cloudflare Managed Challenge or CAPTCHA
 * (HTTP 403/503 with cf-mitigated header or Turnstile challenge page).
 *
 * This signal indicates that the user must perform an interactive verification
 * in an in-app browser/WebView to obtain clearance cookies (cf_clearance).
 */
class CloudflareChallengeException(
    val url: String,
    val host: String,
    val statusCode: Int = 403,
    override val message: String = "Cloudflare security verification required for $host"
) : IOException(message)
