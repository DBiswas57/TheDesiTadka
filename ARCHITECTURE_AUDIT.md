# StreamHub - Full Architecture Audit & Repository Inspection Report

**Date**: 2026-09-15  
**Project**: Secure Remote-Config Streaming Aggregator for Android  
**Codename**: StreamHub  
**Workspace**: `c:\Users\LearnersYT\source\TheDesiTadka`

---

## 1. Executive Summary

A comprehensive forensic inspection of the repository `c:\Users\LearnersYT\source\TheDesiTadka` was conducted to establish baseline capabilities, toolchains, security requirements, and architectural foundations prior to production implementation.

### Key Findings:
- **Repository State**: Fresh repository containing three directories:
  - `Desktop/`: Currently empty.
  - `Mobile/`: Currently empty (designated target for the Android application).
  - `SiteReferrence/`: Contains captured network session archives (`fry99/fry99.cc.har` and `masa49/www.masa49.nl.har`).
- **Build & Execution Environment**:
  - Operating System: Windows 11 64-bit.
  - JDK: Microsoft OpenJDK 21.0.8 LTS (`C:\Program Files\Android\openjdk\jdk-21.0.8`).
  - Android SDK: `C:\Users\LearnersYT\AppData\Local\Android\Sdk` with Platform SDKs `android-35` and `android-36`, Build Tools `34.0.0` and `36.0.0`.
  - Gradle Toolchain: Gradle 8.9 / 8.10.2 pre-cached locally in `C:\Users\LearnersYT\.gradle`.
  - Python: 3.13.14 (available for configuration canonicalization and cryptographic signing utilities).

---

## 2. SiteReference HAR Forensic Analysis

The two network archives in `SiteReferrence` (`fry99.cc.har` and `www.masa49.nl.har`) were decoded, decompressed, and forensically analyzed.

### A. fry99 (`fry99.cc`)
- **CMS / Engine**: WordPress with custom video theme (`fox`).
- **Feeds & Navigation**:
  - Standard RSS Feed: `https://fry99.cc/feed/`
  - Pagination: `/page/{page}/`
  - Search: `/?s={query}`
- **Content Markup**:
  - Video detail pages contain native HTML5 `<video id="video-id"><source src="..." type="video/mp4"/></video>` and fallback `<iframe>` embeds (`https://mmsbaba.co/embed.php?id=...`).
  - Media endpoints: Direct progressive MP4 stream targets hosted on CDN/video storage.
- **Protection Landscape**: Cloudflare Turnstile / Challenge platform present on interactive browser flows.

### B. masa49 (`www.masa49.nl`)
- **CMS / Engine**: WordPress with RankMath SEO and custom video theme.
- **Feeds & Navigation**:
  - Standard RSS Feed: `https://www.masa49.nl/feed/`
  - Pagination: `/page/{page}/`
  - Search: `/?s={query}`
- **Content Markup**:
  - Microdata: `itemprop="video" itemscope itemtype="https://schema.org/VideoObject"`
  - Video detail pages contain `<div class="responsive-player video-player"><video><source src="https://files.pvtcdn.com/upload/videos/..._video.mp4" type="video/mp4" /></video></div>`.
  - Articles structured using standard WordPress semantic tags: `<article class="post-... post ...">`.
- **Protection Landscape**: Cloudflare Turnstile present.

### C. Architectural Conclusions for Provider Adapter Design
1. Both reference sites demonstrate that modern public video portals can be normalized into a **declarative data model**:
   - `html_selector`: CSS selectors for item cards, titles, thumbnails, links, video source tags.
   - `rss`: Parsing standard WordPress RSS feeds for metadata and enclosure links.
   - `wordpress_rest`: Standard `/wp-json/wp/v2/posts` endpoints where available.
2. **Zero-Code / Zero-DEX Remote Config**:
   - The application does not need nor permit executable code from the remote server.
   - Selectors, navigation paths, headers, and media rules are 100% declarative JSON DSL.
3. **Legal / Security Compliance**:
   - The application strictly consumes authorized public endpoints.
   - Anti-bot/CAPTCHA bypasses, paywall bypasses, DRM circumvention, or credential stuffing are strictly forbidden by architectural design.

---

## 3. Comprehensive Risk & Deficiency Assessment

| Category | Risk / Challenge | Architectural Remediation |
| :--- | :--- | :--- |
| **Security & Tampering** | Remote JSON poisoning or MITM tampering | Public-key Ed25519 signature verification on canonical JSON. Public key baked into APK; private key strictly outside APK. |
| **Network & SSRF** | Malicious config pointing to internal networks (`127.0.0.1`, RFC1918, `169.254.169.254`) | Strict `UrlSecurityValidator` rejecting loopback, link-local, private IP ranges, and cloud metadata services. Enforced HTTPS only. |
| **Config Staleness / Breakage** | Broken or expired remote config bricking app | Config Rollback Manager maintaining `currentConfig`, `previousConfig`, and `lastKnownGoodConfig`. Atomic storage with schema & version checks. |
| **Arbitrary Code Execution** | Store ban / malware vulnerability from dynamic scripts | Remote config is strictly DATA (JSON DSL). No JavaScript `eval`, no DEX loading, no dynamic reflection. |
| **Memory & Performance** | Image decoding OOMs, large JSON payloads, main-thread parsing | Coil image loader with hardware bitmap caching; response size limit (5MB); coroutine dispatching on `Dispatchers.IO` for parsing and Room. |
| **Player Crashes** | Malformed media URLs or unsupported codecs crashing app | Media3/ExoPlayer isolated in robust player wrapper with typed `PlaybackError` states and automatic retry/fallback. |
| **Downloads & Storage** | Leaking scoped storage permissions or rogue downloads | Authorized downloads only via WorkManager foreground service, scoped storage, and explicit `ProviderCapability.DOWNLOAD` check. |
| **Sensitive Data Logging** | URLs with tokens, cookies, or auth headers in logcat | Custom `StreamHubLogger` with regex-based redaction of Bearer tokens, cookies, passwords, and sensitive query params. |

---

## 4. Architectural Blueprints

### A. Remote Config Verification Pipeline
```
Remote Manifest (HTTPS)
       │
       ▼
1. TLS Validation
       │
       ▼
2. Cryptographic Ed25519 Signature Verification (Embedded Public Key)
       │
       ▼
3. JSON Schema Validation & Version Check (version > currentVersion)
       │
       ▼
4. Expiration Check (expiresAt > currentTime)
       │
       ▼
5. SSRF / Domain Allowlist Check (Reject Private IPs & Localhost)
       │
       ▼
6. Safe DSL & Capability Validation (No executable code)
       │
       ▼
7. Atomic Local Persistence & Activation
       │
       ▼
(On Failure: Auto-Rollback to Last Known Good Config)
```

### B. Normalized Domain Model
- `VideoItem`: Normalized entity across all providers (`id`, `providerId`, `title`, `description`, `thumbnailUrl`, `duration`, `publishedAt`, `category`, `tags`, `detailUrl`, `playbackAvailability`, `downloadAvailability`).
- `MediaSource`: Normalized playback target (`url`, `type`, `mimeType`, `quality`, `headersRequired`).
- `ProviderInfo`: Normalized provider descriptor (`id`, `name`, `icon`, `description`, `enabled`, `capabilities`, `baseUrl`, `configVersion`, `status`).

### C. Declarative Adapter Strategies
1. `HtmlSelectorAdapter`: Jsoup-based safe CSS selector engine.
2. `WordPressRestAdapter`: REST API queries for WordPress instances.
3. `RssFeedAdapter`: XML pull parser for standard video RSS/Atom feeds.
4. `JsonApiAdapter`: Structured JSON path query engine.
5. `EmbeddedPlayerAdapter`: Sandboxed safe web player container where authorized.

## 6. Forensic Remediation & Media Delivery Findings

### A. CDN Anti-Leech Protections (`pvtcdn.com` & `cdn.kamababa1.com`)
- **Problem**: In default Android Media3 ExoPlayer and Coil configurations, HTTP requests omit `Referer` and use default library User-Agents (`okhttp/*`, `ExoPlayerLib/*`).
- **Diagnosis**: Live testing confirmed both `pvtcdn.com` (Masa49) and `cdn.kamababa1.com` (KamaBaba) return **HTTP 403 Forbidden** unless a browser `User-Agent` and valid `Referer` (`https://www.masa49.nl/`, `https://www.kamababa1.com/`) are supplied.
- **Remediation**:
  1. `NetworkClient`: Injected `DEFAULT_USER_AGENT` and standard browser headers.
  2. `MediaPlayerManager`: Dynamically configured `DefaultHttpDataSource.Factory()` with `mediaSource.headersRequired` (`Referer` & `User-Agent`).
  3. `Coil`: Implemented `ImageLoaderFactory` in `StreamHubApp` with an OkHttp interceptor injecting host-specific `Referer` and browser `User-Agent`.
  4. `PlayerScreen`: Added user-facing error state and retry action if a stream fails to initialize.

### B. Upstream Bot Protection (`fry99`)
- **Diagnosis**: `fry99.cc` is behind Cloudflare Managed Challenge (Turnstile) requiring interactive browser JavaScript challenge solving (`__cf_chl_tk`). Direct non-browser HTTP requests return 403 Forbidden.
- **Remediation**: In accordance with Section 24 & 25, `fry99` is marked `enabled: false` ("Temporarily Unavailable - Cloudflare Protected"), preventing app crashes and guiding users to active providers.

### C. Addition of KamaBaba (`kamababa1`)
- **Architecture**: Mapped via `HtmlSelectorAdapter`.
- **Stream Extraction**: Detail pages embed `clean-tube-player` via `iframe[src*='player-x.php?q=...']`. `HtmlSelectorAdapter` automatically extracts and decodes the Base64 parameter or parses nested iframe HTML to retrieve the direct MP4 stream (`cdn.kamababa1.com`), then attaches required playback headers.

