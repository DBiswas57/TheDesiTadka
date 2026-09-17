package com.thedesitadka.core.network

import com.thedesitadka.core.security.StreamHubLogger
import com.thedesitadka.core.security.UrlSecurityValidator
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class SecurityInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val urlString = request.url.toString()

        if (!UrlSecurityValidator.isUrlSafe(urlString)) {
            StreamHubLogger.e("SecurityInterceptor", "Request blocked by SSRF/Security guardrail: $urlString")
            throw IOException("Security guardrail blocked request to unsafe or disallowed destination: $urlString")
        }

        val response = chain.proceed(request)

        // Validate redirect targets if response is a redirect
        if (response.isRedirect) {
            val redirectLocation = response.header("Location")
            if (redirectLocation != null) {
                val resolvedUrl = request.url.resolve(redirectLocation)?.toString() ?: redirectLocation
                if (!UrlSecurityValidator.isUrlSafe(resolvedUrl)) {
                    StreamHubLogger.e("SecurityInterceptor", "Redirect blocked by SSRF/Security guardrail: $resolvedUrl")
                    throw IOException("Security guardrail blocked redirect to unsafe or disallowed destination: $resolvedUrl")
                }
            }
        }

        return response
    }
}
