from __future__ import annotations

import json
import math
import os
import re
from difflib import SequenceMatcher
from pathlib import Path
from types import SimpleNamespace
from typing import Any, Literal
from urllib.parse import urlencode, urlparse

from .client import RutrackerHttpClient
from .constants import (
    DEFAULT_ALLOWED_PATH_PREFIXES,
    DEFAULT_BACKOFF_BASE_SEC,
    DEFAULT_BACKOFF_MAX_SEC,
    DEFAULT_CONNECT_TIMEOUT_SEC,
    DEFAULT_COOLDOWN_SEC,
    DEFAULT_FALLBACK_MIRROR,
    DEFAULT_JITTER_SEC,
    DEFAULT_MAX_ANTIBOT_EVENTS,
    DEFAULT_MAX_HTML_BYTES,
    DEFAULT_MAX_RETRIES,
    DEFAULT_PRIMARY_MIRROR,
    DEFAULT_READ_TIMEOUT_SEC,
    DEFAULT_REQUEST_INTERVAL_SEC,
    DEFAULT_USER_AGENT,
    SAFE_ENDPOINTS,
)
from .crawl_engine import CrawlEngine
from .extractors import extract_page
from .settings import CrawlSettings, Mode
from .utils import ensure_absolute_base, to_absolute_url, unique_preserve_order, utc_now_iso

AUDIOBOOK_CATEGORY_ID = 33
DEFAULT_OUTPUT_DIR = "rutracker_parser/output"
TOKEN_SPLIT_RE = re.compile(r"[^\w]+", flags=re.UNICODE)
COMMON_RU_SUFFIXES = (
    "иями",
    "ями",
    "ами",
    "иями",
    "ого",
    "ему",
    "ому",
    "ыми",
    "ими",
    "ией",
    "ей",
    "ий",
    "ый",
    "ой",
    "ое",
    "ая",
    "яя",
    "ые",
    "ие",
    "ов",
    "ев",
    "ах",
    "ях",
    "ам",
    "ям",
    "ом",
    "ем",
    "у",
    "ю",
    "а",
    "я",
    "ы",
    "и",
    "е",
    "о",
)


def _resolve_mode(
    mode_requested: Mode,
    login: str,
    password: str,
    *,
    login_env: str,
    password_env: str,
) -> Literal["guest", "auth"]:
    if mode_requested == "guest":
        return "guest"
    if mode_requested == "auth":
        if not login or not password:
            raise ValueError(
                f"Mode 'auth' выбран, но env-переменные {login_env}/{password_env} не заданы"
            )
        return "auth"
    return "auth" if login and password else "guest"


def _coerce_int(value: Any) -> int | None:
    if isinstance(value, bool):
        return None
    if isinstance(value, int):
        return value
    if value is None:
        return None
    text = str(value).strip()
    if not text:
        return None
    digits = "".join(ch for ch in text if ch.isdigit())
    if not digits:
        return None
    try:
        return int(digits)
    except ValueError:
        return None


def _normalize_text(text: str) -> str:
    cleaned = (text or "").lower().replace("ё", "е")
    return " ".join(cleaned.split())


def _stem_ru_token(token: str) -> str:
    if len(token) <= 4:
        return token
    for suffix in COMMON_RU_SUFFIXES:
        if token.endswith(suffix) and len(token) - len(suffix) >= 3:
            return token[: -len(suffix)]
    return token


def _tokenize(text: str, *, stem: bool = False) -> list[str]:
    normalized = _normalize_text(text)
    tokens: list[str] = []
    for raw in TOKEN_SPLIT_RE.split(normalized):
        token = raw.strip("_")
        if len(token) < 2:
            continue
        if stem:
            token = _stem_ru_token(token)
        if token:
            tokens.append(token)
    return tokens


def _find_latest_index_file(output_dir: Path) -> Path | None:
    if not output_dir.exists():
        return None
    candidates = sorted(output_dir.glob("run_*/search/index_compact.json"))
    if not candidates:
        return None
    return candidates[-1]


def _build_mirrors(args: Any) -> list[str]:
    mirrors = [ensure_absolute_base(args.mirror)]
    if not args.no_fallback and args.fallback_mirror:
        mirrors.append(ensure_absolute_base(args.fallback_mirror))
    mirrors.extend(ensure_absolute_base(item) for item in args.extra_mirror)
    return unique_preserve_order(mirrors)


def _build_discovery_settings(
    args: Any, mirrors: list[str], login: str, password: str
) -> CrawlSettings:
    mode_requested: Mode = args.mode
    mode_effective = _resolve_mode(
        mode_requested,
        login,
        password,
        login_env=args.login_env,
        password_env=args.password_env,
    )
    return CrawlSettings(
        mirrors=mirrors,
        mode_requested=mode_requested,
        mode_effective=mode_effective,
        login=login,
        password=password,
        user_agent=args.user_agent,
        output_root=Path(args.output_dir),
        start_urls=[to_absolute_url(mirrors[0], f"/forum/index.php?c={AUDIOBOOK_CATEGORY_ID}")],
        request_interval_sec=max(args.request_interval, 0.2),
        jitter_sec=max(args.jitter, 0.0),
        connect_timeout_sec=max(args.connect_timeout, 1.0),
        read_timeout_sec=max(args.read_timeout, 1.0),
        max_retries=max(args.max_retries, 0),
        backoff_base_sec=max(args.backoff_base, 0.2),
        backoff_max_sec=max(args.backoff_max, 0.5),
        cooldown_sec=max(args.cooldown, 1.0),
        max_pages=1,
        max_depth=0,
        max_html_bytes=max(args.max_html_bytes, 64_000),
        max_antibot_events=max(args.max_antibot_events, 1),
        save_html=False,
        allow_path_prefixes=DEFAULT_ALLOWED_PATH_PREFIXES,
        allow_endpoints=SAFE_ENDPOINTS,
        scenario="search_discovery",
    )


def discover_audiobooks_forum_ids(args: Any) -> list[int]:
    mirrors = _build_mirrors(args)
    login = os.getenv(args.login_env, "").strip()
    password = os.getenv(args.password_env, "").strip()
    settings = _build_discovery_settings(args, mirrors, login, password)
    client = RutrackerHttpClient(settings)

    if settings.mode_effective == "auth":
        ok = client.login()
        if not ok and args.mode == "auth":
            raise RuntimeError("Не удалось авторизоваться для discovery форумов аудиокниг")

    fetched = client.request("GET", f"/forum/index.php?c={AUDIOBOOK_CATEGORY_ID}")
    extracted = extract_page(
        url=fetched.final_url,
        html=fetched.text,
        allowed_hosts={urlparse(m).netloc for m in mirrors},
        allow_path_prefixes=DEFAULT_ALLOWED_PATH_PREFIXES,
        allow_endpoints=SAFE_ENDPOINTS,
    )

    forum_ids: set[int] = set()
    for row in extracted.get("categories", []):
        if row.get("category_id") != AUDIOBOOK_CATEGORY_ID:
            continue
        forum_id = row.get("forum_id")
        if isinstance(forum_id, int):
            forum_ids.add(forum_id)
        for subforum in row.get("subforums", []):
            subforum_id = subforum.get("forum_id")
            if isinstance(subforum_id, int):
                forum_ids.add(subforum_id)

    return sorted(forum_ids)


def _build_start_urls(base_mirror: str, query: str, forum_ids: list[int]) -> list[str]:
    start_urls: list[str] = []
    for forum_id in forum_ids:
        q = urlencode({"f": forum_id, "nm": query})
        start_urls.append(to_absolute_url(base_mirror, f"/forum/tracker.php?{q}"))
    return start_urls


def _build_search_settings(args: Any, forum_ids: list[int]) -> CrawlSettings:
    mirrors = _build_mirrors(args)
    login = os.getenv(args.login_env, "").strip()
    password = os.getenv(args.password_env, "").strip()
    mode_requested: Mode = args.mode
    mode_effective = _resolve_mode(
        mode_requested,
        login,
        password,
        login_env=args.login_env,
        password_env=args.password_env,
    )

    start_urls = _build_start_urls(mirrors[0], args.query, forum_ids)
    total_pages_limit = max(1, len(start_urls) * max(args.max_pages_per_forum, 1))
    return CrawlSettings(
        mirrors=mirrors,
        mode_requested=mode_requested,
        mode_effective=mode_effective,
        login=login,
        password=password,
        user_agent=args.user_agent,
        output_root=Path(args.output_dir),
        start_urls=start_urls,
        request_interval_sec=max(args.request_interval, 0.2),
        jitter_sec=max(args.jitter, 0.0),
        connect_timeout_sec=max(args.connect_timeout, 1.0),
        read_timeout_sec=max(args.read_timeout, 1.0),
        max_retries=max(args.max_retries, 0),
        backoff_base_sec=max(args.backoff_base, 0.2),
        backoff_max_sec=max(args.backoff_max, 0.5),
        cooldown_sec=max(args.cooldown, 1.0),
        max_pages=total_pages_limit,
        max_depth=1,
        max_html_bytes=max(args.max_html_bytes, 64_000),
        max_antibot_events=max(args.max_antibot_events, 1),
        save_html=not args.skip_html,
        allow_path_prefixes=DEFAULT_ALLOWED_PATH_PREFIXES,
        allow_endpoints=frozenset({"tracker.php"}),
        scenario="search_index",
    )


def _read_jsonl(path: Path) -> list[dict]:
    if not path.exists():
        return []
    rows: list[dict] = []
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        rows.append(json.loads(line))
    return rows


def _topic_richness(row: dict) -> int:
    keys = (
        "title",
        "forum_title",
        "uploader",
        "size_text",
        "size_bytes",
        "seeders",
        "leechers",
        "downloads",
        "added_at",
        "torrent_url",
    )
    return sum(1 for key in keys if row.get(key) not in (None, "", [], {}))


def _record_rank(row: dict) -> float:
    seeders = _coerce_int(row.get("seeders")) or 0
    leechers = _coerce_int(row.get("leechers")) or 0
    downloads = _coerce_int(row.get("downloads")) or 0
    freshness = _coerce_int(row.get("added_ts")) or 0
    return (
        float(seeders) * 4.0
        + float(leechers) * 0.6
        + math.log1p(float(downloads)) * 2.0
        + (freshness / 100_000_000.0)
    )


def build_search_index(run_dir: Path, query: str) -> dict:
    topics_rows = _read_jsonl(run_dir / "entities" / "topics.jsonl")
    torrents_rows = _read_jsonl(run_dir / "entities" / "torrents.jsonl")
    forums_rows = _read_jsonl(run_dir / "entities" / "forums.jsonl")
    pages_rows = _read_jsonl(run_dir / "pages" / "pages.jsonl")

    forum_titles: dict[int, str] = {}
    for row in forums_rows:
        forum_id = _coerce_int(row.get("forum_id"))
        title = str(row.get("title") or "").strip()
        if forum_id is not None and title:
            forum_titles[forum_id] = title

    torrents_by_topic: dict[int, dict] = {}
    for row in torrents_rows:
        topic_id = _coerce_int(row.get("topic_id"))
        if topic_id is None:
            continue
        current = torrents_by_topic.get(topic_id)
        if current is None or _topic_richness(row) > _topic_richness(current):
            torrents_by_topic[topic_id] = row

    chosen: dict[int, dict] = {}
    for row in topics_rows:
        if row.get("kind"):
            continue
        source_url = str(row.get("source_url") or "")
        if "tracker.php" not in source_url:
            continue

        topic_id = _coerce_int(row.get("topic_id"))
        if topic_id is None:
            continue
        torrent = torrents_by_topic.get(topic_id, {})
        forum_id = _coerce_int(row.get("forum_id"))
        forum_title = str(row.get("forum_title") or "").strip() or forum_titles.get(
            forum_id or -1, ""
        )

        record = {
            "topic_id": topic_id,
            "title": str(row.get("title") or "").strip(),
            "topic_url": str(row.get("url") or "").strip(),
            "source_url": source_url,
            "forum_id": forum_id,
            "forum_title": forum_title,
            "forum_url": str(row.get("forum_url") or "").strip(),
            "uploader": str(row.get("uploader") or row.get("author") or "").strip(),
            "uploader_url": str(row.get("uploader_url") or row.get("author_url") or "").strip(),
            "uploader_pid": _coerce_int(row.get("uploader_pid")),
            "size_text": str(
                row.get("size_text") or torrent.get("size_text") or torrent.get("label") or ""
            ).strip(),
            "size_bytes": _coerce_int(row.get("size_bytes"))
            or _coerce_int(torrent.get("size_bytes")),
            "seeders": _coerce_int(row.get("seeders")) or _coerce_int(torrent.get("seeders")),
            "leechers": _coerce_int(row.get("leechers")) or _coerce_int(torrent.get("leechers")),
            "downloads": _coerce_int(row.get("downloads")) or _coerce_int(torrent.get("downloads")),
            "added_at": str(row.get("added_at") or "").strip(),
            "added_ts": _coerce_int(row.get("added_ts")),
            "status_text": str(row.get("status_text") or "").strip(),
            "status_title": str(row.get("status_title") or "").strip(),
            "torrent_url": str(row.get("torrent_url") or torrent.get("torrent_url") or "").strip(),
            "query": query,
            "ts": str(row.get("ts") or utc_now_iso()),
        }
        record["rank"] = round(_record_rank(record), 6)

        old = chosen.get(topic_id)
        if old is None:
            chosen[topic_id] = record
            continue
        if _topic_richness(record) > _topic_richness(old):
            chosen[topic_id] = record
            continue
        if (record.get("rank") or 0.0) > (old.get("rank") or 0.0):
            chosen[topic_id] = record

    records = sorted(
        chosen.values(),
        key=lambda item: (
            float(item.get("rank") or 0.0),
            _coerce_int(item.get("seeders")) or 0,
            _coerce_int(item.get("downloads")) or 0,
            _coerce_int(item.get("added_ts")) or 0,
        ),
        reverse=True,
    )

    compact_records = [
        {
            "topic_id": row["topic_id"],
            "title": row.get("title", ""),
            "forum_id": row.get("forum_id"),
            "forum_title": row.get("forum_title", ""),
            "uploader": row.get("uploader", ""),
            "size_text": row.get("size_text", ""),
            "size_bytes": row.get("size_bytes"),
            "seeders": row.get("seeders"),
            "leechers": row.get("leechers"),
            "downloads": row.get("downloads"),
            "added_at": row.get("added_at", ""),
            "added_ts": row.get("added_ts"),
            "status_text": row.get("status_text", ""),
            "topic_url": row.get("topic_url", ""),
            "torrent_url": row.get("torrent_url", ""),
            "rank": row.get("rank", 0.0),
            "search_text": _normalize_text(
                f"{row.get('title', '')} {row.get('forum_title', '')} {row.get('uploader', '')}"
            ),
        }
        for row in records
    ]

    search_dir = run_dir / "search"
    search_dir.mkdir(parents=True, exist_ok=True)

    full_payload = {
        "query": query,
        "generated_at": utc_now_iso(),
        "run_dir": str(run_dir),
        "pages_crawled": len(pages_rows),
        "records_total": len(records),
        "records": records,
    }
    compact_payload = {
        "query": query,
        "generated_at": utc_now_iso(),
        "records_total": len(compact_records),
        "records": compact_records,
    }
    top_payload = {
        "query": query,
        "generated_at": utc_now_iso(),
        "top": compact_records[:50],
    }

    full_path = search_dir / "index_full.json"
    compact_path = search_dir / "index_compact.json"
    top_path = search_dir / "index_top50.json"
    stats_path = search_dir / "index_stats.json"

    full_path.write_text(json.dumps(full_payload, ensure_ascii=False, indent=2), encoding="utf-8")
    compact_path.write_text(
        json.dumps(compact_payload, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    top_path.write_text(json.dumps(top_payload, ensure_ascii=False, indent=2), encoding="utf-8")
    stats_path.write_text(
        json.dumps(
            {
                "query": query,
                "generated_at": utc_now_iso(),
                "records_total": len(records),
                "forums_unique": len(
                    {item.get("forum_id") for item in records if item.get("forum_id") is not None}
                ),
                "with_torrent_url": sum(1 for item in records if item.get("torrent_url")),
            },
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )

    return {
        "run_dir": str(run_dir),
        "query": query,
        "records_total": len(records),
        "index_full": str(full_path),
        "index_compact": str(compact_path),
        "index_top50": str(top_path),
        "index_stats": str(stats_path),
    }


def run_search_index(args: Any) -> dict:
    forum_ids = [value for value in args.forum_id if value > 0]
    if not forum_ids:
        forum_ids = discover_audiobooks_forum_ids(args)

    limit_forums = max(args.limit_forums, 0)
    if limit_forums:
        forum_ids = forum_ids[:limit_forums]
    if not forum_ids:
        raise RuntimeError("Не удалось определить форумы аудиокниг для поиска")

    settings = _build_search_settings(args, forum_ids)
    engine = CrawlEngine(settings)
    crawl_summary = engine.run()

    run_dir = Path(crawl_summary["run_dir"])
    index_summary = build_search_index(run_dir, args.query)
    return {
        "query": args.query,
        "forum_ids": forum_ids,
        "crawl": crawl_summary,
        "index": index_summary,
    }


def _build_idf(records: list[dict]) -> dict[str, float]:
    doc_freq: dict[str, int] = {}
    total_docs = max(len(records), 1)
    for row in records:
        blob = str(
            row.get("search_text")
            or f"{row.get('title', '')} {row.get('forum_title', '')} {row.get('uploader', '')}"
        )
        token_set = set(_tokenize(blob, stem=True))
        for token in token_set:
            doc_freq[token] = doc_freq.get(token, 0) + 1

    return {
        token: math.log((1.0 + total_docs) / (1.0 + freq)) + 1.0 for token, freq in doc_freq.items()
    }


def _partial_hit(query_tokens: list[str], haystack: str) -> bool:
    return any(token in haystack for token in query_tokens if len(token) >= 3)


def search_compact_index(index_path: Path, query: str, limit: int) -> dict:
    payload = json.loads(index_path.read_text(encoding="utf-8"))
    if isinstance(payload, dict):
        records = payload.get("records", [])
    elif isinstance(payload, list):
        records = payload
    else:
        records = []

    query_normalized = _normalize_text(query)
    query_tokens = _tokenize(query_normalized, stem=False)
    query_stems = set(_tokenize(query_normalized, stem=True))
    idf_map = _build_idf(records)

    strict_ranked: list[tuple[float, dict]] = []
    fuzzy_ranked: list[tuple[float, dict]] = []

    for row in records:
        title = _normalize_text(str(row.get("title") or ""))
        uploader = _normalize_text(str(row.get("uploader") or ""))
        forum = _normalize_text(str(row.get("forum_title") or ""))
        blob = _normalize_text(
            str(
                row.get("search_text")
                or f"{row.get('title', '')} {row.get('forum_title', '')} {row.get('uploader', '')}"
            )
        )

        title_stems = set(_tokenize(title, stem=True))
        uploader_stems = set(_tokenize(uploader, stem=True))
        forum_stems = set(_tokenize(forum, stem=True))
        all_stems = title_stems | uploader_stems | forum_stems

        lexical = 0.0
        for token in query_stems:
            token_idf = idf_map.get(token, 1.0)
            if token in title_stems:
                lexical += token_idf * 8.0
            if token in uploader_stems:
                lexical += token_idf * 3.0
            if token in forum_stems:
                lexical += token_idf * 2.0

        phrase_bonus = 0.0
        if query_normalized and query_normalized in title:
            phrase_bonus += 32.0
        elif query_normalized and query_normalized in blob:
            phrase_bonus += 16.0

        fuzzy_title = (
            SequenceMatcher(None, query_normalized, title).ratio()
            if query_normalized and title
            else 0.0
        )
        fuzzy_blob = (
            SequenceMatcher(None, query_normalized, blob).ratio()
            if query_normalized and blob
            else 0.0
        )
        fuzzy_score = max(fuzzy_title, fuzzy_blob)
        fuzzy_bonus = max(0.0, fuzzy_score - 0.46) * 42.0

        seeders = _coerce_int(row.get("seeders")) or 0
        downloads = _coerce_int(row.get("downloads")) or 0
        base_rank = float(row.get("rank") or 0.0)
        freshness = (_coerce_int(row.get("added_ts")) or 0) / 100_000_000.0
        score = (
            lexical * 120.0
            + phrase_bonus
            + fuzzy_bonus
            + base_rank * 2.0
            + seeders * 1.1
            + math.log1p(downloads) * 2.2
            + freshness
        )

        strict_hit = bool(query_stems.intersection(all_stems)) or bool(
            query_normalized and query_normalized in blob
        )
        relaxed_hit = strict_hit or _partial_hit(query_tokens, blob) or fuzzy_score >= 0.58

        if strict_hit:
            strict_ranked.append((score, row))
        elif relaxed_hit:
            fuzzy_ranked.append((score, row))

    strict_ranked.sort(key=lambda item: item[0], reverse=True)
    fuzzy_ranked.sort(key=lambda item: item[0], reverse=True)

    top_ranked = sorted(
        records,
        key=lambda row: (
            float(row.get("rank") or 0.0),
            _coerce_int(row.get("seeders")) or 0,
            _coerce_int(row.get("downloads")) or 0,
            _coerce_int(row.get("added_ts")) or 0,
        ),
        reverse=True,
    )

    strategy = "strict"
    matched = len(strict_ranked)
    best: list[dict]
    if strict_ranked:
        best = [row for _, row in strict_ranked[: max(limit, 1)]]
    elif fuzzy_ranked:
        strategy = "fuzzy"
        matched = len(fuzzy_ranked)
        best = [row for _, row in fuzzy_ranked[: max(limit, 1)]]
    else:
        strategy = "top_rank_fallback"
        matched = 0
        best = top_ranked[: max(limit, 1)]

    return {
        "query": query,
        "limit": max(limit, 1),
        "matched": matched,
        "strategy": strategy,
        "index_records": len(records),
        "results": best,
    }


def _build_search_index_namespace_from_find(args: Any, query: str) -> Any:
    return SimpleNamespace(
        query=query,
        mode=args.mode,
        mirror=args.mirror,
        fallback_mirror=args.fallback_mirror,
        extra_mirror=list(args.extra_mirror),
        no_fallback=args.no_fallback,
        forum_id=list(args.forum_id),
        limit_forums=args.limit_forums,
        max_pages_per_forum=args.max_pages_per_forum,
        request_interval=args.request_interval,
        jitter=args.jitter,
        connect_timeout=args.connect_timeout,
        read_timeout=args.read_timeout,
        max_retries=args.max_retries,
        backoff_base=args.backoff_base,
        backoff_max=args.backoff_max,
        cooldown=args.cooldown,
        max_html_bytes=args.max_html_bytes,
        max_antibot_events=args.max_antibot_events,
        output_dir=args.output_dir,
        login_env=args.login_env,
        password_env=args.password_env,
        user_agent=args.user_agent,
        skip_html=args.skip_html,
    )


def _build_relaxed_refresh_args(args: Any) -> Any:
    refreshed = _build_search_index_namespace_from_find(args, query=args.query)
    if not refreshed.forum_id and refreshed.limit_forums and refreshed.limit_forums <= 2:
        refreshed.limit_forums = 8
    refreshed.max_pages_per_forum = max(2, int(refreshed.max_pages_per_forum))
    refreshed.max_retries = max(2, int(refreshed.max_retries))
    return refreshed


def _resolve_index_for_find(args: Any) -> tuple[Path, dict | None]:
    index_summary: dict | None = None
    explicit_index = bool(args.index)
    if explicit_index:
        index_path = Path(args.index).expanduser().resolve()
    else:
        latest = _find_latest_index_file(Path(args.output_dir))
        index_path = latest.resolve() if latest else Path("")

    if index_path and index_path.is_file():
        return index_path, index_summary
    if explicit_index and index_path and index_path.exists() and not index_path.is_file():
        raise FileNotFoundError(f"Index path is not a file: {index_path}")

    if args.auto_index_if_missing:
        build_args = _build_search_index_namespace_from_find(args, query=args.query)
        index_summary = run_search_index(build_args)
        index_path = Path(index_summary["index"]["index_compact"]).expanduser().resolve()
        return index_path, index_summary

    if args.index:
        raise FileNotFoundError(
            f"Index not found: {index_path}. Use --auto-index-if-missing to build it automatically."
        )
    raise FileNotFoundError(
        f"Index not found in {Path(args.output_dir).resolve()}. "
        "Provide --index or use --auto-index-if-missing."
    )


def run_search_find(args: Any) -> dict:
    index_path, build_summary = _resolve_index_for_find(args)
    result = search_compact_index(index_path=index_path, query=args.query, limit=args.limit)
    result["index_path"] = str(index_path)
    if build_summary:
        result["index_built"] = build_summary.get("index", {})

    needs_refresh = args.auto_refresh_on_empty and (
        result.get("matched", 0) == 0 or result.get("index_records", 0) == 0
    )
    if needs_refresh:
        refresh_args = _build_relaxed_refresh_args(args)
        refresh_summary = run_search_index(refresh_args)
        refreshed_path = Path(refresh_summary["index"]["index_compact"]).expanduser().resolve()
        refreshed = search_compact_index(
            index_path=refreshed_path, query=args.query, limit=args.limit
        )
        refreshed["index_path"] = str(refreshed_path)
        refreshed["index_refreshed"] = refresh_summary.get("index", {})
        result = refreshed

    if result.get("index_records", 0) == 0:
        result["hint"] = (
            "Индекс пуст: расширьте покрытие (больше форумов/страниц) или включите "
            "--auto-refresh-on-empty для автопересборки."
        )
    elif result.get("matched", 0) == 0:
        result["hint"] = (
            "Точных совпадений нет в текущем индексе. Можно перезапросить с более широким "
            "query или обновить индекс (--auto-refresh-on-empty)."
        )

    if args.write:
        target = Path(args.write)
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    return result


def build_search_index_parser() -> Any:
    import argparse

    parser = argparse.ArgumentParser(prog="python -m rutracker_parser search-index")
    parser.add_argument("--query", required=True)
    parser.add_argument("--mode", choices=["auto", "guest", "auth"], default="auto")
    parser.add_argument("--mirror", default=DEFAULT_PRIMARY_MIRROR)
    parser.add_argument("--fallback-mirror", default=DEFAULT_FALLBACK_MIRROR)
    parser.add_argument("--extra-mirror", action="append", default=[])
    parser.add_argument("--no-fallback", action="store_true")
    parser.add_argument("--forum-id", action="append", type=int, default=[])
    parser.add_argument("--limit-forums", type=int, default=0)
    parser.add_argument("--max-pages-per-forum", type=int, default=2)
    parser.add_argument("--request-interval", type=float, default=DEFAULT_REQUEST_INTERVAL_SEC)
    parser.add_argument("--jitter", type=float, default=DEFAULT_JITTER_SEC)
    parser.add_argument("--connect-timeout", type=float, default=DEFAULT_CONNECT_TIMEOUT_SEC)
    parser.add_argument("--read-timeout", type=float, default=DEFAULT_READ_TIMEOUT_SEC)
    parser.add_argument("--max-retries", type=int, default=DEFAULT_MAX_RETRIES)
    parser.add_argument("--backoff-base", type=float, default=DEFAULT_BACKOFF_BASE_SEC)
    parser.add_argument("--backoff-max", type=float, default=DEFAULT_BACKOFF_MAX_SEC)
    parser.add_argument("--cooldown", type=float, default=DEFAULT_COOLDOWN_SEC)
    parser.add_argument("--max-html-bytes", type=int, default=DEFAULT_MAX_HTML_BYTES)
    parser.add_argument("--max-antibot-events", type=int, default=DEFAULT_MAX_ANTIBOT_EVENTS)
    parser.add_argument("--output-dir", default="rutracker_parser/output")
    parser.add_argument("--login-env", default="RUTRACKER_LOGIN")
    parser.add_argument("--password-env", default="RUTRACKER_PASSWORD")
    parser.add_argument("--user-agent", default=DEFAULT_USER_AGENT)
    parser.add_argument("--skip-html", action="store_true")
    return parser


def build_search_find_parser() -> Any:
    import argparse

    parser = argparse.ArgumentParser(prog="python -m rutracker_parser search-find")
    parser.add_argument("--index", default="")
    parser.add_argument("--query", required=True)
    parser.add_argument("--limit", type=int, default=20)
    parser.add_argument("--write", default="")
    parser.add_argument("--output-dir", default=DEFAULT_OUTPUT_DIR)
    parser.add_argument("--auto-index-if-missing", action="store_true")
    parser.add_argument("--auto-refresh-on-empty", action="store_true")

    parser.add_argument("--mode", choices=["auto", "guest", "auth"], default="auto")
    parser.add_argument("--mirror", default=DEFAULT_PRIMARY_MIRROR)
    parser.add_argument("--fallback-mirror", default=DEFAULT_FALLBACK_MIRROR)
    parser.add_argument("--extra-mirror", action="append", default=[])
    parser.add_argument("--no-fallback", action="store_true")
    parser.add_argument("--forum-id", action="append", type=int, default=[])
    parser.add_argument("--limit-forums", type=int, default=0)
    parser.add_argument("--max-pages-per-forum", type=int, default=1)
    parser.add_argument("--request-interval", type=float, default=DEFAULT_REQUEST_INTERVAL_SEC)
    parser.add_argument("--jitter", type=float, default=DEFAULT_JITTER_SEC)
    parser.add_argument("--connect-timeout", type=float, default=DEFAULT_CONNECT_TIMEOUT_SEC)
    parser.add_argument("--read-timeout", type=float, default=DEFAULT_READ_TIMEOUT_SEC)
    parser.add_argument("--max-retries", type=int, default=DEFAULT_MAX_RETRIES)
    parser.add_argument("--backoff-base", type=float, default=DEFAULT_BACKOFF_BASE_SEC)
    parser.add_argument("--backoff-max", type=float, default=DEFAULT_BACKOFF_MAX_SEC)
    parser.add_argument("--cooldown", type=float, default=DEFAULT_COOLDOWN_SEC)
    parser.add_argument("--max-html-bytes", type=int, default=DEFAULT_MAX_HTML_BYTES)
    parser.add_argument("--max-antibot-events", type=int, default=DEFAULT_MAX_ANTIBOT_EVENTS)
    parser.add_argument("--login-env", default="RUTRACKER_LOGIN")
    parser.add_argument("--password-env", default="RUTRACKER_PASSWORD")
    parser.add_argument("--user-agent", default=DEFAULT_USER_AGENT)
    parser.add_argument("--skip-html", action="store_true")
    return parser
