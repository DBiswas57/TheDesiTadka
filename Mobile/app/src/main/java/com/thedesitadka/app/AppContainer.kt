package com.thedesitadka.app

import android.content.Context
import com.thedesitadka.app.data.AdminNoticeRepository
import com.thedesitadka.app.data.DashboardRepository
import com.thedesitadka.app.download.DownloadRepository
import com.thedesitadka.app.storage.AppDatabase
import com.thedesitadka.app.storage.PreferenceStore
import com.thedesitadka.core.config.ConfigCache
import com.thedesitadka.core.config.ConfigRepository
import com.thedesitadka.core.model.AdminNotice
import com.thedesitadka.core.model.ContentPolicy
import com.thedesitadka.core.model.NavigationConfig
import com.thedesitadka.core.model.ProviderCapability
import com.thedesitadka.core.model.ProviderConfig
import com.thedesitadka.core.model.ProviderManifest
import com.thedesitadka.app.monetization.AdConfigRepository
import com.thedesitadka.app.monetization.MonetizationManager
import com.thedesitadka.app.security.AndroidApkIntegrityChecker
import com.thedesitadka.core.config.DomainResolver
import com.thedesitadka.core.model.SelectorConfig
import com.thedesitadka.provider.ProviderEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.thedesitadka.core.config.ConfigValidator
import com.thedesitadka.core.security.StreamHubLogger
import java.io.File

class AppContainer(val context: Context) {

    val database: AppDatabase by lazy { AppDatabase.getInstance(context) }
    val preferenceStore: PreferenceStore by lazy { PreferenceStore(context) }
    val domainResolver: DomainResolver by lazy { DomainResolver(context.filesDir) }
    val providerEngine: ProviderEngine by lazy { ProviderEngine(domainResolver = domainResolver) }

    val configCache: ConfigCache by lazy {
        ConfigCache(File(context.filesDir, "config_cache"))
    }

    val configRepository: ConfigRepository by lazy {
        ConfigRepository(configCache = configCache, appVersion = BuildConfig.VERSION_CODE)
    }

    val downloadRepository: DownloadRepository by lazy {
        DownloadRepository(context, providerEngine, preferenceStore)
    }

    val dashboardRepository: DashboardRepository by lazy {
        DashboardRepository(providerEngine, preferenceStore)
    }

    val adConfigRepository: AdConfigRepository by lazy {
        AdConfigRepository(context)
    }

    val adminNoticeRepository: AdminNoticeRepository by lazy {
        AdminNoticeRepository(context, preferenceStore, configRepository)
    }

    val monetizationManager: MonetizationManager by lazy {
        MonetizationManager(context, adConfigRepository)
    }

    val integrityReport by lazy {
        AndroidApkIntegrityChecker.checkAppIntegrity(context)
    }

    init {
        activateConfigurationIfValid()

        CoroutineScope(Dispatchers.IO).launch {
            monetizationManager.initialize()
        }
    }

    /**
     * Layered security & compatibility check:
     * 1. Verifies APK signing identity and environment integrity
     * 2. Verifies installedAppVersion >= minimumSupportedVersion
     * 3. Validates configuration schema and security rules
     *
     * If validation fails: DO NOT ACTIVATE PROVIDERS, DO NOT LOAD CONTENT.
     * Returns true if activated, false if rejected.
     */
    fun activateConfigurationIfValid(): Boolean {
        // 1. Layered Validation: Verify app signing identity & integrity
        val integrity = AndroidApkIntegrityChecker.checkAppIntegrity(context)
        if (!integrity.isTrusted) {
            StreamHubLogger.e("AppContainer", "Integrity check rejected: ${integrity.details}. Safe restricted mode.")
            return false
        }

        // 2. Validate configuration against installed app version & release tracking
        val defaultManifest = getDefaultManifest()
        val releasePrefs = context.getSharedPreferences("app_release_state", android.content.Context.MODE_PRIVATE)
        val lastSeenVersionCode = releasePrefs.getInt("last_seen_version_code", -1)
        val lastSeenConfigVersion = releasePrefs.getInt("last_seen_config_version", -1)
        val currentVersionCode = BuildConfig.VERSION_CODE
        val isNewRelease = lastSeenVersionCode != currentVersionCode || lastSeenConfigVersion < defaultManifest.configVersion

        if (isNewRelease) {
            StreamHubLogger.i("AppContainer", "New release detected (app: $lastSeenVersionCode -> $currentVersionCode, config: $lastSeenConfigVersion -> ${defaultManifest.configVersion}). Purging all stale caches.")
            configCache.clear()
            com.thedesitadka.app.storage.NavigationStateStore.clearAll()
            com.thedesitadka.core.network.CloudflareHtmlCache.clear()
            try { dashboardRepository.clearCache() } catch (_: Exception) {}
            releasePrefs.edit()
                .putInt("last_seen_version_code", currentVersionCode)
                .putInt("last_seen_config_version", defaultManifest.configVersion)
                .apply()
        }

        val current = if (isNewRelease) null else configCache.loadCurrent()
        val candidate = if (current == null || current.configVersion < defaultManifest.configVersion || current.providers.size < defaultManifest.providers.size) {
            defaultManifest
        } else {
            current
        }

        return try {
            ConfigValidator.validate(
                manifest = candidate,
                currentVersion = 0,
                currentAppVersion = BuildConfig.VERSION_CODE
            )
            configCache.saveValidatedConfig(candidate, markAsLastKnownGood = true)
            configRepository.updateManifest(candidate)
            providerEngine.updateFromManifest(candidate)
            StreamHubLogger.i("AppContainer", "Configuration v${candidate.configVersion} activated for app version ${BuildConfig.VERSION_CODE}")
            true
        } catch (e: Exception) {
            StreamHubLogger.w("AppContainer", "Configuration rejected for app version ${BuildConfig.VERSION_CODE}: ${e.message}")
            // DO NOT ACTIVATE PROVIDERS - leave providerEngine empty
            false
        }
    }

    fun getDefaultManifest(): ProviderManifest = createDefaultManifest()

    companion object {
        fun createDefaultManifest(): ProviderManifest {
        return ProviderManifest(
            schemaVersion = 1,
            configVersion = 174,
            minimumAppVersion = 4,
            forceUpdate = true,
            generatedAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + (365L * 24 * 3600 * 1000),
            adminNotice = AdminNotice(
                id = "notice_2026_10_08_swipe_downloads",
                title = "Welcome to TheDesiTadka Update!",
                message = "We have added exciting new features:\n\n• Swipe Gestures: Easily swipe left or right on the video page and player to jump to the next or previous video.\n• Easy Downloads: Tap the new Downloads icon or status bar on video details to monitor active downloads in real time and return seamlessly.\n• Admin Notice System: Stay informed on regular server updates and new features.",
                date = "2026-10-08",
                dismissible = true,
                showOnce = true,
                enabled = true
            ),
            providers = listOf(
                // 1. KamaBaba
                ProviderConfig(
                    id = "kamababa1",
                    familyId = "clean_tube_family",
                    domains = listOf("https://www.mykamababa.com", "https://mykamababa.com", "https://www.kamababa1.com", "https://kamababa1.com"),
                    validationMarker = "kamababa",
                    name = "KamaBaba",
                    enabled = true,
                    baseUrl = "https://www.mykamababa.com",
                    adapter = "html_selector",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "/?filter=latest",
                        search = "/?s={query}",
                        page = "/page/{page}/?filter=latest",
                        categories = "/categories/"
                    ),
                    selectors = SelectorConfig(
                        item = "article.thumb-block, article.video-preview-item, article",
                        title = "a[title], h2.entry-title a, h2 a",
                        thumbnail = "img.video-main-thumb, img",
                        thumbnailAttr = "src",
                        detailUrl = "a.video-preview-link, a[href*='kamababa'], a",
                        duration = ".duration",
                        detailTitle = "h1",
                        detailDescription = ".entry-content p, .video-details, p",
                        detailThumbnail = "meta[property='og:image'], img.video-main-thumb",
                        player = "iframe[src*='player']",
                        videoSource = "meta[itemprop*='contentUrl'], meta[itemprop*='contentURL'], iframe[src*='player-x'], video source[src], video[src], source[type='video/mp4']",
                        videoSourceAttr = "src"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Aggregation Index",
                        disclaimer = "Aggregated public reference media feed."
                    )
                ),
                // 2. Masa49
                ProviderConfig(
                    id = "masa49",
                    familyId = "masa_network_family",
                    domains = listOf("https://www.masa49.nl", "https://masa49.nl"),
                    validationMarker = "masa",
                    name = "Masa49",
                    enabled = true,
                    baseUrl = "https://www.masa49.nl",
                    adapter = "html_selector",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "/",
                        search = "/?s={query}",
                        page = "/page/{page}/"
                    ),
                    selectors = SelectorConfig(
                        item = "div.video-block, .video-block, article",
                        title = "span.title, a.infos, a[title], h2",
                        thumbnail = "img.video-img, img",
                        thumbnailAttr = "data-src",
                        detailUrl = "a.thumb, a.infos, a",
                        duration = ".duration, .badge-duration",
                        detailTitle = "h1",
                        detailDescription = ".video-description, .desc, .entry-content p",
                        detailThumbnail = "video[poster], meta[property='og:image'], img.video-img, img",
                        player = "video, iframe",
                        videoSource = "video source[src], source[type='video/mp4'], video[src]",
                        videoSourceAttr = "src",
                        relatedItems = "div.video-block, .video-block"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Aggregation Index",
                        disclaimer = "Aggregated public reference feed."
                    )
                ),
                // 3. FSIBlog
                ProviderConfig(
                    id = "fsiblogxx",
                    familyId = "clean_tube_family",
                    domains = listOf("https://www.fsiblogxx.com", "https://fsiblogxx.com"),
                    validationMarker = "fsiblog",
                    name = "FSIBlog",
                    enabled = true,
                    baseUrl = "https://www.fsiblogxx.com",
                    adapter = "html_selector",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM
                    ),
                    navigation = NavigationConfig(
                        home = "/",
                        search = "/?s={query}",
                        page = "/page/{page}/"
                    ),
                    selectors = SelectorConfig(
                        item = "article.type-porn-video, article.porn-video, div.porn-video, article",
                        title = "h2.entry-title a, h2 a, a.entry-title, a",
                        thumbnail = "img.attachment-medium, img",
                        thumbnailAttr = "data-src",
                        detailUrl = "h2.entry-title a, h2 a, a",
                        duration = ".duration, .time",
                        detailTitle = "h1.entry-title, h1",
                        detailDescription = ".entry-content p, .post-content",
                        detailThumbnail = "meta[property='og:image'], img",
                        player = "iframe[src*='player'], video",
                        videoSource = "video source[src], video[src]",
                        videoSourceAttr = "src"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Web Index",
                        disclaimer = "Aggregated public media feed."
                    )
                ),
                // 4. MasaHub2
                ProviderConfig(
                    id = "masahub2",
                    familyId = "masa_network_family",
                    domains = listOf("https://masahub2.com"),
                    validationMarker = "masahub",
                    name = "MasaHub2",
                    enabled = true,
                    baseUrl = "https://masahub2.com",
                    adapter = "html_selector",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM
                    ),
                    navigation = NavigationConfig(
                        home = "/",
                        search = "/?s={query}",
                        page = "/page/{page}/"
                    ),
                    selectors = SelectorConfig(
                        item = "article.vcard, article, div.item",
                        title = "h3.vtitle, .vtitle, h2 a, a.vtitle, h2",
                        thumbnail = ".thumb, img",
                        thumbnailAttr = "style",
                        detailUrl = "a",
                        duration = "span.dur, .badge-duration",
                        detailTitle = "h1",
                        detailDescription = ".entry-content p, .video-details",
                        detailThumbnail = "video[poster], meta[property='og:image'], img",
                        player = "video, iframe",
                        videoSource = "video source[src], source[type='video/mp4'], video[src]",
                        videoSourceAttr = "src"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Web Index",
                        disclaimer = "Aggregated public media feed."
                    )
                ),
                // 5. AagMaal (AagMaal.Webcam / AagMaal.Dates / AagMaal.Date / AagMaal.Run - WordPress Tube)
                ProviderConfig(
                    id = "aagmaal",
                    familyId = "aagmaal_date_family",
                    domains = listOf("https://aagmaal.webcam", "https://aagmaal.dates", "https://aagmaal.date", "https://aagmaal.run"),
                    validationMarker = "aagmaal",
                    name = "AagMaal",
                    enabled = true,
                    baseUrl = "https://aagmaal.webcam",
                    adapter = "html_selector",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "/?filter=latest",
                        search = "/?s={query}",
                        page = "/page/{page}/?filter=latest",
                        categories = "/categories/"
                    ),
                    selectors = SelectorConfig(
                        item = "article.thumb-block, article.loop-video, article.post, article",
                        title = "header.entry-header a, h2.entry-title a, h2 a, a[title], .cat-title",
                        thumbnail = "img[data-src], img[data-lazy-src], img[data-original], img[data-webp], img[src], img",
                        thumbnailAttr = "data-src, data-lazy-src, data-original, data-webp, src",
                        detailUrl = "header.entry-header a, a.thumb-pop, h2.entry-title a, a[title], a",
                        duration = ".duration, span.hd, .thumb-block span",
                        detailTitle = "h1.entry-title, h1",
                        detailDescription = ".entry-content p, .video-details",
                        detailThumbnail = "meta[property='og:image'], img",
                        player = "iframe[src*='tube279'], iframe[src*='streamtape'], iframe[src*='lulu'], iframe[src*='/e/'], a[href*='tube279'], video",
                        videoSource = "video source[src], video[src], source[type='video/mp4']",
                        videoSourceAttr = "src",
                        relatedItems = ".related-posts article, .related article, .videos-list article"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Web Index",
                        disclaimer = "Aggregated public media feed."
                    )
                ),
                // 6. Fry99 (Cloudflare Protected - Solvable via in-app verification)
                ProviderConfig(
                    id = "fry99",
                    familyId = "fry99_family",
                    domains = listOf("https://fry99.cc"),
                    validationMarker = "fry99",
                    name = "Fry99",
                    enabled = true,
                    baseUrl = "https://fry99.cc",
                    adapter = "html_selector",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "/",
                        search = "/?s={query}",
                        page = "/page/{page}/"
                    ),
                    selectors = SelectorConfig(
                        item = "li.video, .video, article, .post",
                        title = "a.title, a.thumb, h2 a, h2, a",
                        thumbnail = "img",
                        thumbnailAttr = "src",
                        detailUrl = "a.title, a.thumb, a",
                        duration = ".video-duration, .duration, .time",
                        detailTitle = "h1.entry-title, h1",
                        detailDescription = ".video_description, .entry-content p, .entry-content",
                        detailThumbnail = "video[poster], meta[property='og:image']",
                        player = "video, iframe",
                        videoSource = "video source[src], video[src], a[href*='.mp4'], iframe[src]",
                        videoSourceAttr = "src",
                        relatedItems = ".releated li.video, .video_list li.video"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Aggregation Index",
                        disclaimer = "Protected by Cloudflare Turnstile. Interactive in-app verification supported."
                    )
                ),
                // 7. HitMaal (Desi Tadka / Web Series)
                ProviderConfig(
                    id = "hitmaal",
                    familyId = "hitmaal_family",
                    domains = listOf("https://hitmaal.io"),
                    validationMarker = "hitmaal",
                    name = "HitMaal",
                    enabled = true,
                    baseUrl = "https://hitmaal.io",
                    adapter = "html_selector",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "/",
                        search = "/?s={query}",
                        page = "/page/{page}/"
                    ),
                    selectors = SelectorConfig(
                        item = "a.video",
                        title = "h2.vtitle, a[title]",
                        thumbnail = "a.video",
                        thumbnailAttr = "data-bg",
                        detailUrl = "a.video",
                        duration = "span.time",
                        detailTitle = "h1",
                        detailDescription = ".entry-content p, .video-details, p",
                        detailThumbnail = "video[poster], meta[property='og:image']",
                        player = "video, iframe",
                        videoSource = "video source[src], video[src]",
                        videoSourceAttr = "src",
                        relatedItems = "a.video"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Aggregation Index",
                        disclaimer = "Aggregated public media feed."
                    )
                ),
                // 8. WebXSeries
                ProviderConfig(
                    id = "webxseries",
                    familyId = "webxseries_family",
                    domains = listOf("https://webxseries.hot"),
                    validationMarker = "webxseries",
                    name = "WebXSeries",
                    enabled = true,
                    baseUrl = "https://webxseries.hot",
                    adapter = "html_selector",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "/",
                        search = "/?s={query}",
                        page = "/page/{page}/",
                        categories = "/hot-web-series/"
                    ),
                    selectors = SelectorConfig(
                        item = "a.video",
                        title = "h2.vtitle, a[title]",
                        thumbnail = "a.video",
                        thumbnailAttr = "data-bg",
                        detailUrl = "a.video",
                        duration = "span.time",
                        detailTitle = "h1",
                        detailDescription = ".entry-content p, .video-details, p",
                        detailThumbnail = "video[poster], meta[property='og:image']",
                        player = "video",
                        videoSource = "video source[src], video[src]",
                        videoSourceAttr = "src",
                        relatedItems = "a.video"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Web Index",
                        disclaimer = "Aggregated public media feed."
                    )
                ),
                // 9. DesiBF
                ProviderConfig(
                    id = "desibf",
                    familyId = "direct_cdn_family",
                    domains = listOf("https://desibf.com"),
                    validationMarker = "desibf",
                    name = "DesiBF",
                    enabled = true,
                    baseUrl = "https://desibf.com",
                    adapter = "html_selector",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "/",
                        search = "/?s={query}",
                        page = "/page/{page}/"
                    ),
                    selectors = SelectorConfig(
                        item = "article, div.item, div.post",
                        title = "h2.entry-title a, h2 a, a[title]",
                        thumbnail = "img",
                        thumbnailAttr = "src",
                        detailUrl = "h2.entry-title a, h2 a, a",
                        duration = ".duration",
                        detailTitle = "h1",
                        detailDescription = ".entry-content p, .video-details",
                        detailThumbnail = "meta[property='og:image'], img",
                        player = "iframe, video",
                        videoSource = "video source[src], video[src], a[href*='.mp4'], iframe[src]",
                        videoSourceAttr = "src",
                        relatedItems = "article"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Web Index",
                        disclaimer = "Aggregated public media feed."
                    )
                ),
                // 10. AntarvasnaBF
                ProviderConfig(
                    id = "antarvasnabf",
                    familyId = "clean_tube_family",
                    domains = listOf("https://antarvasnabf.com"),
                    validationMarker = "antarvasnabf",
                    name = "AntarvasnaBF",
                    enabled = true,
                    baseUrl = "https://antarvasnabf.com",
                    adapter = "html_selector",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "/",
                        search = "/?s={query}",
                        page = "/page/{page}/"
                    ),
                    selectors = SelectorConfig(
                        item = "article, div.item, div.post",
                        title = "h2.entry-title a, h2 a, a[title]",
                        thumbnail = "img",
                        thumbnailAttr = "src",
                        detailUrl = "h2.entry-title a, h2 a, a",
                        duration = ".duration",
                        detailTitle = "h1",
                        detailDescription = ".entry-content p, .video-details",
                        detailThumbnail = "meta[property='og:image'], img",
                        player = "iframe, video",
                        videoSource = "meta[itemprop*='contentUrl' i], meta[itemprop*='contentURL'], video source[src], video[src], a[href*='.mp4'], iframe[src]",
                        videoSourceAttr = "src",
                        relatedItems = "article"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Web Index",
                        disclaimer = "Aggregated public media feed."
                    )
                ),
                // 11. DesiSex
                ProviderConfig(
                    id = "desisex",
                    familyId = "direct_cdn_family",
                    domains = listOf("https://desisex.site", "https://www.desisex.site"),
                    validationMarker = "desisex",
                    name = "DesiSex",
                    enabled = true,
                    baseUrl = "https://desisex.site",
                    adapter = "html_selector",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "/?filter=latest",
                        search = "/?s={query}",
                        page = "/page/{page}/?filter=latest"
                    ),
                    selectors = SelectorConfig(
                        item = "article, div.item, div.post",
                        title = "h2.entry-title a, h2 a, a[title]",
                        thumbnail = "video[poster], img",
                        thumbnailAttr = "poster",
                        detailUrl = "h2.entry-title a, h2 a, a",
                        duration = ".duration",
                        detailTitle = "h1",
                        detailDescription = ".entry-content p, .video-details",
                        detailThumbnail = "meta[property='og:image'], img",
                        player = "iframe, video",
                        videoSource = "meta[itemprop='contentURL'], meta[itemprop='contentUrl'], video source[src], video[src], source[type='video/mp4']",
                        videoSourceAttr = "src",
                        relatedItems = "article"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Web Index",
                        disclaimer = "Aggregated public media feed."
                    )
                ),
                // 12. IxiPorn
                ProviderConfig(
                    id = "ixiporn",
                    familyId = "clean_tube_family",
                    domains = listOf("https://ixiporn.live"),
                    validationMarker = "ixiporn",
                    name = "IxiPorn",
                    enabled = true,
                    baseUrl = "https://ixiporn.live",
                    adapter = "html_selector",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "/",
                        search = "/?s={query}",
                        page = "/page/{page}/"
                    ),
                    selectors = SelectorConfig(
                        item = "div.video-block, .video-block, article",
                        title = "span.title, a.infos, h2 a, a[title]",
                        thumbnail = "img.video-img, img",
                        thumbnailAttr = "data-src",
                        detailUrl = "a.thumb, a.infos, a",
                        duration = ".duration",
                        detailTitle = "h1",
                        detailDescription = ".video-description, .desc, .entry-content p",
                        detailThumbnail = "video[poster], meta[property='og:image'], img",
                        player = "video, iframe",
                        videoSource = "video source[src], video[src], iframe[src]",
                        videoSourceAttr = "src",
                        relatedItems = "div.video-block"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Web Index",
                        disclaimer = "Aggregated public media feed."
                    )
                ),
                // 13. MasaFun
                ProviderConfig(
                    id = "masafun",
                    familyId = "masa_network_family",
                    domains = listOf("https://masafun.art"),
                    validationMarker = "masafun",
                    name = "MasaFun",
                    enabled = true,
                    baseUrl = "https://masafun.art",
                    adapter = "html_selector",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "/",
                        search = "/?s={query}",
                        page = "/page/{page}/"
                    ),
                    selectors = SelectorConfig(
                        item = "article.vcard, article, div.item, div.post",
                        title = "h3.vtitle, h2 a, .video-title, a",
                        thumbnail = ".thumb, img",
                        thumbnailAttr = "style",
                        detailUrl = "a",
                        duration = "span.dur, .badge-duration, .duration",
                        detailTitle = "h1",
                        detailDescription = ".entry-content p, .video-details",
                        detailThumbnail = "video[poster], meta[property='og:image'], img",
                        player = "video, iframe",
                        videoSource = "video source[src], source[type='video/mp4'], video[src]",
                        videoSourceAttr = "src",
                        relatedItems = "article.vcard, article"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Web Index",
                        disclaimer = "Aggregated public media feed."
                    )
                ),
                // 14. UncutMaza
                ProviderConfig(
                    id = "uncutmaza",
                    familyId = "clean_tube_family",
                    domains = listOf("https://uncutmaza.movie", "https://uncutmaza.io", "https://uncutmaza.cc"),
                    validationMarker = "uncutmaza",
                    name = "UncutMaza",
                    enabled = true,
                    baseUrl = "https://uncutmaza.movie",
                    adapter = "html_selector",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "/",
                        search = "/?s={query}",
                        page = "/page/{page}/"
                    ),
                    selectors = SelectorConfig(
                        item = "div.videos a, article.thumb-block, article, div.post, div.item",
                        title = "h2.entry-title a, h2 a, a[title], div.title, .video-title",
                        thumbnail = "img[data-src], img[src], img",
                        thumbnailAttr = "data-src, src",
                        detailUrl = "this",
                        duration = "span.dur, .badge-duration, .duration",
                        detailTitle = "h1.entry-title, h1",
                        detailDescription = ".entry-content p, .video-details, p",
                        detailThumbnail = "meta[property='og:image'], img",
                        player = "video, iframe, div.xplayer",
                        videoSource = "video source[src], video[src], source[type='video/mp4'], meta[itemprop='contentUrl']",
                        videoSourceAttr = "src",
                        relatedItems = ".under-video-block article, article.thumb-block, article"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Web Index",
                        disclaimer = "Aggregated public media feed."
                    )
                ),
                // 15. WowUncut
                ProviderConfig(
                    id = "wowuncut",
                    familyId = "clean_tube_family",
                    domains = listOf("https://wowuncut.com"),
                    validationMarker = "wowuncut",
                    name = "WowUncut",
                    enabled = true,
                    baseUrl = "https://wowuncut.com",
                    adapter = "html_selector",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "/",
                        search = "/?s={query}",
                        page = "/page/{page}/"
                    ),
                    selectors = SelectorConfig(
                        item = "article.thumb-block, article.video-preview-item:not(.slide), article:not(.slide)",
                        title = "a[title], a[data-title], h2.entry-title a, h2 a",
                        thumbnail = "img.video-main-thumb, img",
                        thumbnailAttr = "src",
                        detailUrl = "a[title], a[href*='/video/'], a",
                        duration = ".duration",
                        detailTitle = "h1",
                        detailDescription = ".entry-content p, .video-details",
                        detailThumbnail = "meta[property='og:image'], img",
                        player = "iframe, video",
                        videoSource = "video source[src], video[src], iframe[src]",
                        videoSourceAttr = "src",
                        relatedItems = "article"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Web Index",
                        disclaimer = "Aggregated public media feed."
                    )
                ),
                // 17. DesiKahani2
                ProviderConfig(
                    id = "desikahani2",
                    name = "DesiKahani2",
                    enabled = true,
                    baseUrl = "https://desikahani2.net",
                    adapter = "html_selector",
                    familyId = "kvs_tube_family",
                    domains = listOf("https://desikahani2.net"),
                    validationMarker = "desikahani",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "/videos/",
                        search = "/videos/search/?q={query}",
                        page = "/videos/latest-updates/{page}/"
                    ),
                    selectors = SelectorConfig(
                        item = "div.item, .video-item, div[data-video-id], article",
                        title = "div.title a[title], strong.title, a.title, .video-title, a[title]",
                        thumbnail = "img.thumb, img",
                        thumbnailAttr = "data-webp",
                        detailUrl = "a",
                        duration = "span.duration, .duration",
                        detailTitle = "h1",
                        detailDescription = ".video-description, .entry-content p",
                        detailThumbnail = "meta[property='og:image'], img",
                        player = "video, iframe",
                        videoSource = "a[href*='/videos/get_file/'], video source[src], video[src]",
                        videoSourceAttr = "src",
                        relatedItems = "div.item, .video-item"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Web Index",
                        disclaimer = "Aggregated public media feed."
                    )
                ),
                // 18. DesiTales2
                ProviderConfig(
                    id = "desitales2",
                    name = "DesiTales2",
                    enabled = true,
                    baseUrl = "https://desitales2.com",
                    adapter = "html_selector",
                    familyId = "kvs_tube_family",
                    domains = listOf("https://desitales2.com"),
                    validationMarker = "desitales",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "/videos/",
                        search = "/videos/search/?q={query}",
                        page = "/videos/latest-updates/{page}/"
                    ),
                    selectors = SelectorConfig(
                        item = "div.item, .video-item, div[data-video-id], article",
                        title = "div.title a[title], strong.title, a.title, .video-title, a[title]",
                        thumbnail = "img.thumb, img",
                        thumbnailAttr = "data-webp",
                        detailUrl = "a",
                        duration = "span.duration, .duration",
                        detailTitle = "h1",
                        detailDescription = ".video-description, .entry-content p",
                        detailThumbnail = "meta[property='og:image'], img",
                        player = "video, iframe",
                        videoSource = "a[href*='/videos/get_file/'], video source[src], video[src]",
                        videoSourceAttr = "src",
                        relatedItems = "div.item, .video-item"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Web Index",
                        disclaimer = "Aggregated public media feed."
                    )
                ),
                // 19. IndianSexStories3
                ProviderConfig(
                    id = "indiansexstories3",
                    name = "IndianSexStories3",
                    enabled = true,
                    baseUrl = "https://www.indiansexstories3.com",
                    adapter = "html_selector",
                    familyId = "kvs_tube_family",
                    domains = listOf("https://www.indiansexstories3.com", "https://indiansexstories3.com"),
                    validationMarker = "indiansexstories",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "/videos/latest-updates/",
                        search = "/videos/search/?q={query}",
                        page = "/videos/latest-updates/{page}/",
                        categories = "/videos/categories/"
                    ),
                    selectors = SelectorConfig(
                        item = "div.thumb_rel.item, div.list-videos div.item, div.item",
                        title = "a[title], div.title, strong.title, a.title, .video-title",
                        thumbnail = "img",
                        thumbnailAttr = "data-webp",
                        detailUrl = "a",
                        duration = "span.duration, .duration, .time",
                        detailTitle = "h1.title, h1",
                        detailDescription = ".video-description, .entry-content p",
                        detailThumbnail = "meta[property='og:image'], img",
                        player = "video, iframe",
                        videoSource = "video source[src], video[src], a[href*='/videos/get_file/'][href*='.mp4']",
                        videoSourceAttr = "src",
                        relatedItems = "div.item, .video-item"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Web Index",
                        disclaimer = "Aggregated public media feed."
                    )
                ),
                // 20. XXXIndianStories
                ProviderConfig(
                    id = "xxxindianstories",
                    name = "XXXIndianStories",
                    enabled = true,
                    baseUrl = "https://xxxindianstories.com",
                    adapter = "html_selector",
                    familyId = "kvs_tube_family",
                    domains = listOf("https://xxxindianstories.com"),
                    validationMarker = "xxxindianstories",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "/videos/",
                        search = "/videos/search/?q={query}",
                        page = "/videos/latest-updates/{page}/"
                    ),
                    selectors = SelectorConfig(
                        item = "div.item, .video-item, div[data-video-id], article",
                        title = "div.title a[title], strong.title, a.title, .video-title, a[title]",
                        thumbnail = "img.thumb, img",
                        thumbnailAttr = "data-webp",
                        detailUrl = "a",
                        duration = "span.duration, .duration",
                        detailTitle = "h1",
                        detailDescription = ".video-description, .entry-content p",
                        detailThumbnail = "meta[property='og:image'], img",
                        player = "video, iframe",
                        videoSource = "a[href*='/videos/get_file/'], video source[src], video[src]",
                        videoSourceAttr = "src",
                        relatedItems = "div.item, .video-item"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Web Index",
                        disclaimer = "Aggregated public media feed."
                    )
                ),
                // 20. XMaza
                ProviderConfig(
                    id = "xmaza",
                    name = "XMaza",
                    enabled = true,
                    baseUrl = "https://xmaza.xxx",
                    adapter = "html_selector",
                    familyId = "direct_cdn_family",
                    domains = listOf("https://xmaza.xxx"),
                    validationMarker = "xmaza",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "/",
                        search = "/?s={query}",
                        page = "/page/{page}/"
                    ),
                    selectors = SelectorConfig(
                        item = "a.video, article, div.item, div.post",
                        title = "h2.vtitle, a[title], h2, a",
                        thumbnail = "a.video, img",
                        thumbnailAttr = "data-bg, style",
                        detailUrl = "a.video, a",
                        duration = ".duration",
                        detailTitle = "h1",
                        detailDescription = ".entry-content p",
                        detailThumbnail = "meta[property='og:image'], img",
                        player = "video, iframe",
                        videoSource = "video source[src], video[src], iframe[src]",
                        videoSourceAttr = "src"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Web Index",
                        disclaimer = "Aggregated public media feed."
                    )
                ),
                // 23. XNXX
                ProviderConfig(
                    id = "xnxx",
                    name = "XNXX",
                    enabled = true,
                    baseUrl = "https://www.xnxx.com",
                    adapter = "html_selector",
                    familyId = "xvideos_network_family",
                    domains = listOf("https://www.xnxx.com", "https://xnxx.health", "https://www.xnxx.es", "https://www.xnxx2.com"),
                    validationMarker = "xnxx",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "/todays-selection",
                        search = "/search/{query}/",
                        page = "/todays-selection/{page}",
                        categories = "/tags"
                    ),
                    selectors = SelectorConfig(
                        item = "div.thumb-block[id^='video_']",
                        title = ".thumb-under p a[title], .thumb-under a[title], p.title a, .title a, a[title]",
                        thumbnail = "img.thumb, img[data-src], img",
                        thumbnailAttr = "data-src, data-mzl, src",
                        detailUrl = ".thumb-inside a[href*='/video-'], a[href*='/video-'], p.title a, .thumb a, a[href*='/video']",
                        duration = ".duration, span.duration",
                        detailTitle = "h1.page-title, h1, .video-title",
                        detailDescription = ".video-description, .metadata, p",
                        detailThumbnail = "meta[property='og:image'], img.thumb, img",
                        player = "div#html5video, video, iframe",
                        videoSource = "html5player.setVideoHLS, meta[property='og:video:url'], video source[src]",
                        videoSourceAttr = "content, src",
                        relatedItems = "div.thumb-block[id^='video_']"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Web Index",
                        disclaimer = "Aggregated public media feed."
                    )
                ),
                // 24. XVideos
                ProviderConfig(
                    id = "xvideos",
                    name = "XVideos",
                    enabled = true,
                    baseUrl = "https://www.xvideos.com",
                    adapter = "html_selector",
                    familyId = "xvideos_network_family",
                    domains = listOf("https://www.xvideos.com", "https://www.xvideos3.com", "https://www.xvideos.es", "https://www.xvideos.red"),
                    validationMarker = "xvideos",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "/",
                        search = "/?k={query}",
                        page = "/new/{page}/",
                        categories = "/tags"
                    ),
                    selectors = SelectorConfig(
                        item = "div.thumb-block[id^='video_']",
                        title = ".thumb-under p a[title], .thumb-under a[title], p.title a, a[title]",
                        thumbnail = "img.thumb, img[data-src], img",
                        thumbnailAttr = "data-src, src",
                        detailUrl = ".thumb-inside a[href*='/video-'], a[href*='/video-'], a[href*='/video.'], p.title a, .thumb-under p a, a[href^='/video']",
                        duration = ".duration, span.duration",
                        detailTitle = "h1.page-title, h1",
                        detailDescription = ".video-description, .metadata, p",
                        detailThumbnail = "meta[property='og:image'], img.thumb, img",
                        player = "div#html5video, video, iframe",
                        videoSource = "html5player.setVideoHLS, meta[property='og:video:url'], video source[src]",
                        videoSourceAttr = "content, src"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Web Index",
                        disclaimer = "Aggregated public media feed."
                    )
                ),
                // 24. AagMaal.com (Independent Indian OTT & Web Series)
                ProviderConfig(
                    id = "aagmaal_com",
                    familyId = "aagmaal_com_family",
                    domains = listOf(
                        "https://aagmaal.com",
                        "https://govmaal.com",
                        "https://kambihub.com",
                        "https://cine350.com",
                        "https://aagmaal.bz",
                        "https://kaamuu.cfd",
                        "https://kaamuu.com",
                        "https://sohot.cyou",
                        "https://webmaal.info",
                        "https://www.aagmaal.help",
                        "https://webmaal.bz"
                    ),
                    validationMarker = "vp-card",
                    name = "AagMaal.com",
                    enabled = true,
                    baseUrl = "https://aagmaal.com",
                    adapter = "html_selector",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "/",
                        search = "/?s={query}",
                        page = "/page/{page}/",
                        categories = "/ott/"
                    ),
                    selectors = SelectorConfig(
                        item = "article.vp-card, article.post, article",
                        title = "h2.vp-card__title a, h2.entry-title a, h2 a, a[title]",
                        thumbnail = "a.vp-card__thumb img, img",
                        thumbnailAttr = "src",
                        detailUrl = "a.vp-card__thumb, h2.vp-card__title a, h2.entry-title a, a",
                        duration = ".duration, .vp-card__duration",
                        detailTitle = "h1.vp-single-hero__title, h1.entry-title, h1",
                        detailDescription = ".vp-single-content p, .entry-content p",
                        detailThumbnail = "meta[property='og:image'], img",
                        player = "iframe[src*='cdn1'], iframe[src*='lulu'], iframe[src*='streamtape'], iframe[src*='/e/'], a[href*='luluvdo'], a[href*='streamtape'], video",
                        videoSource = "video source[src], video[src], source[type='video/mp4']",
                        videoSourceAttr = "src",
                        relatedItems = ".vp-related article, .related article, article.vp-card"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Web Index",
                        disclaimer = "Aggregated public media feed."
                    )
                ),
                // 25. BMaal (18+ Hot Web Series & OTT Platforms)
                ProviderConfig(
                    id = "bmaal",
                    familyId = "web_series_family",
                    domains = listOf("https://bmaal.io", "https://bmaal.com"),
                    validationMarker = "bmaal",
                    name = "BMaal",
                    enabled = true,
                    baseUrl = "https://bmaal.io",
                    adapter = "html_selector",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "/",
                        search = "/?s={query}",
                        page = "/page/{page}/",
                        categories = "/ott/"
                    ),
                    selectors = SelectorConfig(
                        item = "article.video-card, article.post, article",
                        title = "h2.loop-title a, h2.loop-title, h2 a, a[title]",
                        thumbnail = "div.video-thumbnail img, img.wp-post-image, img",
                        thumbnailAttr = "src",
                        detailUrl = "div.video-thumbnail a, h2.loop-title a, a",
                        duration = "span.video-duration-badge, .duration",
                        detailTitle = "h1.entry-title, h1",
                        detailDescription = ".series-description, .entry-content p, .video-details, p",
                        detailThumbnail = "meta[property='og:image'], img.wp-post-image, img",
                        player = "div.xplayer, video, iframe",
                        videoSource = "div.xplayer-lazy-source, .xplayer-lazy-source, meta[itemprop*='embedUrl'], video source[src], video[src], source[type='video/mp4']",
                        videoSourceAttr = "data-src",
                        relatedItems = ".related-series article, .related-videos article, article.video-card"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Web Index",
                        disclaimer = "Aggregated public reference media feed."
                    )
                ),
                // 30. ChiggyWiggy
                ProviderConfig(
                    id = "chiggywiggy",
                    familyId = "direct_cdn_family",
                    domains = listOf("https://chiggywiggy.int.in", "https://chiggywiggy.cc", "https://chiggywiggy.com"),
                    validationMarker = "chiggywiggy",
                    name = "ChiggyWiggy",
                    enabled = true,
                    baseUrl = "https://chiggywiggy.int.in",
                    adapter = "html_selector",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "/",
                        search = "/?s={query}",
                        page = "/page/{page}/",
                        categories = "/categories/"
                    ),
                    selectors = SelectorConfig(
                        item = "article.loop-video, article.thumb-block, article.video-preview-item, article, div.video-thumb, div.video-preview, .thumb-block",
                        title = "header.entry-header h2 a, h2.entry-title a, h2 a, a[title], img[alt]",
                        thumbnail = "img[src], img[data-src], img.thumb, img",
                        thumbnailAttr = "data-src, src",
                        detailUrl = "a",
                        duration = "span.duration, .badge-duration, span.time, .video-duration, .duration",
                        detailTitle = "h1.entry-title, h1",
                        detailDescription = ".entry-content p, .video-details, .description, p",
                        detailThumbnail = "meta[property='og:image'], img.thumb, img",
                        player = "iframe[src*='lulust'], iframe[src*='lulu'], video#player, video, iframe",
                        videoSource = "video source[src], video[src], source[type='video/mp4'], div[data-url*='.mp4']"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Web Index",
                        disclaimer = "Aggregated public reference media feed."
                    )
                ),
                // 31. DesiBabe
                ProviderConfig(
                    id = "desibabe",
                    familyId = "direct_cdn_family",
                    domains = listOf("https://desibabe.to", "https://www.desibabe.to", "https://desibabe.net", "https://www.desibabe.net"),
                    validationMarker = "desibabe",
                    name = "DesiBabe",
                    enabled = true,
                    baseUrl = "https://desibabe.to",
                    adapter = "html_selector",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "/",
                        search = "/search?q={query}",
                        page = "/?page={page}",
                        categories = "/categories"
                    ),
                    selectors = SelectorConfig(
                        item = "a[href*='/post/']",
                        title = "h3, img[alt]",
                        thumbnail = "img",
                        thumbnailAttr = "src",
                        detailUrl = "this",
                        duration = ".duration",
                        detailTitle = "h1",
                        detailDescription = ".post-description, p",
                        detailThumbnail = "meta[property='og:image'], img",
                        player = "iframe[src*='downloaddirect.xyz'], iframe, video",
                        videoSource = "iframe[src*='downloaddirect.xyz'], iframe, video source"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Web Index",
                        disclaimer = "Aggregated public reference media feed."
                    )
                ),
                // 32. DesiGirlXX
                ProviderConfig(
                    id = "desigirlxx",
                    familyId = "direct_cdn_family",
                    domains = listOf("https://desigirlxx.beer", "https://www.desigirlxx.beer"),
                    validationMarker = "desigirlxx",
                    name = "DesiGirlXX",
                    enabled = true,
                    baseUrl = "https://desigirlxx.beer",
                    adapter = "html_selector",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "/",
                        search = "/?s={query}",
                        page = "/page/{page}/",
                        categories = "/category/hot-web-series"
                    ),
                    selectors = SelectorConfig(
                        item = "article.loop-video, article.thumb-block, article.video-preview-item",
                        title = "header h2 a, a[title]",
                        thumbnail = "img.video-main-thumb, img",
                        thumbnailAttr = "data-main-thumb, src, data-src",
                        detailUrl = "header h2 a, a",
                        duration = ".duration",
                        detailTitle = "h1.entry-title, h1",
                        detailDescription = ".entry-content p, .video-details, p",
                        detailThumbnail = "meta[property='og:image'], img.wp-post-image, img",
                        player = "iframe[src*='playmate.to'], iframe, video",
                        videoSource = "iframe[src*='playmate.to'], iframe, video source, source[type='video/mp4']"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Web Index",
                        disclaimer = "Aggregated public reference media feed."
                    )
                ),
                // 33. DesiMaals
                ProviderConfig(
                    id = "desimaals",
                    familyId = "direct_cdn_family",
                    domains = listOf("https://www.desimaals.fun", "https://desimaals.fun", "https://desimaals.com", "https://www.desimaals.com"),
                    validationMarker = "desimaals",
                    name = "DesiMaals",
                    enabled = true,
                    baseUrl = "https://www.desimaals.fun",
                    adapter = "html_selector",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "/?filter=latest",
                        search = "/?s={query}",
                        page = "/page/{page}/?filter=latest",
                        categories = "/categories/"
                    ),
                    selectors = SelectorConfig(
                        item = "article.loop-video, article.thumb-block, article.post",
                        title = "header h2 a, a[title]",
                        thumbnail = "img.video-main-thumb, img",
                        thumbnailAttr = "src, data-src",
                        detailUrl = "header h2 a, a",
                        duration = ".duration",
                        detailTitle = "h1.entry-title, h1",
                        detailDescription = ".entry-content p, .video-details, p",
                        detailThumbnail = "meta[property='og:image'], img",
                        player = "iframe[src*='clean-tube-player'], iframe, video",
                        videoSource = "iframe[src*='clean-tube-player'], iframe, video source, source[type='video/mp4']"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Web Index",
                        disclaimer = "Aggregated public reference media feed."
                    )
                ),
                // 34. DesiVideo
                ProviderConfig(
                    id = "desivideo",
                    familyId = "direct_cdn_family",
                    domains = listOf("https://desivideo.net", "https://www.desivideo.net", "https://desivideo.us", "https://www.desivideo.us"),
                    validationMarker = "desivideo",
                    name = "DesiVideo",
                    enabled = true,
                    baseUrl = "https://desivideo.net",
                    adapter = "html_selector",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "/latest-video",
                        search = "/search?s={query}",
                        page = "?page={page}",
                        categories = "/categories"
                    ),
                    selectors = SelectorConfig(
                        item = "article.thumb-block, article.post",
                        title = "a[title]",
                        thumbnail = "video[poster], img",
                        thumbnailAttr = "poster, src",
                        detailUrl = "a",
                        duration = ".duration",
                        detailTitle = "h1",
                        detailDescription = ".video-details, .entry-content p, p",
                        detailThumbnail = "meta[property='og:image'], video[poster], img",
                        player = "video#main-video, video",
                        videoSource = "video#main-video source, meta[property='og:video'], video source, source[type='video/mp4']"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Web Index",
                        disclaimer = "Aggregated public reference media feed."
                    )
                ),
                // 30. BrazzPW
                ProviderConfig(
                    id = "brazzpw",
                    familyId = "clean_tube_family",
                    domains = listOf("https://brazzpw.xyz", "https://brazzpw.com"),
                    validationMarker = "brazzpw",
                    name = "BrazzPW",
                    enabled = true,
                    baseUrl = "https://brazzpw.xyz",
                    adapter = "html_selector",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "/videos/free-brazz-premium-full-new-2026/",
                        search = "/search/free-brazz-premium-full-new-2026/?s={query}",
                        page = "/videos/page/{page}/free-brazz-premium-full-new-2026/",
                        categories = "/categories/free-brazz-premium-full-new-2026/"
                    ),
                    selectors = SelectorConfig(
                        item = "div.videos-list article.thumb-block:not(.slide):not(.bx-clone), div.videos-list .loop-video:not(.slide):not(.bx-clone), article.thumb-block:not(.slide):not(.bx-clone), div.content-area article",
                        title = "a[title], h2.entry-title a, h2 a, a.title, .entry-title",
                        thumbnail = "img.video-main-thumb, img",
                        thumbnailAttr = "data-src, src",
                        detailUrl = "a[href*='/video/'], a[href*='/videos/'], a[href*='brazzpw.xyz/'], a[href*='brazzpw.com/'], a",
                        duration = ".duration",
                        detailTitle = "h1.entry-title, h1",
                        detailDescription = ".entry-content p, p",
                        detailThumbnail = "meta[property='og:image'], img",
                        player = "iframe[src*='player'], video",
                        videoSource = "video source[src], video[src], source[type='video/mp4']",
                        videoSourceAttr = "src"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Aggregation Index",
                        disclaimer = "Aggregated public media feed."
                    )
                ),

                // 32. FPO.xxx
                ProviderConfig(
                    id = "fpo",
                    familyId = "clean_tube_family",
                    domains = listOf("https://www.fpo.xxx", "https://fpo.xxx"),
                    validationMarker = "fpo",
                    name = "FPO.xxx",
                    enabled = true,
                    baseUrl = "https://www.fpo.xxx",
                    adapter = "html_selector",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "/new-1/",
                        search = "/search/{query}/",
                        page = "/new-1/{page}/",
                        categories = "/categories/"
                    ),
                    selectors = SelectorConfig(
                        item = "div.item:not(.pfb-slider-group *), a.item:not(.pfb-slider-group *)",
                        title = "strong.title, a.title, [title]",
                        thumbnail = "img",
                        thumbnailAttr = "data-src, src",
                        detailUrl = "a[href*='/video/'], a[href*='/sites/'], a[href*='/models/'], a",
                        duration = ".duration, span.time",
                        detailTitle = "h1",
                        detailDescription = ".description, p",
                        detailThumbnail = "meta[property='og:image'], img",
                        player = "video",
                        videoSource = "div.kt-player[data-url], [data-url*='.mp4'], [data-url*='/hls/'], video source[src], video[src]",
                        videoSourceAttr = "src"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Web Index",
                        disclaimer = "Aggregated public media feed."
                    )
                ),
                // 33. Hello.porn
                ProviderConfig(
                    id = "hello",
                    familyId = "clean_tube_family",
                    domains = listOf("https://hello.porn"),
                    validationMarker = "hello",
                    name = "Hello.porn",
                    enabled = true,
                    baseUrl = "https://hello.porn",
                    adapter = "html_selector",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "/trending/",
                        search = "/search?q={query}",
                        page = "/trending/{page}/",
                        categories = "/categories/"
                    ),
                    selectors = SelectorConfig(
                        item = "div.items-videos div.item, div.item:not(.navigation *):not(.items-categories *):not(.slider-cat *):not(.pfb-slider-group *)",
                        title = "a.title, strong.title, [title], a",
                        thumbnail = "img",
                        thumbnailAttr = "data-src, src",
                        detailUrl = "a[href*='/videos/'], a[href*='/video/'], a[href*='/pornstar/'], a[href*='/channels/'], a",
                        duration = ".duration",
                        detailTitle = "h1",
                        detailDescription = "p",
                        detailThumbnail = "meta[property='og:image'], img",
                        player = "video",
                        videoSource = "div.kt-player[data-url], [data-url*='.mp4'], [data-url*='/hls/'], video source[src], video[src]",
                        videoSourceAttr = "src"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Aggregation Index",
                        disclaimer = "Aggregated public media feed."
                    )
                ),
                // 34. HQPorner
                ProviderConfig(
                    id = "hqporner",
                    familyId = "clean_tube_family",
                    domains = listOf("https://hqporner.com"),
                    validationMarker = "hqporner",
                    name = "HQPorner",
                    enabled = true,
                    baseUrl = "https://hqporner.com",
                    adapter = "html_selector",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "/",
                        search = "/?q={query}",
                        page = "/hdporn/{page}",
                        categories = "/categories"
                    ),
                    selectors = SelectorConfig(
                        item = "section.box.feature:has(h3), section.feature:has(h3), div.video-item",
                        title = "h3.meta-data-title a, a.title, h3 a, a[title], a",
                        thumbnail = "img",
                        thumbnailAttr = "data-src, src",
                        detailUrl = "a[href*='/hdporn/'], a[href*='/category/'], a[href*='/actress/'], h3 a, a",
                        duration = ".duration",
                        detailTitle = "h1",
                        detailDescription = "p",
                        detailThumbnail = "meta[property='og:image'], img",
                        player = "iframe[src*='mydaddy'], video, iframe",
                        videoSource = "iframe[src*='mydaddy'], iframe[src*='video'], video source[src], video[src], [data-url*='.mp4'], [data-url*='.m3u8']",
                        videoSourceAttr = "src"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Aggregation Index",
                        disclaimer = "Aggregated public media feed."
                    )
                ),
                // 35. MAX.porn
                ProviderConfig(
                    id = "max",
                    familyId = "clean_tube_family",
                    domains = listOf("https://max.porn", "https://www.max.porn"),
                    validationMarker = "max",
                    name = "MAX.porn",
                    enabled = true,
                    baseUrl = "https://max.porn",
                    adapter = "html_selector",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "/",
                        search = "/search/{query}/",
                        page = "/{page}/",
                        categories = "/channels/"
                    ),
                    selectors = SelectorConfig(
                        item = "div.item:not(.swiper-slide):not(.pfb-slider-group *):not(.page):not(.jump):not(.last):not(.next):not(.prev)",
                        title = "a.title, [title], a",
                        thumbnail = "img",
                        thumbnailAttr = "data-src, src",
                        detailUrl = "a[href*='/videos/'], a[href*='/channels/'], a",
                        duration = ".duration_item, .duration",
                        detailTitle = "h1",
                        detailDescription = "p",
                        detailThumbnail = "meta[property='og:image'], img",
                        player = "video",
                        videoSource = "video source[src], video[src]",
                        videoSourceAttr = "src"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Aggregation Index",
                        disclaimer = "Aggregated public media feed."
                    )
                ),
                // 36. Netfapx
                ProviderConfig(
                    id = "netfapx",
                    familyId = "clean_tube_family",
                    domains = listOf("https://netfapx.com"),
                    validationMarker = "netfapx",
                    name = "Netfapx",
                    enabled = true,
                    baseUrl = "https://netfapx.com",
                    adapter = "html_selector",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "/",
                        search = "/?s={query}",
                        page = "/page/{page}/",
                        categories = "/categories/"
                    ),
                    selectors = SelectorConfig(
                        item = "article.pinbox, div.pinbox, article.post, article:not(.pfb-slider-group *)",
                        title = "h2.entry-title a, h2 a, a[title]",
                        thumbnail = "img",
                        thumbnailAttr = "data-src, src",
                        detailUrl = "h2.entry-title a, a[href*='netfapx.com/20'], a[href*='?cat='], a",
                        duration = ".duration",
                        detailTitle = "h1.entry-title, h1",
                        detailDescription = ".entry-content p, p",
                        detailThumbnail = "meta[property='og:image'], img",
                        player = "video, iframe",
                        videoSource = "video source[src], video[src]",
                        videoSourceAttr = "src"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Aggregation Index",
                        disclaimer = "Aggregated public media feed."
                    )
                ),
                // 37. OK.porn
                ProviderConfig(
                    id = "ok_porn",
                    familyId = "clean_tube_family",
                    domains = listOf("https://ok.porn"),
                    validationMarker = "ok.porn",
                    name = "OK.porn",
                    enabled = true,
                    baseUrl = "https://ok.porn",
                    adapter = "html_selector",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "/",
                        search = "/search/{query}/",
                        page = "/{page}/",
                        categories = "/channels/"
                    ),
                    selectors = SelectorConfig(
                        item = "div.list_video_wrapper div.item:not(.swiper-slide):not(.pfb-slider-group *):not(.page):not(.jump):not(.last):not(.next):not(.prev), div.item:not(.swiper-slide):not(.pfb-slider-group *):not(.page):not(.jump):not(.last):not(.next):not(.prev), div.thumb-bl:not(.item):not(.item *):not(.swiper-slide):not(.pfb-slider-group *):not(.page):not(.jump):not(.last):not(.next):not(.prev)",
                        title = "a.title, [title], a",
                        thumbnail = "img",
                        thumbnailAttr = "data-src, src",
                        detailUrl = "a[href*='/video/'], a[href*='/sites/'], a[href*='/models/'], a",
                        duration = ".duration",
                        detailTitle = "h1",
                        detailDescription = "p",
                        detailThumbnail = "meta[property='og:image'], img",
                        player = "video",
                        videoSource = "video source[src], video[src]",
                        videoSourceAttr = "src"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Web Index",
                        disclaimer = "Aggregated public media feed."
                    )
                ),
                // 38. OK.xxx
                ProviderConfig(
                    id = "ok_xxx",
                    familyId = "clean_tube_family",
                    domains = listOf("https://ok.xxx"),
                    validationMarker = "ok.xxx",
                    name = "OK.xxx",
                    enabled = true,
                    baseUrl = "https://ok.xxx",
                    adapter = "html_selector",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "/",
                        search = "/search/{query}/",
                        page = "/{page}/",
                        categories = "/channels/"
                    ),
                    selectors = SelectorConfig(
                        item = "div.list_video_wrapper div.item:not(.swiper-slide):not(.pfb-slider-group *):not(.page):not(.jump):not(.last):not(.next):not(.prev), div.item:not(.swiper-slide):not(.pfb-slider-group *):not(.page):not(.jump):not(.last):not(.next):not(.prev), div.thumb-bl:not(.item):not(.item *):not(.swiper-slide):not(.pfb-slider-group *):not(.page):not(.jump):not(.last):not(.next):not(.prev)",
                        title = "a.title, [title], a",
                        thumbnail = "img",
                        thumbnailAttr = "data-src, src",
                        detailUrl = "a[href*='/video/'], a[href*='/sites/'], a[href*='/models/'], a",
                        duration = ".duration",
                        detailTitle = "h1",
                        detailDescription = "p",
                        detailThumbnail = "meta[property='og:image'], img",
                        player = "video",
                        videoSource = "video source[src], video[src]",
                        videoSourceAttr = "src"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Web Index",
                        disclaimer = "Aggregated public media feed."
                    )
                ),
                // 39. PerfectGirls
                ProviderConfig(
                    id = "perfectgirls",
                    familyId = "clean_tube_family",
                    domains = listOf("https://www.perfectgirls.xxx", "https://perfectgirls.xxx"),
                    validationMarker = "perfectgirls",
                    name = "PerfectGirls",
                    enabled = true,
                    baseUrl = "https://www.perfectgirls.xxx",
                    adapter = "html_selector",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "/",
                        search = "/search/{query}/",
                        page = "/{page}/",
                        categories = "/channels/"
                    ),
                    selectors = SelectorConfig(
                        item = "div.list_video_wrapper div.item:not(.swiper-slide):not(.pfb-slider-group *):not(.page):not(.jump):not(.last):not(.next):not(.prev), div.item:not(.swiper-slide):not(.pfb-slider-group *):not(.page):not(.jump):not(.last):not(.next):not(.prev), div.thumb-bl:not(.item):not(.item *):not(.swiper-slide):not(.pfb-slider-group *):not(.page):not(.jump):not(.last):not(.next):not(.prev)",
                        title = "a.title, [title], a",
                        thumbnail = "img",
                        thumbnailAttr = "data-src, src",
                        detailUrl = "a[href*='/video/'], a[href*='/videos/'], a[href*='/channels/'], a[href*='/pornstars/'], a",
                        duration = ".duration",
                        detailTitle = "h1",
                        detailDescription = "p",
                        detailThumbnail = "meta[property='og:image'], img",
                        player = "video",
                        videoSource = "video source[src], video[src]",
                        videoSourceAttr = "src"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Web Index",
                        disclaimer = "Aggregated public media feed."
                    )
                ),
                // 40. Porn4Days
                ProviderConfig(
                    id = "porn4days",
                    familyId = "clean_tube_family",
                    domains = listOf("https://porn4days.pw"),
                    validationMarker = "porn4days",
                    name = "Porn4Days",
                    enabled = true,
                    baseUrl = "https://porn4days.pw",
                    adapter = "html_selector",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "/newest",
                        search = "/search/{query}",
                        page = "/newest/page{page}/",
                        categories = "/paysitelist"
                    ),
                    selectors = SelectorConfig(
                        item = "div.card.video-card:not(.slick-slider *):not(.slick-slide):not(.slick-slide *)",
                        title = "img[alt], a[title], h5, .card-title, a",
                        thumbnail = "img.card-img-top, img",
                        thumbnailAttr = "src, data-src",
                        detailUrl = "a[href*='video/'], a",
                        duration = ".duration, .badge, .time",
                        detailTitle = "h1",
                        detailDescription = "p",
                        detailThumbnail = "meta[property='og:image'], img",
                        player = "video",
                        videoSource = "video source[src], video[src]",
                        videoSourceAttr = "src"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Aggregation Index",
                        disclaimer = "Aggregated public media feed."
                    )
                ),
                // 41. PornHat
                ProviderConfig(
                    id = "pornhat",
                    familyId = "clean_tube_family",
                    domains = listOf("https://www.pornhat.com", "https://pornhat.com"),
                    validationMarker = "pornhat",
                    name = "PornHat",
                    enabled = true,
                    baseUrl = "https://www.pornhat.com",
                    adapter = "html_selector",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "/",
                        search = "/search/{query}/",
                        page = "/latest-updates/{page}/",
                        categories = "/channels/"
                    ),
                    selectors = SelectorConfig(
                        item = "div.list_video_wrapper div.item:not(.swiper-slide):not(.pfb-slider-group *):not(.page):not(.jump):not(.last):not(.next):not(.prev), div.item:not(.swiper-slide):not(.pfb-slider-group *):not(.page):not(.jump):not(.last):not(.next):not(.prev), div.thumb-bl:not(.item):not(.item *):not(.swiper-slide):not(.pfb-slider-group *):not(.page):not(.jump):not(.last):not(.next):not(.prev)",
                        title = "a.title, [title], a",
                        thumbnail = "img",
                        thumbnailAttr = "data-src, src",
                        detailUrl = "a[href*='/video/'], a[href*='/videos/'], a[href*='/channels/'], a",
                        duration = ".duration",
                        detailTitle = "h1",
                        detailDescription = "p",
                        detailThumbnail = "meta[property='og:image'], img",
                        player = "video",
                        videoSource = "video source[src], video[src]",
                        videoSourceAttr = "src"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Web Index",
                        disclaimer = "Aggregated public media feed."
                    )
                ),
                // 42. PornHD4K
                ProviderConfig(
                    id = "pornhd4k",
                    familyId = "clean_tube_family",
                    domains = listOf("https://pornhd4k.net"),
                    validationMarker = "pornhd4k",
                    name = "PornHD4K",
                    enabled = true,
                    baseUrl = "https://pornhd4k.net",
                    adapter = "html_selector",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "/",
                        search = "/?s={query}",
                        page = "/premium-porn-hd/page-{page}",
                        categories = "/tag/asian"
                    ),
                    selectors = SelectorConfig(
                        item = "div.item, div.ml-item, div.video-item",
                        title = "img[alt], a.title, h3 a, [title], a",
                        thumbnail = "img[data-original], img[data-src], img",
                        thumbnailAttr = "data-original, data-src, src",
                        detailUrl = "a[href*='/movies/'], a",
                        duration = ".duration",
                        detailTitle = "h1",
                        detailDescription = "p",
                        detailThumbnail = "meta[property='og:image'], img",
                        player = "video, iframe",
                        videoSource = "video source[src], video[src]",
                        videoSourceAttr = "src"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Web Index",
                        disclaimer = "Aggregated public media feed."
                    )
                ),
                // 43. PornHouse
                ProviderConfig(
                    id = "pornhouse",
                    familyId = "clean_tube_family",
                    domains = listOf("https://pornhouse.me"),
                    validationMarker = "pornhouse",
                    name = "PornHouse",
                    enabled = true,
                    baseUrl = "https://pornhouse.me",
                    adapter = "html_selector",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "/",
                        search = "/?s={query}",
                        page = "/porn-hd-free-full-1080p/page-{page}",
                        categories = "/tag/asian"
                    ),
                    selectors = SelectorConfig(
                        item = "div.item, div.ml-item, div.video-item",
                        title = "img[alt], a.title, h3 a, [title], a",
                        thumbnail = "img[data-original], img[data-src], img",
                        thumbnailAttr = "data-original, data-src, src",
                        detailUrl = "a[href*='/movies/'], a",
                        duration = ".duration",
                        detailTitle = "h1",
                        detailDescription = "p",
                        detailThumbnail = "meta[property='og:image'], img",
                        player = "video, iframe",
                        videoSource = "video source[src], video[src]",
                        videoSourceAttr = "src"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Web Index",
                        disclaimer = "Aggregated public media feed."
                    )
                ),
                // 44. PornMZ
                ProviderConfig(
                    id = "pornmz",
                    familyId = "clean_tube_family",
                    domains = listOf("https://pornmz.com"),
                    validationMarker = "pornmz",
                    name = "PornMZ",
                    enabled = true,
                    baseUrl = "https://pornmz.com",
                    adapter = "html_selector",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "/",
                        search = "/?s={query}",
                        page = "/page/{page}?filter=latest",
                        categories = "/categories"
                    ),
                    selectors = SelectorConfig(
                        item = "article.post, article.thumb-block, article.video-preview-item, article, div.item",
                        title = "a[title], h2.entry-title a, h2 a, a.title, a",
                        thumbnail = "img.video-main-thumb, img",
                        thumbnailAttr = "src",
                        detailUrl = "a[href*='/video/id='], a[href*='/video/'], a[href*='pornmz.com/video/'], a",
                        duration = ".duration",
                        detailTitle = "h1.entry-title, h1",
                        detailDescription = ".entry-content p, p",
                        detailThumbnail = "meta[property='og:image'], img",
                        player = "iframe, video",
                        videoSource = "video source[src], video[src]",
                        videoSourceAttr = "src"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Aggregation Index",
                        disclaimer = "Aggregated public media feed."
                    )
                ),
                // 45. Pornstars.tube
                ProviderConfig(
                    id = "pornstars_tube",
                    familyId = "clean_tube_family",
                    domains = listOf("https://pornstars.tube", "https://www.pornstars.tube"),
                    validationMarker = "pornstars.tube",
                    name = "Pornstars.tube",
                    enabled = true,
                    baseUrl = "https://pornstars.tube",
                    adapter = "html_selector",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "/models/",
                        search = "/search/{query}/",
                        page = "/models/{page}/",
                        categories = "/models/"
                    ),
                    selectors = SelectorConfig(
                        item = "div.item, div.video-card, div.thumb",
                        title = "a.title, [title], img[alt], a",
                        thumbnail = "img[data-src], img[src], img",
                        thumbnailAttr = "data-src, src",
                        detailUrl = "a[href*='/videos/'], a[href*='/models/'], a",
                        duration = ".duration",
                        detailTitle = "h1",
                        detailDescription = "p",
                        detailThumbnail = "meta[property='og:image'], img",
                        player = "video",
                        videoSource = "video source[src], video[src]",
                        videoSourceAttr = "src"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Web Index",
                        disclaimer = "Aggregated public media feed."
                    )
                ),
                // 46. SxyPrn
                ProviderConfig(
                    id = "sxyprn",
                    familyId = "clean_tube_family",
                    domains = listOf("https://sxyprn.com"),
                    validationMarker = "sxyprn",
                    name = "SxyPrn",
                    enabled = true,
                    baseUrl = "https://sxyprn.com",
                    adapter = "html_selector",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "/",
                        search = "/?s={query}",
                        page = "/orgasm/{page}",
                        categories = "/popular/top-pop.html"
                    ),
                    selectors = SelectorConfig(
                        item = "div.post_el_small:not(.post_el_post), div.post_el:not(.post_el_post)",
                        title = "div.post_text, a.ps_link, a[title]",
                        thumbnail = "img.mini_post_vid_thumb, img[src*='trafficdeposit.com'], .post_vid_thumb img, img",
                        thumbnailAttr = "src, data-src",
                        detailUrl = "a[href*='/post/']",
                        duration = ".post_control_time, .duration",
                        detailTitle = "h1",
                        detailDescription = "p",
                        detailThumbnail = "meta[property='og:image'], img",
                        player = "video#player_el, video.player_el, div.ypsv-download, video, iframe",
                        videoSource = "video#player_el, video.player_el, div.ypsv-download a, a[download], video source[src], video[src]",
                        videoSourceAttr = "src, href"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Web Index",
                        disclaimer = "Aggregated public media feed."
                    )
                ),
                // 47. WatchXXXFree
                ProviderConfig(
                    id = "watchxxxfree",
                    familyId = "clean_tube_family",
                    domains = listOf("https://watchxxxfree.xyz", "https://justfullporn.net"),
                    validationMarker = "watchxxxfree",
                    name = "WatchXXXFree",
                    enabled = true,
                    baseUrl = "https://watchxxxfree.xyz",
                    adapter = "html_selector",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "/?filter=latest",
                        search = "/?s={query}",
                        page = "/page/{page}/?filter=latest",
                        categories = "/categories/"
                    ),
                    selectors = SelectorConfig(
                        item = "div.videos-list article",
                        title = "a[title], img[alt], h2.entry-title a, h2 a, a",
                        thumbnail = "img.video-main-thumb, img",
                        thumbnailAttr = "src",
                        detailUrl = "a[href*='watchxxxfree.xyz/'], a[href*='/'], a",
                        duration = ".duration",
                        detailTitle = "h1.entry-title, h1",
                        detailDescription = ".entry-content p, p",
                        detailThumbnail = "meta[property='og:image'], img",
                        player = "iframe[src*='luluvdo'], iframe[src*='lulustream'], iframe[src*='firestream'], iframe[src*='playmogo'], iframe[src*='vixeo.io'], iframe, video",
                        videoSource = "iframe[src*='luluvdo'], iframe[src*='lulustream'], iframe[src*='firestream'], iframe[src*='playmogo'], iframe[src*='vixeo.io'], video source[src], video[src]",
                        videoSourceAttr = "src"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Aggregation Index",
                        disclaimer = "Aggregated public media feed."
                    )
                ),
                // 48. LalaMasa
                ProviderConfig(
                    id = "lalamasa",
                    familyId = "masa_network_family",
                    domains = listOf("https://lalamasa.mobi"),
                    validationMarker = "lalamasa",
                    name = "LalaMasa",
                    enabled = true,
                    baseUrl = "https://lalamasa.mobi",
                    adapter = "html_selector",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "/",
                        search = "/?s={query}",
                        page = "/page/{page}/",
                        categories = "/categories/"
                    ),
                    selectors = SelectorConfig(
                        item = "article.vcard, article, div.item",
                        title = "h3.vtitle, .vtitle, h2 a, a.vtitle, h2",
                        thumbnail = ".thumb, img",
                        thumbnailAttr = "style",
                        detailUrl = "a",
                        duration = "span.dur, .badge-duration",
                        detailTitle = "h1",
                        detailDescription = ".entry-content p, .video-details",
                        detailThumbnail = "video[poster], meta[property='og:image'], img",
                        player = "video, iframe",
                        videoSource = "video source[src], source[type='video/mp4'], video[src]",
                        videoSourceAttr = "src"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Web Index",
                        disclaimer = "Aggregated public media feed."
                    )
                ),
                // 49. WatchOErotic
                ProviderConfig(
                    id = "watchoerotic",
                    familyId = "erotic_movies_family",
                    domains = listOf("https://watchoerotic.com"),
                    validationMarker = "watchoerotic",
                    name = "WatchOErotic",
                    enabled = true,
                    baseUrl = "https://watchoerotic.com",
                    adapter = "html_selector",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "/",
                        search = "/?s={query}",
                        page = "/page/{page}/",
                        categories = "/genre/desi/"
                    ),
                    selectors = SelectorConfig(
                        item = ".ml-item, div.ml-item",
                        title = ".mli-info h2, h2, a[oldtitle], img[alt]",
                        thumbnail = "img.mli-thumb, img",
                        thumbnailAttr = "data-original, src",
                        detailUrl = "a.ml-mask, a",
                        duration = ".mli-quality",
                        detailTitle = "h1, h2.film-name, .film-info h1, .entry-title",
                        detailDescription = ".film-desc, .film-description, p",
                        detailThumbnail = "meta[property='og:image'], img.thumb, img",
                        player = "iframe[src*='streamoupload'], iframe[data-lazy-src*='streamoupload'], iframe, video",
                        videoSource = "iframe[src*='streamoupload'], iframe[data-lazy-src*='streamoupload']",
                        videoSourceAttr = "src, data-lazy-src"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Web Index",
                        disclaimer = "Aggregated public media feed."
                    )
                ),
                // 49. PRMovies
                ProviderConfig(
                    id = "prmovies",
                    familyId = "erotic_movies_family",
                    domains = listOf("https://prmovies.com"),
                    validationMarker = "prmovies",
                    name = "PRMovies",
                    enabled = true,
                    baseUrl = "https://prmovies.com",
                    adapter = "html_selector",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "/account/",
                        search = "/?s={query}",
                        page = "/account/page/{page}/",
                        categories = "/account/"
                    ),
                    selectors = SelectorConfig(
                        item = ".ml-item, div.ml-item",
                        title = ".mli-info h2, h2, a[oldtitle], img[alt]",
                        thumbnail = "img.mli-thumb, img",
                        thumbnailAttr = "data-original, src",
                        detailUrl = "a.ml-mask, a",
                        duration = ".mli-quality",
                        detailTitle = "[itemprop='name'], h3[itemprop='name'], h1, h2.film-name, .film-info h1, .entry-title",
                        detailDescription = "[itemprop='description'], .desc, .film-desc, .film-description, p",
                        detailThumbnail = "meta[property='og:image'], [itemprop='image'], [itemprop='thumbnailUrl'], img.thumb, img",
                        player = "iframe[src*='streamoupload'], iframe[data-lazy-src*='streamoupload'], iframe, video",
                        videoSource = "iframe[src*='streamoupload'], iframe[data-lazy-src*='streamoupload']",
                        videoSourceAttr = "src, data-lazy-src"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Web Index",
                        disclaimer = "Aggregated public media feed."
                    )
                ),
                // 50. PRMovies Church / PrmoviesM
                ProviderConfig(
                    id = "prmovies_church",
                    familyId = "cinema_movies_family",
                    domains = listOf("https://prmovies.futbol", "https://prmovies.fotbal", "https://prmovies.football", "https://prmovies.church", "https://prmovies.energy"),
                    validationMarker = "prmovies",
                    name = "PrmoviesM",
                    enabled = true,
                    baseUrl = "https://prmovies.futbol",
                    adapter = "html_selector",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "/bollywood-movies-on-prmovies/",
                        search = "/?s={query}",
                        page = "/bollywood-movies-on-prmovies/page/{page}/",
                        categories = "/account/"
                    ),
                    selectors = SelectorConfig(
                        item = ".ml-item, div.ml-item",
                        title = ".mli-info h2, h2, a[oldtitle], img[alt]",
                        thumbnail = "img.mli-thumb, img",
                        thumbnailAttr = "data-original, src",
                        detailUrl = "a.ml-mask, a",
                        duration = ".mli-quality",
                        detailTitle = "[itemprop='name'], h3[itemprop='name'], h1, h2.film-name, .film-info h1, .entry-title",
                        detailDescription = "[itemprop='description'], .desc, .film-desc, .film-description, p",
                        detailThumbnail = "meta[property='og:image'], [itemprop='image'], [itemprop='thumbnailUrl'], img.thumb, img",
                        player = "iframe[src*='streamoupload'], iframe[data-lazy-src*='streamoupload'], iframe, video",
                        videoSource = "iframe[src*='streamoupload'], iframe[data-lazy-src*='streamoupload']",
                        videoSourceAttr = "src, data-lazy-src"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Web Index",
                        disclaimer = "Aggregated public media feed."
                    )
                ),
                // 51. PornHub
                ProviderConfig(
                    id = "pornhub",
                    familyId = "clean_tube_family",
                    domains = listOf("https://www.pornhub.com", "https://www.pornhub.org", "https://www.pornhub.net"),
                    validationMarker = "pornhub",
                    name = "PornHub",
                    enabled = true,
                    baseUrl = "https://www.pornhub.com",
                    adapter = "html_selector",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "/video?o=mr",
                        search = "/video/search?search={query}",
                        page = "/video?o=mr&page={page}",
                        categories = "/categories"
                    ),
                    selectors = SelectorConfig(
                        item = "li.videoBox, .pcVideoListItem, .videoblock, ul#videoCategory li, .nf-videos li, div.phimage",
                        title = "span.title a, .title a, a[title], img[alt]",
                        thumbnail = "img[data-thumb_url], img[data-src], img[data-mediumthumb], img[src]",
                        thumbnailAttr = "data-thumb_url, data-src, data-mediumthumb, src",
                        detailUrl = "a[href*='view_video.php'], a.linkVideoThumb, a[href*='/view_video']",
                        duration = "var.duration, .duration, span.duration",
                        detailTitle = "h1.title span, h1.title, h1",
                        detailDescription = ".video-description, .user-desc, p",
                        detailThumbnail = "meta[property='og:image'], img",
                        player = "div#player, video, iframe",
                        videoSource = "meta[property='og:video:url'], video source",
                        videoSourceAttr = "content, src"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Web Index",
                        disclaimer = "Aggregated public media feed."
                    )
                ),
                // 54. xHamster
                ProviderConfig(
                    id = "xhamster",
                    familyId = "clean_tube_family",
                    domains = listOf("https://xhamster.com", "https://xhamster3.com", "https://xhamster.desi"),
                    validationMarker = "xhamster",
                    name = "xHamster",
                    enabled = true,
                    baseUrl = "https://xhamster.com",
                    adapter = "html_selector",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "/newest",
                        search = "/search/{query}",
                        page = "/newest/{page}",
                        categories = "/categories"
                    ),
                    selectors = SelectorConfig(
                        item = "[data-video-id], .thumb-list__item.video-thumb, .thumb-list__item, article.thumb-list__item, .video-thumb",
                        title = "a[data-video-title], a.video-thumb__image-container[aria-label], .video-thumb-info__name, a[title], .thumb-image-container__image[alt]",
                        thumbnail = "img.thumb-image-container__image, img[data-src], img[src]",
                        thumbnailAttr = "data-src, src",
                        detailUrl = "a[href*='/videos/']",
                        duration = "[data-role='video-duration'], .duration",
                        detailTitle = "h1.page-title, h1",
                        detailDescription = ".video-description, p",
                        detailThumbnail = "meta[property='og:image'], img",
                        player = "video, iframe",
                        videoSource = "meta[property='og:video:url'], video source",
                        videoSourceAttr = "content, src"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Web Index",
                        disclaimer = "Aggregated public media feed."
                    )
                ),
                // 55. RedTube
                ProviderConfig(
                    id = "redtube",
                    familyId = "clean_tube_family",
                    domains = listOf("https://www.redtube.net", "https://www.redtube.com", "https://www.redtube.xxx"),
                    validationMarker = "redtube",
                    name = "RedTube",
                    enabled = true,
                    baseUrl = "https://www.redtube.com",
                    adapter = "html_selector",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "/",
                        search = "/?search={query}",
                        page = "/?page={page}",
                        categories = "/categories"
                    ),
                    selectors = SelectorConfig(
                        item = "li.video_item, div.video_block, .video-thumb, li[data-video-id]",
                        title = "a[title], .video_title a",
                        thumbnail = "img[data-src], img[src]",
                        thumbnailAttr = "data-src, src",
                        detailUrl = "a.video_link, a[href*='/']",
                        duration = ".video_duration, .duration",
                        detailTitle = "h1.video_title, h1",
                        detailDescription = ".video_description, p",
                        detailThumbnail = "meta[property='og:image'], img",
                        player = "div#redtube_player, video, iframe",
                        videoSource = "meta[property='og:video:url']",
                        videoSourceAttr = "content"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Web Index",
                        disclaimer = "Aggregated public media feed."
                    )
                ),
                // 56. YouPorn
                ProviderConfig(
                    id = "youporn",
                    familyId = "clean_tube_family",
                    domains = listOf("https://www.you-porn.com", "https://m.youporn.com", "https://www.youporn.com"),
                    validationMarker = "youporn",
                    name = "YouPorn",
                    enabled = true,
                    baseUrl = "https://www.you-porn.com",
                    adapter = "html_selector",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "/",
                        search = "/search/?query={query}",
                        page = "/?page={page}",
                        categories = "/categories/"
                    ),
                    selectors = SelectorConfig(
                        item = "div.video-box, .four-col-content-item, .video_thumb, [data-video-id]",
                        title = "a[title], .video-title-text, .video-title, span.title, p.tm_category_title, .categoryTitle p",
                        thumbnail = "img[data-src], img[data-thumbnail], img[src]",
                        thumbnailAttr = "data-src, data-thumbnail, src",
                        detailUrl = "a[href*='/watch/']",
                        duration = ".video-duration, .duration",
                        detailTitle = "h1.videoTitle, h1.watch-video-title, h1",
                        detailDescription = ".video-description, p",
                        detailThumbnail = "meta[property='og:image'], img",
                        player = "video, iframe",
                        videoSource = "meta[property='og:video:url']",
                        videoSourceAttr = "content"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Web Index",
                        disclaimer = "Aggregated public media feed."
                    )
                ),
                // 57. Tube8
                ProviderConfig(
                    id = "tube8",
                    familyId = "clean_tube_family",
                    domains = listOf("https://www.tube8.es", "https://www.tube8.com"),
                    validationMarker = "tube8",
                    name = "Tube8",
                    enabled = true,
                    baseUrl = "https://www.tube8.es",
                    adapter = "html_selector",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "/newest/page/1/",
                        search = "/searches?q={query}",
                        page = "/newest/page/{page}/",
                        categories = "/categories.html"
                    ),
                    selectors = SelectorConfig(
                        item = "article.video-box, div.video-box, article[data-video-id]",
                        title = "a.video-title-text, a.tm_video_title, img[alt], a[title]",
                        thumbnail = "img.thumb-image, img[data-src], img[data-poster], img",
                        thumbnailAttr = "data-src, data-poster, src",
                        detailUrl = "a[href*='/porn-video/'], a.tm_video_link, a",
                        duration = "div.video-duration, div.tm_video_duration, .video-properties",
                        detailTitle = "h1.video_title, h1",
                        detailDescription = ".video_description, p",
                        detailThumbnail = "meta[property='og:image'], img",
                        player = "video, iframe",
                        videoSource = "meta[property*='og:video'], meta[property='og:video:url']",
                        videoSourceAttr = "content"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Web Index",
                        disclaimer = "Aggregated public media feed."
                    )
                ),
                // 58. FreeOnes Tube
                ProviderConfig(
                    id = "freeonestube",
                    familyId = "clean_tube_family",
                    domains = listOf("https://freeonestube.com"),
                    validationMarker = "freeones",
                    name = "FreeOnes Tube",
                    enabled = true,
                    baseUrl = "https://freeonestube.com",
                    adapter = "html_selector",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "/",
                        search = "/search?q={query}",
                        page = "/page/{page}/",
                        categories = "/categories"
                    ),
                    selectors = SelectorConfig(
                        item = "div.video-block",
                        title = "a.infos[title], span.title",
                        thumbnail = "img.video-img, img",
                        thumbnailAttr = "src, data-src",
                        detailUrl = "a.thumb[href], a.infos[href]",
                        duration = "span.duration",
                        detailTitle = "h1.page-title, h1",
                        detailDescription = ".video-description, .desc, p",
                        detailThumbnail = "meta[itemprop='thumbnailUrl'][content], meta[property='og:image']",
                        player = "video#fxgp-vod-scene-player, video, iframe",
                        videoSource = "meta[itemprop='contentURL'], video source",
                        videoSourceAttr = "content, src"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Web Index",
                        disclaimer = "Aggregated public media feed."
                    )
                ),
                // 57. SpankBang
                ProviderConfig(
                    id = "spankbang",
                    familyId = "clean_tube_family",
                    domains = listOf("https://spankbang.com", "https://spankbang.party", "https://spankbang.porn", "https://spankbang.net"),
                    validationMarker = "video-item",
                    name = "SpankBang",
                    enabled = true,
                    baseUrl = "https://spankbang.com",
                    adapter = "html_selector",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "/",
                        search = "/s/{query}/",
                        page = "/?page={page}",
                        categories = "/categories"
                    ),
                    selectors = SelectorConfig(
                        item = "div.video-item, div.item",
                        title = "div.video-item a.title, a.title, .n a, a[title]",
                        thumbnail = "img[data-src], img.cover, img",
                        thumbnailAttr = "data-src, src",
                        detailUrl = "a[href*='/video/'], a.title",
                        duration = "span.l, .duration, span.length",
                        detailTitle = "h1",
                        detailDescription = ".video-description, .metadata, p",
                        detailThumbnail = "meta[property='og:image'], img",
                        player = "video#video_player, video, div#player",
                        videoSource = "video source[src], meta[property='og:video:url']",
                        videoSourceAttr = "src, content"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Web Index",
                        disclaimer = "Aggregated public media feed."
                    )
                ),
                // 58. TNAFlix
                ProviderConfig(
                    id = "tnaflix",
                    familyId = "clean_tube_family",
                    domains = listOf("https://www.tnaflix.com", "https://tnaflix.com"),
                    validationMarker = "video-list",
                    name = "TNAFlix",
                    enabled = true,
                    baseUrl = "https://www.tnaflix.com",
                    adapter = "html_selector",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "/",
                        search = "/search.php?what={query}",
                        page = "/featured/{page}",
                        categories = "/categories"
                    ),
                    selectors = SelectorConfig(
                        item = "div[data-vid], div.video-list > div, a.video-thumb",
                        title = "a.video-title, a[title], img[alt], a.video-thumb",
                        thumbnail = "img[data-src], img",
                        thumbnailAttr = "data-src, src",
                        detailUrl = "a.video-title, a.video-thumb, a[href*='/video']",
                        duration = "div.video-duration, .thumb-icon",
                        detailTitle = "h1, .video-title",
                        detailDescription = ".video-description, p",
                        detailThumbnail = "meta[property='og:image'], img",
                        player = "video, div#player",
                        videoSource = "video source[src], meta[property='og:video:url']",
                        videoSourceAttr = "src, content"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Web Index",
                        disclaimer = "Aggregated public media feed."
                    )
                ),
                // 59. EmpFlix
                ProviderConfig(
                    id = "empflix",
                    familyId = "clean_tube_family",
                    domains = listOf("https://www.empflix.com", "https://empflix.com"),
                    validationMarker = "video-list",
                    name = "EmpFlix",
                    enabled = true,
                    baseUrl = "https://www.empflix.com",
                    adapter = "html_selector",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "/",
                        search = "/search.php?what={query}",
                        page = "/featured/{page}",
                        categories = "/categories"
                    ),
                    selectors = SelectorConfig(
                        item = "div[data-vid]",
                        title = "a.video-title, img[alt], a[title]",
                        thumbnail = "img[data-src], img",
                        thumbnailAttr = "data-src, src",
                        detailUrl = "a.video-title, a.video-thumb",
                        duration = "div.video-duration, .thumb-icon",
                        detailTitle = "h1, .video-title",
                        detailDescription = ".video-description, p",
                        detailThumbnail = "meta[property='og:image'], img",
                        player = "video, div#player",
                        videoSource = "video source[src], meta[property='og:video:url']",
                        videoSourceAttr = "src, content"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Web Index",
                        disclaimer = "Aggregated public media feed."
                    )
                ),
                // 60. Beeg
                ProviderConfig(
                    id = "beeg",
                    familyId = "clean_tube_family",
                    domains = listOf("https://store.externulls.com", "https://beeg.com"),
                    validationMarker = "",
                    name = "Beeg",
                    enabled = true,
                    baseUrl = "https://store.externulls.com",
                    adapter = "html_selector",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "https://store.externulls.com/tag/videos/index?limit=48&offset=0",
                        search = "https://store.externulls.com/tag/videos/{query}?limit=48&offset=0",
                        page = "https://store.externulls.com/tag/videos/{slug}?limit=48&offset={offset}",
                        categories = "https://store.externulls.com/tag/recommends?type=other&slug=index"
                    ),
                    selectors = SelectorConfig(
                        item = "div.video, div.thumb, .item",
                        title = "a[title], .title, h3",
                        thumbnail = "img",
                        thumbnailAttr = "src",
                        detailUrl = "a",
                        duration = ".duration",
                        detailTitle = "h1",
                        player = "video",
                        videoSource = "video source",
                        videoSourceAttr = "src"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Web Index",
                        disclaimer = "Aggregated public media feed."
                    )
                ),
                // 61. YouJizz
                ProviderConfig(
                    id = "youjizz",
                    familyId = "clean_tube_family",
                    domains = listOf("https://www.youjizz.com", "https://youjizz.com"),
                    validationMarker = "video-thumb",
                    name = "YouJizz",
                    enabled = true,
                    baseUrl = "https://www.youjizz.com",
                    adapter = "html_selector",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "/",
                        search = "/search/{query}-{page}.html",
                        page = "/most-popular/{page}.html",
                        categories = "/categories"
                    ),
                    selectors = SelectorConfig(
                        item = "div.video-thumb, div.thumb",
                        title = "a.title, a[title], .video-title",
                        thumbnail = "img.lazy, img[data-original], img",
                        thumbnailAttr = "data-original, src",
                        detailUrl = "a[href*='/videos/'], a.thumb",
                        duration = "span.time, .duration",
                        detailTitle = "h3 span.agego-content-title, h3, h1.title, h1",
                        detailDescription = ".video-description, p",
                        detailThumbnail = "meta[property='og:image'], img",
                        player = "video, div#player",
                        videoSource = "dataEncodings, mp4Encodings, video source[src]",
                        videoSourceAttr = "src, content"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Web Index",
                        disclaimer = "Aggregated public media feed."
                    )
                ),
                // 62. Eporner
                ProviderConfig(
                    id = "eporner",
                    familyId = "clean_tube_family",
                    domains = listOf("https://www.eporner.com", "https://eporner.com"),
                    validationMarker = "videos",
                    name = "Eporner",
                    enabled = true,
                    baseUrl = "https://www.eporner.com",
                    adapter = "html_selector",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "/api/v2/video/search/?query=&per_page=30&page=1&thumbsize=big&order=top-weekly&format=json",
                        search = "/api/v2/video/search/?query={query}&per_page=30&page=1&thumbsize=big&format=json",
                        page = "/api/v2/video/search/?query=&per_page=30&page={page}&thumbsize=big&order=top-weekly&format=json",
                        categories = "/cats/"
                    ),
                    selectors = SelectorConfig(
                        item = "div.mb, div.video-box, div[data-id]",
                        title = "p.mbtitle a, a[title], .mbtitle",
                        thumbnail = "img[data-src], img",
                        thumbnailAttr = "data-src, src",
                        detailUrl = "a[href^='/video-'], p.mbtitle a",
                        duration = "span.mblength, span.duration",
                        detailTitle = "h1, .video-title",
                        detailDescription = ".video-description, p",
                        detailThumbnail = "meta[property='og:image'], img",
                        player = "video#video-player, video",
                        videoSource = "video source[src], a[href*='/dload/'], meta[property='og:video:url']",
                        videoSourceAttr = "src, href, content"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Web Index",
                        disclaimer = "Aggregated public media feed."
                    )
                ),
                // 63. DrTuber
                ProviderConfig(
                    id = "drtuber",
                    familyId = "clean_tube_family",
                    domains = listOf("https://www.drtuber.desi", "https://drtuber.desi", "https://m.drtuber.desi", "https://www.drtuber.com", "https://drtuber.com"),
                    validationMarker = "drtuber",
                    name = "DrTuber",
                    enabled = true,
                    baseUrl = "https://m.drtuber.desi",
                    adapter = "html_selector",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "/",
                        search = "/search/videos/{query}/{page}/",
                        page = "/latest-updates/{page}/",
                        categories = "/categories"
                    ),
                    selectors = SelectorConfig(
                        item = "a.th, a[data-video_id], a.video-thumb, div.th",
                        title = "a[title], img[alt], a.title",
                        thumbnail = "img.b-lazy, img[data-src], img",
                        thumbnailAttr = "data-src, src",
                        detailUrl = "a[href*='/video/']",
                        duration = "span.duration, strong.toolbar em, .time",
                        detailTitle = "h1, .page-title",
                        detailDescription = ".video-description, p",
                        detailThumbnail = "meta[property='og:image'], img",
                        player = "video, div#player",
                        videoSource = "a[data-adv_play], video source[src], meta[property='og:video:url']",
                        videoSourceAttr = "href, src, content, poster"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Web Index",
                        disclaimer = "Aggregated public media feed."
                    )
                ),
                // 64. NuVid
                ProviderConfig(
                    id = "nuvid",
                    familyId = "clean_tube_family",
                    domains = listOf("https://m.nuvid.org", "https://www.nuvid.org", "https://www.nuvid.com", "https://nuvid.com", "https://www.nuvid.org/", "https://nuvid.org"),
                    validationMarker = "videos",
                    name = "NuVid",
                    enabled = true,
                    baseUrl = "https://m.nuvid.org",
                    adapter = "html_selector",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "/",
                        search = "/search/videos/{query}/{page}/",
                        page = "/videos/{page}/",
                        categories = "/categories"
                    ),
                    selectors = SelectorConfig(
                        item = "div.vthumb, .box-tumb, div.th, div.video-thumb",
                        title = "span.title, span.title_thumb, a[title], a.thumb_holder, a.title, img[alt]",
                        thumbnail = "img.img_thumb, img[data-src], img",
                        thumbnailAttr = "data-src, src",
                        detailUrl = "a.thumb_holder, a.vid_link, a.video-thumb, a[href*='/video/']",
                        duration = "span.view_time, span.time_thumb, i.time, span.time, span.duration",
                        detailTitle = "h1, .page-title",
                        detailDescription = ".video-description, p",
                        detailThumbnail = "meta[property='og:image'], img#video_preview_thumb, video[poster]",
                        player = "video, div#player",
                        videoSource = "a.b_link, video source[src], meta[property='og:video:url']",
                        videoSourceAttr = "href, src, content"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Web Index",
                        disclaimer = "Aggregated public media feed."
                    )
                ),
                // 65. PornOne
                ProviderConfig(
                    id = "pornone",
                    familyId = "clean_tube_family",
                    domains = listOf("https://pornone.com", "https://www.pornone.com", "https://www.vporn.com"),
                    validationMarker = "thumb-block",
                    name = "PornOne",
                    enabled = true,
                    baseUrl = "https://pornone.com",
                    adapter = "html_selector",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "/videos/",
                        search = "/search/{query}/page/{page}/",
                        page = "/videos/page/{page}/",
                        categories = "/categories/"
                    ),
                    selectors = SelectorConfig(
                        item = "div.thumb-block, div.video-box, div.item",
                        title = "a.title, p.title a, a[title]",
                        thumbnail = "img.thumb, img[data-src], img",
                        thumbnailAttr = "data-src, src",
                        detailUrl = "a[href*='/video/'], a[href*='/videos/']",
                        duration = "span.duration, .time",
                        detailTitle = "h1, .page-title",
                        detailDescription = ".video-description, p",
                        detailThumbnail = "meta[property='og:image'], img",
                        player = "video, div#player",
                        videoSource = "video source[src], meta[property='og:video:url']",
                        videoSourceAttr = "src, content"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Web Index",
                        disclaimer = "Aggregated public media feed."
                    )
                ),
                // 66. DefineBabe
                ProviderConfig(
                    id = "definebabe",
                    familyId = "clean_tube_family",
                    domains = listOf("https://www.definebabe.com", "https://definebabe.com"),
                    validationMarker = "definebabe",
                    name = "DefineBabe",
                    enabled = true,
                    baseUrl = "https://www.definebabe.com",
                    adapter = "html_selector",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "/videos/",
                        search = "/videos/?s={query}",
                        page = "/videos/?page={page}",
                        categories = "/categories/"
                    ),
                    selectors = SelectorConfig(
                        item = "div.models-videos__col, div.models-videos > div",
                        title = "a[title], .models-footer p, .title",
                        thumbnail = "img.thumb, img.img-fluid, img",
                        thumbnailAttr = "data-src, src",
                        detailUrl = "a[href*='/video/'], a.models-image",
                        duration = "span.video, .duration, span.time",
                        detailTitle = "h1",
                        detailDescription = "p",
                        detailThumbnail = "meta[property='og:image'], img",
                        player = "#DF_player, video",
                        videoSource = "video source[src], video[src]",
                        videoSourceAttr = "src"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Web Index",
                        disclaimer = "Aggregated public media feed."
                    )
                ),
                // 67. 3Movs
                ProviderConfig(
                    id = "three_movs",
                    familyId = "clean_tube_family",
                    domains = listOf("https://www.3movs.xxx", "https://3movs.xxx", "https://www.3movs.com", "https://3movs.com"),
                    validationMarker = "3movs",
                    name = "3Movs",
                    enabled = true,
                    baseUrl = "https://www.3movs.xxx",
                    adapter = "html_selector",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "/videos/",
                        search = "/search/{query}/",
                        page = "/videos/{page}/",
                        categories = "/categories/"
                    ),
                    selectors = SelectorConfig(
                        item = "div.item.thumb",
                        title = "a[title], a.title, [title]",
                        thumbnail = "img",
                        thumbnailAttr = "data-webp, data-src, src",
                        detailUrl = "a[href*='/videos/']",
                        duration = ".time, .duration, span.time",
                        detailTitle = "h1",
                        detailDescription = "p",
                        detailThumbnail = "meta[property='og:image'], img",
                        player = "#kt_player, video",
                        videoSource = "meta[itemprop*='contentUrl'], meta[itemprop*='contentURL'], video source[src], video[src], source[type='video/mp4']",
                        videoSourceAttr = "src"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Web Index",
                        disclaimer = "Aggregated public media feed."
                    )
                ),
                // 68. TXXX
                ProviderConfig(
                    id = "txxx",
                    familyId = "clean_tube_family",
                    domains = listOf("https://txxx.com", "https://www.txxx.com"),
                    validationMarker = "txxx",
                    name = "TXXX",
                    enabled = true,
                    baseUrl = "https://txxx.com",
                    adapter = "html_selector",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "/api/json/videos2/86400/str/latest-updates/60/..1.all...json",
                        search = "/api/videos2.php?params=86400/str/relevance/60/search..1.all...&s={query}",
                        page = "/api/json/videos2/86400/str/latest-updates/60/..{page}.all...json",
                        categories = "/api/json/categories/86400/str.all.en.json"
                    ),
                    selectors = SelectorConfig(
                        item = "div.video-item, div.item",
                        title = "a.title, [title]",
                        thumbnail = "img",
                        thumbnailAttr = "src",
                        detailUrl = "a[href*='/videos/']",
                        duration = ".duration",
                        detailTitle = "h1",
                        detailDescription = "p",
                        detailThumbnail = "meta[property='og:image'], img",
                        player = "video",
                        videoSource = "video source[src], video[src]",
                        videoSourceAttr = "src"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Web Index",
                        disclaimer = "Aggregated public media feed."
                    )
                ),
                // 69. Upornia
                ProviderConfig(
                    id = "upornia",
                    familyId = "clean_tube_family",
                    domains = listOf("https://upornia.com", "https://www.upornia.com"),
                    validationMarker = "upornia",
                    name = "Upornia",
                    enabled = true,
                    baseUrl = "https://upornia.com",
                    adapter = "html_selector",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "/api/json/videos2/86400/str/latest-updates/60/..1.all...json",
                        search = "/api/videos2.php?params=86400/str/relevance/60/search..1.all...&s={query}",
                        page = "/api/json/videos2/86400/str/latest-updates/60/..{page}.all...json",
                        categories = "/api/json/categories/86400/str.all.en.json"
                    ),
                    selectors = SelectorConfig(
                        item = "div.video-item, div.item",
                        title = "a.title, [title]",
                        thumbnail = "img",
                        thumbnailAttr = "src",
                        detailUrl = "a[href*='/videos/']",
                        duration = ".duration",
                        detailTitle = "h1",
                        detailDescription = "p",
                        detailThumbnail = "meta[property='og:image'], img",
                        player = "video",
                        videoSource = "video source[src], video[src]",
                        videoSourceAttr = "src"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Web Index",
                        disclaimer = "Aggregated public media feed."
                    )
                ),
                // 70. HDZog
                ProviderConfig(
                    id = "hdzog",
                    familyId = "clean_tube_family",
                    domains = listOf("https://hdzog.com", "https://www.hdzog.com"),
                    validationMarker = "hdzog",
                    name = "HDZog",
                    enabled = true,
                    baseUrl = "https://hdzog.com",
                    adapter = "html_selector",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "/api/json/videos2/86400/str/latest-updates/60/..1.all...json",
                        search = "/api/videos2.php?params=86400/str/relevance/60/search..1.all...&s={query}",
                        page = "/api/json/videos2/86400/str/latest-updates/60/..{page}.all...json",
                        categories = "/api/json/categories/86400/str.all.en.json"
                    ),
                    selectors = SelectorConfig(
                        item = "div.video-item, div.item",
                        title = "a.title, [title]",
                        thumbnail = "img",
                        thumbnailAttr = "src",
                        detailUrl = "a[href*='/videos/']",
                        duration = ".duration",
                        detailTitle = "h1",
                        detailDescription = "p",
                        detailThumbnail = "meta[property='og:image'], img",
                        player = "video",
                        videoSource = "video source[src], video[src]",
                        videoSourceAttr = "src"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Web Index",
                        disclaimer = "Aggregated public media feed."
                    )
                ),
                // 71. MovieNerds
                ProviderConfig(
                    id = "movienerds",
                    familyId = "movie_series_family",
                    domains = listOf("https://movienerds.site", "https://api.movienerds.online", "https://api.themoviedb.org"),
                    validationMarker = "movienerds",
                    name = "MovieNerds",
                    enabled = true,
                    baseUrl = "https://movienerds.site",
                    adapter = "html_selector",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "https://api.themoviedb.org/3/trending/all/day?api_key=43dcff37851866de14d8acdce668a509",
                        search = "https://api.themoviedb.org/3/search/multi?api_key=43dcff37851866de14d8acdce668a509&query={query}",
                        page = "https://api.themoviedb.org/3/trending/all/day?api_key=43dcff37851866de14d8acdce668a509&page={page}",
                        categories = "https://api.themoviedb.org/3/genre/movie/list?api_key=43dcff37851866de14d8acdce668a509"
                    ),
                    selectors = SelectorConfig(
                        item = "div.movie-card, div.item",
                        title = "h2, h3, .title",
                        thumbnail = "img",
                        thumbnailAttr = "src",
                        detailUrl = "a",
                        duration = ".duration",
                        detailTitle = "h1",
                        detailDescription = "p",
                        detailThumbnail = "meta[property='og:image'], img",
                        player = "video",
                        videoSource = "video source[src], video[src]",
                        videoSourceAttr = "src"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Web Index",
                        disclaimer = "Aggregated public media feed."
                    )
                ),
                // 72. Cineapse
                ProviderConfig(
                    id = "cineapse",
                    familyId = "movie_series_family",
                    domains = listOf("https://cineapse.net"),
                    validationMarker = "cineapse",
                    name = "Cineapse",
                    enabled = true,
                    baseUrl = "https://cineapse.net",
                    adapter = "html_selector",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "https://cineapse.net/tmdb/trending/all/day",
                        search = "https://cineapse.net/tmdb/search/multi?query={query}",
                        page = "https://cineapse.net/tmdb/discover/movie?sort_by=popularity.desc&page={page}",
                        categories = "https://cineapse.net/api/categories?kind=movie"
                    ),
                    selectors = SelectorConfig(
                        item = "div.movie-card, div.item",
                        title = "h2, h3, .title",
                        thumbnail = "img",
                        thumbnailAttr = "src",
                        detailUrl = "a",
                        duration = ".duration",
                        detailTitle = "h1",
                        detailDescription = "p",
                        detailThumbnail = "meta[property='og:image'], img",
                        player = "video",
                        videoSource = "video source[src], video[src]",
                        videoSourceAttr = "src"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Web Index",
                        disclaimer = "Aggregated public media feed."
                    )
                ),
                // 73. MasaHub
                ProviderConfig(
                    id = "masahub",
                    familyId = "desi_family",
                    domains = listOf("https://masahub.net.im"),
                    validationMarker = "masahub",
                    name = "MasaHub",
                    enabled = true,
                    baseUrl = "https://masahub.net.im",
                    adapter = "html_selector",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "/",
                        search = "/?s={query}",
                        page = "/page/{page}/",
                        categories = "/categories/"
                    ),
                    selectors = SelectorConfig(
                        item = "main.site-main article.loop-video, article.loop-video",
                        title = "header.entry-header span, a[title], header.entry-header h2 a",
                        thumbnail = "img[data-src], img[src]",
                        thumbnailAttr = "data-src, src",
                        detailUrl = "a",
                        duration = "span.duration, .duration",
                        detailTitle = "h1.entry-title, h1, title",
                        detailDescription = ".entry-content p, .description",
                        detailThumbnail = "meta[property='og:image'], img",
                        player = "iframe[src*='playmate'], iframe[src*='lulust'], iframe[src*='lulu'], video, iframe",
                        videoSource = "iframe[src*='playmate'], iframe[src*='lulust'], iframe[src*='lulu'], video source[src], video[src], iframe",
                        videoSourceAttr = "src",
                        relatedItems = "article.loop-video"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Web Index",
                        disclaimer = "Aggregated public media feed."
                    )
                ),
                // 74. LiveCamRips
                ProviderConfig(
                    id = "livecamrips",
                    familyId = "desi_family",
                    domains = listOf("https://livecamrips.to", "https://www.livecamrips.to"),
                    validationMarker = "livecamrips",
                    name = "LiveCamRips",
                    enabled = true,
                    baseUrl = "https://livecamrips.to",
                    adapter = "html_selector",
                    capabilities = listOf(
                        ProviderCapability.HOME,
                        ProviderCapability.CATEGORY,
                        ProviderCapability.SEARCH,
                        ProviderCapability.DETAILS,
                        ProviderCapability.STREAM,
                        ProviderCapability.DOWNLOAD
                    ),
                    navigation = NavigationConfig(
                        home = "/model/492550/V_Hot_Shila",
                        search = "/search/{query}",
                        page = "/model/492550/V_Hot_Shila/{page}",
                        categories = "/models"
                    ),
                    selectors = SelectorConfig(
                        item = "div.jc-caption",
                        title = "div.tm-text-gray, figcaption, a[href*='/model/'], a[href*='/video/'], a",
                        thumbnail = "img[data-src], img[src]",
                        thumbnailAttr = "src",
                        detailUrl = "figure a[href*='/video/'], figure a[href*='/model/'], a[href*='/video/'], a[href*='/model/'], a",
                        duration = "figcaption span, span.tm-bg-dark, .tm-text-gray",
                        detailTitle = "h1, h2, title",
                        detailDescription = "div.tm-text-primary, p, .description",
                        detailThumbnail = "meta[property='og:image'], img",
                        player = "iframe[src*='uqload'], iframe[src*='abstream'], iframe[src*='mixdrop'], iframe[src*='mdzsmutpcvykb'], iframe[src*='videosh'], iframe[src*='upns'], iframe[src*='vidara'], iframe",
                        videoSource = "iframe[src*='uqload'], iframe[src*='abstream'], iframe[src*='mixdrop'], iframe[src*='mdzsmutpcvykb'], iframe[src*='videosh'], iframe[src*='upns'], iframe[src*='vidara'], iframe",
                        videoSourceAttr = "src",
                        relatedItems = "div.jc-caption"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Web Index",
                        disclaimer = "Aggregated public media feed."
                    )
                )
            )
        )
    }
}
}