package com.thedesitadka.app

import android.app.Application
import android.webkit.CookieManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.thedesitadka.core.network.CookieProvider
import com.thedesitadka.core.network.NetworkClient
import com.thedesitadka.core.security.StreamHubLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

class TheDesiTadkaApp : Application(), ImageLoaderFactory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        StreamHubLogger.i("TheDesiTadkaApp", "StreamHub initializing...")

        // Bridge Android WebView CookieManager to NetworkClient for Cloudflare/CAPTCHA clearance cookies
        try {
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            NetworkClient.cookieProvider = object : CookieProvider {
                override fun getCookies(url: String): String? {
                    return try {
                        cookieManager.getCookie(url)
                    } catch (e: Exception) {
                        null
                    }
                }

                override fun setCookies(url: String, cookies: String) {
                    try {
                        if (cookies.contains(";")) {
                            cookies.split(";").forEach { c ->
                                val trimmed = c.trim()
                                if (trimmed.isNotBlank()) {
                                    cookieManager.setCookie(url, trimmed)
                                }
                            }
                        } else {
                            cookieManager.setCookie(url, cookies)
                        }
                        cookieManager.flush()
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            }
        } catch (e: Exception) {
            StreamHubLogger.w("TheDesiTadkaApp", "CookieManager bridge warning: ${e.message}")
        }

        container = AppContainer(this)
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                container.downloadRepository.recoverInterruptedDownloads()
            } catch (e: Exception) {
                StreamHubLogger.w("TheDesiMediaApp", "Failed to recover interrupted downloads: ${e.message}")
            }
        }
        StreamHubLogger.i("TheDesiMediaApp", "TheDesiMedia initialized successfully")
    }

    override fun newImageLoader(): ImageLoader {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val orig = chain.request()
                val host = orig.url.host.lowercase()
                val referer = when {
                    host.contains("pvtcdn.com") || host.contains("masa49") -> "https://www.masa49.nl/"
                    host.contains("kamababa") -> "https://www.kamababa1.com/"
                    host.contains("fry99") -> "https://fry99.cc/"
                    host.contains("masahub") -> "https://masahub2.com/"
                    host.contains("aagmaal") || host.contains("aagimg") -> "https://aagmaal.date/"
                    host.contains("fsiblog") -> "https://www.fsiblogxx.com/"
                    host.contains("webxseries") -> "https://webxseries.hot/"
                    else -> "https://${orig.url.host}/"
                }

                val reqBuilder = orig.newBuilder()
                    .header("User-Agent", NetworkClient.DEFAULT_USER_AGENT)
                    .header("Referer", referer)
                    .header("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")

                // Inject cookies if available for protected CDNs
                try {
                    val cookies = CookieManager.getInstance().getCookie(orig.url.toString())
                    if (!cookies.isNullOrBlank()) {
                        reqBuilder.header("Cookie", cookies)
                    }
                } catch (e: Exception) {
                    // ignore
                }

                chain.proceed(reqBuilder.build())
            }
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(client)
            .crossfade(true)
            .build()
    }
}
