package com.thedesitadka.app.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.thedesitadka.app.BuildConfig
import com.thedesitadka.core.security.ApkIntegrityManager
import com.thedesitadka.core.security.IntegrityChecker

object AndroidApkIntegrityChecker {

    fun checkAppIntegrity(context: Context): ApkIntegrityManager.IntegrityReport {
        val pm = context.packageManager
        val packageName = context.packageName

        val certBytesList = mutableListOf<ByteArray>()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                packageInfo.signingInfo?.apkContentsSigners?.forEach { signer ->
                    certBytesList.add(signer.toByteArray())
                }
            } else {
                @Suppress("DEPRECATION")
                val packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                packageInfo.signatures?.forEach { sig ->
                    certBytesList.add(sig.toByteArray())
                }
            }
        } catch (e: Exception) {
            // Error extracting certificates
        }

        val basicIntegrity = IntegrityChecker.performCheck(BuildConfig.DEBUG)

        return ApkIntegrityManager.verifyIntegrity(
            actualPackageName = packageName,
            certificateBytesList = certBytesList,
            isDebugBuild = BuildConfig.DEBUG,
            isRooted = basicIntegrity.isRooted
        )
    }
}
