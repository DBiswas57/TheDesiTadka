package com.thedesitadka.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class ProviderCapability {
    HOME,
    CATEGORY,
    SEARCH,
    DETAILS,
    STREAM,
    DOWNLOAD,
    RELATED
}

@Serializable
enum class ProviderStatus {
    ENABLED,
    DISABLED,
    DEGRADED,
    UNAVAILABLE,
    CONFIG_ERROR,
    NETWORK_ERROR,
    PARSER_ERROR
}
