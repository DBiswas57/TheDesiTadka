package com.thedesitadka.core.model

import kotlinx.serialization.Serializable

// =========================================================================
// SECTION 14: ALTERNATIVE MEDIA RESOLVER (FUTURE EXTENSION BOUNDARY)
// =========================================================================

interface AlternativeMediaResolver {
    suspend fun resolveAlternativeStream(contentId: String, metadata: Map<String, String>): Result<MediaSource?>
}

class NoOpAlternativeMediaResolver : AlternativeMediaResolver {
    override suspend fun resolveAlternativeStream(contentId: String, metadata: Map<String, String>): Result<MediaSource?> {
        // Safe initial boundary: No-op fallback
        return Result.success(null)
    }
}

// =========================================================================
// SECTION 15: TORRENT CORE ARCHITECTURE BOUNDARY (INTERFACE ONLY)
// =========================================================================

@Serializable
data class TorrentMetadata(
    val infoHash: String,
    val name: String,
    val sizeBytes: Long,
    val fileCount: Int = 1,
    val magnetUri: String
)

@Serializable
data class MagnetReference(
    val uri: String,
    val dn: String? = null,
    val xt: String? = null
)

interface TorrentSearchProvider {
    val providerId: String
    suspend fun search(query: String): Result<List<TorrentMetadata>>
}

class UnsupportedTorrentSearchProvider : TorrentSearchProvider {
    override val providerId: String = "unsupported"
    override suspend fun search(query: String): Result<List<TorrentMetadata>> {
        return Result.failure(UnsupportedOperationException("Torrent searching is not supported in this build."))
    }
}

// =========================================================================
// SECTION 16: SEEDR INTEGRATION BOUNDARY (INTERFACE ONLY)
// =========================================================================

@Serializable
data class SeedrTransfer(
    val id: String,
    val name: String,
    val progress: Float,
    val sizeBytes: Long,
    val status: String
)

@Serializable
data class SeedrFile(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val downloadUrl: String? = null
)

interface SeedrClient {
    suspend fun authenticate(apiKey: String): Result<Boolean>
    suspend fun getTransfers(): Result<List<SeedrTransfer>>
    suspend fun getFiles(folderId: String? = null): Result<List<SeedrFile>>
}

class MockSeedrClient : SeedrClient {
    override suspend fun authenticate(apiKey: String): Result<Boolean> = Result.success(false)
    override suspend fun getTransfers(): Result<List<SeedrTransfer>> = Result.success(emptyList())
    override suspend fun getFiles(folderId: String?): Result<List<SeedrFile>> = Result.success(emptyList())
}

// =========================================================================
// SECTION 17: PROXY CORE ARCHITECTURE BOUNDARY
// =========================================================================

enum class ProxyType {
    HTTP, SOCKS4, SOCKS5, DIRECT
}

enum class ProxyStatus {
    UNKNOWN, TESTING, ACTIVE, FAILED, EXPIRED
}

@Serializable
data class ProxyConfig(
    val host: String,
    val port: Int,
    val type: ProxyType = ProxyType.HTTP,
    val username: String? = null,
    val password: String? = null
)

@Serializable
data class ProxyHealth(
    val latencyMs: Long,
    val status: ProxyStatus,
    val lastTestedAt: Long
)

interface ProxyProvider {
    suspend fun getCandidateProxies(): List<ProxyConfig>
}

interface ProxyHealthChecker {
    suspend fun checkHealth(proxy: ProxyConfig): ProxyHealth
}

interface ProxySelector {
    fun selectBestProxy(candidates: List<Pair<ProxyConfig, ProxyHealth>>): ProxyConfig?
}
