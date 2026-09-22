#!/usr/bin/env python3
"""
FireStream Standalone Host Resolver Script
Resolves any FireStream embed URL (firestream.site / firestream.to) to its direct playable/downloadable stream.
"""
import sys
import re
import json
import ssl
import urllib.request
import urllib.parse

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

DEFAULT_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

def resolve_firestream(embed_url, referer=None):
    if not embed_url.startswith("http"):
        embed_url = "https://" + embed_url

    # Extract slug
    m_slug = re.search(r'/(?:e|v|embed)/([A-Za-z0-9_-]+)', embed_url)
    if not m_slug:
        m_slug = re.search(r'firestream\.[a-z]+/([A-Za-z0-9_-]+)', embed_url)
    if not m_slug:
        raise ValueError(f"Could not extract video slug from {embed_url}")
    slug = m_slug.group(1)

    parsed = urllib.parse.urlparse(embed_url)
    domain = parsed.netloc or "firestream.site"

    # 1. Fetch embed page HTML
    headers = {
        "User-Agent": DEFAULT_UA,
        "Referer": referer or f"https://{domain}/",
        "Origin": f"https://{domain}"
    }
    req = urllib.request.Request(embed_url, headers=headers)
    with urllib.request.urlopen(req, context=ctx, timeout=15) as resp:
        html = resp.read().decode("utf-8", errors="ignore")

    # 2. Extract token-blob
    m_blob = re.search(r'id=["\']token-blob["\'][^>]*>(.*?)<', html)
    if not m_blob:
        raise ValueError("Could not find token-blob element in HTML")
    blob = m_blob.group(1).strip()

    # Title extraction if present
    title = None
    m_title = re.search(r'"title"\s*:\s*"([^"]+)"', html)
    if m_title:
        title = m_title.group(1)

    # 3. Request resolved stream URL from API
    resolve_url = f"https://{domain}/api/videos/{slug}/resolve"
    post_payload = json.dumps({"blob": blob}).encode("utf-8")
    resolve_headers = {
        "User-Agent": DEFAULT_UA,
        "Content-Type": "application/json",
        "Referer": embed_url,
        "Origin": f"https://{domain}"
    }

    req_resolve = urllib.request.Request(resolve_url, data=post_payload, headers=resolve_headers, method="POST")
    with urllib.request.urlopen(req_resolve, context=ctx, timeout=15) as resp:
        data = json.loads(resp.read().decode("utf-8"))

    stream_url = data.get("signedVideoUrl")
    if not stream_url:
        raise ValueError(f"No signedVideoUrl in resolve response: {data}")

    is_hls = ".m3u8" in stream_url

    return {
        "stream_url": stream_url,
        "type": "hls" if is_hls else "mp4",
        "title": title,
        "headers": {
            "Referer": f"https://{domain}/",
            "Origin": f"https://{domain}",
            "User-Agent": DEFAULT_UA
        }
    }

if __name__ == "__main__":
    test_url = sys.argv[1] if len(sys.argv) > 1 else "https://firestream.site/e/nhbUOAaQ"
    print(f"Resolving FireStream URL: {test_url} ...")
    res = resolve_firestream(test_url)
    print(json.dumps(res, indent=2))
