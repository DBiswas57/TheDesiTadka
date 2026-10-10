package com.thedesitadka.provider

import com.thedesitadka.core.model.MediaSourceType
import com.thedesitadka.core.model.NavigationConfig
import com.thedesitadka.core.model.ProviderCapability
import com.thedesitadka.core.model.ProviderConfig
import com.thedesitadka.core.model.SelectorConfig
import com.thedesitadka.provider.adapters.HtmlSelectorAdapter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SixteenMissingSitesVerificationTest {

    private fun createAdapter(
        id: String,
        baseUrl: String,
        nav: NavigationConfig,
        selectors: SelectorConfig,
        familyId: String? = null
    ): HtmlSelectorAdapter {
        val config = ProviderConfig(
            id = id,
            name = id.replaceFirstChar { it.uppercase() },
            familyId = familyId,
            enabled = true,
            baseUrl = baseUrl,
            adapter = "html_selector",
            capabilities = listOf(
                ProviderCapability.HOME,
                ProviderCapability.CATEGORY,
                ProviderCapability.SEARCH,
                ProviderCapability.DETAILS,
                ProviderCapability.STREAM,
                ProviderCapability.DOWNLOAD
            ),
            navigation = nav,
            selectors = selectors
        )
        return HtmlSelectorAdapter(config)
    }

    // 1. SITE-002: Masa49
    @Test
    fun testMasa49ListingAndPagination() {
        val adapter = createAdapter(
            id = "masa49",
            baseUrl = "https://www.masa49.nl",
            nav = NavigationConfig(home = "/", search = "/?s={query}", page = "/page/{page}/"),
            selectors = SelectorConfig(
                item = "div.video-block, .video-block, article",
                title = "span.title, a.infos, a[title], h2",
                thumbnail = "img.video-img, img",
                thumbnailAttr = "data-src",
                detailUrl = "a.thumb, a.infos, a",
                duration = ".duration, .badge-duration",
                player = "video, iframe",
                videoSource = "video source[src], source[type='video/mp4'], video[src]"
            )
        )
        val sampleHtml = """
            <div class="videos">
                <div class="video-block">
                    <a href="https://www.masa49.nl/video/desi-scene-1/" class="thumb">
                        <img class="video-img" data-src="https://www.masa49.nl/thumbs/1.jpg" src="/placeholder.png" />
                        <span class="duration">15:20</span>
                    </a>
                    <a href="https://www.masa49.nl/video/desi-scene-1/" class="infos">
                        <span class="title">Desi Bhabhi Romance Episode 1</span>
                    </a>
                </div>
            </div>
        """.trimIndent()

        val parsed = adapter.parseListingHtml(sampleHtml, 1).getOrThrow()
        assertEquals(1, parsed.items.size)
        assertEquals("Desi Bhabhi Romance Episode 1", parsed.items[0].title)
        assertEquals("https://www.masa49.nl/video/desi-scene-1/", parsed.items[0].detailUrl)
        assertEquals("https://www.masa49.nl/thumbs/1.jpg", parsed.items[0].thumbnailUrl)

        val p2 = adapter.buildPagedUrl("https://www.masa49.nl/", 2)
        assertEquals("https://www.masa49.nl/page/2/", p2)
    }

    // 2. SITE-009: DesiBF
    @Test
    fun testDesiBfListingAndDirectCdnResolution() {
        val adapter = createAdapter(
            id = "desibf",
            baseUrl = "https://desibf.com",
            nav = NavigationConfig(home = "/", search = "/?s={query}", page = "/page/{page}/"),
            selectors = SelectorConfig(
                item = "article, div.item, div.post",
                title = "h2.entry-title a, h2 a, a[title]",
                thumbnail = "img",
                thumbnailAttr = "src",
                detailUrl = "h2.entry-title a, h2 a, a",
                duration = ".duration",
                videoSource = "video source[src], video[src], a[href*='.mp4'], iframe[src]"
            )
        )
        val sampleHtml = """
            <div class="posts-grid">
                <article class="post">
                    <a href="https://desibf.com/desi-village-couple/">
                        <img src="https://desibf.com/thumbs/couple.jpg" alt="Village Couple" />
                    </a>
                    <h2 class="entry-title">
                        <a href="https://desibf.com/desi-village-couple/">Desi Village Couple Intimate Scene</a>
                    </h2>
                    <span class="duration">18:45</span>
                </article>
            </div>
        """.trimIndent()

        val parsed = adapter.parseListingHtml(sampleHtml, 1).getOrThrow()
        assertEquals(1, parsed.items.size)
        assertEquals("Desi Village Couple Intimate Scene", parsed.items[0].title)
        assertEquals("https://desibf.com/desi-village-couple/", parsed.items[0].detailUrl)
        assertEquals("https://desibf.com/thumbs/couple.jpg", parsed.items[0].thumbnailUrl)

        val detailHtml = """
            <html><body>
                <h1>Desi Village Couple Intimate Scene</h1>
                <video controls>
                    <source src="https://cdn.desibf.com/videos/village_couple.mp4" type="video/mp4" />
                </video>
            </body></html>
        """.trimIndent()
        val media = adapter.parsePlayableMediaHtml(detailHtml, "https://desibf.com/desi-village-couple/").getOrThrow()
        assertEquals(1, media.size)
        assertEquals("https://cdn.desibf.com/videos/village_couple.mp4", media[0].url)
    }

    // 3. SITE-013: MasaFun
    @Test
    fun testMasaFunListingAndThumbnailStyleExtraction() {
        val adapter = createAdapter(
            id = "masafun",
            baseUrl = "https://masafun.art",
            nav = NavigationConfig(home = "/", search = "/?s={query}", page = "/page/{page}/"),
            selectors = SelectorConfig(
                item = "article.vcard, article, div.item, div.post",
                title = "h3.vtitle, h2 a, .video-title, a",
                thumbnail = ".thumb, img",
                thumbnailAttr = "style",
                detailUrl = "a",
                duration = "span.dur, .badge-duration, .duration",
                videoSource = "video source[src], source[type='video/mp4'], video[src]"
            )
        )
        val sampleHtml = """
            <div class="video-grid">
                <article class="vcard">
                    <a href="https://masafun.art/watch/hot-desi-mms/">
                        <div class="thumb" style="background-image: url('https://masafun.art/thumbs/mms1.jpg');"></div>
                        <h3 class="vtitle">Hot Desi MMS Leaked Clip</h3>
                        <span class="dur">10:15</span>
                    </a>
                </article>
            </div>
        """.trimIndent()

        val parsed = adapter.parseListingHtml(sampleHtml, 1).getOrThrow()
        assertEquals(1, parsed.items.size)
        assertEquals("Hot Desi MMS Leaked Clip", parsed.items[0].title)
        assertEquals("https://masafun.art/watch/hot-desi-mms/", parsed.items[0].detailUrl)
        assertEquals("https://masafun.art/thumbs/mms1.jpg", parsed.items[0].thumbnailUrl)
    }

    // 4. SITE-015: WowUncut
    @Test
    fun testWowUncutListingAndDetailParsing() {
        val adapter = createAdapter(
            id = "wowuncut",
            baseUrl = "https://wowuncut.com",
            nav = NavigationConfig(home = "/", search = "/?s={query}", page = "/page/{page}/"),
            selectors = SelectorConfig(
                item = "article.thumb-block, article.video-preview-item:not(.slide), article:not(.slide)",
                title = "a[title], a[data-title], h2.entry-title a, h2 a",
                thumbnail = "img.video-main-thumb, img",
                thumbnailAttr = "src",
                detailUrl = "a[title], a[href*='/video/'], a",
                duration = ".duration",
                videoSource = "video source[src], video[src], iframe[src]"
            )
        )
        val sampleHtml = """
            <div class="videos-list">
                <article class="thumb-block video-preview-item">
                    <a href="https://wowuncut.com/video/desi-uncut-scene/" title="Desi Uncut Premium Episode">
                        <img class="video-main-thumb" src="https://wowuncut.com/thumbs/ep1.jpg" alt="Thumb" />
                        <span class="duration">22:00</span>
                    </a>
                </article>
            </div>
        """.trimIndent()

        val parsed = adapter.parseListingHtml(sampleHtml, 1).getOrThrow()
        assertEquals(1, parsed.items.size)
        assertEquals("Desi Uncut Premium Episode", parsed.items[0].title)
        assertEquals("https://wowuncut.com/video/desi-uncut-scene/", parsed.items[0].detailUrl)
        assertEquals("https://wowuncut.com/thumbs/ep1.jpg", parsed.items[0].thumbnailUrl)
    }

    // 5. SITE-016: DesiKahani2
    @Test
    fun testDesiKahani2KvsStructureAndDataWebp() {
        val adapter = createAdapter(
            id = "desikahani2",
            baseUrl = "https://desikahani2.net",
            familyId = "kvs_tube_family",
            nav = NavigationConfig(home = "/videos/", search = "/videos/search/?q={query}", page = "/videos/latest-updates/{page}/"),
            selectors = SelectorConfig(
                item = "div.item, .video-item, div[data-video-id], article",
                title = "div.title a[title], strong.title, a.title, .video-title, a[title]",
                thumbnail = "img.thumb, img",
                thumbnailAttr = "data-webp",
                detailUrl = "a",
                duration = "span.duration, .duration",
                videoSource = "a[href*='/videos/get_file/'], video source[src], video[src]"
            )
        )
        val sampleHtml = """
            <div class="list-videos">
                <div class="item" data-video-id="5544">
                    <a href="/videos/5544/indian-bhabhi-devar-story/">
                        <img class="thumb" src="/placeholder.jpg" data-webp="https://desikahani2.net/contents/videos_screenshots/5000/5544/preview.webp" />
                        <span class="duration">14:10</span>
                    </a>
                    <div class="title">
                        <a href="/videos/5544/indian-bhabhi-devar-story/" title="Indian Bhabhi Devar Real Story">Indian Bhabhi Devar Real Story</a>
                    </div>
                </div>
            </div>
        """.trimIndent()

        val parsed = adapter.parseListingHtml(sampleHtml, 1).getOrThrow()
        assertEquals(1, parsed.items.size)
        assertEquals("Indian Bhabhi Devar Real Story", parsed.items[0].title)
        assertEquals("https://desikahani2.net/videos/5544/indian-bhabhi-devar-story/", parsed.items[0].detailUrl)
        assertEquals("https://desikahani2.net/contents/videos_screenshots/5000/5544/preview.webp", parsed.items[0].thumbnailUrl)

        val p2 = adapter.buildPagedUrl("https://desikahani2.net/videos/latest-updates/1/", 2)
        assertEquals("https://desikahani2.net/videos/latest-updates/2/", p2)
    }

    // 6. SITE-017: DesiTales2
    @Test
    fun testDesiTales2KvsStructureAndDataWebp() {
        val adapter = createAdapter(
            id = "desitales2",
            baseUrl = "https://desitales2.com",
            familyId = "kvs_tube_family",
            nav = NavigationConfig(home = "/videos/", search = "/videos/search/?q={query}", page = "/videos/latest-updates/{page}/"),
            selectors = SelectorConfig(
                item = "div.item, .video-item, div[data-video-id], article",
                title = "div.title a[title], strong.title, a.title, .video-title, a[title]",
                thumbnail = "img.thumb, img",
                thumbnailAttr = "data-webp",
                detailUrl = "a",
                duration = "span.duration, .duration",
                videoSource = "a[href*='/videos/get_file/'], video source[src], video[src]"
            )
        )
        val sampleHtml = """
            <div class="list-videos">
                <div class="item">
                    <a href="/videos/7711/hot-aunty-romance/">
                        <img class="thumb" data-webp="https://desitales2.com/thumbs/7711.webp" />
                    </a>
                    <strong class="title">
                        <a href="/videos/7711/hot-aunty-romance/" title="Hot Desi Aunty Romance">Hot Desi Aunty Romance</a>
                    </strong>
                </div>
            </div>
        """.trimIndent()

        val parsed = adapter.parseListingHtml(sampleHtml, 1).getOrThrow()
        assertEquals(1, parsed.items.size)
        assertEquals("Hot Desi Aunty Romance", parsed.items[0].title)
        assertEquals("https://desitales2.com/videos/7711/hot-aunty-romance/", parsed.items[0].detailUrl)
        assertEquals("https://desitales2.com/thumbs/7711.webp", parsed.items[0].thumbnailUrl)
    }

    // 7. SITE-019: XXXIndianStories
    @Test
    fun testXxxIndianStoriesKvsParsing() {
        val adapter = createAdapter(
            id = "xxxindianstories",
            baseUrl = "https://xxxindianstories.com",
            familyId = "kvs_tube_family",
            nav = NavigationConfig(home = "/videos/", search = "/videos/search/?q={query}", page = "/videos/latest-updates/{page}/"),
            selectors = SelectorConfig(
                item = "div.item, .video-item, div[data-video-id], article",
                title = "div.title a[title], strong.title, a.title, .video-title, a[title]",
                thumbnail = "img.thumb, img",
                thumbnailAttr = "data-webp",
                detailUrl = "a",
                duration = "span.duration, .duration",
                videoSource = "a[href*='/videos/get_file/'], video source[src], video[src]"
            )
        )
        val sampleHtml = """
            <div class="list-videos">
                <div class="item">
                    <a href="/videos/9900/sexy-desi-audio-story/">
                        <img class="thumb" data-webp="https://xxxindianstories.com/thumbs/9900.webp" />
                        <span class="duration">25:00</span>
                    </a>
                    <div class="title">
                        <a href="/videos/9900/sexy-desi-audio-story/" title="Sexy Desi Audio Story HD">Sexy Desi Audio Story HD</a>
                    </div>
                </div>
            </div>
        """.trimIndent()

        val parsed = adapter.parseListingHtml(sampleHtml, 1).getOrThrow()
        assertEquals(1, parsed.items.size)
        assertEquals("Sexy Desi Audio Story HD", parsed.items[0].title)
        assertEquals("https://xxxindianstories.com/videos/9900/sexy-desi-audio-story/", parsed.items[0].detailUrl)
    }

    // 8. SITE-020: XMaza
    @Test
    fun testXMazaListingAndDataBgParsing() {
        val adapter = createAdapter(
            id = "xmaza",
            baseUrl = "https://xmaza.xxx",
            nav = NavigationConfig(home = "/", search = "/?s={query}", page = "/page/{page}/"),
            selectors = SelectorConfig(
                item = "a.video, article, div.item, div.post",
                title = "h2.vtitle, a[title], h2, a",
                thumbnail = "a.video, img",
                thumbnailAttr = "data-bg, style",
                detailUrl = "a.video, a",
                duration = ".duration",
                videoSource = "video source[src], video[src], iframe[src]"
            )
        )
        val sampleHtml = """
            <div class="videos">
                <a class="video" data-bg="https://xmaza.xxx/thumbs/clip1.jpg" href="https://xmaza.xxx/clip/desi-bhabhi-full/" title="Desi Bhabhi Full Video">
                    <h2 class="vtitle">Desi Bhabhi Full Video</h2>
                    <span class="duration">16:30</span>
                </a>
            </div>
        """.trimIndent()

        val parsed = adapter.parseListingHtml(sampleHtml, 1).getOrThrow()
        assertEquals(1, parsed.items.size)
        assertEquals("Desi Bhabhi Full Video", parsed.items[0].title)
        assertEquals("https://xmaza.xxx/clip/desi-bhabhi-full/", parsed.items[0].detailUrl)
        assertEquals("https://xmaza.xxx/thumbs/clip1.jpg", parsed.items[0].thumbnailUrl)
    }

    // 9. SITE-026: DesiBabe
    @Test
    fun testDesiBabeListingAndSelfLinkParsing() {
        val adapter = createAdapter(
            id = "desibabe",
            baseUrl = "https://desibabe.to",
            nav = NavigationConfig(home = "/", search = "/search?q={query}", page = "/?page={page}"),
            selectors = SelectorConfig(
                item = "a[href*='/post/']",
                title = "h3, img[alt]",
                thumbnail = "img",
                thumbnailAttr = "src",
                detailUrl = "this",
                duration = ".duration",
                videoSource = "iframe[src*='downloaddirect.xyz'], iframe, video source"
            )
        )
        val sampleHtml = """
            <div class="grid">
                <a href="https://desibabe.to/post/hot-actress-clip/" class="post-card">
                    <img src="https://desibabe.to/thumbs/actress.jpg" alt="Hot Actress Clip" />
                    <h3>Hot Actress Clip 1080p</h3>
                    <span class="duration">08:45</span>
                </a>
            </div>
        """.trimIndent()

        val parsed = adapter.parseListingHtml(sampleHtml, 1).getOrThrow()
        assertEquals(1, parsed.items.size)
        assertEquals("Hot Actress Clip 1080p", parsed.items[0].title)
        assertEquals("https://desibabe.to/post/hot-actress-clip/", parsed.items[0].detailUrl)
        assertEquals("https://desibabe.to/thumbs/actress.jpg", parsed.items[0].thumbnailUrl)
    }

    // 10. SITE-027: DesiGirlXX
    @Test
    fun testDesiGirlXXListingAndDetailParsing() {
        val adapter = createAdapter(
            id = "desigirlxx",
            baseUrl = "https://desigirlxx.beer",
            nav = NavigationConfig(home = "/", search = "/?s={query}", page = "/page/{page}/"),
            selectors = SelectorConfig(
                item = "article.loop-video, article.thumb-block, article.video-preview-item",
                title = "header h2 a, a[title]",
                thumbnail = "img.video-main-thumb, img",
                thumbnailAttr = "data-main-thumb, src, data-src",
                detailUrl = "header h2 a, a",
                duration = ".duration",
                videoSource = "iframe[src*='playmate.to'], iframe, video source, source[type='video/mp4']"
            )
        )
        val sampleHtml = """
            <div class="post-list">
                <article class="loop-video">
                    <header>
                        <h2><a href="https://desigirlxx.beer/hot-college-girl/" title="Hot College Girl Private MMS">Hot College Girl Private MMS</a></h2>
                    </header>
                    <a href="https://desigirlxx.beer/hot-college-girl/">
                        <img class="video-main-thumb" data-main-thumb="https://desigirlxx.beer/thumbs/college.jpg" src="/ph.png" />
                        <span class="duration">12:00</span>
                    </a>
                </article>
            </div>
        """.trimIndent()

        val parsed = adapter.parseListingHtml(sampleHtml, 1).getOrThrow()
        assertEquals(1, parsed.items.size)
        assertEquals("Hot College Girl Private MMS", parsed.items[0].title)
        assertEquals("https://desigirlxx.beer/hot-college-girl/", parsed.items[0].detailUrl)
        assertEquals("https://desigirlxx.beer/thumbs/college.jpg", parsed.items[0].thumbnailUrl)
    }

    // 11. SITE-028: DesiMaals
    @Test
    fun testDesiMaalsListingAndDetailParsing() {
        val adapter = createAdapter(
            id = "desimaals",
            baseUrl = "https://www.desimaals.fun",
            nav = NavigationConfig(home = "/?filter=latest", search = "/?s={query}", page = "/page/{page}/?filter=latest"),
            selectors = SelectorConfig(
                item = "article.loop-video, article.thumb-block, article.post",
                title = "header h2 a, a[title]",
                thumbnail = "img.video-main-thumb, img",
                thumbnailAttr = "src, data-src",
                detailUrl = "header h2 a, a",
                duration = ".duration",
                videoSource = "iframe[src*='clean-tube-player'], iframe, video source, source[type='video/mp4']"
            )
        )
        val sampleHtml = """
            <div class="posts">
                <article class="thumb-block">
                    <header>
                        <h2><a href="https://www.desimaals.fun/desi-web-series-ep1/">Desi Web Series Episode 1</a></h2>
                    </header>
                    <a href="https://www.desimaals.fun/desi-web-series-ep1/">
                        <img class="video-main-thumb" src="https://www.desimaals.fun/thumbs/web1.jpg" />
                        <span class="duration">19:30</span>
                    </a>
                </article>
            </div>
        """.trimIndent()

        val parsed = adapter.parseListingHtml(sampleHtml, 1).getOrThrow()
        assertEquals(1, parsed.items.size)
        assertEquals("Desi Web Series Episode 1", parsed.items[0].title)
        assertEquals("https://www.desimaals.fun/desi-web-series-ep1/", parsed.items[0].detailUrl)
    }

    // 12. SITE-029: DesiVideo
    @Test
    fun testDesiVideoListingAndPosterThumbnail() {
        val adapter = createAdapter(
            id = "desivideo",
            baseUrl = "https://desivideo.net",
            nav = NavigationConfig(home = "/latest-video", search = "/search?s={query}", page = "?page={page}"),
            selectors = SelectorConfig(
                item = "article.thumb-block, article.post",
                title = "a[title]",
                thumbnail = "video[poster], img",
                thumbnailAttr = "poster, src",
                detailUrl = "a",
                duration = ".duration",
                player = "video#main-video, video",
                videoSource = "video#main-video source, meta[property='og:video'], video source, source[type='video/mp4']"
            )
        )
        val sampleHtml = """
            <div class="articles">
                <article class="thumb-block">
                    <a href="https://desivideo.net/video-1029/" title="Desi Video Viral Clip">
                        <video poster="https://desivideo.net/posters/1029.jpg"></video>
                        <span class="duration">07:50</span>
                    </a>
                </article>
            </div>
        """.trimIndent()

        val parsed = adapter.parseListingHtml(sampleHtml, 1).getOrThrow()
        assertEquals(1, parsed.items.size)
        assertEquals("Desi Video Viral Clip", parsed.items[0].title)
        assertEquals("https://desivideo.net/video-1029/", parsed.items[0].detailUrl)
        assertEquals("https://desivideo.net/posters/1029.jpg", parsed.items[0].thumbnailUrl)
    }

    // 13. SITE-053: RedTube
    @Test
    fun testRedTubeListingAndPagination() {
        val adapter = createAdapter(
            id = "redtube",
            baseUrl = "https://www.redtube.com",
            nav = NavigationConfig(home = "/", search = "/?search={query}", page = "/?page={page}"),
            selectors = SelectorConfig(
                item = "li.video_item, div.video_block, .video-thumb, li[data-video-id]",
                title = "a[title], .video_title a",
                thumbnail = "img[data-src], img[src]",
                thumbnailAttr = "data-src, src",
                detailUrl = "a.video_link, a[href*='/']",
                duration = ".video_duration, .duration",
                videoSource = "meta[property='og:video:url']"
            )
        )
        val sampleHtml = """
            <ul class="video_list">
                <li class="video_item" data-video-id="998877">
                    <a href="/998877" class="video_link" title="RedTube Desi Exclusive">
                        <img data-src="https://img01.redtubefiles.net/998877/preview.jpg" src="/ph.jpg" />
                        <span class="video_duration">11:15</span>
                    </a>
                </li>
            </ul>
        """.trimIndent()

        val parsed = adapter.parseListingHtml(sampleHtml, 1).getOrThrow()
        assertEquals(1, parsed.items.size)
        assertEquals("RedTube Desi Exclusive", parsed.items[0].title)
        assertEquals("https://www.redtube.com/998877", parsed.items[0].detailUrl)
        assertEquals("https://img01.redtubefiles.net/998877/preview.jpg", parsed.items[0].thumbnailUrl)

        val p2 = adapter.buildPagedUrl("https://www.redtube.com", 2)
        assertEquals("https://www.redtube.com?page=2", p2)
    }

    // 14. SITE-054: YouPorn
    @Test
    fun testYouPornListingAndQueryPagination() {
        val adapter = createAdapter(
            id = "youporn",
            baseUrl = "https://www.you-porn.com",
            nav = NavigationConfig(home = "/", search = "/search/?query={query}", page = "/?page={page}"),
            selectors = SelectorConfig(
                item = "div.video-box, .four-col-content-item, .video_thumb, [data-video-id], a.categoryBox",
                title = "a[title], .video-title-text, .video-title, span.title, p.tm_category_title, .categoryTitle p",
                thumbnail = "img[data-src], img[data-thumbnail], img[src]",
                thumbnailAttr = "data-src, data-thumbnail, src",
                detailUrl = "a[href*='/watch/'], a.categoryBox, a[href*='/category/']",
                duration = ".video-duration, .duration",
                videoSource = "meta[property='og:video:url']"
            )
        )
        val sampleHtml = """
            <div class="video-list">
                <div class="video-box" data-video-id="12345">
                    <a href="/watch/12345/desi-girl-webcam/" title="Desi Girl Webcam Clip">
                        <img data-src="https://fs.you-porn.com/thumbs/12345.jpg" />
                        <span class="video-duration">09:40</span>
                    </a>
                </div>
            </div>
        """.trimIndent()

        val parsed = adapter.parseListingHtml(sampleHtml, 1).getOrThrow()
        assertEquals(1, parsed.items.size)
        assertEquals("Desi Girl Webcam Clip", parsed.items[0].title)
        assertEquals("https://www.you-porn.com/watch/12345/desi-girl-webcam/", parsed.items[0].detailUrl)
        assertEquals("https://fs.you-porn.com/thumbs/12345.jpg", parsed.items[0].thumbnailUrl)

        val p2 = adapter.buildPagedUrl("https://www.you-porn.com", 2)
        assertEquals("https://www.you-porn.com?page=2", p2)
    }

    // 15. SITE-069: Upornia
    @Test
    fun testUporniaJsonApiFeedAndPagination() {
        val adapter = createAdapter(
            id = "upornia",
            baseUrl = "https://upornia.com",
            nav = NavigationConfig(
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
                videoSource = "video source[src], video[src]"
            )
        )
        val sampleJson = """
            {
                "total_count": 100,
                "videos": [
                    {
                        "video_id": "889900",
                        "title": "Upornia Hot Desi Scene",
                        "duration": 650,
                        "scr": "https://img.upornia.com/889900.jpg",
                        "url": "/videos/889900/upornia-hot-desi-scene/"
                    }
                ]
            }
        """.trimIndent()

        val parsed = adapter.parseListingHtml(sampleJson, 1).getOrThrow()
        assertEquals(1, parsed.items.size)
        assertEquals("Upornia Hot Desi Scene", parsed.items[0].title)
        assertEquals("https://upornia.com/videos/889900/upornia-hot-desi-scene/", parsed.items[0].detailUrl)
        assertEquals("https://img.upornia.com/889900.jpg", parsed.items[0].thumbnailUrl)

        val p2 = adapter.buildPagedUrl("https://upornia.com/api/json/videos2/86400/str/latest-updates/60/..1.all...json", 2)
        assertEquals("https://upornia.com/api/json/videos2/86400/str/latest-updates/60/..2.all...json", p2)
    }

    // 16. SITE-070: HDZog
    @Test
    fun testHDZogJsonApiFeedAndPagination() {
        val adapter = createAdapter(
            id = "hdzog",
            baseUrl = "https://hdzog.com",
            nav = NavigationConfig(
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
                videoSource = "video source[src], video[src]"
            )
        )
        val sampleJson = """
            {
                "total_count": 100,
                "videos": [
                    {
                        "video_id": "112233",
                        "title": "HDZog Premium Indian Video",
                        "duration": 900,
                        "scr": "https://img.hdzog.com/112233.jpg",
                        "url": "/videos/112233/hdzog-premium-indian-video/"
                    }
                ]
            }
        """.trimIndent()

        val parsed = adapter.parseListingHtml(sampleJson, 1).getOrThrow()
        assertEquals(1, parsed.items.size)
        assertEquals("HDZog Premium Indian Video", parsed.items[0].title)
        assertEquals("https://hdzog.com/videos/112233/hdzog-premium-indian-video/", parsed.items[0].detailUrl)
        assertEquals("https://img.hdzog.com/112233.jpg", parsed.items[0].thumbnailUrl)

        val p2 = adapter.buildPagedUrl("https://hdzog.com/api/json/videos2/86400/str/latest-updates/60/..1.all...json", 2)
        assertEquals("https://hdzog.com/api/json/videos2/86400/str/latest-updates/60/..2.all...json", p2)
    }
}
