package com.thedesitadka.provider

import com.thedesitadka.core.model.MediaSourceType
import com.thedesitadka.core.model.NavigationConfig
import com.thedesitadka.core.model.ProviderCapability
import com.thedesitadka.core.model.ProviderConfig
import com.thedesitadka.core.model.SelectorConfig
import com.thedesitadka.provider.adapters.HtmlSelectorAdapter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SixBatchSitesIntegrationTest {

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
    fun testTube8LiveIntegration() = runBlocking {
        val adapter = createAdapter(
            id = "tube8",
            baseUrl = "https://www.tube8.es",
            nav = NavigationConfig(
                home = "/newest/page/1/",
                page = "/newest/page/{page}/",
                search = "/searches?q={query}",
                categories = "/categories/"
            ),
            selectors = SelectorConfig(
                item = "article.video-box, div.video-box, article[data-video-id]",
                title = "a.video-title-text, a.tm_video_title, img[alt], a[title]",
                thumbnail = "img.thumb-image, img[data-src], img[data-poster], img",
                thumbnailAttr = "data-src, data-poster, src",
                detailUrl = "a[href*='/porn-video/'], a.tm_video_link, a",
                duration = "div.video-duration, div.tm_video_duration, .video-properties",
                detailTitle = "h1.video_title, h1",
                player = "video, iframe",
                videoSource = "meta[property*='og:video'], meta[property='og:video:url']",
                videoSourceAttr = "content"
            )
        )

        // 1. Pagination
        val p2 = adapter.buildPagedUrl("https://www.tube8.es/newest/page/1/", 2)
        assertEquals("https://www.tube8.es/newest/page/2/", p2)

        // 2. Listing parsing
        val sampleHtml = """
            <article class="video-box pc js_video-box js-pop" data-video-id="423308452">
                <a class="tm_video_link" href="/porn-video/hot-milf-india/423308452/">
                    <img class="thumb-image" data-src="https://ei-ph.t8cdn.com/thumb.jpg" alt="Hot Indian Milf" />
                    <div class="video-duration">13:00</div>
                </a>
                <div class="video-title-wrapper">
                    <a class="video-title-text tm_video_title" href="/porn-video/hot-milf-india/423308452/">Hot Indian Milf</a>
                </div>
            </article>
        """.trimIndent()
        val feed = adapter.parseListingHtml(sampleHtml, 1).getOrThrow()
        assertEquals(1, feed.items.size)
        val item = feed.items[0]
        assertEquals("Hot Indian Milf", item.title)
        assertEquals("https://ei-ph.t8cdn.com/thumb.jpg", item.thumbnailUrl)
        assertEquals("https://www.tube8.es/porn-video/hot-milf-india/423308452/", item.detailUrl)

        // 3. Media stream resolution from Aylo mediaDefinitions with /media/hls/
        val detailHtml = """
            <html>
            <head>
                <script>
                    var flashvars = {
                        "mediaDefinitions": [
                            {"format":"hls","videoUrl":"https:\/\/www.tube8.es\/media\/hls\/?s=token123","quality":"720"},
                            {"format":"mp4","videoUrl":"https:\/\/www.tube8.es\/media\/mp4\/?s=token456","quality":"480"}
                        ]
                    };
                </script>
            </head>
            </html>
        """.trimIndent()
        val mediaResult = adapter.parsePlayableMediaHtml(detailHtml, "https://www.tube8.es/porn-video/hot-milf-india/423308452/")
        assertTrue(mediaResult.isSuccess)
        val media = mediaResult.getOrThrow()
        assertEquals(2, media.size)
        assertEquals(MediaSourceType.HLS, media[0].type)
        assertEquals("https://www.tube8.es/media/hls/?s=token123", media[0].url)
    }

    @Test
    fun testTNAFlixLiveIntegration() = runBlocking {
        val adapter = createAdapter(
            id = "tnaflix",
            baseUrl = "https://www.tnaflix.com",
            nav = NavigationConfig(
                home = "/",
                page = "/featured/{page}",
                search = "/search.php?what={query}",
                categories = "/categories"
            ),
            selectors = SelectorConfig(
                item = "div[data-vid], div.video-list > div, a.video-thumb",
                title = "a.video-title, a[title], img[alt]",
                thumbnail = "img[data-src], img",
                thumbnailAttr = "data-src, src",
                detailUrl = "a.video-title, a.video-thumb, a[href*='/video']",
                duration = "div.video-duration, .thumb-icon",
                detailTitle = "h1",
                videoSource = "video source[src]"
            )
        )

        val sampleHtml = """
            <div class="col-xs-6 mb-3" data-vid="3970438">
                <a class="video-thumb" href="/amateur-porn/Lexi-joins-Sarah/video3970438" title="Lexi joins Sarah">
                    <img data-src="https://img.tnaflix.com/thumb.jpg" alt="Lexi joins Sarah" />
                    <div class="video-duration">06:14</div>
                </a>
                <a class="video-title" href="/amateur-porn/Lexi-joins-Sarah/video3970438">Lexi joins Sarah</a>
            </div>
        """.trimIndent()
        val feed = adapter.parseListingHtml(sampleHtml, 1).getOrThrow()
        assertEquals(1, feed.items.size)
        val item = feed.items[0]
        assertEquals("Lexi joins Sarah", item.title)
        assertEquals("https://img.tnaflix.com/thumb.jpg", item.thumbnailUrl)
        assertEquals("https://www.tnaflix.com/amateur-porn/Lexi-joins-Sarah/video3970438", item.detailUrl)
    }

    @Test
    fun testNuVidLiveIntegration() = runBlocking {
        val adapter = createAdapter(
            id = "nuvid",
            baseUrl = "https://m.nuvid.org",
            nav = NavigationConfig(
                home = "/",
                page = "/videos/{page}",
                search = "/search/{query}/{page}",
                categories = "/categories"
            ),
            selectors = SelectorConfig(
                item = "div.vthumb, .box-tumb, div.th, div.video-thumb",
                title = "span.title, span.title_thumb, a[title], a.thumb_holder, a.title, img[alt]",
                thumbnail = "img.img_thumb, img[data-src], img",
                thumbnailAttr = "data-src, src",
                detailUrl = "a.thumb_holder, a.vid_link, a.video-thumb, a[href*='/video/']",
                duration = "span.view_time, span.time_thumb, i.time, span.time, span.duration",
                detailTitle = "h1, .page-title, title",
                detailThumbnail = "meta[property='og:image'], img#video_preview_thumb, video[poster]"
            )
        )

        val sampleHtml = """
            <!-- 1. First 12 items style: direct src -->
            <div class="vthumb" title="German Threesome Video">
                <a class="thumb_holder" href="/video/8078590/german-threesome">
                    <img class="img_thumb" src="https://g1.nvdst.com/media/videos/tmb/8078590/320_240/6.jpg" alt="German Threesome Video" />
                    <span class="tools_thumb">
                        <span class="view_time">15:03</span>
                        <span class="title_thumb">German Threesome Video</span>
                    </span>
                </a>
            </div>
            <!-- 2. Items 13 to 40 style: lazy loaded with bg.jpg placeholder and real data-src -->
            <div class="vthumb" title="Lesbian Babysitter">
                <a class="thumb_holder" href="/video/6425921/lesbian-babysitter">
                    <img class="img_thumb b-lazy" src="https://g0.nvdst.com/templates/frontend/mobile/images/bg.jpg" data-src="https://g3.nvdst.com/media/videos/tmb/6425921/320_240/3.jpg" alt="Lesbian Babysitter" />
                    <span class="tools_thumb">
                        <span class="view_time">06:09</span>
                        <span class="title_thumb">Lesbian Babysitter</span>
                    </span>
                </a>
            </div>
            <!-- 3. Desktop site style: box-tumb with span.title and i.time -->
            <div class="box-tumb related_vid wrap-better-content">
                <a class="th video-thumb vid_link ch-video" href="/video/4180536/japanese-teen" title="Japanese Teen">
                    <span class="container_image">
                        <img class="image" src="https://g2.nvdst.com/media/videos/tmb/4180536/240_147/11.jpg" alt="Japanese Teen">
                            <i class="time">12:30</i>
                        </img>
                    </span>
                    <span class="title">Japanese Teen</span>
                </a>
            </div>
        """.trimIndent()

        val feed = adapter.parseListingHtml(sampleHtml, 1).getOrThrow()
        assertEquals(3, feed.items.size)
        // Item 1
        assertEquals("German Threesome Video", feed.items[0].title)
        assertEquals("https://g1.nvdst.com/media/videos/tmb/8078590/320_240/6.jpg", feed.items[0].thumbnailUrl)
        assertEquals("https://m.nuvid.org/video/8078590/german-threesome", feed.items[0].detailUrl)

        // Item 2 (verifies data-src is used instead of bg.jpg)
        assertEquals("Lesbian Babysitter", feed.items[1].title)
        assertEquals("https://g3.nvdst.com/media/videos/tmb/6425921/320_240/3.jpg", feed.items[1].thumbnailUrl)
        assertEquals("https://m.nuvid.org/video/6425921/lesbian-babysitter", feed.items[1].detailUrl)

        // Item 3 (desktop .box-tumb)
        assertEquals("Japanese Teen", feed.items[2].title)
        assertEquals("https://g2.nvdst.com/media/videos/tmb/4180536/240_147/11.jpg", feed.items[2].thumbnailUrl)
        assertEquals("https://m.nuvid.org/video/4180536/japanese-teen", feed.items[2].detailUrl)

        // Pagination
        val p2 = adapter.buildPagedUrl("https://m.nuvid.org/videos/1", 2)
        assertEquals("https://m.nuvid.org/videos/2/", p2)

        // 3. NuVid Media Resolution from #download_page (gcdn)
        val playHtmlGcdn = """
            <div id="download_page">
                <div class="thumb-holder video">
                    <h5>German Threesome Video</h5>
                    <div class="th">
                        <img alt="German Threesome Video" src="https://g1.nvdst.com/media/videos/tmb/8078590/320_240/6.jpg"/>
                    </div>
                    <a href="https://gcdn.nuvid.org/mp4/510cde18077a45ae5e3df9e3a5252a5d.mp4?md5=hdoa0VO13UNZaAAJVQGKvw&expire=1790160752&speed=46k&buffer=823k&tip=1" class="b_link"> Continue to download video... </a>
                </div>
            </div>
        """.trimIndent()
        val mediaGcdn = adapter.parsePlayableMediaHtml(playHtmlGcdn, "https://m.nuvid.org/play/8078590").getOrThrow()
        assertEquals(1, mediaGcdn.size)
        assertEquals("https://gcdn.nuvid.org/mp4/510cde18077a45ae5e3df9e3a5252a5d.mp4?md5=hdoa0VO13UNZaAAJVQGKvw&expire=1790160752&speed=46k&buffer=823k&tip=1", mediaGcdn[0].url)
        assertEquals(MediaSourceType.PROGRESSIVE_MP4, mediaGcdn[0].type)
        assertEquals("720p", mediaGcdn[0].quality)
        assertEquals("https://m.nuvid.org/", mediaGcdn[0].headersRequired["Referer"])

        // 4. NuVid Media Resolution from #download_page (xcdn)
        val playHtmlXcdn = """
            <div id="download_page">
                <div class="thumb-holder video">
                    <h5>Big assed busty tit fuck</h5>
                    <a href="https://xcdn.nuvid.org/mp4/5cd6bc6c1598e9d2cf72443d9aa323ec.mp4?cdn_hash=55325ac40dac9242c942f322a404cada&cdn_creation_time=1790177072" class="b_link"> Continue to download video... </a>
                </div>
            </div>
        """.trimIndent()
        val mediaXcdn = adapter.parsePlayableMediaHtml(playHtmlXcdn, "https://m.nuvid.org/play/8725525").getOrThrow()
        assertEquals(1, mediaXcdn.size)
        assertEquals("https://xcdn.nuvid.org/mp4/5cd6bc6c1598e9d2cf72443d9aa323ec.mp4?cdn_hash=55325ac40dac9242c942f322a404cada&cdn_creation_time=1790177072", mediaXcdn[0].url)
        assertEquals(MediaSourceType.PROGRESSIVE_MP4, mediaXcdn[0].type)
        assertEquals("https://m.nuvid.org/", mediaXcdn[0].headersRequired["Referer"])

        // 5. Ensure preview clips (/media/videos/tmb/) are NOT returned as video sources
        val previewOnlyHtml = """
            <video preload="none" poster="https://g0.nvdst.com/media/videos/tmb/8078590/player/6.jpg"></video>
            <img data-webm="https://g0.nvdst.com/media/videos/tmb/8078590/8078590.mp4" />
        """.trimIndent()
        val mediaPreview = adapter.parsePlayableMediaHtml(previewOnlyHtml, "https://m.nuvid.org/video/8078590/german-threesome").getOrNull() ?: emptyList()
        assertTrue(mediaPreview.none { it.url.contains("/media/videos/tmb/") })
    }

    @Test
    fun testSpankBangAndPornOneSelectors() = runBlocking {
        val sbAdapter = createAdapter(
            id = "spankbang",
            baseUrl = "https://spankbang.com",
            nav = NavigationConfig(
                home = "/trending_videos",
                page = "/trending_videos/{page}/",
                search = "/s/{query}/",
                categories = "/categories"
            ),
            selectors = SelectorConfig(
                item = "div.video-item, div.item",
                title = "div.video-item a.title, a.title, .n a, a[title]",
                thumbnail = "img[data-src], img.cover, img",
                thumbnailAttr = "data-src, src",
                detailUrl = "a[href*='/video/'], a.title",
                duration = "span.l, .duration, span.length",
                detailTitle = "h1"
            )
        )

        val sbHtml = """
            <div class="video-item" data-id="12345">
                <a href="/video/12345/awesome-clip" class="title">Awesome Clip</a>
                <img data-src="https://sb.cdn.com/cover123.jpg" class="cover" />
                <span class="l">12:34</span>
            </div>
        """.trimIndent()

        val sbFeed = sbAdapter.parseListingHtml(sbHtml, 1).getOrThrow()
        assertEquals(1, sbFeed.items.size)
        assertEquals("Awesome Clip", sbFeed.items[0].title)
        assertEquals("https://sb.cdn.com/cover123.jpg", sbFeed.items[0].thumbnailUrl)

        val poAdapter = createAdapter(
            id = "pornone",
            baseUrl = "https://pornone.com",
            nav = NavigationConfig(
                home = "/videos/",
                page = "/videos/page/{page}/",
                search = "/search/{query}/page/{page}/",
                categories = "/categories/"
            ),
            selectors = SelectorConfig(
                item = "div.thumb-block, div.video-box, div.item",
                title = "a.title, p.title a, a[title]",
                thumbnail = "img.thumb, img[data-src], img",
                thumbnailAttr = "data-src, src",
                detailUrl = "a[href*='/video/'], a[href*='/videos/']",
                duration = "span.duration, .time"
            )
        )

        val poHtml = """
            <div class="thumb-block">
                <a href="/video/4567/sample-porn-one" class="title">Sample Porn One Video</a>
                <img data-src="https://po.cdn.com/thumb4567.jpg" class="thumb" />
                <span class="duration">22:15</span>
            </div>
        """.trimIndent()

        val poFeed = poAdapter.parseListingHtml(poHtml, 1).getOrThrow()
        assertEquals(1, poFeed.items.size)
        assertEquals("Sample Porn One Video", poFeed.items[0].title)
        assertEquals("https://po.cdn.com/thumb4567.jpg", poFeed.items[0].thumbnailUrl)
    }
}
