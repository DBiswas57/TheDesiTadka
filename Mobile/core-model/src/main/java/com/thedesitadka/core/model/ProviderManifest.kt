package com.thedesitadka.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ProviderManifest(
    val schemaVersion: Int = 1,
    val configVersion: Int = 1,
    val generatedAt: Long = 0L,
    val expiresAt: Long = Long.MAX_VALUE,
    val minimumAppVersion: Int = 1,
    val recommendedAppVersion: Int = 1,
    val forceUpdate: Boolean = false,
    val maintenanceMode: Boolean = false,
    val providers: List<ProviderConfig> = emptyList()
)

@Serializable
data class ProviderConfig(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    val baseUrl: String,
    val adapter: String, // "html_selector", "wordpress_rest", "rss", "json_api", "embedded_player"
    val capabilities: List<ProviderCapability> = emptyList(),
    val icon: String? = null,
    val description: String = "",
    val navigation: NavigationConfig? = null,
    val selectors: SelectorConfig? = null,
    val apiConfig: ApiConfig? = null,
    val contentPolicy: ContentPolicy? = null,
    val domains: List<String> = emptyList(),
    val familyId: String? = null,
    val validationMarker: String? = null
)

@Serializable
data class NavigationConfig(
    val home: String = "/",
    val search: String = "/?s={query}",
    val page: String = "/page/{page}/",
    val categories: String? = null,
    val categoryPage: String? = null
)

@Serializable
data class SelectorConfig(
    // Feed item selectors
    val item: String = "article, div.item, div.post",
    val title: String = "h2 a, .title, a",
    val thumbnail: String = "img",
    val thumbnailAttr: String = "src", // e.g. "src", "data-src"
    val detailUrl: String = "a",
    val duration: String? = null,
    
    // Details page selectors
    val detailTitle: String = "h1",
    val detailDescription: String? = ".description, .content, article p",
    val detailThumbnail: String? = ".featured img, meta[property='og:image']",
    val player: String? = "video, iframe, .player",
    val videoSource: String? = "video source[src], source[type='video/mp4']",
    val videoSourceAttr: String = "src",
    val relatedItems: String? = ".related-videos article, .related article"
)

@Serializable
data class ApiConfig(
    val postsEndpoint: String = "/wp-json/wp/v2/posts",
    val searchEndpoint: String = "/wp-json/wp/v2/posts?search={query}&page={page}",
    val categoriesEndpoint: String? = "/wp-json/wp/v2/categories",
    val customHeaders: Map<String, String> = emptyMap()
)

@Serializable
data class SignedPayload(
    val payload: String, // Canonical JSON string of ProviderManifest
    val signature: String, // Base64-encoded Ed25519 signature
    val keyId: String = "primary-v1"
)
