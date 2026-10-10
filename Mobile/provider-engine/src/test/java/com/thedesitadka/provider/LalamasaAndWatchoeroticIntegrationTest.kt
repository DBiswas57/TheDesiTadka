package com.thedesitadka.provider

import com.thedesitadka.core.model.ContentPolicy
import com.thedesitadka.core.model.MediaSourceType
import com.thedesitadka.core.model.NavigationConfig
import com.thedesitadka.core.model.ProviderCapability
import com.thedesitadka.core.model.ProviderConfig
import com.thedesitadka.core.model.SelectorConfig
import com.thedesitadka.provider.adapters.HtmlSelectorAdapter
import com.thedesitadka.provider.plugins.HostResolverEngine
import com.thedesitadka.provider.plugins.StreamouploadResolverPlugin
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LalamasaAndWatchoeroticIntegrationTest {

    private val lalamasaConfig = ProviderConfig(
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
    )

    private val watchoeroticConfig = ProviderConfig(
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
    )

    @Test
    fun testLalamasaListingParsing() {
        val file = File("c:/Users/LearnersYT/source/TheDesiTadka/scratch/lalamasa.html")
        val html = if (file.exists()) {
            file.readText(Charsets.UTF_8)
        } else {
            """
            <!DOCTYPE html>
            <html>
            <body>
                <div class="video-list">
                    <article class="vcard">
                        <a href="https://lalamasa.mobi/video-1/">
                            <div class="thumb" style="background-image: url('https://lalamasa.mobi/thumb1.jpg')"></div>
                            <h3 class="vtitle">Desi Bhabhi Romance</h3>
                            <span class="dur">15:30</span>
                        </a>
                    </article>
                    <article class="vcard">
                        <a href="https://lalamasa.mobi/video-2/">
                            <div class="thumb" style="background-image: url('https://lalamasa.mobi/thumb2.jpg')"></div>
                            <h3 class="vtitle">College Girl Fun</h3>
                            <span class="dur">10:15</span>
                        </a>
                    </article>
                </div>
                <div class="pagination">
                    <a class="next" href="/page/2/">Next Page</a>
                </div>
            </body>
            </html>
            """.trimIndent()
        }

        val adapter = HtmlSelectorAdapter(lalamasaConfig)
        val feedResult = adapter.parseListingHtml(html, page = 1)
        assertTrue("Listing parsing must succeed", feedResult.isSuccess)

        val feed = feedResult.getOrThrow()
        assertTrue("Must extract items from LalaMasa", feed.items.isNotEmpty())
        println("LalaMasa extracted items: ${feed.items.size}")

        val first = feed.items.first()
        assertFalse("Title must not be empty", first.title.isBlank())
        assertTrue("DetailUrl must point to lalamasa", first.detailUrl.contains("lalamasa.mobi"))
        assertTrue("ThumbnailUrl must be resolved", first.thumbnailUrl.startsWith("http"))
        assertTrue("Duration must be parsed", (first.durationSeconds ?: 0L) > 0L)
        assertTrue("Has next page must be true", feed.hasNextPage)
    }

    @Test
    fun testLalamasaDetailAndMediaParsing() {
        val file = File("c:/Users/LearnersYT/source/TheDesiTadka/scratch/lalamasa_detail.html")
        val html = if (file.exists()) {
            file.readText(Charsets.UTF_8)
        } else {
            """
            <!DOCTYPE html>
            <html>
            <head><title>Hot Paki Wife Teacher Student Roleplay Sex - LalaMasa</title></head>
            <body>
                <h1>Hot Paki Wife Teacher Student Roleplay Sex</h1>
                <div class="entry-content">
                    <p>Exclusive romantic desi scene.</p>
                </div>
                <video poster="https://lalamasa.mobi/poster.jpg" controls>
                    <source src="https://lalamasa.mobi/media/video.mp4" type="video/mp4" />
                </video>
            </body>
            </html>
            """.trimIndent()
        }

        val adapter = HtmlSelectorAdapter(lalamasaConfig)
        val detailUrl = "https://lalamasa.mobi/hot-paki-wife-teacher-student-roleplay-sex/"
        val detailsResult = adapter.parseDetailsHtml(html, detailUrl)
        assertTrue("Details parsing must succeed", detailsResult.isSuccess)
        val detail = detailsResult.getOrThrow()
        assertFalse("Detail title must not be empty", detail.title.isBlank())

        val mediaResult = adapter.parsePlayableMediaHtml(html, detailUrl)
        assertTrue("Media parsing must succeed", mediaResult.isSuccess)
        val sources = mediaResult.getOrThrow()
        assertTrue("Must extract at least one playable media source", sources.isNotEmpty())

        val source = sources.first()
        assertEquals(MediaSourceType.PROGRESSIVE_MP4, source.type)
        assertEquals("video/mp4", source.mimeType)
        assertTrue("Must be direct mp4 stream", source.url.endsWith(".mp4") || source.url.contains(".mp4"))
    }

    @Test
    fun testLalamasaCategories() {
        val file = File("c:/Users/LearnersYT/source/TheDesiTadka/scratch/lalamasa.html")
        val html = if (file.exists()) {
            file.readText(Charsets.UTF_8)
        } else {
            """
            <!DOCTYPE html>
            <html>
            <body>
                <ul class="categories">
                    <li><a href="https://lalamasa.mobi/category/desi/">Desi</a></li>
                    <li><a href="https://lalamasa.mobi/category/bhabhi/">Bhabhi</a></li>
                </ul>
            </body>
            </html>
            """.trimIndent()
        }

        val adapter = HtmlSelectorAdapter(lalamasaConfig)
        val categories = adapter.parseCategoriesHtml(html)
        assertNotNull(categories)
        // Never crash, extract categories if present
        for (cat in categories) {
            assertFalse(cat.id.isBlank())
            assertFalse(cat.name.isBlank())
            assertTrue(cat.url.startsWith("http"))
        }
    }

    @Test
    fun testWatchoeroticListingParsing() {
        val file = File("c:/Users/LearnersYT/source/TheDesiTadka/scratch/watchoerotic.html")
        val html = if (file.exists()) {
            file.readText(Charsets.UTF_8)
        } else {
            buildString {
                append("<!DOCTYPE html><html><body><div class=\"movies-list\">")
                for (i in 1..32) {
                    append("""
                        <div class="ml-item">
                            <a class="ml-mask" href="https://watchoerotic.com/movie-$i/">
                                <img class="mli-thumb" data-original="https://image.tmdb.org/t/p/w185/poster$i.jpg" src="about:blank" alt="Movie $i" />
                                <span class="mli-quality">HD</span>
                                <div class="mli-info"><h2>Erotic Romance $i</h2></div>
                            </a>
                        </div>
                    """.trimIndent())
                }
                append("<ul class=\"pagination\"><li><a href=\"/page/2/\">2</a></li></ul>")
                append("</div></body></html>")
            }
        }

        val adapter = HtmlSelectorAdapter(watchoeroticConfig)
        val feedResult = adapter.parseListingHtml(html, page = 1)
        assertTrue("Listing parsing must succeed", feedResult.isSuccess)

        val feed = feedResult.getOrThrow()
        assertTrue("Must extract items from WatchOErotic", feed.items.size >= 30)
        println("WatchOErotic extracted items: ${feed.items.size}")

        val first = feed.items.first()
        assertFalse("Title must not be empty", first.title.isBlank())
        assertTrue("DetailUrl must point to watchoerotic", first.detailUrl.contains("watchoerotic.com"))
        assertTrue("ThumbnailUrl must be extracted from data-original", first.thumbnailUrl.startsWith("http"))
        assertTrue("ThumbnailUrl must contain image host", first.thumbnailUrl.contains("tmdb.org") || first.thumbnailUrl.contains("media-amazon"))
        assertTrue("Has next page must be true", feed.hasNextPage)
    }

    @Test
    fun testWatchoeroticCategories() {
        val file = File("c:/Users/LearnersYT/source/TheDesiTadka/scratch/watchoerotic.html")
        val html = if (file.exists()) {
            file.readText(Charsets.UTF_8)
        } else {
            """
            <!DOCTYPE html>
            <html>
            <body>
                <ul class="sub-menu">
                    <li><a href="https://watchoerotic.com/genre/desi/">Desi Erotic</a></li>
                    <li><a href="https://watchoerotic.com/genre/romance/">Romance</a></li>
                </ul>
            </body>
            </html>
            """.trimIndent()
        }

        val adapter = HtmlSelectorAdapter(watchoeroticConfig)
        val categories = adapter.parseCategoriesHtml(html)
        assertTrue("Must extract genres/categories from WatchOErotic", categories.isNotEmpty())
        println("WatchOErotic categories extracted: ${categories.size}")

        val catNames = categories.map { it.name.lowercase() }
        assertTrue("Must contain Desi genre", catNames.any { it.contains("desi") })
        for (cat in categories) {
            assertFalse("Category id cannot be blank", cat.id.isBlank())
            assertFalse("Category name cannot be blank", cat.name.isBlank())
            assertTrue("Category url must be absolute", cat.url.startsWith("http"))
        }
    }

    @Test
    fun testWatchoeroticDetailAndMediaResolution() {
        val sampleDetailHtml = """
            <!DOCTYPE html>
            <html>
            <head><title>Hot Desi Romance Watch Online - WatchOErotic</title></head>
            <body>
                <h1 class="film-name">Hot Desi Romance</h1>
                <div class="film-desc"><p>A romantic erotic drama.</p></div>
                <div id="content-embed">
                    <iframe data-lazy-src="https://streamoupload.xyz/embed-6nvkbhqn7ex1.html" src="about:blank" width="100%" height="100%"></iframe>
                </div>
            </body>
            </html>
        """.trimIndent()

        val adapter = HtmlSelectorAdapter(watchoeroticConfig)
        val detailResult = adapter.parseDetailsHtml(sampleDetailHtml, "https://watchoerotic.com/hot-desi-romance/")
        assertTrue(detailResult.isSuccess)
        val detail = detailResult.getOrThrow()
        assertEquals("Hot Desi Romance", detail.title)

        // Test HostResolverEngine on the detail page
        val doc = Jsoup.parse(sampleDetailHtml, "https://watchoerotic.com")
        val candidateUrls = HostResolverEngine.extractCandidateUrls(doc)
        assertEquals(1, candidateUrls.size)
        assertEquals("https://streamoupload.xyz/embed-6nvkbhqn7ex1.html", candidateUrls.first())

        // Test Streamoupload resolver from local HTML
        val streamHtmlFile = File("c:/Users/LearnersYT/source/TheDesiTadka/scratch/streamoupload.html")
        val streamHtml = if (streamHtmlFile.exists()) {
            streamHtmlFile.readText(Charsets.UTF_8)
        } else {
            """
            <html>
            <head>
            <script type='text/javascript'>
            eval(function(p,a,c,k,e,d){while(c--)if(k[c])p=p.replace(new RegExp('\\b'+c+'\\b','g'),k[c]);return p}('jwplayer("vplayer").setup({sources:[{file:"https://pnam.streamoupload.xyz/hls/master.m3u8"}]});',1,1,'||'.split('|'),0,{}))
            </script>
            </head>
            <body><div id="vplayer"></div></body>
            </html>
            """.trimIndent()
        }
        val resolver = StreamouploadResolverPlugin()
        val mediaResult = resolver.resolveFromHtml(streamHtml, candidateUrls.first())
        assertTrue("Media resolution must succeed: ${mediaResult.exceptionOrNull()?.message}", mediaResult.isSuccess)

        val mediaSource = mediaResult.getOrThrow()
        assertEquals(MediaSourceType.HLS, mediaSource.type)
        assertEquals("application/vnd.apple.mpegurl", mediaSource.mimeType)
        assertTrue(mediaSource.url.contains(".m3u8"))
        assertEquals("https://streamoupload.xyz/", mediaSource.headersRequired?.get("Referer"))
    }
}
