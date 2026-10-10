package com.thedesitadka.app.data

import android.content.Context
import com.thedesitadka.app.BuildConfig
import com.thedesitadka.app.storage.PreferenceStore
import com.thedesitadka.core.config.ConfigRepository
import com.thedesitadka.core.model.AdminNotice
import com.thedesitadka.core.security.StreamHubLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Repository responsible for managing dynamic Admin Notices and broadcasts.
 * Admins can publish notices to GitHub at any time without recompiling or signing APKs.
 * Users see notices as popups which can be dismissed.
 */
class AdminNoticeRepository(
    private val context: Context,
    private val preferenceStore: PreferenceStore,
    private val configRepository: ConfigRepository
) {
    companion object {
        const val DEFAULT_ADMIN_NOTICE_URL =
            "https://raw.githubusercontent.com/DBiswas57/TheDesiTadka/main/config-tools/admin-notice.json"
        private const val TAG = "AdminNoticeRepository"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    private val _activeNoticeFlow = MutableStateFlow<AdminNotice?>(null)
    val activeNoticeFlow: StateFlow<AdminNotice?> = _activeNoticeFlow.asStateFlow()

    /**
     * Checks for new or pending admin notices from remote endpoint or fallback manifest.
     */
    suspend fun checkNotice(): AdminNotice? = withContext(Dispatchers.IO) {
        try {
            var candidateNotice: AdminNotice? = null

            // 1. Fetch live notice JSON directly from remote endpoint (no app compile needed)
            try {
                val request = Request.Builder()
                    .url(DEFAULT_ADMIN_NOTICE_URL)
                    .header("Cache-Control", "no-cache, no-store")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrBlank()) {
                            candidateNotice = json.decodeFromString<AdminNotice>(body)
                        }
                    }
                }
            } catch (e: Exception) {
                StreamHubLogger.w(TAG, "Direct admin notice fetch failed: ${e.message}. Falling back to manifest.")
            }

            // 2. Fall back to manifest's adminNotice if remote direct fetch was empty
            if (candidateNotice == null) {
                candidateNotice = configRepository.manifestFlow.value.adminNotice
            }

            if (candidateNotice == null || !candidateNotice!!.enabled) {
                _activeNoticeFlow.value = null
                return@withContext null
            }

            val notice = candidateNotice!!

            // 3. Version criteria check
            val currentVersionCode = BuildConfig.VERSION_CODE
            if (currentVersionCode < notice.minAppVersion || currentVersionCode > notice.maxAppVersion) {
                StreamHubLogger.d(TAG, "Notice ${notice.id} version criteria not met ($currentVersionCode not in ${notice.minAppVersion}..${notice.maxAppVersion})")
                _activeNoticeFlow.value = null
                return@withContext null
            }

            // 4. Check if notice was already dismissed by this user
            if (notice.showOnce) {
                val lastDismissedId = preferenceStore.getLastDismissedNoticeId()
                if (lastDismissedId == notice.id && notice.id.isNotBlank()) {
                    StreamHubLogger.d(TAG, "Notice ${notice.id} has already been dismissed by user.")
                    _activeNoticeFlow.value = null
                    return@withContext null
                }
            }

            StreamHubLogger.i(TAG, "Active admin notice to display: ${notice.title} (id: ${notice.id})")
            _activeNoticeFlow.value = notice
            notice
        } catch (e: Exception) {
            StreamHubLogger.e(TAG, "Error checking admin notice: ${e.message}")
            _activeNoticeFlow.value = null
            null
        }
    }

    /**
     * Dismiss the current notice. Records notice ID so that it won't show again if showOnce is true.
     */
    suspend fun dismissNotice(notice: AdminNotice) = withContext(Dispatchers.IO) {
        try {
            if (notice.id.isNotBlank()) {
                preferenceStore.setLastDismissedNoticeId(notice.id)
            }
            _activeNoticeFlow.value = null
        } catch (e: Exception) {
            StreamHubLogger.e(TAG, "Error dismissing notice: ${e.message}")
            _activeNoticeFlow.value = null
        }
    }
}
