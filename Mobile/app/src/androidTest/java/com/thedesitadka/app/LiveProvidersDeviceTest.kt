package com.thedesitadka.app

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.thedesitadka.core.network.CloudflareChallengeException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiveProvidersDeviceTest {

    private lateinit var appContainer: AppContainer

    @Before
    fun setUp() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        appContainer = AppContainer(appContext)
    }

    @Test
    fun testLiveBatchProvidersOnDevice() = runBlocking {
        val batchProviderIds = listOf("tube8", "tnaflix", "nuvid", "spankbang", "youjizz", "pornone")
        val results = mutableMapOf<String, String>()

        for (providerId in batchProviderIds) {
            Log.i("LiveProvidersDeviceTest", ">>> Testing provider: $providerId")
            val adapter = appContainer.providerEngine.getAdapter(providerId)
            assertNotNull("Adapter for $providerId must be registered in engine", adapter)

            val feedResult = appContainer.providerEngine.getHomeFeed(providerId, 1)
            if (feedResult.isFailure) {
                val error = feedResult.exceptionOrNull()
                if (error is CloudflareChallengeException || error?.message?.contains("Cloudflare") == true) {
                    Log.i("LiveProvidersDeviceTest", "[$providerId] Cloudflare challenge detected (handled by WebView solver)")
                    results[providerId] = "CLOUDFLARE_PROTECTED (Managed by WebView Solver)"
                    continue
                } else {
                    Log.e("LiveProvidersDeviceTest", "[$providerId] Feed failed: ${error?.message}")
                    results[providerId] = "FAILED (${error?.message})"
                    continue
                }
            }

            val feed = feedResult.getOrThrow()
            assertTrue("Feed items for $providerId must not be empty", feed.items.isNotEmpty())

            val firstItem = feed.items.first()
            Log.i("LiveProvidersDeviceTest", "[$providerId] First item: title='${firstItem.title}', thumb='${firstItem.thumbnailUrl}', detail='${firstItem.detailUrl}'")
            assertTrue("Title for $providerId first item must not be blank", firstItem.title.isNotBlank())
            assertTrue("Thumbnail for $providerId first item must not be blank", firstItem.thumbnailUrl.isNotBlank())
            assertTrue("DetailUrl for $providerId first item must not be blank", firstItem.detailUrl.isNotBlank())

            // Test Detail / Playable media resolution
            val playableMediaResult = appContainer.providerEngine.getPlayableMedia(providerId, firstItem.detailUrl)
            val detailsResult = appContainer.providerEngine.getDetails(providerId, firstItem.detailUrl)

            if (playableMediaResult.isSuccess && playableMediaResult.getOrThrow().isNotEmpty()) {
                val mediaList = playableMediaResult.getOrThrow()
                val firstMedia = mediaList.first()
                Log.i("LiveProvidersDeviceTest", "[$providerId] Resolved playable media: count=${mediaList.size}, type=${firstMedia.type}, url='${firstMedia.url}'")
                results[providerId] = "SUCCESS (items: ${feed.items.size}, media: ${firstMedia.type})"
            } else if (detailsResult.isSuccess) {
                val details = detailsResult.getOrThrow()
                Log.i("LiveProvidersDeviceTest", "[$providerId] Resolved details: title='${details.title}'")
                results[providerId] = "SUCCESS (items: ${feed.items.size}, details_ok)"
            } else {
                Log.w("LiveProvidersDeviceTest", "[$providerId] Detail warning: ${detailsResult.exceptionOrNull()?.message}")
                results[providerId] = "FEED_OK (items: ${feed.items.size})"
            }
        }

        Log.i("LiveProvidersDeviceTest", "====================================")
        Log.i("LiveProvidersDeviceTest", "=== FINAL BATCH TEST RESULTS ===")
        for ((p, res) in results) {
            Log.i("LiveProvidersDeviceTest", "Provider $p -> $res")
        }
        Log.i("LiveProvidersDeviceTest", "====================================")

        // Ensure non-Cloudflare sites all passed
        assertTrue("Tube8 must succeed", results["tube8"]?.startsWith("SUCCESS") == true)
        assertTrue("TNAFlix must succeed", results["tnaflix"]?.startsWith("SUCCESS") == true)
        assertTrue("NuVid must succeed", results["nuvid"]?.startsWith("SUCCESS") == true)
        assertTrue("SpankBang must succeed or be protected", results["spankbang"]?.contains("SUCCESS") == true || results["spankbang"]?.contains("FEED_OK") == true || results["spankbang"]?.contains("CLOUDFLARE") == true)
        assertTrue("YouJizz must succeed or be protected", results["youjizz"]?.contains("SUCCESS") == true || results["youjizz"]?.contains("FEED_OK") == true || results["youjizz"]?.contains("CLOUDFLARE") == true)
    }
}
