package com.thedesitadka.core.config

import com.thedesitadka.core.model.ProviderManifest
import com.thedesitadka.core.model.SignedPayload
import com.thedesitadka.core.model.StreamHubError
import com.thedesitadka.core.security.Ed25519Verifier
import com.thedesitadka.core.security.StreamHubLogger
import kotlinx.serialization.json.Json

object ConfigVerifier {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        isLenient = true
    }

    data class VerificationResult(
        val isSuccess: Boolean,
        val manifest: ProviderManifest?,
        val error: StreamHubError?
    )

    /**
     * Executes cryptographic signature verification and schema/operational validation
     * on an incoming signed remote configuration bundle.
     */
    fun verifySignedPayload(
        signedJson: String,
        currentVersion: Int = 0,
        currentAppVersion: Int = 1,
        publicKeyBase64: String = Ed25519Verifier.DEFAULT_PUBLIC_KEY_BASE64
    ): VerificationResult {
        return try {
            // Step 1: Decode SignedPayload envelope
            val signedEnvelope = try {
                json.decodeFromString(SignedPayload.serializer(), signedJson)
            } catch (e: Exception) {
                // If not in SignedPayload envelope, try directly as JSON if test/unsigned mode
                throw StreamHubError.ConfigurationError("Failed to parse signed envelope: ${e.message}", e)
            }

            // Step 2: Cryptographic Ed25519 Signature Verification
            val isSignatureValid = Ed25519Verifier.verify(
                canonicalPayload = signedEnvelope.payload,
                signatureBase64 = signedEnvelope.signature,
                publicKeyBase64 = publicKeyBase64
            )

            if (!isSignatureValid) {
                throw StreamHubError.SecurityError(
                    reason = "SIGNATURE_INVALID",
                    message = "Remote config Ed25519 signature check failed. Possible payload tampering or unauthorized key."
                )
            }

            // Step 3: Parse ProviderManifest
            val manifest = json.decodeFromString(ProviderManifest.serializer(), signedEnvelope.payload)

            // Step 4: Validate schema, version, expiration, SSRF, and safe DSL
            ConfigValidator.validate(
                manifest = manifest,
                currentVersion = currentVersion,
                currentAppVersion = currentAppVersion
            )

            StreamHubLogger.i("ConfigVerifier", "Configuration v${manifest.configVersion} verified successfully")
            VerificationResult(isSuccess = true, manifest = manifest, error = null)
        } catch (e: StreamHubError) {
            StreamHubLogger.e("ConfigVerifier", "Config verification failed: ${e.message}")
            VerificationResult(isSuccess = false, manifest = null, error = e)
        } catch (e: Exception) {
            StreamHubLogger.e("ConfigVerifier", "Unexpected error during verification: ${e.message}")
            VerificationResult(
                isSuccess = false,
                manifest = null,
                error = StreamHubError.ConfigurationError("Unexpected verification failure: ${e.message}", e)
            )
        }
    }
}
