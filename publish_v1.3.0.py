#!/usr/bin/env python3
import hashlib
import json
import os
import shutil
import subprocess
import sys
import urllib.request
import urllib.error

print("=" * 60)
print("   TheDesiTadka v1.3.0 Major Release Publisher (Python)")
print("=" * 60)

repo = "DBiswas57/TheDesiTadka"
tag_name = "v1.3.0"
release_title = "TheDesiTadka v1.3.0 - Major UI Revamp, Multi-Color Palettes & 74 Sites Verified"

repo_root = os.path.dirname(os.path.abspath(__file__))
built_apk = os.path.join(repo_root, "Mobile", "app", "build", "outputs", "apk", "release", "app-release.apk")
dest_apk = os.path.join(repo_root, "release", "TheDesiTadka-release.apk")
versioned_apk = os.path.join(repo_root, "release", "TheDesiTadka-v1.3.0-release.apk")

# 1. Copy built APK
if os.path.exists(built_apk):
    print("[*] Copying built APK to release directory...")
    os.makedirs(os.path.dirname(dest_apk), exist_ok=True)
    shutil.copy2(built_apk, dest_apk)
    shutil.copy2(built_apk, versioned_apk)
    print(f"[+] Copied to {dest_apk} and {versioned_apk}")

if not os.path.exists(dest_apk):
    print(f"[-] Error: APK not found at {dest_apk}", file=sys.stderr)
    sys.exit(1)

with open(dest_apk, "rb") as f:
    apk_bytes = f.read()
file_hash = hashlib.sha256(apk_bytes).hexdigest()
print(f"[+] APK SHA-256: {file_hash}")
print(f"[+] APK Size: {len(apk_bytes) / (1024 * 1024):.2f} MB")

# 2. Retrieve GitHub Token
print("\n[*] Retrieving GitHub credentials...")
cred_input = b"protocol=https\nhost=github.com\nusername=DBiswas57\n"
res = subprocess.run(["git", "credential", "fill"], input=cred_input, stdout=subprocess.PIPE, check=True)
token = None
for line in res.stdout.decode("utf-8").splitlines():
    if line.startswith("password="):
        token = line[len("password="):].strip()
        break

if not token:
    print("[-] Error: Could not retrieve GitHub token.", file=sys.stderr)
    sys.exit(1)

headers = {
    "Authorization": f"Bearer {token}",
    "Accept": "application/vnd.github+json",
    "User-Agent": "TheDesiTadka-Publisher"
}

# Verify user
req = urllib.request.Request("https://api.github.com/user", headers=headers)
with urllib.request.urlopen(req) as resp:
    user_data = json.loads(resp.read().decode("utf-8"))
    print(f"[+] Authenticated as GitHub user: {user_data.get('login')}")

# 3. Prepare Release Notes
release_notes = f"""# TheDesiTadka v1.3.0 Major Release 🚀

Welcome to **TheDesiTadka v1.3.0** — our biggest and most comprehensive update yet, featuring a total visual transformation, multi-threaded search, dynamic color styling, and full 74-site verification!

---

### ✨ What's New in v1.3.0:

#### 1. 🎨 Modern Glassmorphism & 3D UI Revamp
- **Floating Glassmorphic Bottom Dock**: High-translucency frosted glass dock with specular edge borders, ambient glow drops, and spring-bouncy active tab animations.
- **3D Interactive Video Cards**: High-definition rounded corners (14dp), specular highlight borders, tactile pressed-state micro-bounce animations, and frosted glass metadata pills.
- **Hero Carousel 3D Parallax**: Dynamic 3D depth scaling, luminous gradient scrims, and pulsating "Watch Now" action buttons.
- **Frosted Category Navigation**: Translucent glass filter chips with active gradient glow and border highlights.

#### 2. 🌈 5 Dynamic Cinematic Color Palettes
Easily switch color palettes on the fly from **Settings -> Visual Style & Color Palettes**:
- **Sunset Amber** *(Default)*: Warm cinematic amber gold & fiery coral gradient.
- **Cyber Neon**: Hyper-futuristic cyan neon & high-voltage acid magenta.
- **Oceanic Aurora**: Deep sea sapphire & luminous arctic cyan.
- **Emerald Matrix**: Cybernetic emerald jade & energetic lime accents.
- **Crimson Royale**: Luxurious ruby rose & majestic royal purple velvet.

#### 3. ⚡ Multi-Threaded Real-Time Progressive Search
- Instant concurrent parallel search across all providers.
- Real-time progressive streaming: results display instantly as each site resolves without waiting 2-5 minutes!
- Source progress indicator tracking completed and total sources in real-time.

#### 4. 🛡️ Deep 74-Site Pipeline Verification
- Fully verified all 74 enlisted sites across:
  - Home feed content grab & Jsoup CSS selector parsing.
  - Video stream extraction & HostResolverEngine embed resolution (Luluvdo, Vixeo, Firestream, Streamoupload, etc.).
  - Search query templates (`{{query}}`) and query encodings.
  - Category / special link detection & multi-page navigation.
  - Strict HTTPS enforcement & SSRF safety.
  - Safe sanitized download file pipelines.

---

### 📦 Package & Security Verification
- **Package Name**: `com.thedesitadka.app`
- **Version Code**: `24`
- **Version Name**: `1.3.0`
- **Config Manifest Version**: `v175` (74 Active Verified Providers)
- **APK SHA-256**: `{file_hash}`
"""

# 4. Create or Update Release
print(f"\n[*] Creating or updating release {tag_name} on GitHub...")
release_id = None
try:
    req = urllib.request.Request(f"https://api.github.com/repos/{repo}/releases/tags/{tag_name}", headers=headers)
    with urllib.request.urlopen(req) as resp:
        rel = json.loads(resp.read().decode("utf-8"))
        release_id = rel["id"]
        print(f"[*] Found existing release ID: {release_id}")
        patch_payload = json.dumps({"name": release_title, "body": release_notes}).encode("utf-8")
        patch_req = urllib.request.Request(
            f"https://api.github.com/repos/{repo}/releases/{release_id}",
            data=patch_payload,
            headers={**headers, "Content-Type": "application/json"},
            method="PATCH"
        )
        with urllib.request.urlopen(patch_req) as patch_resp:
            print(f"[+] Updated release metadata for: {release_id}")
except urllib.error.HTTPError as e:
    if e.code == 404:
        create_payload = json.dumps({
            "tag_name": tag_name,
            "target_commitish": "main",
            "name": release_title,
            "body": release_notes,
            "draft": False,
            "prerelease": False
        }).encode("utf-8")
        create_req = urllib.request.Request(
            f"https://api.github.com/repos/{repo}/releases",
            data=create_payload,
            headers={**headers, "Content-Type": "application/json"},
            method="POST"
        )
        with urllib.request.urlopen(create_req) as create_resp:
            rel = json.loads(create_resp.read().decode("utf-8"))
            release_id = rel["id"]
            print(f"[+] Created release: {release_id} at {rel.get('html_url')}")
    else:
        raise

# Retrieve assets
req = urllib.request.Request(f"https://api.github.com/repos/{repo}/releases/{release_id}", headers=headers)
with urllib.request.urlopen(req) as resp:
    release_info = json.loads(resp.read().decode("utf-8"))
    existing_assets = release_info.get("assets", [])

# 5. Upload APK Asset
apk_name = "TheDesiTadka-release.apk"
print(f"\n[*] Processing asset: {apk_name}...")
for a in existing_assets:
    if a["name"] == apk_name:
        print(f"[*] Deleting existing asset {a['id']}...")
        del_req = urllib.request.Request(
            f"https://api.github.com/repos/{repo}/releases/assets/{a['id']}",
            headers=headers,
            method="DELETE"
        )
        urllib.request.urlopen(del_req).close()

upload_url = f"https://uploads.github.com/repos/{repo}/releases/{release_id}/assets?name={apk_name}"
print(f"[*] Uploading {apk_name} ({len(apk_bytes) / (1024 * 1024):.2f} MB)...")
up_req = urllib.request.Request(
    upload_url,
    data=apk_bytes,
    headers={
        **headers,
        "Content-Type": "application/vnd.android.package-archive",
        "Content-Length": str(len(apk_bytes))
    },
    method="POST"
)
with urllib.request.urlopen(up_req) as up_resp:
    up_data = json.loads(up_resp.read().decode("utf-8"))
    print(f"[+] Uploaded: {up_data['name']} -> {up_data['browser_download_url']}")

# 6. Upload signed-manifest.json Asset
manifest_path = os.path.join(repo_root, "config-tools", "signed-manifest.json")
if os.path.exists(manifest_path):
    manifest_name = "signed-manifest.json"
    print(f"\n[*] Processing asset: {manifest_name}...")
    with open(manifest_path, "rb") as f:
        manifest_bytes = f.read()

    for a in existing_assets:
        if a["name"] == manifest_name:
            print(f"[*] Deleting existing asset {a['id']}...")
            del_req = urllib.request.Request(
                f"https://api.github.com/repos/{repo}/releases/assets/{a['id']}",
                headers=headers,
                method="DELETE"
            )
            urllib.request.urlopen(del_req).close()

    manifest_upload_url = f"https://uploads.github.com/repos/{repo}/releases/{release_id}/assets?name={manifest_name}"
    m_req = urllib.request.Request(
        manifest_upload_url,
        data=manifest_bytes,
        headers={
            **headers,
            "Content-Type": "application/json",
            "Content-Length": str(len(manifest_bytes))
        },
        method="POST"
    )
    with urllib.request.urlopen(m_req) as m_resp:
        m_data = json.loads(m_resp.read().decode("utf-8"))
        print(f"[+] Uploaded: {m_data['name']} -> {m_data['browser_download_url']}")

# 7. Verify live release
print("\n[*] Verifying release is live...")
v_req = urllib.request.Request(f"https://api.github.com/repos/{repo}/releases/tags/{tag_name}", headers=headers)
with urllib.request.urlopen(v_req) as v_resp:
    v_data = json.loads(v_resp.read().decode("utf-8"))
    print(f"[+] Release verified: {len(v_data['assets'])} asset(s) attached:")
    for a in v_data["assets"]:
        print(f"    - {a['name']}: {a['browser_download_url']}")

# 8. Verify AppUpdateManager Endpoint
print("\n[*] Verifying latest release API endpoint for app update detection...")
latest_req = urllib.request.Request(f"https://api.github.com/repos/{repo}/releases/latest", headers=headers)
with urllib.request.urlopen(latest_req) as l_resp:
    latest_data = json.loads(l_resp.read().decode("utf-8"))
    print(f"[+] Latest release tag on GitHub: {latest_data.get('tag_name')}")
    print(f"[+] Latest release name:           {latest_data.get('name')}")
    apk_assets = [a for a in latest_data.get("assets", []) if a["name"].endswith(".apk")]
    if apk_assets:
        print(f"[+] Latest release APK URL:        {apk_assets[0]['browser_download_url']}")

    if latest_data.get("tag_name") == tag_name:
        print(f"\n[SUCCESS] App update detection verified! All app users will now automatically detect v1.3.0 update.")
    else:
        print(f"[*] Note: Latest tag is {latest_data.get('tag_name')}, target is {tag_name}")

print("\n" + "=" * 60)
print("   v1.3.0 Release Published Successfully!")
print("=" * 60)
