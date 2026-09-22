# LuluVdo / LuluStream External Video Host Resolver Plugin

## Overview
This plugin resolves external hosted video iframes from `https://luluvdo.com/e/{slug}`, `https://lulustream.com/e/{slug}`, `https://luluvid.com/e/{slug}`, and `https://playmogo.com/e/{slug}` into direct HLS (`.m3u8`) streaming links hosted on high-speed CDN edge nodes (such as `*.tnmr.org`) for smooth playback and background downloading in **TheDesiTadka**.

## Why this plugin is needed
Sites like `watchxxxfree.xyz` embed third-party video player iframes:
```html
<iframe src="https://luluvdo.com/e/aft5mblywcq6" frameborder="0" marginwidth="0" marginheight="0" scrolling="no" width="100%" height="100%" allowfullscreen></iframe>
```
Because the video is hosted on an external video delivery network protected by Dean Edwards JavaScript obfuscation, standard HTML video tag scraping fails. This plugin extracts and unpacks the obfuscated player script to find the authenticated stream URL.

## Player Architecture & Resolution Protocol
1. **Slug & Mirror Handling**:
   - Primary embed: `https://luluvdo.com/e/{slug}`
   - Alternate mirrors: `https://lulustream.com/e/{slug}`, `https://luluvid.com/e/{slug}`, `https://playmogo.com/e/{slug}`
   - Download page normalization: URLs with `/d/{slug}` are normalized to `/e/{slug}`.

2. **Dean Edwards Obfuscation**:
   The player configuration is packed inside an `eval(function(p,a,c,k,e,d)...)` block:
   ```javascript
   eval(function(p,a,c,k,e,d){while(c--)if(k[c])p=p.replace(new RegExp('\\b'+c.toString(a)+'\\b','g'),k[c]);return p}('l("36").82({81:[{28:"z://80.7z.1w/7y/7x/7w/7v/7u.7t?t=7s&s=3n&e=7r&f=3o&i=0.3&7q=0"}],...
   ```

3. **Unpacking Algorithm**:
   - Extract the four arguments: `payload`, `radix`, `count`, and `keywords` (pipe-delimited).
   - Match all base-N word tokens `\b[0-9a-zA-Z]+\b`.
   - Map each token through the symbol table to restore the clean JavaScript.

4. **Stream URL Extraction**:
   Once unpacked, JWPlayer configuration reveals the direct master playlist:
   ```javascript
   jwplayer("vplayer").setup({
     sources: [{
       file: "https://vqawoebottj7u.tnmr.org/hls2/03/04343/aft5mblywcq6_h/master.m3u8?t=...&s=...&e=28800&f=21719700&i=0.3&sp=0"
     }],
     image: "https://img.lulucdn.com/aft5mblywcq6_xt.jpg"
   });
   ```

5. **Playback & Download Headers**:
   - `Referer: https://luluvdo.com/`
   - `Origin: https://luluvdo.com`
   - `User-Agent: (Browser UA - CRITICAL: The CDN edge node *.tnmr.org cryptographically locks the token ?t=... to the exact User-Agent string that loaded the embed HTML. The stream request MUST send the identical User-Agent, or it returns HTTP 403 Forbidden).`

## Android Engine Integration
Implemented natively in:
- `com.thedesitadka.provider.plugins.LuluvdoResolverPlugin`
- `com.thedesitadka.provider.plugins.HostResolverEngine`
- `com.thedesitadka.provider.adapters.HtmlSelectorAdapter`
