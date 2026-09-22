package com.thedesitadka.app.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
    val isForceUpdate: Boolean = false,
    val latestVersionName: String,
    val currentVersionName: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val expectedSha256: String?
)

object AppUpdateManager {

    private const val GITHUB_REPO = "DBiswas57/TheDesiTadka"
    private val REPO_CANDIDATE_URLS = listOf(
        "https://api.github.com/repos/DBiswas57/TheDesiTadka/releases/latest"
    )

    /**
     * Queries the official GitHub repository for the latest release.
     * Compares semver and tags against the current app build.
     */
    suspend fun checkForUpdates(): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        val currentVersion = BuildConfig.VERSION_NAME.trim()
        if (BuildConfig.DEBUG) {
            return@withContext Result.success(
                UpdateInfo(
                    isUpdateAvailable = false,
                    isForceUpdate = false,
                    latestVersionName = currentVersion,
                    currentVersionName = currentVersion,
                    releaseTitle = "Up to date (Debug)",
                    releaseNotes = "Debug test build — update check bypassed for testing.",
                    downloadUrl = "",
                    expectedSha256 = null
                )
            )
        }
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
                    // If 404, releases/latest means no releases published yet on repository
                    if (e.message?.contains("404") == true) {
                        StreamHubLogger.i("AppUpdateManager", "No releases published yet on $apiUrl")
                        return@withContext Result.success(
                            UpdateInfo(
                                isUpdateAvailable = false,
                                isForceUpdate = false,
                                latestVersionName = currentVersion,
                                currentVersionName = currentVersion,
                                releaseTitle = "Up to date",
                                releaseNotes = "No new updates available.",
                                downloadUrl = "",
                                expectedSha256 = null
                            )
                        )
                    }
                }
            }

            if (jsonString.isNullOrBlank()) {
                throw lastError ?: IllegalStateException("Unable to fetch update manifest from official release sources")
            }

            val root = Json.parseToJsonElement(jsonString).jsonObject

            val tagName = root["tag_name"]?.jsonPrimitive?.content ?: ""
            val releaseTitle = root["name"]?.jsonPrimitive?.content ?: tagName
            val rawNotes = root["body"]?.jsonPrimitive?.content ?: ""
            val releaseNotes = rawNotes.replace("**", "").replace("##", "").trim()

            val cleanTag = tagName.removePrefix("v").trim()

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
            // ALL available updates are mandatory — no optional updates in this system
            val isForce = isNewer

            val info = UpdateInfo(
                isUpdateAvailable = isNewer && apkUrl.isNotBlank(),
                isForceUpdate = isForce,
                latestVersionName = cleanTag,
                currentVersionName = currentVersion,
                releaseTitle = releaseTitle,
                releaseNotes = releaseNotes,
                downloadUrl = apkUrl,
                expectedSha256 = expectedSha
            )

            StreamHubLogger.i("AppUpdateManager", "Checked update: current=$currentVersion, latest=$cleanTag, isAvailable=${info.isUpdateAvailable}, isForce=${info.isForceUpdate}")
            Result.success(info)
        } catch (e: Exception) {
            StreamHubLogger.w("AppUpdateManager", "Update check failed: ${e.message}")
            Result.failure(e)
        }
    }

    fun getDownloadedUpdateFile(context: Context, versionName: String): File? {
        val updatesDir = File(context.cacheDir, "updates")
        val file = File(updatesDir, "TheDesiTadka-$versionName.apk")
        return if (file.exists() && file.length() > 1024 * 1024 && verifyArchive(context, file)) file else null
    }

    fun verifyArchive(context: Context, targetFile: File): Boolean {
        return try {
            val pm = context.packageManager
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES
            }
            val archiveInfo = pm.getPackageArchiveInfo(targetFile.absolutePath, flags) ?: return false
            if (archiveInfo.packageName != ApkIntegrityManager.EXPECTED_PACKAGE_NAME) return false
            verifyArchiveCertificate(context, targetFile)
        } catch (e: Exception) {
            false
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

            // Check if already downloaded and verified
            if (targetFile.exists() && targetFile.length() > 1024 * 1024) {
                val shaMatches = updateInfo.expectedSha256.isNullOrBlank() || computeFileSha256(targetFile).equals(updateInfo.expectedSha256, ignoreCase = true)
                if (shaMatches && verifyArchive(context, targetFile)) {
                    StreamHubLogger.i("AppUpdateManager", "Using existing verified update APK: ${targetFile.name}")
                    onProgress(1f)
                    return@withContext Result.success(targetFile)
                }
            }

            val tempFile = File(updatesDir, "TheDesiTadka-${updateInfo.latestVersionName}.apk.tmp")
            if (tempFile.exists()) tempFile.delete()

            // 1. Download binary safely into temp file
            val url = URL(updateInfo.downloadUrl)
            val connection = url.openConnection()
            connection.connect()

            val contentLength = connection.contentLength
            var downloaded = 0L

            connection.getInputStream().use { input ->
                FileOutputStream(tempFile).use { output ->
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
                val computedSha = computeFileSha256(tempFile)
                if (!computedSha.equals(updateInfo.expectedSha256, ignoreCase = true)) {
                    tempFile.delete()
                    val err = "Update checksum mismatch! Expected ${updateInfo.expectedSha256}, got $computedSha"
                    StreamHubLogger.e("AppUpdateManager", err)
                    return@withContext Result.failure(SecurityException(err))
                }
            }

            // 3. Security Check: Validate Package Name and Certificate via PackageManager
            if (!verifyArchive(context, tempFile)) {
                tempFile.delete()
                val err = "Untrusted or corrupt package in update APK! Installation aborted."
                StreamHubLogger.e("AppUpdateManager", err)
                return@withContext Result.failure(SecurityException(err))
            }

            if (targetFile.exists()) targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            StreamHubLogger.i("AppUpdateManager", "Update APK verified successfully: ${targetFile.name}")
            Result.success(targetFile)
        } catch (e: Exception) {
            StreamHubLogger.e("AppUpdateManager", "Download and verify failed: ${e.message}")
            Result.failure(e)
        }
    }

    fun canRequestPackageInstalls(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openUnknownAppSourcesSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                StreamHubLogger.e("AppUpdateManager", "Failed to open unknown app sources settings: ${e.message}")
            }
        }
    }

    /**
     * Prompts the system package installer using the secure FileProvider.
     * Ensures proper permissions and checks before starting the system installer.
     */
    fun launchInstallIntent(context: Context, apkFile: File): Boolean {
        return try {
            if (!apkFile.exists()) {
                StreamHubLogger.e("AppUpdateManager", "APK file does not exist: ${apkFile.absolutePath}")
                return false
            }

            apkFile.setReadable(true, false)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
                StreamHubLogger.w("AppUpdateManager", "Install permission not granted. Redirecting to settings...")
                openUnknownAppSourcesSettings(context)
                return false
            }

            val uri = FileProvider.getUriForFile(
                context,
                "com.thedesitadka.app.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

            // Explicitly grant read URI permission
            val resInfoList = try {
                context.packageManager.queryIntentActivities(installIntent, PackageManager.MATCH_DEFAULT_ONLY)
            } catch (e: Exception) {
                emptyList()
            }
            for (resolveInfo in resInfoList) {
                val packageName = resolveInfo.activityInfo?.packageName
                if (!packageName.isNullOrBlank()) {
                    context.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }

            context.startActivity(installIntent)
            StreamHubLogger.i("AppUpdateManager", "Installer intent launched successfully for ${apkFile.name}")
            true
        } catch (e: Exception) {
            StreamHubLogger.e("AppUpdateManager", "Failed to launch installer: ${e.message}")
            false
        }
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

    internal fun isVersionNewer(latest: String, current: String): Boolean {
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
