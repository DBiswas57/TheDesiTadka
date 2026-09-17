package com.thedesitadka.app.media

import android.content.Context
import android.webkit.CookieManager
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import android.net.Uri
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.thedesitadka.core.model.MediaSource
import com.thedesitadka.core.model.MediaSourceType
import com.thedesitadka.core.security.StreamHubLogger
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class MediaPlayerManager(
    private val context: Context,
    private val onPositionUpdated: ((Long, Long) -> Unit)? = null
) {

    private var exoPlayer: ExoPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var progressTrackerJob: Job? = null

    private val _playbackStateFlow = MutableStateFlow(PlayerPlaybackState())
    val playbackStateFlow: StateFlow<PlayerPlaybackState> = _playbackStateFlow.asStateFlow()

    private var previousVolume = 1.0f

    fun getPlayer(): ExoPlayer {
        if (exoPlayer == null) {
            initPlayer()
        }
        return exoPlayer!!
    }

    private fun initPlayer() {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(25000)

        val upstreamDataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(upstreamDataSourceFactory)

        exoPlayer = ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                playWhenReady = true
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        syncState()
                        if (state == Player.STATE_READY && isPlaying) {
                            startProgressTracking()
                        } else if (state == Player.STATE_ENDED || state == Player.STATE_IDLE) {
                            stopProgressTracking()
                        }
                    }

                    override fun onIsPlayingChanged(playing: Boolean) {
                        syncState()
                        if (playing) {
                            startProgressTracking()
                        } else {
                            stopProgressTracking()
                            exoPlayer?.let {
                                onPositionUpdated?.invoke(it.currentPosition, it.duration)
                            }
                        }
                    }

                    override fun onPositionDiscontinuity(
                        oldPosition: Player.PositionInfo,
                        newPosition: Player.PositionInfo,
                        reason: Int
                    ) {
                        syncState()
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        StreamHubLogger.e("MediaPlayerManager", "ExoPlayer Error [${error.errorCode}]: ${error.message}")
                        stopProgressTracking()
                        _playbackStateFlow.value = _playbackStateFlow.value.copy(
                            errorMessage = error.message ?: "Playback error",
                            errorCode = error.errorCode,
                            isPlaying = false,
                            isBuffering = false
                        )
                    }
                })
            }
    }

    private fun startProgressTracking() {
        progressTrackerJob?.cancel()
        progressTrackerJob = scope.launch {
            while (isActive) {
                syncState()
                delay(250) // Poll authoritative ExoPlayer clock smoothly 4 times/sec
            }
        }
    }

    private fun stopProgressTracking() {
        progressTrackerJob?.cancel()
        progressTrackerJob = null
    }

    private fun syncState() {
        val player = exoPlayer ?: return
        val curPos = player.currentPosition.coerceAtLeast(0L)
        val dur = player.duration.coerceAtLeast(0L)
        val bufPos = player.bufferedPosition.coerceAtLeast(0L)
        val playing = player.isPlaying
        val state = player.playbackState

        val wasSeeking = _playbackStateFlow.value.isSeeking
        val isSeeking = if (state == Player.STATE_READY || state == Player.STATE_ENDED) false else wasSeeking
        val isBuffering = state == Player.STATE_BUFFERING || isSeeking

        _playbackStateFlow.value = _playbackStateFlow.value.copy(
            currentPositionMs = curPos,
            durationMs = dur,
            bufferedPositionMs = bufPos,
            isPlaying = playing,
            exoPlaybackState = state,
            isBuffering = isBuffering,
            isSeeking = isSeeking,
            isPrepared = state != Player.STATE_IDLE,
            isEnded = state == Player.STATE_ENDED,
            playbackSpeed = player.playbackParameters.speed,
            isMuted = player.volume == 0f,
            errorMessage = null,
            errorCode = null
        )
    }

    fun prepareAndPlay(mediaSource: MediaSource, resumePositionMs: Long = 0L) {
        val player = getPlayer()

        val rawUrl = mediaSource.url.trim()
        val isOffline = rawUrl.startsWith("/") || rawUrl.startsWith("file://") || rawUrl.startsWith("content://")
        val uri = when {
            rawUrl.startsWith("content://") || rawUrl.startsWith("file://") -> Uri.parse(rawUrl)
            rawUrl.startsWith("/") -> Uri.fromFile(File(rawUrl))
            else -> Uri.parse(rawUrl)
        }

        StreamHubLogger.i("MediaPlayerManager", "prepareAndPlay: uri=$uri, isOffline=$isOffline, type=${mediaSource.type}")

        val mediaItemBuilder = MediaItem.Builder()
            .setUri(uri)

        when (mediaSource.type) {
            MediaSourceType.HLS -> mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
            MediaSourceType.DASH -> mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_MPD)
            MediaSourceType.PROGRESSIVE_MP4 -> mediaItemBuilder.setMimeType(MimeTypes.VIDEO_MP4)
            MediaSourceType.WEBM -> mediaItemBuilder.setMimeType(MimeTypes.VIDEO_WEBM)
            MediaSourceType.EMBEDDED_WEB -> return
        }

        val mediaItem = mediaItemBuilder.build()

        val requestProperties = mutableMapOf<String, String>()
        requestProperties["User-Agent"] = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
        requestProperties.putAll(mediaSource.headersRequired)

        if (!isOffline) {
            // Inject domain-matched Referer required by media CDNs if not already present
            val urlLower = mediaSource.url.lowercase()
            val existingRef = requestProperties["Referer"]
            if (existingRef.isNullOrBlank()) {
                when {
                    urlLower.contains("ixiporn") -> requestProperties["Referer"] = "https://ixiporn.live/"
                    urlLower.contains("wowuncut") -> requestProperties["Referer"] = "https://wowuncut.com/"
                    urlLower.contains("antarvasna") -> requestProperties["Referer"] = "https://antarvasnabf.com/"
                    urlLower.contains("uncutmaza") -> requestProperties["Referer"] = "https://uncutmaza.cc/"
                    urlLower.contains("desisex") -> requestProperties["Referer"] = "https://desisex.site/"
                    urlLower.contains("indiansexstories") -> requestProperties["Referer"] = "https://indiansexstories3.com/"
                    urlLower.contains("tube279.com") || urlLower.contains("siesta583") -> requestProperties["Referer"] = "https://tube279.com/"
                    urlLower.contains("tnmr.org") || urlLower.contains("lulucdn") || urlLower.contains("lulustream") || urlLower.contains("luluvdo") -> requestProperties["Referer"] = "https://luluvdo.com/"
                    urlLower.contains("tpead.net") || urlLower.contains("streamtape.com") || urlLower.contains("tapecontent.net") -> requestProperties["Referer"] = "https://streamtape.com/"
                    urlLower.contains("ixifile") || urlLower.contains("streamclean") || urlLower.contains("cdn2.ixifile.xyz") || urlLower.contains("streamcrypt") -> {
                        requestProperties["Referer"] = if (urlLower.contains("ixiporn")) "https://ixiporn.live/" else "https://wowuncut.com/"
                    }
                    urlLower.contains("mydown.biz") || urlLower.contains("masahub") -> requestProperties["Referer"] = "https://masahub2.com/"
                    urlLower.contains("pvtcdn.com") || urlLower.contains("masa49") -> requestProperties["Referer"] = "https://www.masa49.nl/"
                    urlLower.contains("kamababa") -> requestProperties["Referer"] = "https://www.kamababa1.com/"
                    urlLower.contains("fry99") -> requestProperties["Referer"] = "https://fry99.cc/"
                    urlLower.contains("hitmaal") -> requestProperties["Referer"] = "https://hitmaal.io/"
                    urlLower.contains("fsiblog") -> requestProperties["Referer"] = "https://fsiblogxx.com/"
                    urlLower.contains("webxseries") -> requestProperties["Referer"] = "https://webxseries.hot/"
                    urlLower.contains("aagmaal") -> requestProperties["Referer"] = "https://aagmaal.com/"
                    urlLower.contains("xhpingcdn") || urlLower.contains("xhcdn") || urlLower.contains("xhamster") -> requestProperties["Referer"] = "https://xhamster.desi/"
                }
            }

            // Always ensure Origin matches Referer if Referer is present
            val referer = requestProperties["Referer"]
            if (!referer.isNullOrBlank() && !requestProperties.containsKey("Origin")) {
                try {
                    val uriRef = Uri.parse(referer)
                    requestProperties["Origin"] = "${uriRef.scheme}://${uriRef.host}"
                } catch (_: Exception) {}
            }

            // Inject cookies from CookieManager if available for domain
            try {
                val cookies = CookieManager.getInstance().getCookie(mediaSource.url)
                if (!cookies.isNullOrBlank()) {
                    requestProperties["Cookie"] = cookies
                }
            } catch (e: Exception) {
                StreamHubLogger.w("MediaPlayerManager", "Cookie injection warning: ${e.message}")
            }
        }

        val upstreamDataSourceFactory = if (isOffline) {
            DefaultDataSource.Factory(context)
        } else {
            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(requestProperties["User-Agent"] ?: "Mozilla/5.0")
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(30000)
                .setDefaultRequestProperties(requestProperties)
            DefaultDataSource.Factory(context, httpDataSourceFactory)
        }

        val sourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(upstreamDataSourceFactory)

        val exoSource = sourceFactory.createMediaSource(mediaItem)
        player.setMediaSource(exoSource)

        if (resumePositionMs > 0L) {
            player.seekTo(resumePositionMs)
        }
        player.prepare()
        player.play()
        syncState()
    }

    fun play() {
        exoPlayer?.play()
        syncState()
    }

    fun pause() {
        exoPlayer?.pause()
        stopProgressTracking()
        syncState()
        exoPlayer?.let { onPositionUpdated?.invoke(it.currentPosition, it.duration) }
    }

    fun seekTo(positionMs: Long) {
        val player = exoPlayer ?: return
        val clamped = positionMs.coerceIn(0L, player.duration.coerceAtLeast(0L))
        _playbackStateFlow.value = _playbackStateFlow.value.copy(
            currentPositionMs = clamped,
            isSeeking = true,
            isBuffering = true
        )
        player.seekTo(clamped)
        syncState()
        onPositionUpdated?.invoke(clamped, player.duration)
    }

    fun seekForward(deltaMs: Long = 10000L) {
        val player = exoPlayer ?: return
        val target = (player.currentPosition + deltaMs).coerceAtMost(player.duration.coerceAtLeast(0L))
        seekTo(target)
    }

    fun seekBackward(deltaMs: Long = 10000L) {
        val player = exoPlayer ?: return
        val target = (player.currentPosition - deltaMs).coerceAtLeast(0L)
        seekTo(target)
    }

    fun setSpeed(speed: Float) {
        val player = exoPlayer ?: return
        player.playbackParameters = PlaybackParameters(speed)
        syncState()
    }

    fun toggleMute() {
        val player = exoPlayer ?: return
        if (player.volume > 0f) {
            previousVolume = player.volume
            player.volume = 0f
        } else {
            player.volume = if (previousVolume > 0f) previousVolume else 1.0f
        }
        syncState()
    }

    fun release() {
        stopProgressTracking()
        exoPlayer?.let {
            onPositionUpdated?.invoke(it.currentPosition, it.duration)
            it.release()
        }
        exoPlayer = null
        _playbackStateFlow.value = PlayerPlaybackState()
    }
}
