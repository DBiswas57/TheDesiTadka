package com.thedesitadka.provider.plugins

import com.thedesitadka.core.model.MediaSource

/**
 * Interface for external video hosting resolver plugins.
 * Resolves third-party video iframe embeds (e.g. vixeo.io) into direct playable and downloadable media sources.
 */
interface HostResolverPlugin {
    val id: String
    val name: String
    val supportedDomains: List<String>

    fun canHandle(url: String): Boolean

    suspend fun resolve(embedUrl: String, parentUrl: String? = null): Result<MediaSource>

    fun resolveFromHtml(html: String, embedUrl: String, parentUrl: String? = null): Result<MediaSource>? = null
}

