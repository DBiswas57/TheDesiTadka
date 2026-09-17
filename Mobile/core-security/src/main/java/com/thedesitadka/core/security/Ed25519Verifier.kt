package com.thedesitadka.core.security

import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.nio.charset.StandardCharsets
import java.util.Base64

object Ed25519Verifier {

    // Default embedded production public key for StreamHub signed manifests
    // 32-byte Ed25519 public key in Base64 (RFC 8032)
    // Corresponding private key is held strictly in secure deployment/CI environment
    const val DEFAULT_PUBLIC_KEY_BASE64: String = "MCowBQYDK2VwAyEAbTQY87AN8BulCWatsickY/GWzK5SzWcVxdrDd6jYalU="

    /**
     * Verifies that [signatureBase64] is a valid Ed25519 signature of [canonicalPayload]
     * using [publicKeyBase64] (defaults to [DEFAULT_PUBLIC_KEY_BASE64]).
     */
    fun verify(
        canonicalPayload: String,
        signatureBase64: String,
        publicKeyBase64: String = DEFAULT_PUBLIC_KEY_BASE64
    ): Boolean {
        return try {
            val pubKeyBytes = decodePublicKey(publicKeyBase64)
            val sigBytes = Base64.getDecoder().decode(signatureBase64.trim())
            val messageBytes = canonicalPayload.toByteArray(StandardCharsets.UTF_8)

            if (pubKeyBytes.size != 32) {
                StreamHubLogger.e("Ed25519Verifier", "Invalid public key length: ${pubKeyBytes.size} (expected 32)")
                return false
            }
            if (sigBytes.size != 64) {
                StreamHubLogger.e("Ed25519Verifier", "Invalid signature length: ${sigBytes.size} (expected 64)")
                return false
            }

            val pubKeyParams = Ed25519PublicKeyParameters(pubKeyBytes, 0)
            val signer = Ed25519Signer()
            signer.init(false, pubKeyParams)
            signer.update(messageBytes, 0, messageBytes.size)
            val isValid = signer.verifySignature(sigBytes)

            if (!isValid) {
                StreamHubLogger.w("Ed25519Verifier", "Ed25519 signature verification failed")
            }
            isValid
        } catch (e: Exception) {
            StreamHubLogger.e("Ed25519Verifier", "Exception during signature verification: ${e.message}")
            false
        }
    }

    private fun decodePublicKey(keyStr: String): ByteArray {
        val raw = Base64.getDecoder().decode(keyStr.trim())
        // If the key is X.509 SubjectPublicKeyInfo encoded (44 bytes), extract the last 32 bytes
        return if (raw.size == 44 && raw[0] == 0x30.toByte()) {
            raw.copyOfRange(raw.size - 32, raw.size)
        } else {
            raw
        }
    }
}
