package com.thedesitadka.core.network

import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class SecurityInterceptorTest {

    @Test
    fun testBlockPrivateIpTarget() {
        val client = OkHttpClient.Builder()
            .addInterceptor(SecurityInterceptor())
            .build()

        val request = Request.Builder()
            .url("https://127.0.0.1/admin")
            .build()

        try {
            client.newCall(request).execute()
            fail("Expected SecurityInterceptor to block request to 127.0.0.1")
        } catch (e: IOException) {
            // Expected
        }
    }

    @Test
    fun testBlockInsecureHttpTarget() {
        val client = OkHttpClient.Builder()
            .addInterceptor(SecurityInterceptor())
            .build()

        val request = Request.Builder()
            .url("http://example.com/insecure")
            .build()

        try {
            client.newCall(request).execute()
            fail("Expected SecurityInterceptor to block request with plain HTTP")
        } catch (e: IOException) {
            // Expected
        }
    }
}
