# TheDesiTadka: 27-Site Forensic Provider Verification Matrix

This verification matrix provides the final, end-to-end forensic verification records for all **27 reference websites** found in `SiteReferrence` and `SiteRef_Picture`. Each provider was audited across the entire media lifecycle: Domain Health, Home Feed Discovery, Item Page Parsing, Direct Stream Extraction, Video Playback (Media3/ExoPlayer), Offline Download Feasibility, Cloudflare State Clearance, and Anti-Hotlinking CDN Header Verification.

---

## 27-Site Comprehensive Verification Matrix

| # | Provider Name | Base Domain | Extraction Method | Home Feed | Item Page | Stream Extracted | Playback Verified | Download Feasibility | Cloudflare Challenge State | CDN Headers Required | Retention / Final Status | Notes / Limitations |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | **AagMaal** | `aagmaal.date` | HTML + Tube279 Embed Parser | PASS | PASS | PASS | PASS | Direct MP4 | NOT_REQUIRED | `Referer: https://tube279.com/` | **RETAINED (PASS)** | Primary active mirror of AagMaal network. Resolves Tube279 player hashes to progressive MP4 streams. |
| 2 | **AagMaal Com** | `aagmaal.com` | HTML + Tube279 Embed Parser | PASS | PASS | PASS | PASS | Direct MP4 | NOT_REQUIRED | `Referer: https://tube279.com/` | **RETAINED (PASS)** | Domain alias resolving to active `aagmaal.date` catalog. Streams require Tube279 embed Referer. |
| 3 | **AntarvasnaBF** | `antarvasnabf.com` | HTML + Clean-Tube Base64 Decoder | PASS | PASS | PASS | PASS | Direct MP4 | NOT_REQUIRED | `Referer: https://antarvasnabf.com/` | **RETAINED (PASS)** | Clean-Tube base64 iframe parsed via Jsoup. Spaces in decrypted stream URL URL-encoded (`%20`). |
| 4 | **DesiBF** | `desibf.com` | HTML Selector + Direct CDN | PASS | PASS | PASS | PASS | Direct MP4 | NOT_REQUIRED | Default | **RETAINED (PASS)** | Direct CDN progressive MP4 hosted on `cdn.desibf.com`. Standard Range headers supported. |
| 5 | **DesiKahani2** | `desikahani2.net` | KVS (Kernel Video Sharing) | PASS | PASS | PASS | PASS | Direct MP4 | NOT_REQUIRED | Default | **RETAINED (PASS)** | KVS video section routed to `/videos/` with `data-webp` thumbnails & `/videos/get_file/` MP4. |
| 6 | **DesiSex** | `desisex.site` | HTML Selector + Direct CDN | PASS | PASS | PASS | PASS | Direct MP4 | NOT_REQUIRED | Default | **RETAINED (PASS)** | Poster thumbnail extracted from `<video poster>`; protocol-relative `//cdn2` resolved to HTTPS. |
| 7 | **DesiTales2** | `desitales2.com` | KVS (Kernel Video Sharing) | PASS | PASS | PASS | PASS | Direct MP4 | NOT_REQUIRED | Default | **RETAINED (PASS)** | KVS video section routed to `/videos/` with `data-webp` thumbnails & `/videos/get_file/` MP4. |
| 8 | **Fry99** | `fry99.cc` | HTML Selector + Stream Tape/Lulu | PASS | PASS | PASS | PASS | Direct MP4 / External | VERIFIED (Clearance Cached) | `Referer: https://fry99.cc/` | **RETAINED (PASS)** | Cloudflare Turnstile handled via in-app WebView without premature dismissal; clearance cached. |
| 9 | **FSIBlog** | `fsiblogxx.com` | WordPress Video Taxonomy + Clean-Tube | PASS | PASS | PASS | PASS | Direct MP4 | NOT_REQUIRED | `Referer: https://fsiblogxx.com/` | **RETAINED (PASS)** | Filtered to video-only posts (`type-porn-video`). Data-src thumbnail extracted; Clean-Tube base64 player parsed. |
| 10 | **HitMaal** | `hitmaal.io` | WordPress HTML Selector ("Desi Tadka") | PASS | PASS | PASS | PASS | Direct MP4 | NOT_REQUIRED | Default | **RETAINED (PASS)** | WordPress video theme; responsive player extraction with `data-bg` thumbnails. |
| 11 | **IndianPornGirl** | `indianporngirl.org` | WordPress Paywall Member System | PASS | PASS | BLOCKED | BLOCKED | BLOCKED | NOT_REQUIRED | N/A | **REMOVED (PAYWALL)** | **EXCISED FROM APP**. Video detail pages require paid subscription membership (`/membership-account/membership-levels/`). |
| 12 | **IndianSexStories3** | `indiansexstories3.com` | KVS (Kernel Video Sharing) | PASS | PASS | PASS | PASS | Direct MP4 | NOT_REQUIRED | Default | **RETAINED (PASS)** | KVS video section routed to `/videos/` with `data-webp` thumbnails & `/videos/get_file/` MP4. |
| 13 | **IxiPorn** | `ixiporn.live` | HTML + Clean-Tube Base64 Decoder | PASS | PASS | PASS | PASS | Direct MP4 | NOT_REQUIRED | `Referer: https://ixiporn.live/` | **RETAINED (PASS)** | Clean-Tube base64 iframe parsed via Jsoup. Stream routed via `cdn2.ixifile.xyz` with source Referer. |
| 14 | **KamaBaba1** | `kamababa1.com` | HTML + Clean-Tube Base64 Decoder | PASS | PASS | PASS | PASS | Direct MP4 | NOT_REQUIRED | `Referer: https://kamababa1.com/` | **RETAINED (PASS)** | Clean-Tube base64 + direct CDN MP4 with referer routing. |
| 15 | **Masa49** | `masa49.nl` | Masa Engine (`.vcard`, background-image) | PASS | PASS | PASS | PASS | Direct MP4 | NOT_REQUIRED | `Referer: https://www.masa49.nl/` | **RETAINED (PASS)** | Private CDN (`files.pvtcdn.com`) requires matching host Referer. |
| 16 | **MasaFun** | `masafun.art` | Masa Engine (`.vcard`, background-image) | PASS | PASS | PASS | PASS | Direct MP4 | NOT_REQUIRED | `Referer: https://masafun.art/` | **RETAINED (PASS)** | Video cards use `.vcard` with background-image style thumbnails; streams routed via `server2.mydown.biz`. |
| 17 | **MasaHub2** | `masahub2.com` | Masa Engine (`.vcard`, background-image) | PASS | PASS | PASS | PASS | Direct MP4 | NOT_REQUIRED | `Referer: https://masahub2.com/` | **RETAINED (PASS)** | Tuned `h3.vtitle` selector and regex CSS background-image thumbnail extractor. Streams via `server2.mydown.biz`. |
| 18 | **PornX11** | `pornx11.com` | HTML + LuluVdo / Packer Unpacker | PASS | PASS | PASS | PASS | Direct MP4 | NOT_REQUIRED | `Referer: https://pornx11.com/` | **RETAINED (PASS)** | Packed JavaScript embeds unpacked via Dean Edwards P.A.C.K.E.R. parser to extract source stream. |
| 19 | **UncutMaza** | `uncutmaza.cc` | HTML + Clean-Tube Base64 Decoder | PASS | PASS | PASS | PASS | Direct MP4 | NOT_REQUIRED | `Referer: https://uncutmaza.cc/` | **RETAINED (PASS)** | Clean-Tube base64 query decoded with space encoding. Stream served via CDN with Referer validation. |
| 20 | **WebXSeries** | `webxseries.hot` | REST API JSON Feed | PASS | PASS | PASS | PASS | Direct MP4 | NOT_REQUIRED | Default | **RETAINED (PASS)** | REST API JSON feed parsing with direct stream URL resolution. |
| 21 | **WowMasti** | `wowmasti.com` | HTML + LuluStream Player Parser | PASS | PASS | PASS | PASS | Direct MP4 / External | VERIFIED (Clearance Cached) | `Referer: https://wowmasti.com/` | **RETAINED (PASS)** | Cloudflare Turnstile challenge verified without premature dismissal; LuluStream player extraction. |
| 22 | **WowUncut** | `wowuncut.com` | HTML + Clean-Tube Base64 Decoder | PASS | PASS | PASS | PASS | Direct MP4 | NOT_REQUIRED | `Referer: https://wowuncut.com/` | **RETAINED (PASS)** | Title extracted from `header.entry-header span` / `a[data-title]`; Clean-Tube spaces encoded. |
| 23 | **XHamster** | `xhamster.com` | Desktop & Mobile HTML Video Selectors | PASS | PASS | PASS | PASS | Segmented (External Fallback) | NOT_REQUIRED | `Referer: https://xhamster.com/` | **RETAINED (PASS)** | Mobile video thumb selectors (`.mobile-video-thumb`, `a.mobile-video-thumb__name`). HLS stream fallback to browser. |
| 24 | **XMaza** | `xmaza.xxx` | HTML Selector (`a.video.lazy-bg`) | PASS | PASS | PASS | PASS | Direct MP4 | NOT_REQUIRED | `Referer: https://xmaza.xxx/` | **RETAINED (PASS)** | Item selector updated to `a.video` with `data-bg` and background-image style thumbnail extraction. |
| 25 | **XNXX** | `xnxx.com` | XNXX Network HTML + Proprietary JS | PASS | PASS | PASS | PASS | Direct MP4 (High/Low) | NOT_REQUIRED | `Referer: https://www.xnxx.com/` | **RETAINED (PASS)** | Home feed configured to `/hot`. Proprietary `html5player.setVideoUrl` extracted for High & Low quality MP4. |
| 26 | **XVideos** | `xvideos.com` | XVideos Network HTML + Proprietary JS | PASS | PASS | PASS | PASS | Direct MP4 (High/Low) | NOT_REQUIRED | `Referer: https://www.xvideos.com/` | **RETAINED (PASS)** | Proprietary `html5player.setVideoUrl` High/Low/HLS stream extraction. Thumbnails parsed from `.thumb-under`. |
| 27 | **XXXIndianStories** | `xxxindianstories.com` | KVS (Kernel Video Sharing) | PASS | PASS | PASS | PASS | Direct MP4 | NOT_REQUIRED | Default | **RETAINED (PASS)** | KVS video section routed to `/videos/` with `data-webp` thumbnails & `/videos/get_file/` MP4. |

---

## Metric Breakdown & Final Distribution

- **Total Reference Websites Audited**: 27
- **Total Providers Registered in TheDesiTadka Catalog**: 26 (v121)
- **PASS (Full Journey Verified: Discovery → Parse → Play → Download/Fallback)**: 26
- **EXCISED / REMOVED (External Paywall Restriction)**: 1 (`indianporngirl.org` — Paid subscription required for all detail video playback)
- **Direct MP4 Download Viability**: 25 Providers
- **Segmented / HLS with Seamless External Browser Fallback**: 1 Provider (`xhamster`)
- **Cloudflare Turnstile Verified Providers**: 2 Providers (`fry99`, `wowmasti`)
- **Anti-Hotlinking CDN Protected Streams (Dynamic Referer/Origin Injected)**: 14 Providers
