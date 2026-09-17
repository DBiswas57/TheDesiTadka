package com.thedesitadka.app

import android.content.Context
import com.thedesitadka.app.data.DashboardRepository
import com.thedesitadka.app.download.DownloadRepository
import com.thedesitadka.app.storage.AppDatabase
import com.thedesitadka.app.storage.PreferenceStore
import com.thedesitadka.core.config.ConfigCache
import com.thedesitadka.core.config.ConfigRepository
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
        DownloadRepository(context, providerEngine)
    }

    val dashboardRepository: DashboardRepository by lazy {
        DashboardRepository(providerEngine)
    }

    val adConfigRepository: AdConfigRepository by lazy {
        AdConfigRepository(context)
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

        // 2. Validate configuration against installed app version
        val defaultManifest = getDefaultManifest()
        val current = configCache.loadCurrent()
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

    private fun createDefaultManifest(): ProviderManifest {
        return ProviderManifest(
            schemaVersion = 1,
            configVersion = 133,
            minimumAppVersion = 4,
            forceUpdate = true,
            generatedAt = System.currentTimeMillis(),
            providers = listOf(
                // 1. KamaBaba
                ProviderConfig(
                    id = "kamababa1",
                    familyId = "clean_tube_family",
                    domains = listOf("https://www.kamababa1.com", "https://kamababa1.com"),
                    validationMarker = "kamababa",
                    name = "KamaBaba",
                    enabled = true,
                    baseUrl = "https://www.kamababa1.com",
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
                        item = "article.thumb-block, article.video-preview-item, article",
                        title = "a[title], h2.entry-title a, h2 a",
                        thumbnail = "img.video-main-thumb, img",
                        thumbnailAttr = "src",
                        detailUrl = "a[href*='kamababa1.com/']",
                        duration = ".duration",
                        detailTitle = "h1",
                        detailDescription = ".entry-content p, .video-details, p",
                        detailThumbnail = "meta[property='og:image'], img.video-main-thumb",
                        player = "iframe[src*='player']",
                        videoSource = "meta[itemprop='contentURL'], video source[src], video[src], source[type='video/mp4']",
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
                // 5. AagMaal
                ProviderConfig(
                    id = "aagmaal",
                    familyId = "aagmaal_family",
                    domains = listOf("https://aagmaal.date", "https://aagmaal.com"),
                    validationMarker = "aagmaal",
                    name = "AagMaal",
                    enabled = true,
                    baseUrl = "https://aagmaal.date",
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
                        item = "article.post, div.post, article",
                        title = "h2.entry-title a, h2 a, a[title]",
                        thumbnail = "img",
                        thumbnailAttr = "src",
                        detailUrl = "h2.entry-title a, a.entry-title, h2 a, a",
                        duration = ".duration",
                        detailTitle = "h1.entry-title, h1",
                        detailDescription = ".entry-content p, .video-details",
                        detailThumbnail = "meta[property='og:image'], img",
                        player = "iframe[src*='/e/'], iframe[src*='cdn1'], iframe[src*='tube279'], iframe[src*='lulu'], iframe[src*='streamtape'], video",
                        videoSource = "video source[src], video[src], source[type='video/mp4']",
                        videoSourceAttr = "src",
                        relatedItems = ".related-posts article, .related article"
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
                    domains = listOf("https://uncutmaza.cc"),
                    validationMarker = "uncutmaza",
                    name = "UncutMaza",
                    enabled = true,
                    baseUrl = "https://uncutmaza.cc",
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
                        page = "/page/{page}"
                    ),
                    selectors = SelectorConfig(
                        item = "article.thumb-block",
                        title = "h2.entry-title a, h2 a, a[title]",
                        thumbnail = "img",
                        thumbnailAttr = "src",
                        detailUrl = "h2.entry-title a, h2 a, a",
                        duration = ".duration",
                        detailTitle = "h1",
                        detailDescription = ".entry-content p, .video-details",
                        detailThumbnail = "meta[property='og:image'], img",
                        player = "iframe[src*='player']",
                        videoSource = "meta[itemprop='contentUrl'], meta[itemprop='contentURL'], video source[src], video[src], source[type='video/mp4']",
                        videoSourceAttr = "src",
                        relatedItems = ".under-video-block article, article.thumb-block"
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
                    baseUrl = "https://xnxx.health",
                    adapter = "html_selector",
                    familyId = "xvideos_network_family",
                    domains = listOf("https://xnxx.health", "https://www.xnxx.com", "https://xnxx.com"),
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
                        home = "/",
                        search = "/search/{query}/",
                        page = "/{page}"
                    ),
                    selectors = SelectorConfig(
                        item = "div.thumb-block.thumb-cat, div.thumb-block, div.mozaique > div",
                        title = "p.title a, a[title], .title a",
                        thumbnail = "img",
                        thumbnailAttr = "src",
                        detailUrl = "p.title a, a[href*='/todays-selection'], a[href*='/search/'], a[href*='/your-suggestions/'], a[href^='/video'], a",
                        duration = "span.duration",
                        detailTitle = "h1",
                        detailThumbnail = "meta[property='og:image']",
                        player = "video",
                        videoSource = "video source[src]",
                        videoSourceAttr = "src",
                        relatedItems = "div.thumb-block:not(.thumb-cat), div.mozaique > div, div.thumb-block"
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
                    domains = listOf("https://www.xvideos.com", "https://xvideos.com"),
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
                        page = "/new/{page}/"
                    ),
                    selectors = SelectorConfig(
                        item = "div.mozaique > div, div.thumb-block",
                        title = "p.title a, a[title]",
                        thumbnail = "img",
                        thumbnailAttr = "data-src",
                        detailUrl = "p.title a, .thumb-under p a, a[href^='/video']",
                        duration = "span.duration",
                        detailTitle = "h1",
                        detailThumbnail = "meta[property='og:image']",
                        player = "video",
                        videoSource = "video source[src]",
                        videoSourceAttr = "src"
                    ),
                    contentPolicy = ContentPolicy(
                        rightsStatus = "Public Web Index",
                        disclaimer = "Aggregated public media feed."
                    )
                ),
                // 24. AagMaal.com (Domain variant of AagMaal family)
                ProviderConfig(
                    id = "aagmaal_com",
                    familyId = "aagmaal_family",
                    domains = listOf("https://aagmaal.com", "https://aagmaal.date"),
                    validationMarker = "aagmaal",
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
                        page = "/page/{page}/"
                    ),
                    selectors = SelectorConfig(
                        item = "article, div.post, div.item",
                        title = "h2.entry-title a, h2 a, a[title]",
                        thumbnail = "img",
                        thumbnailAttr = "src",
                        detailUrl = "h2.entry-title a, h2 a, a",
                        duration = ".duration",
                        detailTitle = "h1",
                        detailDescription = ".entry-content p",
                        detailThumbnail = "meta[property='og:image'], img",
                        player = "iframe[src*='/e/'], iframe[src*='cdn1'], iframe[src*='tube279'], iframe[src*='lulu'], iframe[src*='streamtape'], video",
                        videoSource = "video source[src], video[src], source[type='video/mp4']",
                        videoSourceAttr = "src"
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