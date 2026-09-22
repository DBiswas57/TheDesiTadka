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

class MovieNerdsAndCineapseTest {

    private fun createMovieNerdsAdapter(): HtmlSelectorAdapter {
        val config = ProviderConfig(
            id = "movienerds",
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
                page = "&page={page}",
                search = "https://api.themoviedb.org/3/search/multi?api_key=43dcff37851866de14d8acdce668a509&query={query}",
                categories = "https://api.themoviedb.org/3/genre/movie/list?api_key=43dcff37851866de14d8acdce668a509"
            ),
            selectors = SelectorConfig(
                item = "results",
                title = "title, name",
                thumbnail = "poster_path, backdrop_path",
                detailUrl = "id",
                duration = "runtime"
            )
        )
        return HtmlSelectorAdapter(config)
    }

    private fun createCineapseAdapter(): HtmlSelectorAdapter {
        val config = ProviderConfig(
            id = "cineapse",
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
                home = "/tmdb/discover/movie?include_adult=false&include_video=false&language=en-US&sort_by=popularity.desc",
                page = "&page={page}",
                search = "/tmdb/search/multi?include_adult=false&language=en-US&query={query}",
                categories = "/api/categories?kind=movie"
            ),
            selectors = SelectorConfig(
                item = "results",
                title = "title, name",
                thumbnail = "poster_path, backdrop_path",
                detailUrl = "id",
                duration = "runtime"
            )
        )
        return HtmlSelectorAdapter(config)
    }

    @Test
    fun testMovieNerdsListingAndPagination() = runBlocking {
        val adapter = createMovieNerdsAdapter()

        // 1. Pagination check
        val p2 = adapter.buildPagedUrl("https://api.themoviedb.org/3/trending/all/day?api_key=43dcff37851866de14d8acdce668a509", 2)
        assertTrue("Page parameter must be present", p2.contains("page=2"))

        // 2. Listing parsing (TMDB JSON results)
        val tmdbJson = """
            {
              "page": 1,
              "results": [
                {
                  "id": 533535,
                  "title": "Deadpool & Wolverine",
                  "overview": "A listless Wade Wilson toils away in civilian life.",
                  "poster_path": "/8cdWjvZQUExUUTzyp4t6EDMubfO.jpg",
                  "vote_average": 7.7,
                  "release_date": "2024-07-24"
                },
                {
                  "id": 94605,
                  "name": "Arcane",
                  "overview": "Amid the stark discord of twin cities Piltover and Zaun...",
                  "poster_path": "/abf8tZvJE87vtFQvg9OygUQW0Dg.jpg",
                  "vote_average": 8.7,
                  "first_air_date": "2021-11-06"
                }
              ]
            }
        """.trimIndent()

        val feed = adapter.parseListingHtml(tmdbJson, 1).getOrThrow()
        assertEquals(2, feed.items.size)

        val item1 = feed.items[0]
        assertEquals("Deadpool & Wolverine", item1.title)
        assertTrue(item1.detailUrl.contains("533535"))
        assertTrue(item1.thumbnailUrl.contains("8cdWjvZQUExUUTzyp4t6EDMubfO.jpg"))

        val item2 = feed.items[1]
        assertEquals("Arcane", item2.title)
        assertTrue(item2.detailUrl.contains("94605"))
        assertTrue(item2.thumbnailUrl.contains("abf8tZvJE87vtFQvg9OygUQW0Dg.jpg"))
    }

    @Test
    fun testMovieNerdsStreamAndSubtitlesParsing() {
        val adapter = createMovieNerdsAdapter()

        val streamJson = """
            {
              "success": true,
              "title": "Deadpool & Wolverine",
              "streams": [
                {
                  "server": "VidSrc Ultra",
                  "quality": "1080p",
                  "type": "hls",
                  "url": "https://api.movienerds.online/api/stream/proxy.m3u8?url=https%3A%2F%2Fvidsrc.stream%2Fmaster.m3u8"
                },
                {
                  "server": "Vaplayer Direct",
                  "quality": "1080p",
                  "type": "hls",
                  "url": "https://api.movienerds.online/api/stream/proxy.m3u8?url=https%3A%2F%2Fvaplayer.com%2Fmaster.m3u8",
                  "audio": "Hindi + English"
                },
                {
                  "server": "CineJoy Vanguard",
                  "quality": "720p",
                  "type": "hls",
                  "url": "https://api.movienerds.online/api/stream/proxy.m3u8?url=https%3A%2F%2Fcinejoy.vip%2Fmaster.m3u8"
                }
              ]
            }
        """.trimIndent()

        val subJson = """
            {
              "success": true,
              "subtitles": [
                {
                  "language": "English",
                  "url": "https://subtitles.movienerds.online/en/533535.vtt"
                },
                {
                  "language": "Hindi",
                  "url": "https://subtitles.movienerds.online/hi/533535.vtt"
                }
              ]
            }
        """.trimIndent()

        val result = adapter.parseMovieNerdsStreamJson(streamJson, "https://movienerds.site/movie/533535", subJson)
        assertTrue("Stream result must be success", result.isSuccess)
        val sources = result.getOrThrow()
        assertEquals(3, sources.size)

        // Stream 1: VidSrc Ultra 1080p
        val s1 = sources[0]
        assertEquals("VidSrc Ultra (1080p)", s1.quality)
        assertEquals(MediaSourceType.HLS, s1.type)
        assertEquals("application/x-mpegURL", s1.mimeType)
        assertTrue(s1.url.contains("api.movienerds.online"))
        assertEquals("https://movienerds.site/", s1.headersRequired["Referer"])
        assertNotNull(s1.metadata["subtitles"])
        assertTrue(s1.metadata["subtitles"]!!.contains("English::https://subtitles.movienerds.online/en/533535.vtt"))
        assertTrue(s1.metadata["subtitles"]!!.contains("Hindi::https://subtitles.movienerds.online/hi/533535.vtt"))

        // Stream 2: Vaplayer Direct with multi-audio
        val s2 = sources[1]
        assertEquals("Vaplayer Direct (1080p - Hindi + English)", s2.quality)
        assertEquals("Hindi + English", s2.metadata["audio"])
    }

    @Test
    fun testCineapseListingAndCategories() = runBlocking {
        val adapter = createCineapseAdapter()

        // 1. Pagination check
        val p3 = adapter.buildPagedUrl("https://cineapse.net/tmdb/trending/all/day", 3)
        assertTrue("Page parameter must be present", p3.contains("page=3"))

        // 2. Listing parsing
        val cineapseListingJson = """
            {
              "page": 1,
              "results": [
                {
                  "id": 912649,
                  "title": "Venom: The Last Dance",
                  "overview": "Eddie and Venom are on the run.",
                  "poster_path": "/aosm8NMQ3UzyAcg78uvnzLoKySC.jpg"
                }
              ]
            }
        """.trimIndent()

        val feed = adapter.parseListingHtml(cineapseListingJson, 1).getOrThrow()
        assertEquals(1, feed.items.size)
        assertEquals("Venom: The Last Dance", feed.items[0].title)
        assertTrue(feed.items[0].detailUrl.contains("912649"))

        // 3. Category parsing (/api/categories format with groups and items)
        val categoriesJson = """
            {
              "groups": [
                {
                  "name": "Genres",
                  "items": [
                    { "id": 28, "name": "Action" },
                    { "id": 12, "name": "Adventure" },
                    { "id": 878, "name": "Science Fiction" }
                  ]
                },
                {
                  "name": "Watch Providers",
                  "items": [
                    { "id": 8, "name": "Netflix" },
                    { "id": 119, "name": "Amazon Prime" },
                    { "id": 337, "name": "Disney Plus" }
                  ]
                }
              ]
            }
        """.trimIndent()

        val cats = adapter.parseCategoriesHtml(categoriesJson)
        assertTrue(cats.size >= 6)
        assertTrue(cats.any { it.name.contains("Action") })
        assertTrue(cats.any { it.name.contains("Science Fiction") })
        assertTrue(cats.any { it.name.contains("Netflix") })
        assertTrue(cats.any { it.name.contains("Disney Plus") })
    }

    @Test
    fun testCineapseMultiServerStreamGeneration() {
        val adapter = createCineapseAdapter()

        // 1. Movie multi-server streams
        val movieSources = adapter.buildCineapseMediaSources("https://cineapse.net/movie/533535")
        assertEquals(6, movieSources.size)

        // Check VidLink movie
        val vidlink = movieSources.find { it.metadata["server"] == "VidLink" }
        assertNotNull("VidLink server must exist", vidlink)
        assertEquals("https://vidlink.pro/movie/533535", vidlink!!.url)
        assertEquals("https://cineapse.net/", vidlink.headersRequired["Referer"])

        // Check CineSrc movie
        val cinesrc = movieSources.find { it.metadata["server"] == "CineSrc" }
        assertNotNull("CineSrc server must exist", cinesrc)
        assertEquals("https://cinesrc.com/embed/movie/533535", cinesrc!!.url)

        // Check Solar movie
        val solar = movieSources.find { it.metadata["server"] == "Solar" }
        assertNotNull("Solar server must exist", solar)
        assertEquals("https://embed.su/embed/movie/533535", solar!!.url)

        // Check VidSrc CC movie
        val vidsrc = movieSources.find { it.metadata["server"] == "VidSrc CC" }
        assertNotNull("VidSrc CC server must exist", vidsrc)
        assertEquals("https://vidsrc.cc/v2/embed/movie/533535", vidsrc!!.url)

        // 2. TV show multi-server streams (Season 2 Episode 3)
        val tvSources = adapter.buildCineapseMediaSources("https://cineapse.net/tv/94605/season/2/episode/3")
        assertEquals(6, tvSources.size)

        val tvVidlink = tvSources.find { it.metadata["server"] == "VidLink" }
        assertNotNull(tvVidlink)
        assertEquals("https://vidlink.pro/tv/94605/2/3", tvVidlink!!.url)

        val tvCinesrc = tvSources.find { it.metadata["server"] == "CineSrc" }
        assertNotNull(tvCinesrc)
        assertEquals("https://cinesrc.com/embed/tv/94605/2/3", tvCinesrc!!.url)

        val tvSolar = tvSources.find { it.metadata["server"] == "Solar" }
        assertNotNull(tvSolar)
        assertEquals("https://embed.su/embed/tv/94605/2/3", tvSolar!!.url)

        val tvVidsrc = tvSources.find { it.metadata["server"] == "VidSrc CC" }
        assertNotNull(tvVidsrc)
        assertEquals("https://vidsrc.cc/v2/embed/tv/94605/2/3", tvVidsrc!!.url)
    }
}
