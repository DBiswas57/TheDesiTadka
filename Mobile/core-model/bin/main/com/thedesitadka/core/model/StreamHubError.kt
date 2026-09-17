package com.thedesitadka.core.model

sealed class StreamHubError(override val message: String, override val cause: Throwable? = null) : Exception(message, cause) {
    data class NetworkError(val code: Int = 0, override val message: String, override val cause: Throwable? = null) : StreamHubError(message, cause)
    data class ConfigurationError(override val message: String, override val cause: Throwable? = null) : StreamHubError(message, cause)
    data class ProviderUnavailable(val providerId: String, override val message: String) : StreamHubError(message)
    data class ParsingError(val target: String, override val message: String, override val cause: Throwable? = null) : StreamHubError(message, cause)
    data class PlaybackError(val code: Int = 0, override val message: String, override val cause: Throwable? = null) : StreamHubError(message, cause)
    data class DownloadError(val code: Int = 0, override val message: String, override val cause: Throwable? = null) : StreamHubError(message, cause)
    data class SecurityError(val reason: String, override val message: String) : StreamHubError(message)
}
