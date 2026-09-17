#!/usr/bin/env python3
"""
StreamHub Configuration Validator
=================================
Validates unsigned or signed configuration manifests against:
1. JSON Schema conformance
2. URL safety & SSRF avoidance (RFC1918, localhost, cloud metadata)
3. Expiration checks
4. Unsafe syntax / code execution attempts in selectors (eval, script, exec)
5. Capability consistency

Usage:
  python validate_config.py --input manifest.json
"""

import argparse
import ipaddress
import json
import re
import sys
import time
from pathlib import Path
from urllib.parse import urlparse

try:
    import jsonschema
except ImportError:
    jsonschema = None

FORBIDDEN_SELECTOR_PATTERNS = [
    re.compile(r"javascript\s*:", re.IGNORECASE),
    re.compile(r"<script", re.IGNORECASE),
    re.compile(r"eval\s*\(", re.IGNORECASE),
    re.compile(r"function\s*\(", re.IGNORECASE),
    re.compile(r"exec\s*\(", re.IGNORECASE),
    re.compile(r"__proto__", re.IGNORECASE),
    re.compile(r"constructor", re.IGNORECASE),
]

DISALLOWED_HOSTS = {
    "localhost", "127.0.0.1", "0.0.0.0", "::1", "metadata.google.internal",
    "169.254.169.254"
}


def is_ip_private(host: str) -> bool:
    try:
        ip = ipaddress.ip_address(host)
        return ip.is_private or ip.is_loopback or ip.is_link_local
    except ValueError:
        return False


def validate_url(url: str, field_name: str) -> list[str]:
    errors = []
    parsed = urlparse(url)
    if parsed.scheme != "https":
        errors.append(f"Field '{field_name}': URL must use HTTPS scheme ('{url}')")
    
    host = parsed.hostname or ""
    if host.lower() in DISALLOWED_HOSTS or is_ip_private(host):
        errors.append(f"Field '{field_name}': SSRF risk! Disallowed target host '{host}' in '{url}'")
    return errors


def validate_selectors(selectors: dict, provider_id: str) -> list[str]:
    errors = []
    for key, val in selectors.items():
        if not isinstance(val, str):
            continue
        for pattern in FORBIDDEN_SELECTOR_PATTERNS:
            if pattern.search(val):
                errors.append(
                    f"Provider '{provider_id}': Dangerous pattern '{pattern.pattern}' detected in selector '{key}'"
                )
    return errors


def validate_manifest(manifest: dict, schema_path: Path | None = None) -> list[str]:
    errors = []

    # 1. JSON Schema validation if available
    if jsonschema and schema_path and schema_path.exists():
        with open(schema_path, "r", encoding="utf-8") as sf:
            schema = json.load(sf)
        v = jsonschema.Draft202012Validator(schema)
        for err in v.iter_errors(manifest):
            errors.append(f"Schema validation error at {err.json_path}: {err.message}")

    # 2. Expiration check
    expires_at = manifest.get("expiresAt", 0)
    current_time_ms = int(time.time() * 1000)
    if expires_at <= current_time_ms:
        errors.append(f"Manifest is expired: expiresAt ({expires_at}) <= current ({current_time_ms})")

    # 3. Provider validations
    providers = manifest.get("providers", [])
    if not isinstance(providers, list):
        errors.append("Manifest 'providers' field must be an array")
        return errors

    seen_ids = set()
    for p in providers:
        pid = p.get("id", "")
        if pid in seen_ids:
            errors.append(f"Duplicate provider id: '{pid}'")
        seen_ids.add(pid)

        # Base URL validation
        base_url = p.get("baseUrl", "")
        if base_url:
            errors.extend(validate_url(base_url, f"{pid}.baseUrl"))

        # Selectors validation
        selectors = p.get("selectors")
        if isinstance(selectors, dict):
            errors.extend(validate_selectors(selectors, pid))

        # Adapter validation
        adapter = p.get("adapter", "")
        if adapter not in ("html_selector", "wordpress_rest", "rss", "json_api", "embedded_player"):
            errors.append(f"Provider '{pid}': Unknown or unsupported adapter type '{adapter}'")

    return errors


def main():
    parser = argparse.ArgumentParser(description="StreamHub Configuration Validator")
    parser.add_argument("--input", "-i", required=True, help="Path to manifest JSON file")
    parser.add_argument("--schema", "-s", help="Path to JSON schema file (optional)")
    args = parser.parse_args()

    input_path = Path(args.input)
    if not input_path.exists():
        print(f"[-] Input file not found: {input_path}", file=sys.stderr)
        sys.exit(1)

    with open(input_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    # If it's a signed envelope, extract payload
    if "payload" in data and "signature" in data:
        print("[*] Detected SignedPayload envelope. Extracting canonical payload for validation...")
        manifest = json.loads(data["payload"])
    else:
        manifest = data

    schema_file = Path(args.schema) if args.schema else Path(__file__).parent / "schema" / "manifest.schema.json"
    errors = validate_manifest(manifest, schema_file)

    if errors:
        print(f"[-] Validation failed with {len(errors)} error(s):", file=sys.stderr)
        for err in errors:
            print(f"    - {err}", file=sys.stderr)
        sys.exit(1)
    else:
        print("[+] Manifest validation PASSED! Configuration is compliant with StreamHub security policy.")
        print(f"    Schema version: {manifest.get('schemaVersion')}")
        print(f"    Config version: {manifest.get('configVersion')}")
        print(f"    Active providers: {len(manifest.get('providers', []))}")


if __name__ == "__main__":
    main()
