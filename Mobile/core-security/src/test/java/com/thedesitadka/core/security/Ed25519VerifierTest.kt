package com.thedesitadka.core.security

import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64

class Ed25519VerifierTest {

    @Test
    fun testValidSignatureVerification() {
        val random = SecureRandom()
        val keyPairGen = Ed25519KeyPairGenerator()
        keyPairGen.init(Ed25519KeyGenerationParameters(random))
        val keyPair = keyPairGen.generateKeyPair()

        val privKey = keyPair.private as Ed25519PrivateKeyParameters
        val pubKey = keyPair.public as Ed25519PublicKeyParameters

        val pubKeyBase64 = Base64.getEncoder().encodeToString(pubKey.encoded)

        val payload = "{\"schemaVersion\":1,\"configVersion\":42,\"providers\":[]}"
        val signer = Ed25519Signer()
        signer.init(true, privKey)
        val payloadBytes = payload.toByteArray(StandardCharsets.UTF_8)
        signer.update(payloadBytes, 0, payloadBytes.size)
        val sigBytes = signer.generateSignature()
        val sigBase64 = Base64.getEncoder().encodeToString(sigBytes)

        val isValid = Ed25519Verifier.verify(payload, sigBase64, pubKeyBase64)
        assertTrue("Expected signature to be valid", isValid)
    }

    @Test
    fun testTamperedPayloadFailsVerification() {
        val random = SecureRandom()
        val keyPairGen = Ed25519KeyPairGenerator()
        keyPairGen.init(Ed25519KeyGenerationParameters(random))
        val keyPair = keyPairGen.generateKeyPair()

        val privKey = keyPair.private as Ed25519PrivateKeyParameters
        val pubKey = keyPair.public as Ed25519PublicKeyParameters
        val pubKeyBase64 = Base64.getEncoder().encodeToString(pubKey.encoded)

        val originalPayload = "{\"schemaVersion\":1,\"configVersion\":42}"
        val signer = Ed25519Signer()
        signer.init(true, privKey)
        val payloadBytes = originalPayload.toByteArray(StandardCharsets.UTF_8)
        signer.update(payloadBytes, 0, payloadBytes.size)
        val sigBase64 = Base64.getEncoder().encodeToString(signer.generateSignature())

        val tamperedPayload = "{\"schemaVersion\":1,\"configVersion\":43}"
        val isValid = Ed25519Verifier.verify(tamperedPayload, sigBase64, pubKeyBase64)
        assertFalse("Tampered payload must fail verification", isValid)
    }

    @Test
    fun testInvalidOrMalformedSignature() {
        val payload = "{\"test\": true}"
        assertFalse("Empty signature must fail", Ed25519Verifier.verify(payload, ""))
        assertFalse("Malformed base64 must fail", Ed25519Verifier.verify(payload, "not-valid-base64!@#"))
        assertFalse("Truncated signature must fail", Ed25519Verifier.verify(payload, Base64.getEncoder().encodeToString(ByteArray(32))))
    }
}
