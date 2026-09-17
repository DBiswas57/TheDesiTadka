package com.thedesitadka.core.config

import com.thedesitadka.core.model.ProviderManifest
import com.thedesitadka.core.security.StreamHubLogger
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

class ConfigCache(private val baseDir: File) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        isLenient = true
    }

    private val currentConfigFile: File get() = File(baseDir, "current_config.json")
    private val previousConfigFile: File get() = File(baseDir, "previous_config.json")
    private val lastKnownGoodConfigFile: File get() = File(baseDir, "last_known_good_config.json")

    init {
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
    }

    @Synchronized
    fun loadCurrent(): ProviderManifest? {
        return loadFromFile(currentConfigFile) ?: loadLastKnownGood()
    }

    @Synchronized
    fun loadLastKnownGood(): ProviderManifest? {
        return loadFromFile(lastKnownGoodConfigFile)
    }

    @Synchronized
    fun loadPrevious(): ProviderManifest? {
        return loadFromFile(previousConfigFile)
    }

    @Synchronized
    fun saveValidatedConfig(manifest: ProviderManifest, markAsLastKnownGood: Boolean = true) {
        val serialized = json.encodeToString(ProviderManifest.serializer(), manifest)

        // Rotate current to previous
        if (currentConfigFile.exists()) {
            try {
                if (previousConfigFile.exists()) previousConfigFile.delete()
                currentConfigFile.copyTo(previousConfigFile, overwrite = true)
            } catch (e: Exception) {
                StreamHubLogger.w("ConfigCache", "Failed to backup current config to previous: ${e.message}")
            }
        }

        // Atomically write new current
        atomicWrite(currentConfigFile, serialized)

        // Optionally update last known good
        if (markAsLastKnownGood) {
            atomicWrite(lastKnownGoodConfigFile, serialized)
        }
        StreamHubLogger.i("ConfigCache", "Config v${manifest.configVersion} stored atomically")
    }

    @Synchronized
    fun rollback(): ProviderManifest? {
        StreamHubLogger.w("ConfigCache", "Initiating config rollback...")
        val previous = loadPrevious()
        if (previous != null) {
            val serialized = json.encodeToString(ProviderManifest.serializer(), previous)
            atomicWrite(currentConfigFile, serialized)
            StreamHubLogger.i("ConfigCache", "Rolled back to previous config v${previous.configVersion}")
            return previous
        }

        val lastGood = loadLastKnownGood()
        if (lastGood != null) {
            val serialized = json.encodeToString(ProviderManifest.serializer(), lastGood)
            atomicWrite(currentConfigFile, serialized)
            StreamHubLogger.i("ConfigCache", "Rolled back to last-known-good config v${lastGood.configVersion}")
            return lastGood
        }

        StreamHubLogger.e("ConfigCache", "Rollback failed: no previous or last-known-good configuration available")
        return null
    }

    private fun loadFromFile(file: File): ProviderManifest? {
        if (!file.exists() || file.length() == 0L) return null
        return try {
            val content = file.readText(Charsets.UTF_8)
            json.decodeFromString(ProviderManifest.serializer(), content)
        } catch (e: Exception) {
            StreamHubLogger.e("ConfigCache", "Failed to decode config from ${file.name}: ${e.message}")
            null
        }
    }

    private fun atomicWrite(targetFile: File, content: String) {
        val tempFile = File(baseDir, "${targetFile.name}.tmp")
        try {
            tempFile.writeText(content, Charsets.UTF_8)
            if (targetFile.exists()) {
                targetFile.delete()
            }
            if (!tempFile.renameTo(targetFile)) {
                // Fallback copy if rename fails across file systems
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()
            throw IOException("Failed to atomically write ${targetFile.name}: ${e.message}", e)
        }
    }
}
