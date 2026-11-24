#!/usr/bin/env python3
"""Download and validate content at build time so the installed app is offline-first."""
from __future__ import annotations
import json
import sys
import time
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ANDROID_ASSETS = ROOT / "android" / "app" / "src" / "main" / "assets" / "www"
OUTPUTS = {
    "quran-data.json": "quran",
    "nawawi-hadiths.json": "nawawi",
    "bukhari-100.json": "bukhari",
}

URLS = {
    "quran": ["https://api.alquran.cloud/v1/quran/quran-uthmani"],
    "nawawi": [
        "https://cdn.jsdelivr.net/gh/fawazahmed0/hadith-api@1/editions/ara-nawawi.min.json",
        "https://raw.githubusercontent.com/fawazahmed0/hadith-api/1/editions/ara-nawawi.json",
    ],
    "bukhari": [
        "https://cdn.jsdelivr.net/gh/fawazahmed0/hadith-api@1/editions/ara-bukhari.min.json",
        "https://raw.githubusercontent.com/fawazahmed0/hadith-api/1/editions/ara-bukhari.json",
    ],
}


def fetch_json(url: str):
    request = urllib.request.Request(url, headers={"User-Agent": "ZadAlQolob-Offline-AssetBuilder/1.0"})
    with urllib.request.urlopen(request, timeout=60) as response:
        if response.status != 200:
            raise RuntimeError(f"HTTP {response.status}: {url}")
        return json.loads(response.read().decode("utf-8"))


def download(name: str):
    last_error = None
    for url in URLS[name]:
        for attempt in range(3):
            try:
                return fetch_json(url)
            except Exception as exc:  # retry transient CDN/API failures
                last_error = exc
                if attempt < 2:
                    time.sleep(2 * (attempt + 1))
    raise RuntimeError(f"Unable to download {name} data: {last_error}")


def build_quran():
    payload = download("quran")
    if payload.get("code") != 200 or not isinstance(payload.get("data"), dict):
        raise ValueError("Quran API returned an invalid response")
    source = payload["data"]
    surahs = source.get("surahs") or []
    if len(surahs) != 114:
        raise ValueError(f"Expected 114 surahs, received {len(surahs)}")
    ayahs = []
    for surah in surahs:
        for ayah in surah.get("ayahs") or []:
            if not ayah.get("text") or not ayah.get("page") or not ayah.get("juz"):
                raise ValueError(f"Incomplete Quran ayah in surah {surah.get('number')}")
            ayahs.append({
                **ayah,
                "surah": {
                    "number": surah["number"],
                    "name": surah["name"],
                    "englishName": surah.get("englishName", ""),
                },
            })
    if len(ayahs) != 6236:
        raise ValueError(f"Expected 6236 ayahs, received {len(ayahs)}")
    return {"surahs": surahs, "ayahs": ayahs, "edition": source.get("edition", {})}


def build_nawawi():
    payload = download("nawawi")
    hadiths = payload.get("hadiths") if isinstance(payload, dict) else None
    if not isinstance(hadiths, list) or len(hadiths) != 42:
        raise ValueError("Expected exactly 42 Nawawi hadiths")
    if any(not item.get("text") for item in hadiths):
        raise ValueError("Nawawi collection contains an empty hadith")
    return {"hadiths": sorted(hadiths, key=lambda item: int(item.get("hadithnumber", 0)))}


def build_bukhari():
    payload = download("bukhari")
    hadiths = payload.get("hadiths") if isinstance(payload, dict) else None
    if not isinstance(hadiths, list) or len(hadiths) < 100:
        raise ValueError("Bukhari source does not contain at least 100 hadiths")
    first = hadiths[:100]
    if any(not item.get("text") for item in first):
        raise ValueError("Bukhari first 100 contains an empty hadith")
    return {"hadiths": first}


def main():
    ANDROID_ASSETS.mkdir(parents=True, exist_ok=True)
    builders = {"quran": build_quran, "nawawi": build_nawawi, "bukhari": build_bukhari}
    for filename, key in OUTPUTS.items():
        print(f"Preparing bundled {key} data...")
        data = builders[key]()
        encoded = json.dumps(data, ensure_ascii=False, separators=(",", ":"))
        # The root copy supports local browser preview; the Android copy is packaged into the APK.
        (ROOT / filename).write_text(encoded, encoding="utf-8")
        (ANDROID_ASSETS / filename).write_text(encoded, encoding="utf-8")
        print(f"  {filename}: {len(encoded.encode('utf-8')):,} bytes")
    print("Offline content assets are ready and validated.")


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        sys.exit(1)
