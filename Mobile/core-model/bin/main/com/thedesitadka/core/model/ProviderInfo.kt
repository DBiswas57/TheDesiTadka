package com.thedesitadka.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ProviderInfo(
    val id: String,
    val name: String,
    val icon: String? = null,
    val description: String = "",
    val enabled: Boolean = true,
    val capabilities: List<ProviderCapability> = emptyList(),
    val baseUrl: String,
    val configVersion: Int = 1,
    val status: ProviderStatus = ProviderStatus.ENABLED,
    val contentPolicy: ContentPolicy? = null
)

@Serializable
data class ContentPolicy(
    val rightsStatus: String = "Publicly Available / Fair Use Aggregation",
    val providerTermsUrl: String? = null,
    val privacyPolicyUrl: String? = null,
    val disclaimer: String? = null
)
