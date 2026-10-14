#!/usr/bin/env python3
"""Opt-in Linux endpoint hygiene reporter for Sibyl. No remote command execution."""
import argparse
import hashlib
import hmac
import json
import os
import platform
import socket
import ssl
import sys
import time
import uuid
from urllib.parse import urlparse
from urllib.request import Request, urlopen


def snapshot():
    asset_id = os.environ.get("SIBYL_ASSET_ID") or socket.gethostname()
    hostname = socket.gethostname()
    ip = os.environ.get("SIBYL_DEVICE_IPV4", "")
    if not ip:
        raise ValueError("Set explicit SIBYL_DEVICE_IPV4 from managed IPAM")
    return {
        "assetId": asset_id,
        "hostname": hostname,
        "ipv4": ip,
        "osName": platform.system() + " " + platform.release(),
        "patchState": os.environ.get("SIBYL_PATCH_STATE", "unknown"),
        "protectionState": os.environ.get("SIBYL_PROTECTION_STATE", "unknown"),
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    report = snapshot()
    body = json.dumps(report, separators=(",", ":"), ensure_ascii=True).encode("utf-8")
    if args.dry_run:
        print(json.dumps(report, indent=2))
        return
    url = os.environ.get("SIBYL_COLLECTOR_URL", "")
    collector = os.environ.get("SIBYL_COLLECTOR_ID", "")
    key_hex = os.environ.get("SIBYL_COLLECTOR_KEY_HEX", "")
    u = urlparse(url)
    if u.scheme != "https" or not u.hostname or u.username or u.password:
        raise ValueError("A trusted HTTPS SIBYL_COLLECTOR_URL is required")
    if not collector or len(bytes.fromhex(key_hex)) < 32:
        raise ValueError("Collector identity and 256-bit key required")
    stamp = str(int(time.time()))
    nonce = str(uuid.uuid4())
    hashed = hashlib.sha256(body).hexdigest()
    canonical = (stamp + "\n" + nonce + "\n" + collector + "\n" + hashed).encode()
    signature = hmac.new(bytes.fromhex(key_hex), canonical, hashlib.sha256).hexdigest()
    target = url.rstrip("/") + "/api/v1/collector/snapshot"
    headers = {
        "Content-Type": "application/json",
        "X-Sibyl-Collector": collector,
        "X-Sibyl-Time": stamp,
        "X-Sibyl-Nonce": nonce,
        "X-Sibyl-Signature": signature,
    }
    req = Request(target, data=body, headers=headers, method="POST")
    # Use the OS trust store, never disable TLS peer or hostname validation.
    with urlopen(req, timeout=15, context=ssl.create_default_context()) as response:
        if response.status != 202:
            raise RuntimeError("Collector rejected by Sibyl: HTTP " + str(response.status))
        print("Sibyl collector observation accepted")


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print("Collector failed: " + str(exc), file=sys.stderr)
        sys.exit(1)
