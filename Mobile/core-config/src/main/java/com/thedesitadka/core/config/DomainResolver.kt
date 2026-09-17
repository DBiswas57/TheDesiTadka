package com.thedesitadka.core.config

import com.thedesitadka.core.model.ProviderConfig
import com.thedesitadka.core.network.NetworkClient
import com.thedesitadka.core.security.StreamHubLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

/**
 * DomainResolver implements the Domain-Family Architecture for TheDesiMedia.
 * It dynamically discovers, health-checks, resolves HTTP redirects, and caches
 * active working domains for providers that change TLDs over time.
 */
class DomainResolver(
    private val cacheDir: File? = null,
    private val cacheTtlMs: Long = 24 * 60 * 60 * 1000L // 24 Hours TTL
) {
    data class ActiveDomainRecord(
        val domain: String,
        val lastVerifiedMs: Long,
        val status: String
    )

    private val activeDomains = ConcurrentHashMap<String, ActiveDomainRecord>()
    private val mutexMap = ConcurrentHashMap<String, Mutex>()
    private val cacheFile: File? = cacheDir?.let { File(it, "active_domains.json") }

    init {
        loadCache()
    }

    private fun getMutex(providerId: String): Mutex {
        return mutexMap.computeIfAbsent(providerId) { Mutex() }
    }

    /**
     * Resolves the active working domain for the specified provider.
     * Uses memory & disk cache if fresh; otherwise conducts health check across candidates.
     */
    suspend fun resolveActiveDomain(config: ProviderConfig, forceRefresh: Boolean = false): String = withContext(Dispatchers.IO) {
        val providerId = config.id
        val mutex = getMutex(providerId)

        mutex.withLock {
            val cached = activeDomains[providerId]
            val now = System.currentTimeMillis()

            if (!forceRefresh && cached != null && (now - cached.lastVerifiedMs < cacheTtlMs)) {
                return@withContext cached.domain
            }

            // Build candidate list starting with cached domain, baseUrl, then alternative domains
            val candidates = mutableListOf<String>()
            if (cached != null && cached.domain.isNotBlank()) {
                candidates.add(cached.domain)
            }
            if (!candidates.contains(config.baseUrl)) {
                candidates.add(config.baseUrl)
            }
            config.domains.forEach { domain ->
                if (!candidates.contains(domain)) {
                    candidates.add(domain)
                }
            }

            if (candidates.isEmpty()) {
                return@withContext config.baseUrl
            }

            for (candidate in candidates) {
                val healthyDomain = checkDomainHealth(candidate, config.validationMarker)
                if (healthyDomain != null) {
                    val record = ActiveDomainRecord(
                        domain = healthyDomain,
                        lastVerifiedMs = now,
                        status = "AVAILABLE"
                    )
                    activeDomains[providerId] = record
                    saveCache()
                    StreamHubLogger.i("DomainResolver", "Active domain resolved for provider '${config.name}': status=AVAILABLE")
                    return@withContext healthyDomain
                }
            }

            // Fallback: If all candidates fail, return default baseUrl safely
            StreamHubLogger.w("DomainResolver", "All domain candidates unreachable for provider '${config.name}'. Falling back to default baseUrl.")
            return@withContext config.baseUrl
        }
    }

    /**
     * Checks if a candidate domain is healthy and matches provider markers.
     */
    private suspend fun checkDomainHealth(candidateUrl: String, validationMarker: String?): String? = withContext(Dispatchers.IO) {
        try {
            val normalizedUrl = if (!candidateUrl.startsWith("http://") && !candidateUrl.startsWith("https://")) {
                "https://$candidateUrl"
            } else {
                candidateUrl
            }.trimEnd('/')

            val response = NetworkClient.fetchString(normalizedUrl)

            // Validate content marker if specified
            if (!validationMarker.isNullOrBlank()) {
                if (!response.contains(validationMarker, ignoreCase = true)) {
                    StreamHubLogger.w("DomainResolver", "Domain health check failed: Marker '$validationMarker' missing in response")
                    return@withContext null
                }
            }

            // Detect parking pages / domain-for-sale / ISP blocks
            val lowerResponse = response.lowercase()
            val blockedMarkers = listOf("domain for sale", "buy this domain", "site under maintenance", "dns error", "blocked by order")
            if (blockedMarkers.any { lowerResponse.contains(it) }) {
                StreamHubLogger.w("DomainResolver", "Domain health check rejected: Parking or block page detected")
                return@withContext null
            }

            return@withContext normalizedUrl
        } catch (e: Exception) {
            StreamHubLogger.d("DomainResolver", "Domain health check failed for candidate: ${e.message}")
            return@withContext null
        }
    }

    /**
     * Invalidate active domain on failure so next request triggers candidate discovery.
     */
    fun markDomainFailed(providerId: String) {
        activeDomains.remove(providerId)
        saveCache()
    }

    private fun loadCache() {
        try {
            val file = cacheFile ?: return
            if (!file.exists()) return
            val json = file.readText(Charsets.UTF_8)
            // Simple robust JSON parser for internal domain cache
            val regex = Regex(""""([^"]+)":\s*\{\s*"domain":\s*"([^"]+)",\s*"lastVerifiedMs":\s*(\d+),\s*"status":\s*"([^"]+)"\s*\}""")
            regex.findAll(json).forEach { match ->
                val id = match.groupValues[1]
                val domain = match.groupValues[2]
                val time = match.groupValues[3].toLongOrNull() ?: 0L
                val status = match.groupValues[4]
                activeDomains[id] = ActiveDomainRecord(domain, time, status)
            }
        } catch (e: Exception) {
            StreamHubLogger.w("DomainResolver", "Could not load domain cache: ${e.message}")
        }
    }

    private fun saveCache() {
        try {
            val file = cacheFile ?: return
            val parent = file.parentFile
            if (parent != null && !parent.exists()) parent.mkdirs()
            val sb = StringBuilder()
            sb.append("{\n")
            val entries = activeDomains.entries.toList()
            for (i in entries.indices) {
                val entry = entries[i]
                val comma = if (i < entries.size - 1) "," else ""
                sb.append("""  "${entry.key}": { "domain": "${entry.value.domain}", "lastVerifiedMs": ${entry.value.lastVerifiedMs}, "status": "${entry.value.status}" }$comma""").append("\n")
            }
            sb.append("}\n")
            file.writeText(sb.toString(), Charsets.UTF_8)
        } catch (e: Exception) {
            StreamHubLogger.w("DomainResolver", "Could not save domain cache: ${e.message}")
        }
    }
}
