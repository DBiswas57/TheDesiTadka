# TheDesiMedia — Player Architecture & Playback Synchronization

## Overview
TheDesiMedia replaces legacy ad-hoc video components with a modern, high-performance media engine built directly on AndroidX Media3 (ExoPlayer). It completely eliminates fake timer progression, ensuring the seek bar and UI controls adhere strictly to authoritative player clock states.

---

## 1. Playback Architecture

```
 Media Source (MP4 / HLS / DASH)
         ↓
  DefaultHttpDataSource (User-Agent + Referer + CookieManager)
         ↓
  ExoPlayer (Media3 Core)
         ↓
  MediaPlayerManager (Authoritative PlayerController)
         ↓
  StateFlow<PlayerPlaybackState> (250ms active polling ticker)
         ↓
  PlaybackViewModel / PlayerScreen (Compose Reactive UI)
```

---

## 2. Authoritative Synchronization (Fix for Seek Bar Bug #1)
In previous versions, playback position was only sampled during state transitions, leaving the seek bar static or dependent on arbitrary timers.

The refactored `MediaPlayerManager` employs an authoritative StateFlow pipeline:
- While `isPlaying == true`, a dedicated Coroutine polling job samples:
  - `exoPlayer.currentPosition`
  - `exoPlayer.duration`
  - `exoPlayer.bufferedPosition`
  - `exoPlayer.playbackState`
  - `exoPlayer.playbackParameters.speed`
- **Zero Fake Timers**: Time is never incremented artificially. If buffering occurs, `isPlaying` becomes false and the ticker holds position until Media3 resumes playback.
- **Progress Ratio Calculation**:
  ```kotlin
  val progressRatio: Float
      get() = if (durationMs > 0L) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
  ```
- **Drag-to-Seek Support**:
  - `Slider` tracks local drag state (`isDragging`, `dragProgressRatio`) without fighting the incoming StateFlow.
  - Upon user release (`onValueChangeFinished`), `playerManager.seekTo(...)` dispatches the seek to ExoPlayer, snapping the player and UI immediately.

---

## 3. Supported Features
- **Authoritative Progress Scrubber**: Smooth seek bar with secondary buffered progress indicator.
- **Rewind & Forward**: 10-second fast seek buttons (`seekBackward(10000L)`, `seekForward(10000L)`).
- **Playback Speed Control**: Configurable dropdown supporting 0.5x, 0.75x, 1.0x, 1.25x, 1.5x, and 2.0x speeds.
- **Mute & Volume Control**: Instant toggle between active volume and 0.
- **Picture-in-Picture (PiP)**: Android O+ compliant with 16:9 aspect ratio window transition.
- **Screen Lock**: Prevents unintended touches from scrubbing or pausing playback.
- **Live Stream Handling**: Live streams (duration <= 0 or unspecified) automatically suppress seeking and display a prominent `LIVE` indicator.
- **Resume Playback**: Integrates with `WatchHistoryDao` to restore playback position automatically upon opening content.

---

## 4. Orientation & Fullscreen Management
The application strictly respects system UX conventions:
- **Browsing & Discovery**: Locked to `ActivityInfo.SCREEN_ORIENTATION_PORTRAIT`.
- **Fullscreen Mode**: Toggling fullscreen enters `ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE` and enables Android WindowInsets immersive sticky mode (hiding system status and navigation bars).
- **Safe Restoration**: When exiting fullscreen or when the screen composable is disposed, the orientation is restored to portrait and system bars are revealed.
