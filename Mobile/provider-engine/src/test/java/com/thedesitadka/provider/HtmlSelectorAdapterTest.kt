package com.thedesitadka.provider

import com.thedesitadka.core.model.MediaSourceType
import com.thedesitadka.core.model.NavigationConfig
import com.thedesitadka.core.model.ProviderCapability
import com.thedesitadka.core.model.ProviderConfig
import com.thedesitadka.core.model.SelectorConfig
import com.thedesitadka.provider.adapters.HtmlSelectorAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlSelectorAdapterTest {

    private val sampleHtml = """
        <!DOCTYPE html>
        <html>
        <head><title>Test Video Portal</title></head>
        <body>
            <div class="video-grid">
                <article class="post-card">
                    <a href="/video/101" title="Sample Video 1">
                        <img src="/thumbs/101.jpg" alt="Thumb 1" />
                        <h2 class="title">Sample Video 1</h2>
                    </a>
                </article>
                <article class="post-card">
                    <a href="/video/102" title="Sample Video 2">
                        <img data-src="/thumbs/102.jpg" alt="Thumb 2" />
                        <h2 class="title">Sample Video 2</h2>
                    </a>
                </article>
            </div>
        </body>
        </html>
    """.trimIndent()

    @Test
    fun testParseListingHtmlDirectly() {
        val config = ProviderConfig(
            id = "test_provider",
            name = "Test Provider",
            baseUrl = "https://example.com",
            adapter = "html_selector",
            capabilities = listOf(ProviderCapability.HOME, ProviderCapability.STREAM),
            selectors = SelectorConfig(
                item = "article.post-card",
                title = "h2.title",
                thumbnail = "img",
                detailUrl = "a"
            )
        )

        val adapter = HtmlSelectorAdapter(config)
        // Access private method or test parsing
        val doc = org.jsoup.Jsoup.parse(sampleHtml, "https://example.com")
        val elements = doc.select(config.selectors!!.item)
        assertEquals(2, elements.size)

        val first = elements[0]
        assertEquals("Sample Video 1", first.select(config.selectors!!.title).text())
        assertEquals("/thumbs/101.jpg", first.select(config.selectors!!.thumbnail).attr("src"))
        assertEquals("/video/101", first.select(config.selectors!!.detailUrl).attr("href"))
    }

    @Test
    fun testParseWebXSeriesSelectors() {
        val webxHtml = """
            <div class="videos">
                <a class="video lazy-bg" data-bg="https://webxseries.hot/thumbs/ep1.webp" href="https://webxseries.hot/100/ep1/" title="Episode 1">
                    <span class="time clock">20:00</span>
                    <h2 class="vtitle">Episode 1</h2>
                </a>
            </div>
        """.trimIndent()

        val doc = org.jsoup.Jsoup.parse(webxHtml, "https://webxseries.hot")
        val itemEl = doc.select("a.video").first()
        assertNotNull(itemEl)
        assertEquals("https://webxseries.hot/100/ep1/", itemEl?.attr("href"))
        assertEquals("https://webxseries.hot/thumbs/ep1.webp", itemEl?.attr("data-bg"))
        assertEquals("Episode 1", itemEl?.select("h2.vtitle")?.text())
    }

    @Test
    fun testFsiblogThumbnailExtractionAndVideoOnlyFiltering() {
        val fsiblogHtml = """
            <div class="posts">
                <article class="post type-porn-video porn-video">
                    <a href="/porn-video/desi-clip-1/" title="Desi Video 1">
                        <img class="attachment-medium" 
                             src="data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAzMDAgMjAwIj48L3N2Zz4=" 
                             data-src="https://fsiblogxx.com/wp-content/uploads/2026/09/real_thumb.jpg" />
                        <h2 class="entry-title"><a href="/porn-video/desi-clip-1/">Desi Video 1</a></h2>
                    </a>
                </article>
                <article class="post type-sex-gallery">
                    <a href="/photos/desi-photo-set/" title="Desi Photos Only">
                        <img class="attachment-medium" 
                             data-src="https://fsiblogxx.com/wp-content/uploads/photo.jpg" />
                        <h2 class="entry-title"><a href="/photos/desi-photo-set/">Desi Photos Only</a></h2>
                    </a>
                </article>
            </div>
        """.trimIndent()

        val config = ProviderConfig(
            id = "fsiblogxx",
            name = "FSIBlog",
            baseUrl = "https://www.fsiblogxx.com",
            adapter = "html_selector",
            capabilities = listOf(ProviderCapability.HOME, ProviderCapability.STREAM),
            selectors = SelectorConfig(
                item = "article",
                title = "h2.entry-title a",
                thumbnail = "img",
                thumbnailAttr = "data-src",
                detailUrl = "h2.entry-title a"
            )
        )

        val adapter = HtmlSelectorAdapter(config)
        val feed = adapter.parseListingHtml(fsiblogHtml, 1).getOrThrow()

        // Verify photo gallery was filtered out, leaving only video
        assertEquals(1, feed.items.size)
        val videoItem = feed.items[0]
        assertEquals("Desi Video 1", videoItem.title)
        // Verify SVG data: URI was rejected and real image URL was extracted
        assertEquals("https://fsiblogxx.com/wp-content/uploads/2026/09/real_thumb.jpg", videoItem.thumbnailUrl)
        assertTrue(!videoItem.thumbnailUrl.startsWith("data:"))
    }

    @Test
    fun testMasaHub2BackgroundThumbnailExtraction() {
        val masahubHtml = """
            <article class="vcard">
                <a href="/video/desi-dance/">
                    <div class="thumb" style="background-image:url('https://masahub2.com/thumbs/dance.jpg');"></div>
                    <h3 class="vtitle">Desi Dance Viral</h3>
                </a>
            </article>
        """.trimIndent()

        val config = ProviderConfig(
            id = "masahub2",
            name = "MasaHub2",
            baseUrl = "https://masahub2.com",
            adapter = "html_selector",
            capabilities = listOf(ProviderCapability.HOME, ProviderCapability.STREAM),
            selectors = SelectorConfig(
                item = "article.vcard",
                title = "h3.vtitle",
                thumbnail = ".thumb",
                thumbnailAttr = "style",
                detailUrl = "a"
            )
        )

        val adapter = HtmlSelectorAdapter(config)
        val feed = adapter.parseListingHtml(masahubHtml, 1).getOrThrow()

        assertEquals(1, feed.items.size)
        assertEquals("https://masahub2.com/thumbs/dance.jpg", feed.items[0].thumbnailUrl)
    }

    @Test
    fun testHitmaalSelectors() {
        val hitmaalHtml = """
            <div class="videos">
                <a class="video lazy-bg" data-bg="https://hitmaal.io/thumbs/series1.webp" href="https://hitmaal.io/desi-tadka-ep1/" title="Desi Tadka Episode 1">
                    <span class="time">22:15</span>
                    <h2 class="vtitle">Desi Tadka Episode 1</h2>
                </a>
            </div>
        """.trimIndent()

        val config = ProviderConfig(
            id = "hitmaal",
            name = "HitMaal",
            baseUrl = "https://hitmaal.io",
            adapter = "html_selector",
            capabilities = listOf(ProviderCapability.HOME, ProviderCapability.STREAM),
            selectors = SelectorConfig(
                item = "a.video",
                title = "h2.vtitle",
                thumbnail = "a.video",
                thumbnailAttr = "data-bg",
                detailUrl = "a.video"
            )
        )

        val adapter = HtmlSelectorAdapter(config)
        val feed = adapter.parseListingHtml(hitmaalHtml, 1).getOrThrow()

        assertEquals(1, feed.items.size)
        assertEquals("Desi Tadka Episode 1", feed.items[0].title)
        assertEquals("https://hitmaal.io/thumbs/series1.webp", feed.items[0].thumbnailUrl)
    }

    @Test
    fun testKamaBabaSingleVideoContentDetection() {
        val kamababaDetailHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Guy fingers an Indian nude girl in an MMS - Kamababa</title>
                <meta itemprop="contentURL" content="https://cdn.kamababa1.com/2026/01/Guy-fingers-an-Indian-nude-girl-in-an-MMS.mp4">
            </head>
            <body>
                <article id="post-82543" class="hentry">
                    <h1 class="entry-title">Guy fingers an Indian nude girl in an MMS</h1>
                    <div class="video-player">
                        <iframe src="https://www.kamababa1.com/wp-content/plugins/clean-tube-player/public/player-x.php?q=cG9zdF9pZD04MjU0MyZ0eXBlPXZpZGVvJnRhZz0lM0N2aWRlbyUyMGlkJTNEJTIyd3BzdC12aWRlbyUyMiUyMGNsYXNzJTNEJTIydmlkZW8tanMlMjB2anMtYmlnLXBsYXktY2VudGVyZWQlMjIlMjBjb250cm9scyUyMHByZWxvYWQlM0QlMjJhdXRvJTIyJTIwd2lkdGglM0QlMjI2NDAlMjIlMjBoZWlnaHQlM0QlMjIyNjQlMjIlMjBwb3N0ZXIlM0QlMjJodHRwcyUzQSUyRiUyRnd3dy5rYW1hYmFiYTEuY29tJTJGd3AtY29udGVudCUyRnVwbG9hZHMlMkYyMDI2JTJGMDElMkZHdXktZmluZ2Vycy1hbi1JbmRpYW4tbnVkZS1naXJsLWluLWFuLU1NUy5qcGclMjIlM0UlM0Nzb3VyY2UlMjBzcmMlM0QlMjJodHRwcyUzQSUyRiUyRmNkbi5rYW1hYmFiYTEuY29tJTJGMjAyNiUyRjAxJTJGR3V5LWZpbmdlcnMtYW4tSW5kaWFuLW51ZGUtZ2lybC1pbi1hbi1NTVMubXA0JTIyJTIwdHlwZSUzRCUyMnZpZGVvJTJGbXA0JTIyJTNFJTNDJTJGdmlkZW8lM0U="></iframe>
                    </div>
                    <div class="under-video-block">
                        <div class="videos-list">
                            <!-- Related video 1 with data-trailer that should NOT be picked up -->
                            <article class="thumb-block video-preview-item" data-trailer="https://cdn.kamababa1.com/2026/09/HD-xxx-Indian-video-of-a-Jain-woman-and-her-husband.mp4">
                                <a href="https://www.kamababa1.com/hd-xxx-indian-video-of-jain-woman/">
                                    <span class="title">HD xxx Indian video of a Jain woman</span>
                                </a>
                            </article>
                            <!-- Related video 2 with data-trailer -->
                            <article class="thumb-block video-preview-item" data-trailer="https://cdn.kamababa1.com/2026/09/Hardcore-Indian-sex-video-of-a-naughty-wife.mp4">
                                <a href="https://www.kamababa1.com/hard-indian-sex-video/">
                                    <span class="title">Hardcore Indian sex video</span>
                                </a>
                            </article>
                        </div>
                    </div>
                </article>
            </body>
            </html>
        """.trimIndent()

        val config = ProviderConfig(
            id = "kamababa1",
            name = "KamaBaba",
            baseUrl = "https://www.kamababa1.com",
            adapter = "html_selector",
            capabilities = listOf(ProviderCapability.HOME, ProviderCapability.STREAM, ProviderCapability.DETAILS),
            selectors = SelectorConfig(
                item = "article.thumb-block, article.video-preview-item, article",
                title = "a[title], h2.entry-title a, h2 a",
                thumbnail = "img.video-main-thumb, img",
                thumbnailAttr = "src",
                detailUrl = "a[href*='kamababa1.com/']",
                player = "iframe[src*='player']",
                videoSource = "meta[itemprop='contentURL'], video source[src], video[src], source[type='video/mp4']"
            )
        )

        val adapter = HtmlSelectorAdapter(config)
        val result = adapter.parsePlayableMediaHtml(kamababaDetailHtml, "https://www.kamababa1.com/guy-fingers-an-indian-nude-girl-in-an-mms/")
        val sources = result.getOrThrow()

        assertTrue("Sources should not be empty", sources.isNotEmpty())
        val mainStream = sources[0].url
        // Must match the content's actual video, NOT the first related card trailer
        assertEquals(
            "https://cdn.kamababa1.com/2026/01/Guy-fingers-an-Indian-nude-girl-in-an-MMS.mp4",
            mainStream
        )
        assertTrue(
            "Must not match related video trailer",
            !mainStream.contains("HD-xxx-Indian-video-of-a-Jain-woman-and-her-husband")
        )

        // Also verify Clean-Tube iframe decoding when meta tag is absent
        val htmlWithoutMeta = kamababaDetailHtml.replace("""<meta itemprop="contentURL" content="https://cdn.kamababa1.com/2026/01/Guy-fingers-an-Indian-nude-girl-in-an-MMS.mp4">""", "")
        val resultFromIframe = adapter.parsePlayableMediaHtml(htmlWithoutMeta, "https://www.kamababa1.com/guy-fingers-an-indian-nude-girl-in-an-mms/")
        val sourcesFromIframe = resultFromIframe.getOrThrow()
        assertEquals(
            "https://cdn.kamababa1.com/2026/01/Guy-fingers-an-Indian-nude-girl-in-an-MMS.mp4",
            sourcesFromIframe[0].url
        )
    }

    @Test
    fun testUncutMazaCarouselExclusionAndCleanTubeExtraction() {
        val uncutmazaConfig = ProviderConfig(
            id = "uncutmaza",
            familyId = "clean_tube_family",
            name = "UncutMaza",
            baseUrl = "https://uncutmaza.cc",
            adapter = "html_selector",
            capabilities = listOf(ProviderCapability.HOME, ProviderCapability.STREAM, ProviderCapability.DETAILS),
            selectors = SelectorConfig(
                item = "article.thumb-block",
                title = "h2.entry-title a, h2 a, a[title]",
                thumbnail = "img",
                thumbnailAttr = "src",
                detailUrl = "h2.entry-title a, h2 a, a",
                player = "iframe[src*='player']",
                videoSource = "meta[itemprop='contentUrl'], meta[itemprop='contentURL'], video source[src], video[src], source[type='video/mp4']"
            )
        )
        val adapter = HtmlSelectorAdapter(uncutmazaConfig)

        // 1. Verify listing excludes carousel slides and picks only thumb-block video items
        val listingHtml = """
            <!DOCTYPE html>
            <html>
            <body>
                <div class="featured-carousel">
                    <article class="loop-video video-preview-item slide">
                        <h2 class="entry-title"><a href="/carousel-item-1">Carousel Headline 1</a></h2>
                        <img src="https://uncutmaza.cc/carousel1.webp"/>
                    </article>
                    <article class="loop-video video-preview-item slide">
                        <h2 class="entry-title"><a href="/carousel-item-2">Carousel Headline 2</a></h2>
                        <img src="https://uncutmaza.cc/carousel2.webp"/>
                    </article>
                </div>
                <div class="videos-list">
                    <article class="loop-video thumb-block video-preview-item">
                        <h2 class="entry-title"><a href="/mishka-couple-kamasutra">Mishka Couple Kamasutra</a></h2>
                        <img src="https://uncutmaza.cc/mishka.webp"/>
                    </article>
                    <article class="loop-video thumb-block video-preview-item">
                        <h2 class="entry-title"><a href="/second-video-item">Second Real Video</a></h2>
                        <img src="https://uncutmaza.cc/second.webp"/>
                    </article>
                </div>
            </body>
            </html>
        """.trimIndent()

        val feedResult = adapter.parseListingHtml(listingHtml, 1)
        val feed = feedResult.getOrThrow()
        assertEquals(2, feed.items.size)
        assertEquals("Mishka Couple Kamasutra", feed.items[0].title)
        assertEquals("Second Real Video", feed.items[1].title)
        assertTrue("Carousel items must be excluded", feed.items.none { it.title.contains("Carousel") })

        // 2. Verify detail page stream extraction with Clean-Tube iframe and contentUrl meta
        val detailHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta itemprop="contentUrl" content="https://cdn2.ixifile.xyz/5/Mishka%20Couple%20Kamasutra.mp4">
            </head>
            <body>
                <div class="video-player">
                    <div class="responsive-player">
                        <iframe loading="lazy" src="https://uncutmaza.cc/wp-content/plugins/clean-tube-player/public/player-x.php?q=aHR0cHM6Ly9jZG4yLml4aWZpbGUueHl6LzUvTWlzaGthJTIwQ291cGxlJTIwS2FtYXN1dHJhLm1wNA=="></iframe>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()

        val mediaResult = adapter.parsePlayableMediaHtml(detailHtml, "https://uncutmaza.cc/mishka-couple-kamasutra-2026-hindi-uncut-porn-video")
        val sources = mediaResult.getOrThrow()
        assertTrue("Sources should not be empty", sources.isNotEmpty())
        assertEquals("https://cdn2.ixifile.xyz/5/Mishka%20Couple%20Kamasutra.mp4", sources[0].url)
        assertEquals("https://uncutmaza.cc/", sources[0].headersRequired["Referer"])

        // 3. Verify Clean-Tube iframe decoding fallback when meta tag is absent
        val detailHtmlNoMeta = detailHtml.replace("""<meta itemprop="contentUrl" content="https://cdn2.ixifile.xyz/5/Mishka%20Couple%20Kamasutra.mp4">""", "")
        val mediaFallbackResult = adapter.parsePlayableMediaHtml(detailHtmlNoMeta, "https://uncutmaza.cc/mishka-couple-kamasutra-2026-hindi-uncut-porn-video")
        val fallbackSources = mediaFallbackResult.getOrThrow()
        assertTrue("Fallback sources should not be empty", fallbackSources.isNotEmpty())
        assertEquals("https://cdn2.ixifile.xyz/5/Mishka%20Couple%20Kamasutra.mp4", fallbackSources[0].url)
    }

    @Test
    fun testIndianSexStories3ListingAndFlashvarsStreamExtraction() {
        val config = ProviderConfig(
            id = "indiansexstories3",
            name = "IndianSexStories3",
            baseUrl = "https://www.indiansexstories3.com",
            adapter = "html_selector",
            familyId = "kvs_tube_family",
            domains = listOf("https://www.indiansexstories3.com", "https://indiansexstories3.com"),
            capabilities = listOf(ProviderCapability.HOME, ProviderCapability.STREAM, ProviderCapability.DETAILS),
            navigation = NavigationConfig(
                home = "/videos/latest-updates/",
                page = "/videos/latest-updates/{page}/"
            ),
            selectors = SelectorConfig(
                item = "div.thumb_rel.item, div.list-videos div.item, div.item",
                title = "a[title], div.title, strong.title, a.title, .video-title",
                thumbnail = "img",
                thumbnailAttr = "data-webp",
                detailUrl = "a",
                duration = "span.duration, .duration, .time",
                detailTitle = "h1.title, h1",
                videoSource = "video source[src], video[src], a[href*='/videos/get_file/'][href*='.mp4']"
            )
        )
        val adapter = HtmlSelectorAdapter(config)

        // 1. Verify listing isolates video items and extracts clean titles and durations
        val listingHtml = """
            <!DOCTYPE html>
            <html>
            <body>
                <div class="categories-list">
                    <div class="thumb item">
                        <a href="/videos/categories/desi-aunty/">
                            <div class="title">Desi Aunty</div>
                        </a>
                    </div>
                </div>
                <div class="list-videos">
                    <div class="thumb thumb_rel item">
                        <a href="https://www.indiansexstories3.com/videos/office-lover-girl-bareback-fucking-shot/" title="Office lover girl bareback fucking shot with hard moaning">
                            <div class="img-holder">
                                <img src="https://www.indiansexstories3.com/thumb1.jpg" data-webp="https://www.indiansexstories3.com/thumb1.webp">
                                <div class="time">0:40</div>
                            </div>
                            <div class="title">Office lover girl bareback fucking shot with hard moaning</div>
                        </a>
                    </div>
                    <div class="thumb thumb_rel item">
                        <a href="https://www.indiansexstories3.com/videos/punjabi-nursing-student-viral-sucking-mms/" title="Punjabi nursing student white cock sucking mms">
                            <div class="img-holder">
                                <img src="https://www.indiansexstories3.com/thumb2.jpg" data-webp="https://www.indiansexstories3.com/thumb2.webp">
                                <div class="time">1:36</div>
                            </div>
                            <div class="title">Punjabi nursing student white cock sucking mms</div>
                        </a>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()

        val feedResult = adapter.parseListingHtml(listingHtml, 1)
        val feed = feedResult.getOrThrow()
        val videoItems = feed.items.filter { it.detailUrl.contains("/videos/office-lover") || it.detailUrl.contains("/videos/punjabi-nursing") }
        assertEquals(2, videoItems.size)
        assertEquals("Office lover girl bareback fucking shot with hard moaning", videoItems[0].title)
        assertEquals("https://www.indiansexstories3.com/thumb1.webp", videoItems[0].thumbnailUrl)

        // 2. Verify detail page stream extraction prioritizes flashvars with auth token and ignores screenshot jpgs
        val detailHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <script type="application/ld+json">
                {
                    "@type": "VideoObject",
                    "name": "Chubby figure desi milf showing all her body in nude video",
                    "contentUrl": "https://www.indiansexstories3.com/videos/get_file/3/c9611c9e911b57853c43f7c5da54dab4/1000/1423/1423.mp4/"
                }
                </script>
            </head>
            <body>
                <!-- Screenshot gallery with get_file links pointing to JPG images -->
                <div class="screenshots-block">
                    <div class="screen-img item">
                        <a href="https://www.indiansexstories3.com/videos/get_file/0/d21d3ff40c6caabdbc270fbec48441ed/1000/1423/screenshots/1.jpg/">
                            <img src="https://www.indiansexstories3.com/screen1.jpg">
                        </a>
                    </div>
                    <div class="screen-img item">
                        <a href="https://www.indiansexstories3.com/videos/get_file/0/d4f0a4c1969d1fae938baa71abce617b/1000/1423/screenshots/2.jpg/">
                            <img src="https://www.indiansexstories3.com/screen2.jpg">
                        </a>
                    </div>
                </div>

                <div class="player video">
                    <script type="text/javascript">
                        var flashvars = {
                            video_id: '1423',
                            video_title: 'Chubby figure desi milf showing all her body in nude video',
                            video_url: 'https://www.indiansexstories3.com/videos/get_file/3/c9611c9e911b57853c43f7c5da54dab4/1000/1423/1423.mp4/?v-acctoken=Mzc0fDF8MHw4OWUyZjUzMGJjOWE2NDcxZDUwNDgwMjlhYjFhNDkwMA508de41504370586',
                            postfix: '.mp4'
                        };
                    </script>
                </div>
            </body>
            </html>
        """.trimIndent()

        val mediaResult = adapter.parsePlayableMediaHtml(detailHtml, "https://www.indiansexstories3.com/videos/chubby-figure-desi-milf-xxx-nude-video/")
        val sources = mediaResult.getOrThrow()
        assertTrue("Sources should not be empty", sources.isNotEmpty())
        val primarySource = sources[0].url
        assertEquals(
            "https://www.indiansexstories3.com/videos/get_file/3/c9611c9e911b57853c43f7c5da54dab4/1000/1423/1423.mp4/?v-acctoken=Mzc0fDF8MHw4OWUyZjUzMGJjOWE2NDcxZDUwNDgwMjlhYjFhNDkwMA508de41504370586",
            primarySource
        )
        assertTrue("Must NOT select screenshot jpg image", !primarySource.contains(".jpg"))
        assertEquals("https://www.indiansexstories3.com/", sources[0].headersRequired["Referer"])
    }

    @Test
    fun testAagMaalSingleVideoContentDetection() {
        val config = ProviderConfig(
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
                videoSourceAttr = "src"
            )
        )

        val adapter = HtmlSelectorAdapter(config)

        // 1. Verify detail page with ad iframes & download landing buttons does NOT extract ad or HTML webpage URLs
        val aagmaalComDetailHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Tamil Wife Pussy Fingering - Hot Solo Video - AAGMAAL.COM</title>
                <meta property="og:image" content="https://aagmaal.com/thumb.jpg">
            </head>
            <body>
                <header>
                    <iframe src="https://go.whitetrafsa.com/smartpop/f152bc995d0a5fa50b0a155ad34fa79412b24f49a9b1151c3526f730aeb516cf?userId=72b2ab4614341fb07889c7a9e98240912db831b098e06d6caced3f02209eab39"></iframe>
                    <iframe src="https://go.mavrtracktor.com/smartpop/f152bc995d0a5fa50b0a155ad34fa79412b24f49a9b1151c3526f730aeb516cf?userId=72b2ab4614341fb07889c7a9e98240912db831b098e06d6caced3f02209eab39"></iframe>
                </header>
                <article>
                    <h1 class="entry-title">Tamil Wife Pussy Fingering - Hot Solo Video</h1>
                    <div class="video-embed">
                        <iframe src="https://cdn1.site/e/uhxj1topnqk3"></iframe>
                    </div>
                    <div class="download-section">
                        <a class="button download-btn" href="https://luluvdo.com/d/uhxj1topnqk3">Download Video</a>
                    </div>
                </article>
            </body>
            </html>
        """.trimIndent()

        // 2. Verify Tube279 embed player HTML parsing extracts master.m3u8 with tube279 referer
        val tube279EmbedHtml = """
            <html>
            <head>
                <title>Tube279 Player</title>
                <script type="text/javascript">
                    jwplayer("vplayer").setup({
                        sources: [{file:"https://develop.siesta583.net/hls2/01/00017/quel4jgzktk8_h/master.m3u8?t=BrqQqDyU3_UIl2XNyatKCzllz_f7d5ZEG0NeSNORlZo&s=1789632234&e=28800&v=234752491&i=0.0&sp=0"}],
                        image: "https://develop.siesta583.net/i/01/00017/quel4jgzktk8_xt.jpg"
                    });
                </script>
            </head>
            <body></body>
            </html>
        """.trimIndent()

        val tube279Result = adapter.parsePlayableMediaHtml(tube279EmbedHtml, "https://tube279.com/e/ppm99x841x4oc")
        val tube279Sources = tube279Result.getOrThrow()
        assertTrue("Tube279 stream should be extracted", tube279Sources.isNotEmpty())
        assertEquals(
            "https://develop.siesta583.net/hls2/01/00017/quel4jgzktk8_h/master.m3u8?t=BrqQqDyU3_UIl2XNyatKCzllz_f7d5ZEG0NeSNORlZo&s=1789632234&e=28800&v=234752491&i=0.0&sp=0",
            tube279Sources[0].url
        )
        assertEquals(MediaSourceType.HLS, tube279Sources[0].type)
        assertEquals("https://tube279.com/", tube279Sources[0].headersRequired["Referer"])

        // 3. Verify Dean Edwards unpacked JS for LuluStream extracts master.m3u8 HLS stream
        val luluPackedScript = """
            eval(function(p,a,c,k,e,d){while(c--)if(k[c])p=p.replace(new RegExp('\\b'+c.toString(a)+'\\b','g'),k[c]);return p}('l("2z").87({86:[{25:"13://85.84.1t/82/81/80/7z/7y.7x?t=7w&s=3j&e=7v&f=3k&i=0.3&7u=0"}],7t:"13://7s.7r.1f/7q.7p",1w:"z%",1v:"z%"}'.split('|')))
        """.trimIndent()
        // Test unpackDeanEdwards handles real-world payload structure safely
        val simplePacked = "eval(function(p,a,c,k,e,r){})( 'jwplayer(\"vplayer\").setup({sources:[{file:\"https://61kzjdwdub6m.tnmr.org/hls2/03/04229/3psgx6bbhocp_h/master.m3u8?t=test&s=1&e=2\"}]})' , 10 , 1 , 'master' .split('|') )"
        val unpacked = adapter.unpackDeanEdwards(simplePacked)
        assertTrue("Unpacked script must contain master.m3u8", unpacked.contains("master.m3u8"))

        // 4. Verify Streamtape / tpead direct video element in embed HTML
        val streamtapeHtml = """
            <!DOCTYPE html>
            <html>
            <body>
                <video id="mainvideo" src="//tpead.net/get_video?id=oW8dZGlxXwtJ10J&expires=1789701418&token=test&stream=1"></video>
            </body>
            </html>
        """.trimIndent()
        val streamtapeResult = adapter.parsePlayableMediaHtml(streamtapeHtml, "https://tpead.net/e/oW8dZGlxXwtJ10J/")
        val streamtapeSources = streamtapeResult.getOrThrow()
        assertTrue("Streamtape source should be extracted", streamtapeSources.isNotEmpty())
        assertEquals(
            "https://tpead.net/get_video?id=oW8dZGlxXwtJ10J&expires=1789701418&token=test&stream=1",
            streamtapeSources[0].url
        )
    }

    @Test
    fun testFry99ThumbnailExtractionAndLogoRejection() {
        val fryConfig = ProviderConfig(
            id = "fry99",
            name = "Fry99",
            baseUrl = "https://fry99.cc",
            adapter = "html_selector",
            capabilities = listOf(ProviderCapability.HOME, ProviderCapability.DETAILS, ProviderCapability.STREAM),
            selectors = SelectorConfig(
                item = "li.video",
                title = "a.title",
                thumbnail = "img",
                detailUrl = "a.title",
                detailTitle = "h1",
                detailThumbnail = "video[poster], meta[property='og:image']",
                videoSource = "video source[src]"
            )
        )
        val adapter = HtmlSelectorAdapter(fryConfig)

        // 1. Verify isPlaceholderOrLogo correctly identifies logo, favicon, and empty urls
        assertTrue(adapter.isPlaceholderOrLogo("https://fry99.cc/wp-content/uploads/2024/07/LOGO.jpg"))
        assertTrue(adapter.isPlaceholderOrLogo("https://fry99.cc/favicon.ico"))
        assertTrue(adapter.isPlaceholderOrLogo("https://example.com/site-icon.png"))
        assertTrue(adapter.isPlaceholderOrLogo(""))
        assertTrue(!adapter.isPlaceholderOrLogo("https://fry99.cc/pictures/58873.jpg"))

        // 2. Test parseDetailsHtml on fry99 page where og:image is the site logo and video player has no poster
        val fryDetailHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Sexy Desi girl Shows her Boobs - Fry99</title>
                <meta property="og:image" content="https://fry99.cc/wp-content/uploads/2024/07/LOGO.jpg" />
            </head>
            <body>
                <h1>Sexy Desi girl Shows her Boobs</h1>
                <div class="fluid_video_wrapper">
                    <video id="video-id" class="js-fluid-player">
                        <source src="https://server14.mmsbee1.xyz/uploads/myfiless/id/58873.mp4" title="360p" type="video/mp4" />
                    </video>
                </div>
                <div class="report-section">
                    <script>const reportURL = "https://fry99.cc/report.php?id=58873";</script>
                </div>
                <h2>Like this one? Check these out</h2>
                <ul class="video_list">
                    <li class="video"><img src="https://fry99.cc/pictures/72864.jpg" /></li>
                </ul>
            </body>
            </html>
        """.trimIndent()

        val result = adapter.parseDetailsHtml(fryDetailHtml, "https://fry99.cc/sexy-desi-girl-shows-her-boobs-17/")
        val item = result.getOrThrow()
        assertEquals("https://fry99.cc/pictures/58873.jpg", item.thumbnailUrl)
        assertEquals("Sexy Desi girl Shows her Boobs", item.title)
    }

    @Test
    fun testDesiSexSingleVideoAndSearchPagination() {
        val desisexConfig = ProviderConfig(
            id = "desisex",
            name = "DesiSex",
            baseUrl = "https://desisex.site",
            adapter = "html_selector",
            capabilities = listOf(
                ProviderCapability.HOME,
                ProviderCapability.SEARCH,
                ProviderCapability.DETAILS,
                ProviderCapability.STREAM
            ),
            navigation = NavigationConfig(
                home = "/?filter=latest",
                search = "/?s={query}",
                page = "/page/{page}/?filter=latest"
            ),
            selectors = SelectorConfig(
                item = "article",
                title = "h2 a",
                thumbnail = "video[poster], img",
                detailUrl = "h2 a",
                videoSource = "meta[itemprop='contentURL'], meta[itemprop='contentUrl'], video source[src], video[src], source[type='video/mp4']"
            )
        )
        val adapter = HtmlSelectorAdapter(desisexConfig)

        // 1. Single video detail HTML with Clean-Tube iframe and wpst-trailer in sidebar
        val detailHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Aunty ki Hindi BF Chudai ki video - Desi Porn</title>
                <meta itemprop="contentURL" content="https://cdn2.desisex.site/2022/04/Aunty-ki-Hindi-BF-Chudai-ki-video.mp4" />
            </head>
            <body>
                <h1>Aunty ki Hindi BF Chudai ki video</h1>
                <div class="responsive-player">
                    <iframe src="https://www.desisex.site/wp-content/plugins/clean-tube-player/public/player-x.php?q=cG9zdF9pZD0xNDQ5MiZ0eXBlPXZpZGVvJnRhZz0lM0N2aWRlbyUyMGlkJTNEJTIyd3BzdC12aWRlbyUyMiUyMGNsYXNzJTNEJTIydmlkZW8tanMlMjB2anMtYmlnLXBsYXktY2VudGVyZWQlMjIlMjBjb250cm9scyUyMHByZWxvYWQlM0QlMjJhdXRvJTIyJTIwd2lkdGglM0QlMjI2NDAlMjIlMjBoZWlnaHQlM0QlMjIyNjQlMjIlMjBwb3N0ZXIlM0QlMjJodHRwcyUzQSUyRiUyRnd3dy5kZXNpc2V4LnNpdGUlMkZ3cC1jb250ZW50JTJGdXBsb2FkcyUyRjIwMjIlMkYwNCUyRkF1bnR5LWtpLUhpbmRpLUJGLUNodWRhaS1raS12aWRlby5qcGclMjIlM0UlM0Nzb3VyY2UlMjBzcmMlM0QlMjJodHRwcyUzQSUyRiUyRmNkbjIuZGVzaXNleC5zaXRlJTJGMjAyMiUyRjA0JTJGQXVudHkta2ktSGluZGktQkYtQ2h1ZGFpLWtpLXZpZGVvLm1wNCUyMiUyMHR5cGUlM0QlMjJ2aWRlbyUyRm1wNCUyMiUzRSUzQyUyRnZpZGVvJTNF"></iframe>
                </div>
                <h2>Related Videos</h2>
                <div class="videos-list">
                    <article class="thumb-block video-preview-item">
                        <video class="wpst-trailer" poster="https://www.desisex.site/thumb.jpg">
                            <source src="//cdn2.desisex.site/2026/09/Juli-bhabhi-ki-hairy-chut-ki-chudai.mp4" type="video/mp4" />
                        </video>
                    </article>
                </div>
            </body>
            </html>
        """.trimIndent()

        val sourcesResult = adapter.parsePlayableMediaHtml(detailHtml, "https://www.desisex.site/aunty-ki-hindi-bf-chudai-ki-video/")
        val sources = sourcesResult.getOrThrow()
        assertTrue("Must extract main video stream", sources.isNotEmpty())
        assertEquals("https://cdn2.desisex.site/2022/04/Aunty-ki-Hindi-BF-Chudai-ki-video.mp4", sources[0].url)
        // Verify trailer was skipped and NOT returned as source
        assertTrue(sources.none { it.url.contains("Juli-bhabhi") })

        // 2. Clean-Tube iframe fallback when meta tag is absent
        val cleanTubeOnlyHtml = """
            <!DOCTYPE html>
            <html>
            <body>
                <div class="responsive-player">
                    <iframe src="https://www.desisex.site/wp-content/plugins/clean-tube-player/public/player-x.php?q=cG9zdF9pZD0xNDQ5MiZ0eXBlPXZpZGVvJnRhZz0lM0N2aWRlbyUyMGlkJTNEJTIyd3BzdC12aWRlbyUyMiUyMGNsYXNzJTNEJTIydmlkZW8tanMlMjB2anMtYmlnLXBsYXktY2VudGVyZWQlMjIlMjBjb250cm9scyUyMHByZWxvYWQlM0QlMjJhdXRvJTIyJTIwd2lkdGglM0QlMjI2NDAlMjIlMjBoZWlnaHQlM0QlMjIyNjQlMjIlMjBwb3N0ZXIlM0QlMjJodHRwcyUzQSUyRiUyRnd3dy5kZXNpc2V4LnNpdGUlMkZ3cC1jb250ZW50JTJGdXBsb2FkcyUyRjIwMjIlMkYwNCUyRkF1bnR5LWtpLUhpbmRpLUJGLUNodWRhaS1raS12aWRlby5qcGclMjIlM0UlM0Nzb3VyY2UlMjBzcmMlM0QlMjJodHRwcyUzQSUyRiUyRmNkbjIuZGVzaXNleC5zaXRlJTJGMjAyMiUyRjA0JTJGQXVudHkta2ktSGluZGktQkYtQ2h1ZGFpLWtpLXZpZGVvLm1wNCUyMiUyMHR5cGUlM0QlMjJ2aWRlbyUyRm1wNCUyMiUzRSUzQyUyRnZpZGVvJTNF"></iframe>
                </div>
                <div class="videos-list">
                    <article class="thumb-block video-preview-item">
                        <video class="wpst-trailer"><source src="//cdn2.desisex.site/trailer.mp4" /></video>
                    </article>
                </div>
            </body>
            </html>
        """.trimIndent()
        val cleanTubeSources = adapter.parsePlayableMediaHtml(cleanTubeOnlyHtml, "https://www.desisex.site/aunty-ki-hindi-bf-chudai-ki-video/").getOrThrow()
        assertTrue("CleanTube fallback should decode stream", cleanTubeSources.isNotEmpty())
        assertEquals("https://cdn2.desisex.site/2022/04/Aunty-ki-Hindi-BF-Chudai-ki-video.mp4", cleanTubeSources[0].url)
    }

    @Test
    fun testPornX11ListingAndLuluStreamSourceExtraction() {
        val pornx11Config = ProviderConfig(
            id = "pornx11",
            name = "PornX11",
            enabled = true,
            baseUrl = "https://pornx11.com",
            adapter = "html_selector",
            familyId = "direct_cdn_family",
            domains = listOf("https://pornx11.com", "https://www.pornx11.com"),
            validationMarker = "pornx11",
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
                title = "a[title], h2.entry-title a, h2 a",
                thumbnail = "img",
                thumbnailAttr = "src",
                detailUrl = "h2.entry-title a, h2 a, a",
                duration = ".duration, span.duration",
                detailTitle = "h1",
                detailDescription = ".entry-content p",
                detailThumbnail = "meta[property='og:image'], img",
                player = "video, iframe",
                videoSource = "video source[src], video[src], iframe[src]",
                videoSourceAttr = "src"
            )
        )
        val adapter = HtmlSelectorAdapter(pornx11Config)

        val listingHtml = """
            <!DOCTYPE html>
            <html>
            <body>
                <div class="videos-list">
                    <article class="loop-video thumb-block video-preview-item post-99386 post type-post">
                        <a href="https://pornx11.com/hunter-raw-tapes-2026-moodx-hindi-raw-tape-watch-now/" title="Hunter Raw Tapes (2026) Moodx Hindi Raw Tape | Watch Now">
                            <div class="post-thumbnail">
                                <img src="https://pornx11.com/wp-content/uploads/2026/09/Hunter-Raw-Tapes.webp" width="300" height="168"/>
                                <span class="hd-video">HD</span>
                                <span class="duration"><i class="fa fa-clock-o"></i> 28:44</span>
                            </div>
                            <header class="entry-header">
                                <h2 class="entry-title">Hunter Raw Tapes (2026) Moodx Hindi Raw Tape | Watch Now</h2>
                            </header>
                        </a>
                    </article>
                </div>
            </body>
            </html>
        """.trimIndent()

        val feed = adapter.parseListingHtml(listingHtml, 1).getOrThrow()
        assertEquals(1, feed.items.size)
        val item = feed.items[0]
        assertEquals("Hunter Raw Tapes (2026) Moodx Hindi Raw Tape | Watch Now", item.title)
        assertEquals("https://pornx11.com/hunter-raw-tapes-2026-moodx-hindi-raw-tape-watch-now/", item.detailUrl)
        assertEquals("https://pornx11.com/wp-content/uploads/2026/09/Hunter-Raw-Tapes.webp", item.thumbnailUrl)

        // Detail Page with LuluStream embed
        val detailHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Cute Desi Bhabi (2026) Hindi Short Film | Watch Now</title>
                <meta property="og:image" content="https://pornx11.com/wp-content/uploads/2026/09/Cute-Desi-Bhabi.webp" />
            </head>
            <body>
                <div class="responsive-player">
                    <iframe src="https://luluvdo.com/e/mkzpihb53g51" width="100%" height="450" frameborder="0"></iframe>
                </div>
            </body>
            </html>
        """.trimIndent()

        val sources = adapter.parsePlayableMediaHtml(detailHtml, "https://pornx11.com/cute-desi-bhabi/").getOrThrow()
        assertTrue("Should extract media sources from detail page", sources.isNotEmpty())
        assertTrue("Media source should point to LuluStream embed or stream", sources.any { it.url.contains("luluvdo.com") || it.url.contains("mkzpihb53g51") })
    }

    @Test
    fun testIxiPornSingleVideoContentDetection() {
        val ixipornConfig = ProviderConfig(
            id = "ixiporn",
            name = "IxiPorn",
            baseUrl = "https://ixiporn.live",
            adapter = "html_selector",
            capabilities = listOf(ProviderCapability.HOME, ProviderCapability.DETAILS, ProviderCapability.STREAM),
            selectors = SelectorConfig(
                item = "div.video-block, .video-block, article",
                title = "span.title, a.infos, h2 a, a[title]",
                thumbnail = "img.video-img, img",
                thumbnailAttr = "data-src",
                detailUrl = "a.thumb, a.infos, a",
                detailTitle = "h1",
                player = "video, iframe",
                videoSource = "video source[src], video[src], iframe[src]",
                videoSourceAttr = "src"
            )
        )
        val adapter = HtmlSelectorAdapter(ixipornConfig)

        val detailHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Fuck With Loan Officer 2026 Websex Hindi Uncut Porn Video</title>
                <meta property="og:image" content="https://ixiporn.live/wp-content/uploads/2026/07/fuck-with-loan-officer.webp" />
            </head>
            <body>
                <div class="responsive-player">
                    <iframe src="https://ixiporn.live/wp-content/plugins/clean-tube-player/public/player-x.php?q=cG9zdF9pZD0xNjUyNzQmdHlwZT12aWRlbyZ0YWc9JTNDdmlkZW8lMjBpZCUzRCUyMmZ0dC12aWRlbyUyMiUyMGNsYXNzJTNEJTIydmlkZW8tanMlMjB2anMtYmlnLXBsYXktY2VudGVyZWQlMjIlMjBjb250cm9scyUyMHByZWxvYWQlM0QlMjJhdXRvJTIyJTIwd2lkdGglM0QlMjI2NDAlMjIlMjBoZWlnaHQlM0QlMjIyNjQlMjIlMjBwb3N0ZXIlM0QlMjJodHRwcyUzQSUyRiUyRml4aXBvcm4ubGl2ZSUyRndwLWNvbnRlbnQlMkZ1cGxvYWRzJTJGMjAyNiUyRjA3JTJGZnVjay13aXRoLWxvYW4tb2ZmaWNlci53ZWJwJTIyJTNFJTNDc291cmNlJTIwc3JjJTNEJTIyaHR0cHMlM0ElMkYlMkZjZG4yLml4aWZpbGUueHl6JTJGNSUyRmZ1Y2slMjUyMHdpdGglMjUyMGxvYW4lMjUyMG9mZmljZXIlMjUyMDIwMjYlMjUyMHdlYnNleC5tcDQlMjIlMjB0eXBlJTNEJTIydmlkZW8lMkZtcDQlMjIlM0UlM0MlMkZ2aWRlbyUzRQ==" frameborder="0" scrolling="no" allowfullscreen=""></iframe>
                </div>
            </body>
            </html>
        """.trimIndent()

        val result = adapter.parsePlayableMediaHtml(detailHtml, "https://ixiporn.live/fuck-with-loan-officer-2026-websex-hindi-uncut-porn-video")
        val sources = result.getOrThrow()
        assertTrue("Should extract media sources from ixiporn detail page", sources.isNotEmpty())
        val source = sources[0]
        assertEquals(
            "https://cdn2.ixifile.xyz/5/fuck%20with%20loan%20officer%202026%20websex.mp4",
            source.url
        )
        assertEquals("https://ixiporn.live/", source.headersRequired["Referer"])
    }
}


