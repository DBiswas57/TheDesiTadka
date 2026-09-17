package com.thedesitadka.core.security

import java.security.MessageDigest

/**
 * Layered anti-modification and APK integrity verification manager.
 * Enforces package identity, certificate pinning, and environment validation
 * without brittle self-hashing or destructive crash loops.
 */
object ApkIntegrityManager {

    const val EXPECTED_PACKAGE_NAME = "com.thedesitadka.app"

    /**
     * Pinned official release certificate SHA-256 fingerprints (hex with colons or flat).
     * Add any authorized signing keys (e.g. production release key, authorized CI keys).
     */
    val PINNED_RELEASE_CERTIFICATE_DIGESTS = setOf(
        "A8:71:0D:32:8B:DF:C9:83:19:62:3C:99:98:8C:F3:D2:C3:57:A8:6A:B9:4C:6A:D2:B6:7C:1F:B4:73:90:3F:08",
        "A8710D328BDFC98319623C99988CF3D2C357A86AB94C6AD2B67C1FB473903F08",
        "77:0F:84:71:69:A4:6F:6E:62:85:F0:AC:3C:FB:5E:F0:36:60:86:66:E7:7B:27:E9:6B:16:D3:C0:15:BB:DF:28",
        "770F847169A46F6E6285F0AC3CFB5EF036608666E77B27E96B16D3C015BBDF28"
    )

    enum class IntegrityVerdict {
        OFFICIAL_RELEASE,
        DEVELOPMENT_DEBUG,
        TAMPERED_PACKAGE,
        TAMPERED_CERTIFICATE,
        TAMPERED_ENVIRONMENT
    }

    data class IntegrityReport(
        val verdict: IntegrityVerdict,
        val packageName: String,
        val isPackageValid: Boolean,
        val isCertificatePinned: Boolean,
        val certificateFingerprints: List<String>,
        val isDebuggable: Boolean,
        val isRooted: Boolean,
        val isTrusted: Boolean,
        val details: String
    )

    /**
     * Evaluates application integrity based on runtime package and signing parameters.
     * Non-destructive: returns a verdict allowing the app to run in a safe, restricted
     * mode without monetization or update trust rather than crashing.
     */
    fun verifyIntegrity(
        actualPackageName: String,
        certificateBytesList: List<ByteArray>,
        isDebugBuild: Boolean,
        isRooted: Boolean
    ): IntegrityReport {
        val isPackageValid = actualPackageName == EXPECTED_PACKAGE_NAME ||
                (isDebugBuild && actualPackageName == "$EXPECTED_PACKAGE_NAME.debug")

        val fingerprints = certificateBytesList.map { computeSha256(it) }
        val isCertificatePinned = fingerprints.any { fp ->
            PINNED_RELEASE_CERTIFICATE_DIGESTS.any { pinned ->
                pinned.equals(fp, ignoreCase = true) ||
                        pinned.replace(":", "").equals(fp.replace(":", ""), ignoreCase = true)
            }
        }

        val verdict: IntegrityVerdict
        val isTrusted: Boolean
        val details: String

        when {
            !isPackageValid -> {
                verdict = IntegrityVerdict.TAMPERED_PACKAGE
                isTrusted = false
                details = "Package name mismatch: actual '$actualPackageName' vs expected '$EXPECTED_PACKAGE_NAME'"
            }
            isDebugBuild -> {
                verdict = IntegrityVerdict.DEVELOPMENT_DEBUG
                isTrusted = true
                details = "Development debug build with test signing"
            }
            isCertificatePinned -> {
                if (isRooted) {
                    verdict = IntegrityVerdict.TAMPERED_ENVIRONMENT
                    isTrusted = false
                    details = "Official certificate verified, but hostile rooted runtime detected"
                } else {
                    verdict = IntegrityVerdict.OFFICIAL_RELEASE
                    isTrusted = true
                    details = "Official release certificate verified: ${fingerprints.firstOrNull() ?: "OK"}"
                }
            }
            else -> {
                verdict = IntegrityVerdict.TAMPERED_CERTIFICATE
                isTrusted = false
                details = "Untrusted signing certificate: ${fingerprints.joinToString()}"
            }
        }

        if (!isTrusted) {
            StreamHubLogger.w("ApkIntegrityManager", "Integrity check warning: $details (Verdict: $verdict)")
        } else {
            StreamHubLogger.i("ApkIntegrityManager", "Integrity verified: $details (Verdict: $verdict)")
        }

        return IntegrityReport(
            verdict = verdict,
            packageName = actualPackageName,
            isPackageValid = isPackageValid,
            isCertificatePinned = isCertificatePinned,
            certificateFingerprints = fingerprints,
            isDebuggable = isDebugBuild,
            isRooted = isRooted,
            isTrusted = isTrusted,
            details = details
        )
    }

    /**
     * Computes the SHA-256 fingerprint of raw certificate bytes.
     */
    fun computeSha256(bytes: ByteArray): String {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(bytes)
            digest.joinToString(":") { "%02X".format(it) }
        } catch (e: Exception) {
            ""
        }
    }
}
