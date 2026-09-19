#!/usr/bin/env python3
"""
Vixeo.io Standalone Resolver Script
Resolves any vixeo.io embed URL to its direct playable/downloadable stream.
"""
import sys
import re
import base64
import json
import urllib.request

def resolve_vixeo(embed_url, referer=None):
    if not embed_url.startswith("http"):
        embed_url = "https://" + embed_url
    
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Referer": referer or "https://vixeo.io/",
        "Origin": "https://vixeo.io"
    }
    
    req = urllib.request.Request(embed_url, headers=headers)
    with urllib.request.urlopen(req, timeout=15) as resp:
        html = resp.read().decode("utf-8", errors="ignore")
    
    m = re.search(r'id=[\x27\x22]streamsonic-player-root[\x27\x22][^>]*data-config=[\x27\x22]([^\x27\x22]+)[\x27\x22]', html)
    if not m:
        raise ValueError("streamsonic-player-root data-config not found in HTML")
    
    raw_b64 = m.group(1)
    config_json = base64.b64decode(raw_b64).decode("utf-8")
    config = json.loads(config_json)
    
    source_encoded = config.get("source", "").replace("|", "").strip()
    if not source_encoded or len(source_encoded) % 2 != 0:
        raise ValueError("Invalid encoded source string")
    
    stream_url = bytes.fromhex(source_encoded).decode("latin1")[::-1]
    is_mp4 = config.get("isMp4", False)
    
    return {
        "stream_url": stream_url,
        "type": "mp4" if is_mp4 else "hls",
        "title": config.get("title"),
        "headers": {
            "Referer": "https://vixeo.io/",
            "Origin": "https://vixeo.io"
        }
    }

if __name__ == "__main__":
    test_url = sys.argv[1] if len(sys.argv) > 1 else "https://vixeo.io/e/EQkJKzs7LwIf"
    print(f"Resolving {test_url} ...")
    res = resolve_vixeo(test_url)
    print(json.dumps(res, indent=2))
