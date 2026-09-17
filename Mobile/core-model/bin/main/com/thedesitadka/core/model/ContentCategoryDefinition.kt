package com.thedesitadka.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ContentCategoryDefinition(
    val id: String,
    val name: String,
    val description: String = "",
    val icon: String? = null,
    val sortOrder: Int = 0,
    val enabled: Boolean = true,
    val providerIds: List<String> = emptyList()
) {
    companion object {
        val DEFAULT_CATEGORIES = listOf(
            ContentCategoryDefinition(
                id = "all",
                name = "All",
                description = "All available aggregated content",
                sortOrder = 0,
                enabled = true
            ),
            ContentCategoryDefinition(
                id = "free",
                name = "Free Streaming",
                description = "Open and publicly indexed media",
                sortOrder = 1,
                enabled = true
            ),
            ContentCategoryDefinition(
                id = "featured",
                name = "Featured",
                description = "Top curated selections",
                sortOrder = 2,
                enabled = true
            ),
            ContentCategoryDefinition(
                id = "premium",
                name = "Premium Catalog",
                description = "Authorized preview and premium catalog listings",
                sortOrder = 3,
                enabled = true
            )
        )
    }
}
