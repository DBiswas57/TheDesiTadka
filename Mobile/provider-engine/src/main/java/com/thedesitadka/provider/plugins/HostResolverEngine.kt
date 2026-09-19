package com.thedesitadka.provider.plugins

import com.thedesitadka.core.model.MediaSource
import com.thedesitadka.core.security.StreamHubLogger
import org.jsoup.nodes.Document
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Extensible engine for resolving third-party video host iframes and embeds.
 *
 * Automatically inspects web pages for embeds hosted on external video providers
 * (e.g. Vixeo, Vidsonic, etc.) and routes them to registered plugins.
 */
object HostResolverEngine {

    private val plugins = CopyOnWriteArrayList<HostResolverPlugin>()

    init {
        // Register default built-in host plugins
        registerPlugin(VixeoResolverPlugin())
    }

    fun registerPlugin(plugin: HostResolverPlugin) {
        if (plugins.none { it.id == plugin.id }) {
            plugins.add(plugin)
            StreamHubLogger.d("HostResolverEngine", "Registered host resolver plugin: ${plugin.name} (${plugin.id})")
        }
    }

    fun getPlugins(): List<HostResolverPlugin> = plugins.toList()

    fun findPluginForUrl(url: String): HostResolverPlugin? {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return null
        return plugins.firstOrNull { it.canHandle(trimmed) }
    }

    fun canHandle(url: String): Boolean = findPluginForUrl(url) != null

    suspend fun resolve(embedUrl: String, parentUrl: String? = null): Result<MediaSource> {
        val plugin = findPluginForUrl(embedUrl)
            ?: return Result.failure(IllegalArgumentException("No host resolver plugin found for URL: $embedUrl"))

        return plugin.resolve(embedUrl, parentUrl)
    }

    /**
     * Scans an HTML document for iframe or link embeds matching any registered host plugin.
     */
    fun extractCandidateUrls(doc: Document): List<String> {
        val candidates = mutableListOf<String>()

        // 1. Scan iframes
        val iframes = doc.select("iframe[src]")
        for (iframe in iframes) {
            val src = iframe.attr("src").trim()
            if (src.isNotBlank() && canHandle(src) && !candidates.contains(src)) {
                candidates.add(src)
            }
        }

        // 2. Scan meta video/embed tags
        val metaEmbeds = doc.select("meta[property='og:video'], meta[name='twitter:player'], meta[itemprop*='embedURL'], meta[itemprop*='embedUrl']")
        for (m in metaEmbeds) {
            val content = m.attr("content").trim()
            if (content.isNotBlank() && canHandle(content) && !candidates.contains(content)) {
                candidates.add(content)
            }
        }

        // 3. Scan external host links
        val links = doc.select("a[href]")
        for (a in links) {
            val href = a.attr("href").trim()
            if (href.isNotBlank() && canHandle(href) && !candidates.contains(href)) {
                candidates.add(href)
            }
        }

        return candidates
    }

    /**
     * Attempts to resolve the first supported external host embed found in the document.
     */
    suspend fun resolveFirstSupportedEmbed(doc: Document, parentUrl: String? = null): Result<MediaSource>? {
        val candidateUrls = extractCandidateUrls(doc)
        for (url in candidateUrls) {
            val result = resolve(url, parentUrl)
            if (result.isSuccess) {
                return result
            }
        }
        return null
    }
}
