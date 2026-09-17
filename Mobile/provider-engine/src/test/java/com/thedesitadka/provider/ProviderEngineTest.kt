package com.thedesitadka.provider

import com.thedesitadka.core.model.ProviderCapability
import com.thedesitadka.core.model.ProviderConfig
import com.thedesitadka.core.model.ProviderManifest
import com.thedesitadka.core.model.ProviderStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ProviderEngineTest {

    @Test
    fun testUpdateFromManifestDynamicRegistration() {
        val engine = ProviderEngine()

        val manifest1 = ProviderManifest(
            schemaVersion = 1,
            configVersion = 1,
            providers = listOf(
                ProviderConfig(
                    id = "provider_a",
                    name = "Provider A",
                    baseUrl = "https://a.com",
                    adapter = "html_selector",
                    capabilities = listOf(ProviderCapability.HOME)
                )
            )
        )

        engine.updateFromManifest(manifest1)
        assertNotNull(engine.getAdapter("provider_a"))
        assertEquals(1, engine.getActiveProviders().size)

        // Remote server adds Provider B without APK rebuild
        val manifest2 = ProviderManifest(
            schemaVersion = 1,
            configVersion = 2,
            providers = listOf(
                ProviderConfig(
                    id = "provider_a",
                    name = "Provider A",
                    baseUrl = "https://a.com",
                    adapter = "html_selector",
                    capabilities = listOf(ProviderCapability.HOME)
                ),
                ProviderConfig(
                    id = "provider_b",
                    name = "Provider B",
                    baseUrl = "https://b.com",
                    adapter = "rss",
                    capabilities = listOf(ProviderCapability.HOME, ProviderCapability.STREAM)
                )
            )
        )

        engine.updateFromManifest(manifest2)
        assertNotNull(engine.getAdapter("provider_a"))
        assertNotNull(engine.getAdapter("provider_b"))
        assertEquals(2, engine.getActiveProviders().size)

        // Remote server retires Provider A
        val manifest3 = ProviderManifest(
            schemaVersion = 1,
            configVersion = 3,
            providers = listOf(
                ProviderConfig(
                    id = "provider_b",
                    name = "Provider B",
                    baseUrl = "https://b.com",
                    adapter = "rss"
                )
            )
        )

        engine.updateFromManifest(manifest3)
        assertNull("Provider A must be unloaded", engine.getAdapter("provider_a"))
        assertNotNull(engine.getAdapter("provider_b"))
        assertEquals(1, engine.getActiveProviders().size)
    }

    @Test
    fun testHealthMonitorStateTransitions() {
        val monitor = ProviderHealthMonitor()
        assertEquals(ProviderStatus.ENABLED, monitor.getStatus("prov_1"))

        // 2 errors -> DEGRADED
        monitor.recordError("prov_1")
        monitor.recordError("prov_1")
        assertEquals(ProviderStatus.DEGRADED, monitor.getStatus("prov_1"))

        // 5 errors -> UNAVAILABLE
        monitor.recordError("prov_1")
        monitor.recordError("prov_1")
        monitor.recordError("prov_1")
        assertEquals(ProviderStatus.UNAVAILABLE, monitor.getStatus("prov_1"))

        // Success -> recovered to ENABLED
        monitor.recordSuccess("prov_1")
        assertEquals(ProviderStatus.ENABLED, monitor.getStatus("prov_1"))
    }
}
