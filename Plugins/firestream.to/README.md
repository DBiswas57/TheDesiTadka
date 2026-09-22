# FireStream.to External Video Host Resolver Plugin

## Overview
This plugin resolves external hosted video iframes from `https://firestream.site/e/{slug}` or `https://firestream.to/e/{slug}` into direct HLS (`.m3u8`) and MP4 video streams hosted on `fr-cdn-*.firestream.to` for smooth playback and background downloading in **TheDesiTadka**.

## Why this plugin is needed
Many adult streaming sites such as `watchxxxfree.xyz` embed third-party FireStream player iframes:
```html
<iframe src="https://firestream.site/e/WAop-hsu" width="100%" height="100%" frameborder="0" allowfullscreen></iframe>
```
Because the video file is not self-hosted on the main blog website, the app cannot play the video without resolving the embed iframe to its authenticated CDN streaming URL.

## Player Architecture & Resolution Protocol
1. **Slug Extraction**:
   Extract the slug identifier from the embed URL:
   - `https://firestream.site/e/WAop-hsu` -> `slug = "WAop-hsu"`
   - `https://firestream.to/v/nhbUOAaQ` -> `slug = "nhbUOAaQ"`

2. **Token Blob Extraction**:
   The embed page contains a hidden plain-text token element:
   ```html
   <script id="token-blob" type="text/plain">vV0OJImpCZxQUbjpxZ32r/dRxhseWd7nhc3kcadFtl0c4ipqipwYJYcePmlnTv/7QonY7/8lw2O+JI6SfeXBIQ==</script>
   ```

3. **API Resolution**:
   Send an HTTP POST request to the resolver endpoint:
   - Endpoint: `https://{domain}/api/videos/{slug}/resolve`
   - Method: `POST`
   - Headers:
     ```
     Content-Type: application/json
     Referer: https://{domain}/e/{slug}
     Origin: https://{domain}
     User-Agent: (Browser UA)
     ```
   - Request Body:
     ```json
     {
       "blob": "vV0OJImpCZxQUbjpxZ32r/dRxhseWd7nhc3kcadFtl0c4ipqipwYJYcePmlnTv/7QonY7/8lw2O+JI6SfeXBIQ=="
     }
     ```

4. **Response Parsing**:
   The endpoint responds with JSON containing the signed stream URL:
   ```json
   {
     "signedVideoUrl": "https://fr-cdn-1.firestream.to/encodings/.../video.mp4/video.m3u8?md5=...&expires=...",
     "signedVideoSdUrl": null
   }
   ```

5. **Playback and Download Headers**:
   Streaming and downloading the resulting HLS or MP4 stream requires:
   - `Referer: https://{domain}/`
   - `Origin: https://{domain}`
   - `User-Agent: (Browser UA)`

## Android Engine Integration
Implemented natively in:
- `com.thedesitadka.provider.plugins.FirestreamResolverPlugin`
- `com.thedesitadka.provider.plugins.HostResolverEngine`
- `com.thedesitadka.provider.adapters.HtmlSelectorAdapter`
