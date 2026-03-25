from __future__ import annotations

import hashlib
from collections.abc import Iterable
from datetime import UTC, datetime
from urllib.parse import parse_qs, urlencode, urljoin, urlparse, urlunparse


def utc_now_iso() -> str:
    return datetime.now(tz=UTC).isoformat(timespec="seconds")


def ensure_absolute_base(url: str) -> str:
    raw = url.strip()
    if not raw:
        raise ValueError("Empty mirror URL")
    if not raw.startswith(("http://", "https://")):
        raw = f"https://{raw}"
    parsed = urlparse(raw)
    if not parsed.netloc:
        raise ValueError(f"Invalid mirror URL: {url}")
    normalized = parsed._replace(path="", params="", query="", fragment="")
    return urlunparse(normalized).rstrip("/")


def to_absolute_url(base_url: str, value: str) -> str:
    return normalize_url(urljoin(f"{base_url}/", value.strip()))


def normalize_url(raw_url: str) -> str:
    parsed = urlparse(raw_url)
    query_pairs = parse_qs(parsed.query, keep_blank_values=True)
    sorted_pairs: list[tuple[str, str]] = []
    for key in sorted(query_pairs.keys()):
        for item in sorted(query_pairs[key]):
            sorted_pairs.append((key, item))
    clean_query = urlencode(sorted_pairs, doseq=True)
    fragmentless = parsed._replace(fragment="", query=clean_query)
    return urlunparse(fragmentless)


def hash_url(url: str) -> str:
    return hashlib.sha256(url.encode("utf-8", errors="ignore")).hexdigest()


def query_value_as_int(url: str, key: str) -> int | None:
    parsed = urlparse(url)
    values = parse_qs(parsed.query).get(key)
    if not values:
        return None
    try:
        return int(values[0])
    except (TypeError, ValueError):
        return None


def unique_preserve_order(items: Iterable[str]) -> list[str]:
    seen: set[str] = set()
    ordered: list[str] = []
    for item in items:
        if item in seen:
            continue
        seen.add(item)
        ordered.append(item)
    return ordered
