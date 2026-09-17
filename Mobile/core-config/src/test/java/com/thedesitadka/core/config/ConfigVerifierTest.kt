package com.thedesitadka.core.config

import com.thedesitadka.core.model.ProviderCapability
import com.thedesitadka.core.model.ProviderConfig
import com.thedesitadka.core.model.ProviderManifest
import com.thedesitadka.core.model.SelectorConfig
import com.thedesitadka.core.model.SignedPayload
import kotlinx.serialization.json.Json
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64

class ConfigVerifierTest {

    private lateinit var pubKeyBase64: String
    private lateinit var privKey: Ed25519PrivateKeyParameters
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setup() {
        val random = SecureRandom()
        val keyPairGen = Ed25519KeyPairGenerator()
        keyPairGen.init(Ed25519KeyGenerationParameters(random))
        val keyPair = keyPairGen.generateKeyPair()

        privKey = keyPair.private as Ed25519PrivateKeyParameters
        val pubKey = keyPair.public as Ed25519PublicKeyParameters
        pubKeyBase64 = Base64.getEncoder().encodeToString(pubKey.encoded)
    }

    private fun signPayload(payload: String): String {
        val signer = Ed25519Signer()
        signer.init(true, privKey)
        val bytes = payload.toByteArray(StandardCharsets.UTF_8)
        signer.update(bytes, 0, bytes.size)
        val sig = signer.generateSignature()
        val sigBase64 = Base64.getEncoder().encodeToString(sig)

        val envelope = SignedPayload(payload = payload, signature = sigBase64)
        return json.encodeToString(SignedPayload.serializer(), envelope)
    }

    @Test
    fun testVerifyValidSignedManifest() {
        val manifest = ProviderManifest(
            schemaVersion = 1,
            configVersion = 10,
            providers = listOf(
                ProviderConfig(
                    id = "example_provider",
                    name = "Example Provider",
                    baseUrl = "https://example.com",
                    adapter = "html_selector",
                    capabilities = listOf(ProviderCapability.HOME, ProviderCapability.STREAM)
                )
            )
        )
        val manifestJson = json.encodeToString(ProviderManifest.serializer(), manifest)
        val signedEnvelopeJson = signPayload(manifestJson)

        val result = ConfigVerifier.verifySignedPayload(
            signedJson = signedEnvelopeJson,
            currentVersion = 5,
            publicKeyBase64 = pubKeyBase64
        )

        assertTrue("Valid manifest should succeed", result.isSuccess)
        assertNotNull(result.manifest)
        assertEquals(10, result.manifest!!.configVersion)
        assertEquals("example_provider", result.manifest!!.providers[0].id)
    }

    @Test
    fun testRejectInvalidSignature() {
        val manifest = ProviderManifest(schemaVersion = 1, configVersion = 10)
        val manifestJson = json.encodeToString(ProviderManifest.serializer(), manifest)
        // Sign payload with one string, but envelope has another
        val envelope = SignedPayload(
            payload = manifestJson,
            signature = Base64.getEncoder().encodeToString(ByteArray(64)) // bogus signature
        )
        val signedJson = json.encodeToString(SignedPayload.serializer(), envelope)

        val result = ConfigVerifier.verifySignedPayload(
            signedJson = signedJson,
            currentVersion = 5,
            publicKeyBase64 = pubKeyBase64
        )

        assertFalse("Invalid signature must fail", result.isSuccess)
    }

    @Test
    fun testRejectReplayOldVersion() {
        val manifest = ProviderManifest(schemaVersion = 1, configVersion = 4)
        val manifestJson = json.encodeToString(ProviderManifest.serializer(), manifest)
        val signedJson = signPayload(manifestJson)

        val result = ConfigVerifier.verifySignedPayload(
            signedJson = signedJson,
            currentVersion = 10, // Current version is 10, incoming is 4
            publicKeyBase64 = pubKeyBase64
        )

        assertFalse("Old version replay must be rejected", result.isSuccess)
    }

    @Test
    fun testRejectExpiredConfig() {
        val manifest = ProviderManifest(
            schemaVersion = 1,
            configVersion = 15,
            expiresAt = System.currentTimeMillis() - 10000 // In the past
        )
        val manifestJson = json.encodeToString(ProviderManifest.serializer(), manifest)
        val signedJson = signPayload(manifestJson)

        val result = ConfigVerifier.verifySignedPayload(
            signedJson = signedJson,
            currentVersion = 10,
            publicKeyBase64 = pubKeyBase64
        )

        assertFalse("Expired config must be rejected", result.isSuccess)
    }

    @Test
    fun testRejectMaliciousSelector() {
        val manifest = ProviderManifest(
            schemaVersion = 1,
            configVersion = 12,
            providers = listOf(
                ProviderConfig(
                    id = "bad_provider",
                    name = "Bad Provider",
                    baseUrl = "https://example.com",
                    adapter = "html_selector",
                    selectors = SelectorConfig(
                        item = "div.item <script>alert(1)</script>"
                    )
                )
            )
        )
        val manifestJson = json.encodeToString(ProviderManifest.serializer(), manifest)
        val signedJson = signPayload(manifestJson)

        val result = ConfigVerifier.verifySignedPayload(
            signedJson = signedJson,
            currentVersion = 10,
            publicKeyBase64 = pubKeyBase64
        )

        assertFalse("Malicious selector must be rejected", result.isSuccess)
    }

    @Test
    fun testConfigCacheRollback() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "streamhub_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        try {
            val cache = ConfigCache(tempDir)

            val config1 = ProviderManifest(schemaVersion = 1, configVersion = 1)
            cache.saveValidatedConfig(config1, markAsLastKnownGood = true)

            val config2 = ProviderManifest(schemaVersion = 1, configVersion = 2)
            cache.saveValidatedConfig(config2, markAsLastKnownGood = false)

            assertEquals(2, cache.loadCurrent()?.configVersion)

            // Perform rollback
            val rolledBack = cache.rollback()
            assertNotNull(rolledBack)
            assertEquals(1, rolledBack!!.configVersion)
            assertEquals(1, cache.loadCurrent()?.configVersion)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
