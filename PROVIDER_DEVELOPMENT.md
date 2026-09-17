# TheDesiMedia — Provider Development Guide

## 1. Overview
TheDesiMedia providers are **declarative plugins** defined within the cryptographically signed JSON configuration manifest. Adding or modifying a streaming portal, video blog, or media provider requires **zero Kotlin code** and **zero APK recompilation**, provided the source can be expressed by one of the built-in adapter engines.

---

## 2. Supported Adapter Engines

| Adapter Type | Engine | Best Suited For |
|---|---|---|
| `html_selector` | `HtmlSelectorAdapter` (Jsoup) | Standard web portals, video blogs, and public HTML pages. |
| `wordpress_rest`| `WordPressRestAdapter` (OkHttp + JSON) | WordPress installations exposing `/wp-json/wp/v2/posts`. |
| `rss` | `RssFeedAdapter` (XML Pull Parser) | Video podcasts, YouTube/Vimeo RSS feeds, and Media RSS feeds. |
| `json_api` | `JsonApiAdapter` (REST JSON) | Custom REST APIs exposing JSON video catalogs. |
| `embedded_player` | `EmbeddedPlayerAdapter` | Authorized public portals requiring web player iframes. |

---

## 3. Provider Capabilities & Separation

Each provider declares supported operations:
- `HOME`: Supports browsing the primary home feed.
- `CATEGORY`: Supports category-based browsing.
- `SEARCH`: Supports keyword search queries.
- `DETAILS`: Supports loading extended video detail pages.
- `STREAM`: Provides direct playable media streams (MP4, HLS, DASH).
- `DOWNLOAD`: Explicitly authorizes progressive media files for offline saving.

> **Crucial Rule**: Media playback capability does not imply download authorization. Download buttons are only enabled when the provider explicitly declares `DOWNLOAD` capability and direct downloadable media exists.

---

## 4. Current Built-In Providers Matrix

| Provider ID | Name | Base URL | Media Extraction |
|---|---|---|---|
| `kamababa1` | KamaBaba | `https://www.kamababa1.com` | Direct MP4 source tags |
| `masa49` | Masa49 | `https://www.masa49.nl` | Direct MP4 (`files.pvtcdn.com` with Referer) |
| `fsiblogxx` | FSIBlog | `https://www.fsiblogxx.com` | Direct video tags |
| `masahub2` | MasaHub2 | `https://masahub2.com` | Direct video source tags |
| `aagmaal` | AagMaal | `https://aagmaal.com` | Nested `/e/` embed iframe resolvers |
| `fry99` | Fry99 | `https://fry99.cc` | Cloudflare Turnstile with in-app verification |
| `webxseries` | WebXSeries | `https://webxseries.hot` | Root anchor `a.video` with `data-bg` and direct MP4 |
| `public_domain` | Public Media Archive | `https://commondatastorage.googleapis.com` | Google Cloud Storage public media archive |

---

## 5. Adding a New Provider via Remote Config

### Step 1: Author Provider Definition in JSON
```json
{
  "id": "my_new_site",
  "name": "My New Site",
  "enabled": true,
  "baseUrl": "https://example-videos.org",
  "adapter": "html_selector",
  "capabilities": ["HOME", "SEARCH", "DETAILS", "STREAM", "DOWNLOAD"],
  "navigation": {
    "home": "/",
    "search": "/?s={query}",
    "page": "/page/{page}/",
    "categories": "/categories/"
  },
  "selectors": {
    "item": "article.post, div.video-card",
    "title": "h2.title a, a[title]",
    "thumbnail": "img.thumb, img",
    "thumbnailAttr": "src",
    "detailUrl": "a.detail-link, h2 a",
    "duration": ".duration",
    "detailTitle": "h1.entry-title",
    "detailDescription": ".entry-content p",
    "detailThumbnail": "meta[property='og:image'], img",
    "player": "video, iframe",
    "videoSource": "video source[src], video[src]",
    "videoSourceAttr": "src"
  },
  "contentPolicy": {
    "isAuthorizedPublicSource": true,
    "rightsStatus": "Public Web Index",
    "termsUrl": "https://example-videos.org/terms",
    "privacyPolicyUrl": "https://example-videos.org/privacy"
  }
}
```

### Step 2: Validate the Manifest
```bash
python config-tools/validate_config.py --manifest config-tools/sample-manifest.json
```

### Step 3: Sign the Manifest with Ed25519
```bash
python config-tools/sign_config.py \
    --input config-tools/sample-manifest.json \
    --private-key-file keys/ed25519_private.pem \
    --output dist/manifest.json
```

### Step 4: Deploy
Push the signed envelope to your CDN or GitHub Pages endpoint. All installed TheDesiMedia applications will automatically ingest and activate the new provider on their next configuration sync!
