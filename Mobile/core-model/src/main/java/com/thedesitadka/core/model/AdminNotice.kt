package com.thedesitadka.core.model

import kotlinx.serialization.Serializable

/**
 * Model representing an administrative notice or update broadcast from the admin.
 * Admins can post notices at any time with title, message, optional action button,
 * and dismissal settings.
 */
@Serializable
data class AdminNotice(
    val id: String = "",
    val title: String = "Notice from Admin",
    val message: String = "",
    val date: String? = null,
    val actionText: String? = null,
    val actionUrl: String? = null,
    val dismissible: Boolean = true,
    val showOnce: Boolean = true,
    val enabled: Boolean = true,
    val minAppVersion: Int = 0,
    val maxAppVersion: Int = Int.MAX_VALUE
)
