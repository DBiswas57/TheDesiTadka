#!/usr/bin/env python3
"""
LuluVdo / LuluStream Standalone Host Resolver Script
Resolves any luluvdo.com, lulustream.com, luluvid.com, or playmogo.com embed URL to its direct playable stream.
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

def unpack_dean_edwards(script):
    """Unpacks Dean Edwards packed JavaScript code."""
    def base_n(num, b):
        digits = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
        if num == 0:
            return "0"
        res = []
        while num:
            res.append(digits[num % b])
            num //= b
        return "".join(reversed(res))

    m = re.search(r'eval\(function\(p,a,c,k,e,[rd]\)\{.*?\}\)?\s*\(\s*[\'"](.+?)[\'"]\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*[\'"]([^\'"]*)[\'"]\s*\.\s*split\s*\(\s*[\'"]\|[\'"]\s*\)', script, re.DOTALL)
    if not m:
        return script

    payload = m.group(1)
    radix = int(m.group(2))
    count = int(m.group(3))
    sym_tab = m.group(4).split("|")

    digits = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"

    def decode_token(token):
        val = 0
        for ch in token:
            idx = digits.find(ch)
            if idx < 0 or idx >= radix:
                return -1
            val = val * radix + idx
        return val

    def replace_word(match):
        token = match.group(0)
        idx = decode_token(token)
        if 0 <= idx < len(sym_tab) and sym_tab[idx]:
            return sym_tab[idx]
        return token

    return re.sub(r'\b[0-9a-zA-Z]+\b', replace_word, payload)

def resolve_luluvdo(embed_url, referer=None):
    if not embed_url.startswith("http"):
        embed_url = "https://" + embed_url

    # Normalize /d/ to /e/
    embed_url = re.sub(r'/(?:d)/([A-Za-z0-9_-]+)', r'/e/\1', embed_url)

    parsed = urllib.parse.urlparse(embed_url)
    domain = parsed.netloc or "luluvdo.com"

    # 1. Fetch embed page HTML
    headers = {
        "User-Agent": DEFAULT_UA,
        "Referer": referer or f"https://{domain}/",
        "Origin": f"https://{domain}"
    }

    req = urllib.request.Request(embed_url, headers=headers)
    with urllib.request.urlopen(req, context=ctx, timeout=15) as resp:
        html = resp.read().decode("utf-8", errors="ignore")

    # 2. Unpack Dean Edwards packed script if present
    unpacked = unpack_dean_edwards(html) if "eval(function(p,a,c,k,e" in html else html

    # 3. Extract stream URL from JWPlayer setup or direct regex
    stream_url = None
    m_jw = re.search(r'sources:\s*\[\{\s*file:\s*[\x22\x27]([^\x22\x27]+)[\x22\x27]', unpacked)
    if m_jw:
        stream_url = m_jw.group(1)
    else:
        m_direct = re.search(r'https?://[^\s\x22\x27<>]+\.(?:m3u8|mp4)[^\s\x22\x27<>]*', unpacked)
        if m_direct:
            stream_url = m_direct.group(0)

    if not stream_url:
        raise ValueError(f"Could not extract stream URL from {embed_url}")

    # Extract poster if present
    poster = None
    m_img = re.search(r'image:\s*[\x22\x27]([^\x22\x27]+)[\x22\x27]', unpacked)
    if m_img:
        poster = m_img.group(1)

    is_hls = ".m3u8" in stream_url

    return {
        "stream_url": stream_url,
        "type": "hls" if is_hls else "mp4",
        "poster": poster,
        "headers": {
            "Referer": f"https://{domain}/",
            "Origin": f"https://{domain}",
            "User-Agent": DEFAULT_UA
        }
    }

if __name__ == "__main__":
    test_url = sys.argv[1] if len(sys.argv) > 1 else "https://luluvdo.com/e/aft5mblywcq6"
    print(f"Resolving LuluVdo URL: {test_url} ...")
    res = resolve_luluvdo(test_url)
    print(json.dumps(res, indent=2))
