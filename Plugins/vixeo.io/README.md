# Vixeo.io External Video Host Resolver Plugin

## Overview
This plugin resolves external hosted video iframes from `https://vixeo.io/e/{id}` into direct HLS/MP4 streams on `vidsonic.net` for playback and download in **TheDesiTadka**.

## Why this plugin is needed
Sites like `watchxxxfree.xyz` embed third-party player iframes:
```html
<iframe src="https://vixeo.io/e/EQkJKzs7LwIf" ...></iframe>
```
Because the video is not self-hosted or served directly via standard video tags, the app must resolve the embed iframe via this plugin to extract the real playback URL.

## DOM Structure & Resolution Mechanism
1. **Container Element**:
   The embed page contains a root container element:
   ```html
   <div id="streamsonic-player-root" data-config="..."></div>
   ```
2. **Configuration Decoding**:
   The `data-config` attribute is a Base64-encoded JSON object:
   ```json
   {
     "source": "3833646630|6465326366|...",
     "isMp4": false,
     "title": "unmasking-a-threesome_480p",
     "poster": "/poster/EQkJKzs7LwIf",
     "thumbnails": "/thumbnails/EQkJKzs7LwIf/thumbnails.m3u8"
   }
   ```
3. **Stream URL Decryption**:
   - Strip all `|` delimiters.
   - Parse each 2-character hexadecimal byte into an ASCII/latin1 character.
   - Reverse the characters: `[...T].reverse().join("")`.
   - Result:
     `https://sfy-01-fr.vidsonic.net/secure/132/EQkJKzs7LwIf/video.mp4/index.m3u8?server_id=2&expires=...&file_id=EQkJKzs7LwIf&md5=...`
4. **Required Headers**:
   Playback and downloads require:
   - `Referer: https://vixeo.io/`
   - `Origin: https://vixeo.io`
   - `User-Agent: (Browser UA)`

## Android Engine Integration
Implemented natively in:
- `com.thedesitadka.provider.plugins.HostResolverPlugin`
- `com.thedesitadka.provider.plugins.HostResolverEngine`
- `com.thedesitadka.provider.plugins.VixeoResolverPlugin`
