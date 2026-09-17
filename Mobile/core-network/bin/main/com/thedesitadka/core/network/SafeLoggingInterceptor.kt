package com.thedesitadka.core.network

import com.thedesitadka.core.security.StreamHubLogger
import okhttp3.Interceptor
import okhttp3.Response

class SafeLoggingInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val sanitizedUrl = StreamHubLogger.redact(request.url.toString())
        StreamHubLogger.d("Network", "--> ${request.method} $sanitizedUrl")

        val startNs = System.nanoTime()
        val response: Response
        try {
            response = chain.proceed(request)
        } catch (e: Exception) {
            StreamHubLogger.w("Network", "<-- HTTP FAILED for $sanitizedUrl: ${e.message}")
            throw e
        }

        val tookMs = (System.nanoTime() - startNs) / 1e6
        StreamHubLogger.d("Network", "<-- ${response.code} ${response.message} ($tookMs ms) for $sanitizedUrl")
        return response
    }
}
