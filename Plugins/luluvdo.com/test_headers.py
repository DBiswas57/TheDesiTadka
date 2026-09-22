import urllib.request, ssl

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

# Resolve fresh URL
from luluvdo_resolver import resolve_luluvdo
res = resolve_luluvdo("https://luluvdo.com/e/aft5mblywcq6")
m3u8 = res["stream_url"]
print("Resolved m3u8:", m3u8)

test_cases = [
    ('ExoPlayer standard headers', {
        'User-Agent': 'Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36',
        'Referer': 'https://luluvdo.com/',
        'Origin': 'https://luluvdo.com'
    }),
    ('No Origin', {
        'User-Agent': 'Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36',
        'Referer': 'https://luluvdo.com/'
    }),
    ('No Referer or Origin', {
        'User-Agent': 'Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36'
    }),
    ('With Origin and without slash in Referer', {
        'User-Agent': 'Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36',
        'Referer': 'https://luluvdo.com',
        'Origin': 'https://luluvdo.com'
    })
]

for name, h in test_cases:
    try:
        req = urllib.request.Request(m3u8, headers=h)
        with urllib.request.urlopen(req, context=ctx, timeout=8) as resp:
            print(f'{name}: SUCCESS {resp.status}, Content-Type={resp.headers.get("Content-Type")}')
    except Exception as e:
        print(f'{name}: FAILED {e}')
