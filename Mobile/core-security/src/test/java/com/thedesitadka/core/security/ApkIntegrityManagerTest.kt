package com.thedesitadka.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class ApkIntegrityManagerTest {

    @Test
    fun testOfficialReleaseVerificationPasses() {
        val rawCertBytes = "dummy_official_cert_payload".toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val expectedSha = md.digest(rawCertBytes).joinToString(":") { "%02X".format(it) }

        // Test with pinned cert
        val report = ApkIntegrityManager.verifyIntegrity(
            actualPackageName = "com.thedesitadka.app",
            certificateBytesList = listOf(rawCertBytes),
            isDebugBuild = false,
            isRooted = false
        )

        // Since rawCertBytes is not in default hardcoded PINNED set, it should yield TAMPERED_CERTIFICATE
        assertEquals(ApkIntegrityManager.IntegrityVerdict.TAMPERED_CERTIFICATE, report.verdict)
        assertFalse(report.isTrusted)
    }

    @Test
    fun testDevelopmentDebugBuildAllowed() {
        val rawCertBytes = "debug_cert".toByteArray()
        val report = ApkIntegrityManager.verifyIntegrity(
            actualPackageName = "com.thedesitadka.app",
            certificateBytesList = listOf(rawCertBytes),
            isDebugBuild = true,
            isRooted = false
        )

        assertEquals(ApkIntegrityManager.IntegrityVerdict.DEVELOPMENT_DEBUG, report.verdict)
        assertTrue(report.isTrusted)
    }

    @Test
    fun testTamperedPackageRejected() {
        val report = ApkIntegrityManager.verifyIntegrity(
            actualPackageName = "com.fake.modded.app",
            certificateBytesList = emptyList(),
            isDebugBuild = false,
            isRooted = false
        )

        assertEquals(ApkIntegrityManager.IntegrityVerdict.TAMPERED_PACKAGE, report.verdict)
        assertFalse(report.isTrusted)
        assertFalse(report.isPackageValid)
    }

    @Test
    fun testSha256Formatting() {
        val data = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val hash = ApkIntegrityManager.computeSha256(data)
        assertTrue(hash.isNotEmpty())
        assertTrue(hash.contains(":"))
    }
}
