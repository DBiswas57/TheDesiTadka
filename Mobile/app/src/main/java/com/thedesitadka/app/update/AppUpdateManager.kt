package com.thedesitadka.app.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import com.thedesitadka.app.BuildConfig
import com.thedesitadka.core.network.NetworkClient
import com.thedesitadka.core.security.ApkIntegrityManager
import com.thedesitadka.core.security.StreamHubLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.security.MessageDigest

data class UpdateInfo(
    val isUpdateAvailable: Boolean,
    val latestVersionName: String,
    val currentVersionName: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val expectedSha256: String?
)

object AppUpdateManager {

    private const val GITHUB_REPO = "TheDesiTadka"
    private val REPO_CANDIDATE_URLS = listOf(
        "https://api.github.com/repos/DBiswas57/TheDesiTadka/releases/latest",
        "https://api.github.com/repos/LearnersYT/TheDesiTadka/releases/latest"
    )

    /**
     * Queries the official GitHub repository for the latest release.
     * Compares semver and tags against the current app build.
     */
    suspend fun checkForUpdates(): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        try {
            val headers = mapOf(
                "Accept" to "application/vnd.github.v3+json",
                "User-Agent" to "TheDesiTadka-Updater"
            )

            var jsonString: String? = null
            var lastError: Exception? = null
            for (apiUrl in REPO_CANDIDATE_URLS) {
                try {
                    jsonString = NetworkClient.fetchString(apiUrl, headers = headers)
                    if (jsonString.isNotBlank()) break
                } catch (e: Exception) {
                    lastError = e
                }
            }

            if (jsonString.isNullOrBlank()) {
                throw lastError ?: IllegalStateException("Unable to fetch update manifest from official release sources")
            }

            val root = Json.parseToJsonElement(jsonString).jsonObject

            val tagName = root["tag_name"]?.jsonPrimitive?.content ?: ""
            val releaseTitle = root["name"]?.jsonPrimitive?.content ?: tagName
            val releaseNotes = root["body"]?.jsonPrimitive?.content ?: ""

            val cleanTag = tagName.removePrefix("v").trim()
            val currentVersion = BuildConfig.VERSION_NAME.trim()

            // Find APK in release assets
            val assets = root["assets"]?.jsonArray ?: emptyList()
            var apkUrl = ""
            for (assetElement in assets) {
                val assetObj = assetElement.jsonObject
                val name = assetObj["name"]?.jsonPrimitive?.content ?: ""
                val browserDownloadUrl = assetObj["browser_download_url"]?.jsonPrimitive?.content ?: ""
                if (name.endsWith(".apk", ignoreCase = true) && browserDownloadUrl.isNotBlank()) {
                    apkUrl = browserDownloadUrl
                    break
                }
            }

            // Extract optional SHA-256 checksum from release description if published
            val shaRegex = Regex("""[A-Fa-f0-9]{64}""")
            val expectedSha = shaRegex.find(releaseNotes)?.value

            val isNewer = isVersionNewer(cleanTag, currentVersion)

            val info = UpdateInfo(
                isUpdateAvailable = isNewer && apkUrl.isNotBlank(),
                latestVersionName = cleanTag,
                currentVersionName = currentVersion,
                releaseTitle = releaseTitle,
                releaseNotes = releaseNotes,
                downloadUrl = apkUrl,
                expectedSha256 = expectedSha
            )

            StreamHubLogger.i("AppUpdateManager", "Checked update: current=$currentVersion, latest=$cleanTag, isAvailable=${info.isUpdateAvailable}")
            Result.success(info)
        } catch (e: Exception) {
            StreamHubLogger.w("AppUpdateManager", "Update check failed: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Downloads the official update APK into the app's cache directory and
     * cryptographically verifies its package identity and signing certificate
     * before permitting installation.
     */
    suspend fun downloadAndVerifyUpdate(
        context: Context,
        updateInfo: UpdateInfo,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val targetFile = File(updatesDir, "TheDesiTadka-${updateInfo.latestVersionName}.apk")

            // 1. Download binary safely
            val url = URL(updateInfo.downloadUrl)
            val connection = url.openConnection()
            connection.connect()

            val contentLength = connection.contentLength
            var downloaded = 0L

            connection.getInputStream().use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloaded += bytesRead
                        if (contentLength > 0) {
                            onProgress(downloaded.toFloat() / contentLength.toFloat())
                        }
                    }
                }
            }

            // 2. Validate SHA-256 Checksum if provided in official metadata
            if (!updateInfo.expectedSha256.isNullOrBlank()) {
                val computedSha = computeFileSha256(targetFile)
                if (!computedSha.equals(updateInfo.expectedSha256, ignoreCase = true)) {
                    targetFile.delete()
                    val err = "Update checksum mismatch! Expected ${updateInfo.expectedSha256}, got $computedSha"
                    StreamHubLogger.e("AppUpdateManager", err)
                    return@withContext Result.failure(SecurityException(err))
                }
            }

            // 3. Security Check: Validate Package Name and Certificate via PackageManager
            val pm = context.packageManager
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES
            }

            val archiveInfo = pm.getPackageArchiveInfo(targetFile.absolutePath, flags)
                ?: run {
                    targetFile.delete()
                    return@withContext Result.failure(SecurityException("Corrupt or invalid APK archive"))
                }

            // Validate package identity
            if (archiveInfo.packageName != ApkIntegrityManager.EXPECTED_PACKAGE_NAME) {
                targetFile.delete()
                val err = "Untrusted APK package name: ${archiveInfo.packageName} (expected ${ApkIntegrityManager.EXPECTED_PACKAGE_NAME})"
                StreamHubLogger.e("AppUpdateManager", err)
                return@withContext Result.failure(SecurityException(err))
            }

            // Validate signing certificate matches current installed application
            val isCertValid = verifyArchiveCertificate(context, targetFile)
            if (!isCertValid) {
                targetFile.delete()
                val err = "Untrusted signing certificate in update APK! Installation aborted."
                StreamHubLogger.e("AppUpdateManager", err)
                return@withContext Result.failure(SecurityException(err))
            }

            StreamHubLogger.i("AppUpdateManager", "Update APK verified successfully: ${targetFile.name}")
            Result.success(targetFile)
        } catch (e: Exception) {
            StreamHubLogger.e("AppUpdateManager", "Download and verify failed: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Prompts the system package installer using the secure FileProvider.
     */
    fun launchInstallIntent(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "com.thedesitadka.app.fileprovider",
            apkFile
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        context.startActivity(installIntent)
    }

    private fun verifyArchiveCertificate(context: Context, apkFile: File): Boolean {
        return try {
            val pm = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val archiveInfo = pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
                val currentInfo = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)

                val archiveSigners = archiveInfo?.signingInfo?.apkContentsSigners?.map { it.toByteArray() } ?: emptyList()
                val currentSigners = currentInfo.signingInfo?.apkContentsSigners?.map { it.toByteArray() } ?: emptyList()

                if (archiveSigners.isEmpty() || currentSigners.isEmpty()) return false

                // Ensure at least one certificate matches
                archiveSigners.any { archCert ->
                    currentSigners.any { currCert -> archCert.contentEquals(currCert) }
                }
            } else {
                @Suppress("DEPRECATION")
                val archiveInfo = pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                val currentInfo = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)

                val archiveSigners = archiveInfo?.signatures ?: emptyArray()
                val currentSigners = currentInfo.signatures ?: emptyArray()

                archiveSigners.any { archCert ->
                    currentSigners.any { currCert -> archCert.toByteArray().contentEquals(currCert.toByteArray()) }
                }
            }
        } catch (e: Exception) {
            StreamHubLogger.w("AppUpdateManager", "Certificate verification error: ${e.message}")
            false
        }
    }

    private fun computeFileSha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                md.update(buffer, 0, bytesRead)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun isVersionNewer(latest: String, current: String): Boolean {
        val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }

        val length = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until length) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }
}
