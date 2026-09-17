package com.thedesitadka.core.network

/**
 * Interface to provide and persist cookies across network requests.
 * Bridged to Android's CookieManager in the application layer.
 */
interface CookieProvider {
    fun getCookies(url: String): String?
    fun setCookies(url: String, cookies: String)
}
