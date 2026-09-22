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
                search = "/search/?s={query}",
                page = "/videos/?page={page}",
                categories = "/categories/"
            ),
            selectors = SelectorConfig(
                item = "div.models-videos__col, div.models-videos > div",
                title = "a.models-image, a[title], .title",
                thumbnail = "img.img-fluid, img",
                thumbnailAttr = "data-src, src",
                detailUrl = "a.models-image, a[href*='/video/']",
                duration = ".duration, span.time"
            )
        )

        // 1. Pagination
        val p2 = adapter.buildPagedUrl("https://www.definebabe.com/videos/", 2)
        assertEquals("https://www.definebabe.com/videos/?page=2", p2)
        val p2Cat = adapter.buildPagedUrl("https://www.definebabe.com/view/Doggystyle/", 2)
        assertEquals("https://www.definebabe.com/view/Doggystyle/?page=2", p2Cat)

        // 2. Listing parsing
        val sampleHtml = """
            <div class="models-videos">
                <div class="models-videos__col">
                    <a href="/video/2xzh/Black-haired-Newbie/" class="models-image js-video-load" title="Black-haired newbie gets boned">
                        <img class="img-fluid" src="//s3.definebabe.com/p/1/195/178792/pic11_.webp" alt="Black-haired newbie gets boned" />
                        <span class="duration">24:15</span>
                    </a>
                </div>
            </div>
        """.trimIndent()
        val feed = adapter.parseListingHtml(sampleHtml, 1).getOrThrow()
        assertEquals(1, feed.items.size)
        assertEquals("Black-haired newbie gets boned", feed.items[0].title)
        assertTrue(feed.items[0].detailUrl.contains("/video/2xzh/Black-haired-Newbie/"))
        assertEquals("https://s3.definebabe.com/p/1/195/178792/pic11_.webp", feed.items[0].thumbnailUrl)

        // 3. Category parsing
        val catHtml = """
            <a href="/view/Doggystyle/">Doggystyle<span>48052</span></a>
            <a href="/view/facial/">Facial<span>21818</span></a>
        """.trimIndent()
        val cats = adapter.parseCategoriesHtml(catHtml)
        assertEquals(2, cats.size)
        assertEquals("Doggystyle48052", cats[0].name)
        assertTrue(cats[0].url.contains("/view/Doggystyle/"))

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
        assertEquals("https://www.definebabe.com/", media[0].headersRequired["Referer"])
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
                item = "div.item.thumb",
                title = "a[title], a.title, [title]",
                thumbnail = "img",
                thumbnailAttr = "data-src, src",
                detailUrl = "a[href*='/videos/']",
                duration = ".duration"
            )
        )

        // 1. Pagination
        val p2 = adapter.buildPagedUrl("https://www.3movs.com/latest-updates/", 2)
        assertEquals("https://www.3movs.com/latest-updates/2/", p2)

        // 2. Listing parsing
        val sampleHtml = """
            <div class="item thumb">
                <a href="/videos/445152/nasty-trans-tattooed-stud/" title="Nasty trans with Long Hair">
                    <img data-src="https://img.3movs.com/contents/videos_screenshots/445000/445152/320x180/1.jpg" alt="Nasty trans" />
                    <span class="duration">18:42</span>
                </a>
            </div>
        """.trimIndent()
        val feed = adapter.parseListingHtml(sampleHtml, 1).getOrThrow()
        assertEquals(1, feed.items.size)
        assertEquals("Nasty trans with Long Hair", feed.items[0].title)
        assertTrue(feed.items[0].detailUrl.contains("/videos/445152/"))
        assertEquals("https://img.3movs.com/contents/videos_screenshots/445000/445152/320x180/1.jpg", feed.items[0].thumbnailUrl)

        // 3. Media stream
        val detailHtml = """
            <script>
            var video_url = 'https://stream.3movs.com/v/445152_720.mp4';
            var video_alt_url = 'https://stream.3movs.com/v/445152_1080.mp4';
            </script>
        """.trimIndent()
        val media = adapter.parsePlayableMediaHtml(detailHtml, "https://www.3movs.com/videos/445152/nasty-trans-tattooed-stud/").getOrThrow()
        assertTrue(media.isNotEmpty())
        assertTrue(media.any { it.url == "https://stream.3movs.com/v/445152_720.mp4" })
        assertTrue(media.any { it.url == "https://stream.3movs.com/v/445152_1080.mp4" })
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
}
