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

class Sites18ToLastTest {

    private fun createAdapter(id: String, baseUrl: String, nav: NavigationConfig, selectors: SelectorConfig): HtmlSelectorAdapter {
        val config = ProviderConfig(
            id = id,
            name = id.replaceFirstChar { it.uppercase() },
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

    @Test
    fun testSite18PerfectGirls() = runBlocking {
        val adapter = createAdapter(
            id = "perfectgirls",
            baseUrl = "https://www.perfectgirls.xxx",
            nav = NavigationConfig(home = "/", page = "/{page}/", search = "/search/{query}/", categories = "/channels/"),
            selectors = SelectorConfig(
                item = "div.list_video_wrapper div.item, div.item, div.thumb-bl",
                title = "a.title, [title], a",
                thumbnail = "img",
                thumbnailAttr = "data-src, src",
                detailUrl = "a[href*='/video/'], a[href*='/videos/'], a",
                duration = ".duration"
            )
        )

        // 1. Pagination
        val p2 = adapter.buildPagedUrl("https://www.perfectgirls.xxx", 2)
        assertEquals("https://www.perfectgirls.xxx/2/", p2)

        // 2. Listing parsing
        val sampleHtml = """
            <div class="list_video_wrapper">
                <div class="item">
                    <a href="/video/12345/brunette-babe/" class="title" title="Brunette Babe Video">
                        <img src="https://img.perfectgirls.xxx/123.jpg" alt="Brunette Babe" />
                        <span class="duration">12:34</span>
                    </a>
                </div>
            </div>
        """.trimIndent()
        val feed = adapter.parseListingHtml(sampleHtml, 1).getOrThrow()
        assertEquals(1, feed.items.size)
        assertEquals("Brunette Babe Video", feed.items[0].title)
        assertTrue(feed.items[0].detailUrl.contains("/video/12345/brunette-babe/"))

        // 3. Media stream
        val detailHtml = """
            <video id="player">
                <source src="https://stream.perfectgirls.xxx/v/12345.mp4" type="video/mp4" label="720p">
            </video>
        """.trimIndent()
        val media = adapter.parsePlayableMediaHtml(detailHtml, "https://www.perfectgirls.xxx/video/12345/brunette-babe/").getOrThrow()
        assertTrue(media.isNotEmpty())
        assertEquals("https://stream.perfectgirls.xxx/v/12345.mp4", media[0].url)
        assertEquals("https://www.perfectgirls.xxx/", media[0].headersRequired["Referer"])
    }

    @Test
    fun testSite19DefineBabe() = runBlocking {
        val adapter = createAdapter(
            id = "definebabe",
            baseUrl = "https://www.definebabe.com",
            nav = NavigationConfig(
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
                duration = "span.video, .duration, span.time"
            )
        )

        // 1. Pagination
        val p2 = adapter.buildPagedUrl("https://www.definebabe.com/videos/", 2)
        assertEquals("https://www.definebabe.com/videos/?page=2", p2)
        val p2Cat = adapter.buildPagedUrl("https://www.definebabe.com/view/Doggystyle/", 2)
        assertEquals("https://www.definebabe.com/view/Doggystyle/?page=2", p2Cat)
        val p2Search = adapter.buildPagedUrl("https://www.definebabe.com/videos/?s=step+mom", 2)
        assertEquals("https://www.definebabe.com/videos/?s=step+mom&page=2", p2Search)

        // 2. Listing parsing
        val sampleHtml = """
            <div class="models-videos">
                <div class="models-videos__col">
                    <a href="/video/2xzh/Black-haired-Newbie/" class="models-image js-video-load" title="Black-haired newbie gets boned">
                        <img class="img-fluid thumb" src="//s3.definebabe.com/p/1/195/178792/pic11_.webp" alt="Black-haired newbie gets boned" />
                    </a>
                    <div class="models-footer">
                        <p class="mb-1">Black-haired newbie gets boned</p>
                        <span class="video">24:15</span>
                    </div>
                </div>
            </div>
        """.trimIndent()
        val feed = adapter.parseListingHtml(sampleHtml, 1).getOrThrow()
        assertEquals(1, feed.items.size)
        assertEquals("Black-haired newbie gets boned", feed.items[0].title)
        assertTrue(feed.items[0].detailUrl.contains("/video/2xzh/Black-haired-Newbie/"))
        assertEquals("https://s3.definebabe.com/p/1/195/178792/pic11_.webp", feed.items[0].thumbnailUrl)
        assertEquals(1455L, feed.items[0].durationSeconds)

        // 3. Category parsing
        val catHtml = """
            <div class="models-wost__col">
                <a href="/view/japanese/" class="models-image">
                    <img loading="lazy" src="//s3.definebabe.com/t/3/728/2b92ad730abe227631696b9c6c7b5ce9_4_big.webp" class="img-fluid">
                </a>
                <div class="models-footer d-flex align-items-sm-center flex-column flex-sm-row">
                    <p class="mb-1 mb-sm-0">Japanese</p>
                    <div class="d-flex align-items-center ml-sm-auto">
                        <span class="video d-flex align-items-center">1.2K</span>
                    </div>
                </div>
            </div>
            <a href="/view/facial/">Facial<span>21818</span></a>
        """.trimIndent()
        val cats = adapter.parseCategoriesHtml(catHtml)
        assertEquals(2, cats.size)
        assertEquals("Japanese", cats[0].name)
        assertTrue(cats[0].url.contains("/view/japanese/"))
        assertEquals("Facial21818", cats[1].name)
        assertTrue(cats[1].url.contains("/view/facial/"))

        // 4. PlayerJS Media parsing
        val detailHtml = """
            <html>
            <script>
            var player = new Playerjs({
                id:"DF_player",
                url:"https://www.definebabe.com/video/2xzh/Black-haired-Newbie/",
                file:"[720p]//www.definebabe.com/player/get_video.php?q=720p,[480p]//www.definebabe.com/player/get_video.php?q=480p",
                default_quality: "720p"
            });
            </script>
            </html>
        """.trimIndent()
        val media = adapter.parsePlayableMediaHtml(detailHtml, "https://www.definebabe.com/video/2xzh/Black-haired-Newbie/").getOrThrow()
        assertTrue(media.isNotEmpty())
        assertEquals("https://www.definebabe.com/player/get_video.php?q=720p", media[0].url)
        assertEquals("720p", media[0].quality)
        assertEquals(MediaSourceType.PROGRESSIVE_MP4, media[0].type)
        assertEquals("https://www.definebabe.com/video/2xzh/Black-haired-Newbie/", media[0].headersRequired["Referer"])
    }

    @Test
    fun testSite203Movs() = runBlocking {
        val adapter = createAdapter(
            id = "three_movs",
            baseUrl = "https://www.3movs.com",
            nav = NavigationConfig(
                home = "/latest-updates/",
                search = "/search/{query}/",
                page = "/latest-updates/{page}/",
                categories = "/categories/"
            ),
            selectors = SelectorConfig(
                item = "div.item.thumb, div.thumb_cat, div.thumbs > div.item",
                title = "a[title], a.title, [title]",
                thumbnail = "img",
                thumbnailAttr = "data-webp, data-src, src",
                detailUrl = "a[href*='/videos/']",
                duration = ".time, .duration, span.time"
            )
        )

        // 1. Pagination
        val p2 = adapter.buildPagedUrl("https://www.3movs.com/latest-updates/", 2)
        assertEquals("https://www.3movs.com/latest-updates/2/", p2)

        // 2. Listing parsing with live 3Movs DOM (data-webp, .time class)
        val sampleHtml = """
            <div class="thumbs">
                <div class="item thumb">
                    <a class="wrap_image" href="/videos/461865/passionate-bedroom-tryst/" title="Passionate Bedroom Tryst">
                        <img data-webp="https://img.3movs.com/contents/videos_screenshots/461000/461865/preview.webp" data-src="https://img.3movs.com/contents/videos_screenshots/461000/461865/preview.jpg" alt="Passionate Bedroom Tryst" />
                        <span class="time">12:34</span>
                    </a>
                </div>
            </div>
        """.trimIndent()
        val feed = adapter.parseListingHtml(sampleHtml, 1).getOrThrow()
        assertEquals(1, feed.items.size)
        assertEquals("Passionate Bedroom Tryst", feed.items[0].title)
        assertTrue(feed.items[0].detailUrl.contains("/videos/461865/"))
        assertEquals("https://img.3movs.com/contents/videos_screenshots/461000/461865/preview.webp", feed.items[0].thumbnailUrl)
        assertEquals(754L, feed.items[0].durationSeconds)

        // 3. Category parsing
        val catHtml = """
            <div class="thumbs">
                <div class="thumb_cat item">
                    <a class="th_cat" href="/categories/anal/" title="Anal">
                        <span class="title">Anal</span>
                    </a>
                </div>
                <div class="thumb_cat item">
                    <a class="th_cat" href="/categories/blowjob/" title="Blowjob">
                        <span class="title">Blowjob</span>
                    </a>
                </div>
            </div>
        """.trimIndent()
        val catFeed = adapter.parseCategoriesHtml(catHtml)
        assertEquals(2, catFeed.size)
        assertEquals("Anal", catFeed[0].name)
        assertTrue(catFeed[0].url.contains("/categories/anal/"))

        // 4. Media stream (flashvars format with access tokens)
        val detailHtml = """
            <script>
            var flashvars = {
                video_url: 'https://www.3movs.com/get_file/8/bfb2390f7a07bf1ef2e8b9195b452e50/461865.mp4/?v-acctoken=942512f46cf',
                video_alt_url: 'https://www.3movs.com/get_file/8/bfb2390f7a07bf1ef2e8b9195b452e50/461865_lq.mp4/?v-acctoken=942512f46cf'
            };
            </script>
        """.trimIndent()
        val media = adapter.parsePlayableMediaHtml(detailHtml, "https://www.3movs.com/videos/461865/passionate-bedroom-tryst/").getOrThrow()
        assertTrue(media.isNotEmpty())
        assertTrue(media.any { it.url.contains("461865.mp4/?v-acctoken=") })
        assertTrue(media.any { it.url.contains("461865_lq.mp4/?v-acctoken=") })
        assertEquals("https://www.3movs.com/", media[0].headersRequired["Referer"])
    }

    @Test
    fun testSites21To23AvsNetworkTxxxUporniaHdzog() = runBlocking {
        val adapter = createAdapter(
            id = "txxx",
            baseUrl = "https://txxx.com",
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
                detailUrl = "a[href*='/videos/']"
            )
        )

        // 1. Pagination
        val p2 = adapter.buildPagedUrl("https://txxx.com/api/json/videos2/86400/str/latest-updates/60/..1.all...json", 2)
        assertEquals("https://txxx.com/api/json/videos2/86400/str/latest-updates/60/..2.all...json", p2)
        val p3Cat = adapter.buildPagedUrl("https://txxx.com/api/json/videos2/86400/str/latest-updates/60/categories.hd.1.all...json", 3)
        assertEquals("https://txxx.com/api/json/videos2/86400/str/latest-updates/60/categories.hd.3.all...json", p3Cat)

        // 2. JSON Listing parsing
        val jsonListing = """
            {
                "videos": [
                    {
                        "video_id": "21792501",
                        "title": "My Friend's Naughty Stepmom",
                        "dir": "my-friends-naughty-stepmom",
                        "duration": "25:11",
                        "scr": "https://tn.txxx.tube/contents/videos_screenshots/21792000/21792501/288x162/1.jpg",
                        "pv": "vp2.txxx.com/c14/videos/21792000/21792501/21792501_tr.mp4"
                    }
                ],
                "page": 1,
                "total_count": 100
            }
        """.trimIndent()
        val feed = adapter.parseListingHtml(jsonListing, 1).getOrThrow()
        assertEquals(1, feed.items.size)
        val item = feed.items[0]
        assertEquals("21792501", item.id)
        assertEquals("My Friend's Naughty Stepmom", item.title)
        assertEquals("https://txxx.com/videos/21792501/my-friends-naughty-stepmom/", item.detailUrl)
        assertEquals("https://tn.txxx.tube/contents/videos_screenshots/21792000/21792501/288x162/1.jpg", item.thumbnailUrl)
        assertEquals("https://vp2.txxx.com/c14/videos/21792000/21792501/21792501_tr.mp4", item.metadata["previewUrl"])

        // 3. JSON Categories parsing
        val jsonCategories = """
            {
                "categories": [
                    {
                        "category_id": "32",
                        "title": "HD Porn",
                        "dir": "hd",
                        "total_videos": "1124188"
                    },
                    {
                        "category_id": "8",
                        "title": "Big Tits",
                        "dir": "big-tits",
                        "total_videos": "875334"
                    }
                ]
            }
        """.trimIndent()
        val cats = adapter.parseCategoriesHtml(jsonCategories)
        assertEquals(2, cats.size)
        assertEquals("32", cats[0].id)
        assertEquals("HD Porn", cats[0].name)
        assertEquals("https://txxx.com/api/json/videos2/86400/str/latest-updates/60/categories.hd.1.all...json", cats[0].url)

        // 4. Base164 Decoding test
        val encodedUrl = "L2dldF9maWxlLzI1L2Yw\u041cjQ4YjU3ZmQ1ZjQy\u041cWI3Y2QyNDJkNT\u04154NzNl\u041czZk\u041czU2OT\u041c1\u041cjhh\u041cS8y\u041cTc5\u041cj\u0410w\u041c\u04218y\u041cTc5\u041cjUw\u041cS8y\u041cTc5\u041cjUw\u041cV9ocS5tcDQvP2Q9\u041cTUx\u041cSZicj0yNT\u0415mdGk9\u041cTc5\u041cD\u04101ODk0Ng~~"
        val decoded = adapter.base164Decode(encodedUrl)
        assertTrue("Base164 decode must return mp4 path", decoded.contains("21792501_hq.mp4"))

        // 5. Media resolution (AVS dynamic stream or fallback trailer)
        val fallbackHtml = """
            <html>
            <script>
            var video = { "pv": "vp2.txxx.com/c14/videos/21792000/21792501/21792501_tr.mp4" };
            </script>
            </html>
        """.trimIndent()
        val media = adapter.parsePlayableMediaHtml(fallbackHtml, "https://txxx.com/videos/21792501/my-stepmom/").getOrThrow()
        assertTrue(media.isNotEmpty())
        assertTrue(media.any { it.url.contains("21792501") })
        assertEquals("https://txxx.com/", media[0].headersRequired["Referer"])
    }

    @Test
    fun test3MovsXxxFeedAndPagination() = runBlocking {
        val adapter = createAdapter(
            id = "three_movs",
            baseUrl = "https://www.3movs.xxx",
            nav = NavigationConfig(
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
                duration = ".time, .duration, span.time"
            )
        )
        // 1. Pagination check
        val p2 = adapter.buildPagedUrl("https://www.3movs.xxx/videos/", 2)
        assertEquals("https://www.3movs.xxx/videos/2/", p2)

        // 2. Listing parsing
        val sampleHtml = """
            <div class="thumbs">
                <div class="item thumb">
                    <a class="wrap_image" href="https://www.3movs.xxx/videos/462806/busty-brunette-dani-enjoys-making-herself-cum/" title="Busty Brunette Dani Enjoys Making Herself Cum">
                        <img data-webp="https://img.3movs.xxx/contents/videos_screenshots/462000/462806/321x181/12.jpg" alt="Busty Brunette" />
                        <span class="time">8:23</span>
                    </a>
                </div>
            </div>
        """.trimIndent()
        val feed = adapter.parseListingHtml(sampleHtml, 1).getOrThrow()
        assertEquals(1, feed.items.size)
        assertEquals("Busty Brunette Dani Enjoys Making Herself Cum", feed.items[0].title)
        assertEquals("https://img.3movs.xxx/contents/videos_screenshots/462000/462806/321x181/12.jpg", feed.items[0].thumbnailUrl)
        assertEquals(503L, feed.items[0].durationSeconds)
    }

    @Test
    fun testEmpFlixTitleExtraction() = runBlocking {
        val adapter = createAdapter(
            id = "empflix",
            baseUrl = "https://www.empflix.com",
            nav = NavigationConfig(
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
                duration = "div.video-duration, .thumb-icon"
            )
        )
        val sampleHtml = """
            <div class="col-xs-6 mb-3" data-vid="3728878">
                <a class="thumb video-thumb bg-dark" href="https://www.empflix.com/video3728878">
                    <img src="https://cdnl.empflix.com/thumb/3728878/17.jpg" alt="Ryan Reid seduces hot couple" />
                    <span class="video-duration">08:00</span>
                </a>
                <a class="video-title text-break" href="https://www.empflix.com/video3728878">
                    Ryan Reid seduces hot couple Kylie Rocket and Mick in BnB threesome
                </a>
            </div>
        """.trimIndent()
        val feed = adapter.parseListingHtml(sampleHtml, 1).getOrThrow()
        assertEquals(1, feed.items.size)
        assertEquals("Ryan Reid seduces hot couple Kylie Rocket and Mick in BnB threesome", feed.items[0].title)
        assertEquals("https://cdnl.empflix.com/thumb/3728878/17.jpg", feed.items[0].thumbnailUrl)
    }

    @Test
    fun testDrTuberCardSelection() = runBlocking {
        val adapter = createAdapter(
            id = "drtuber",
            baseUrl = "https://m.drtuber.desi",
            nav = NavigationConfig(home = "/", search = "/search/videos/{query}/{page}/", page = "/latest-updates/{page}/", categories = "/categories"),
            selectors = SelectorConfig(
                item = "a.th, a[data-video_id], a.video-thumb, div.th",
                title = "a[title], img[alt], a.title",
                thumbnail = "img.b-lazy, img[data-src], img",
                thumbnailAttr = "data-src, src",
                detailUrl = "a[href*='/video/']"
            )
        )
        val sampleHtml = """
            <div class="th thumb2x">
                <a class="th wrap-better-content" data-video_id="9384615" href="/video/9384615/xxx-romance-time-chut-mari-mara-hai" title="Xxx Romance Time Chut Mari Mara Hai">
                    <img src="https://g1.drtst.com/media/videos/tmb/9384615/240_180/12.jpg?1" alt="Xxx Romance Time" />
                </a>
                <a class="th wrap-better-content" data-video_id="9789530" href="/video/9789530/ellie-nova-horny-stepsister" title="Ellie Nova Horny Stepsister">
                    <img src="https://g2.drtst.com/media/videos/tmb/9789530/240_180/11.jpg?1" alt="Ellie Nova" />
                </a>
            </div>
        """.trimIndent()
        val feed = adapter.parseListingHtml(sampleHtml, 1).getOrThrow()
        assertEquals(2, feed.items.size)
        assertEquals("Xxx Romance Time Chut Mari Mara Hai", feed.items[0].title)
        assertEquals("Ellie Nova Horny Stepsister", feed.items[1].title)
    }

    @Test
    fun testEpornerApiJsonFeed() = runBlocking {
        val adapter = createAdapter(
            id = "eporner",
            baseUrl = "https://www.eporner.com",
            nav = NavigationConfig(
                home = "/api/v2/video/search/?query=&per_page=30&page=1&thumbsize=big&order=top-weekly&format=json",
                search = "/api/v2/video/search/?query={query}&per_page=30&page=1&thumbsize=big&format=json",
                page = "/api/v2/video/search/?query=&per_page=30&page={page}&thumbsize=big&order=top-weekly&format=json",
                categories = "/categories/"
            ),
            selectors = SelectorConfig(
                item = "div.mb, div.video-box, div[data-id]",
                title = "p.mbtitle a, a[title], .mbtitle",
                thumbnail = "img[data-src], img",
                thumbnailAttr = "data-src, src",
                detailUrl = "a[href^='/video-'], p.mbtitle a"
            )
        )
        val jsonApi = """
            {
                "count": 30,
                "total_count": 100000,
                "page": 1,
                "per_page": 30,
                "total_pages": 3334,
                "videos": [
                    {
                        "id": "py8fJDYVmWW",
                        "title": "Viral Sexy Social Media Influencer Hot Sex",
                        "url": "https://www.eporner.com/video-py8fJDYVmWW/viral-sexy-social-media-influencer-hot-sex/",
                        "length_sec": 1112,
                        "default_thumb": {
                            "size": "big",
                            "src": "https://static-ca-cdn.eporner.com/thumbs/static4/1/18/182/18227430/4_360.jpg"
                        }
                    }
                ]
            }
        """.trimIndent()
        val feed = adapter.parseListingHtml(jsonApi, 1).getOrThrow()
        assertEquals(1, feed.items.size)
        assertEquals("Viral Sexy Social Media Influencer Hot Sex", feed.items[0].title)
        assertEquals("https://static-ca-cdn.eporner.com/thumbs/static4/1/18/182/18227430/4_360.jpg", feed.items[0].thumbnailUrl)
        assertEquals(1112L, feed.items[0].durationSeconds)
        assertEquals("https://www.eporner.com/video-py8fJDYVmWW/viral-sexy-social-media-influencer-hot-sex/", feed.items[0].detailUrl)
        assertTrue(feed.hasNextPage)
    }

    @Test
    fun testAntarvasnaBfTurnstileNoFalseSecurityErrorAndPlayerResolution() = runBlocking {
        val adapter = createAdapter(
            id = "antarvasnabf",
            baseUrl = "https://antarvasnabf.com",
            nav = NavigationConfig(home = "/", search = "/?s={query}", page = "/page/{page}/"),
            selectors = SelectorConfig(
                item = "article, div.item, div.post",
                title = "h2.entry-title a, h2 a, a[title]",
                thumbnail = "img",
                thumbnailAttr = "src",
                detailUrl = "h2.entry-title a, h2 a, a",
                player = "iframe, video",
                videoSource = "iframe[src*='clean-tube-player'], iframe[src*='player-x'], video source[src], a[href*='.mp4']",
                videoSourceAttr = "src"
            )
        )
        // Detail page containing Cloudflare Turnstile script on WordPress comment form
        // and player-x.php?q= base64 payload
        val qPayload = "cG9zdF9pZD0xNTQ2ODkmdHlwZT12aWRlbyZ0YWc9JTNDdmlkZW8lMjBpZCUzRCUyMndwc3QtdmlkZW8lMjIlMjBjbGFzcyUzRCUyMnZpZGVvLWpzJTIwdmpzLWJpZy1wbGF5LWNlbnRlcmVkJTIyJTIwY29udHJvbHMlMjBwcmVsb2FkJTNEJTIyYXV0byUyMiUyMHdpZHRoJTNEJTIyNjQwJTIyJTIwaGVpZ2h0JTNEJTIyMjY0JTIyJTIwcG9zdGVyJTNEJTIyaHR0cHMlM0ElMkYlMkZ3d3cuYW50YXJ2YXNuYWJmLmNvbSUyRndwLWNvbnRlbnQlMkZ1cGxvYWRzJTJGMjAyNiUyRjA2JTJGZGVzaS1jb2xsZWdlLWdpcmwtYmRzbS12aWRlby02NDB4MzYwLmpwZyUyMiUzRSUzQ3NvdXJjZSUyMHNyYyUzRCUyMmh0dHBzJTNBJTJGJTJGY2RuMi5hbnRhcnZhc25hYmYuY29tJTJGMjAyNiUyRjA2JTJGZGVzaS1naXJsLWZ1bGwtYmRzbS1jaHVkYWktdmlkZW8ubXA0JTIyJTIwdHlwZSUzRCUyMnZpZGVvJTJGbXA0JTIyJTNFJTNDJTJGdmlkZW8lM0U="
        val detailHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Jawan college girl ki kutiya jaisi bdsm chudai video</title>
                <script src="https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit"></script>
                <script>
                    (function(w,d){var q=w.cfturnstileQueue=w.cfturnstileQueue||[];})();
                </script>
            </head>
            <body>
                <h1>Jawan college girl ki kutiya jaisi bdsm chudai video</h1>
                <iframe src="https://www.antarvasnabf.com/wp-content/plugins/clean-tube-player/public/player-x.php?q=$qPayload"></iframe>
            </body>
            </html>
        """.trimIndent()

        // Verify that this HTML does NOT throw CloudflareChallengeException
        val mediaResult = adapter.parsePlayableMediaHtml(detailHtml, "https://www.antarvasnabf.com/jawan-college-girl-ki-kutiya-jaisi-bdsm-chudai-video/")
        assertTrue("Media parsing should succeed without false security challenge", mediaResult.isSuccess)
        val media = mediaResult.getOrThrow()
        assertTrue(media.isNotEmpty())
        assertTrue(media.any { it.url.contains("desi-girl-full-bdsm-chudai-video.mp4") })
        assertEquals("https://www.antarvasnabf.com/", media[0].headersRequired["Referer"])
    }
}
