from __future__ import annotations

import json
import math
import os
import re
import threading
from collections import Counter
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import replace
from difflib import SequenceMatcher
from pathlib import Path
from types import SimpleNamespace
from typing import Any, Literal
from urllib.parse import parse_qsl, urlencode, urlparse, urlunparse

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
from .mode_matrix import feature_matrix, mode_summary
from .settings import CrawlSettings, Mode
from .storage import CrawlStorage
from .utils import (
    ensure_absolute_base,
    normalize_url,
    query_value_as_int,
    to_absolute_url,
    unique_preserve_order,
    utc_now_iso,
)

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
COMMENT_LOW_SIGNAL_TOKENS = {
    "спасибо",
    "благодарю",
    "благодарность",
    "благодарочка",
    "ап",
    "up",
    "+",
    "ок",
    "класс",
    "супер",
    "норм",
    "огонь",
    "жду",
}
DEFAULT_PARALLEL_INDEX_WORKERS = 2
MAX_PARALLEL_INDEX_WORKERS = 6
DEFAULT_INCREMENTAL_PAGES_WINDOW = 8
DEFAULT_HARD_MAX_PAGES_PER_FORUM = 450
DEFAULT_TRACKER_PAGE_SIZE = 50


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


def _find_latest_index_full_file(output_dir: Path) -> Path | None:
    if not output_dir.exists():
        return None
    candidates = sorted(output_dir.glob("run_*/search/index_full.json"))
    if not candidates:
        return None
    return candidates[-1]


def _find_latest_comments_index_file(output_dir: Path) -> Path | None:
    if not output_dir.exists():
        return None
    candidates = sorted(output_dir.glob("run_*/search/comments_index.jsonl"))
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


def _replace_url_query(url: str, updates: dict[str, str | int | None]) -> str:
    parsed = urlparse(url)
    query_pairs = parse_qsl(parsed.query, keep_blank_values=True)
    query_map: dict[str, str] = {}
    for key, value in query_pairs:
        query_map[key] = value
    for key, value in updates.items():
        if value is None:
            query_map.pop(key, None)
            continue
        query_map[key] = str(value)
    encoded = urlencode(sorted(query_map.items()), doseq=True)
    normalized = parsed._replace(query=encoded)
    return normalize_url(urlunparse(normalized))


def _infer_tracker_page_size(url: str, extracted: dict) -> int:
    pagination = extracted.get("pagination") or {}
    starts: list[int] = []
    for item in pagination.get("links", []):
        start_value = _coerce_int(item.get("start"))
        if start_value is None:
            start_value = query_value_as_int(str(item.get("url") or ""), "start")
        if isinstance(start_value, int) and start_value >= 0:
            starts.append(start_value)
    starts = sorted(set(starts))
    if len(starts) >= 2:
        diffs = [b - a for a, b in zip(starts, starts[1:], strict=False) if b - a > 0]
        if diffs:
            return max(1, min(diffs))

    from_url = query_value_as_int(url, "per_page")
    if isinstance(from_url, int) and from_url > 0:
        return from_url

    topics = [row for row in extracted.get("topics", []) if not row.get("kind")]
    topic_count = len(topics)
    if 5 <= topic_count <= 200:
        return topic_count
    return DEFAULT_TRACKER_PAGE_SIZE


def _clamp_parallel_workers(raw_value: Any) -> int:
    try:
        workers = int(raw_value)
    except (TypeError, ValueError):
        workers = DEFAULT_PARALLEL_INDEX_WORKERS
    return min(max(workers, 1), MAX_PARALLEL_INDEX_WORKERS)


def _resolve_tracker_total_pages(extracted: dict) -> int:
    tracker_details = extracted.get("tracker_details") or {}
    pagination = extracted.get("pagination") or {}
    total_pages = _coerce_int(tracker_details.get("total_pages"))
    if total_pages is None:
        total_pages = _coerce_int(pagination.get("max_page_number"))
    if total_pages is None or total_pages < 1:
        return 1
    return total_pages


def _plan_pages_for_forum(
    *,
    total_pages: int,
    max_pages_per_forum: int,
    incremental_mode: bool,
    incremental_pages_window: int,
    hard_cap: int,
) -> int:
    planned = max(total_pages, 1)
    if max_pages_per_forum > 0:
        planned = min(planned, max_pages_per_forum)
    elif incremental_mode:
        planned = min(planned, max(incremental_pages_window, 1))
    planned = min(planned, max(hard_cap, 1))
    return max(planned, 1)


def _run_parallel_tracker_harvest(
    args: Any,
    settings: CrawlSettings,
    forum_ids: list[int],
) -> dict:
    mirrors = settings.mirrors
    allowed_hosts = {urlparse(mirror).netloc for mirror in mirrors}
    start_urls = _build_start_urls(mirrors[0], args.query, forum_ids)

    workers = _clamp_parallel_workers(getattr(args, "workers", DEFAULT_PARALLEL_INDEX_WORKERS))
    max_pages_per_forum = max(int(getattr(args, "max_pages_per_forum", 0) or 0), 0)
    hard_cap = max(
        int(getattr(args, "hard_max_pages_per_forum", DEFAULT_HARD_MAX_PAGES_PER_FORUM) or 1), 1
    )
    incremental_pages_window = max(
        int(getattr(args, "incremental_pages_window", DEFAULT_INCREMENTAL_PAGES_WINDOW) or 1), 1
    )
    incremental_mode = bool(getattr(args, "incremental", False)) and not bool(
        getattr(args, "force", False)
    )

    bootstrap_client = RutrackerHttpClient(settings)
    logged_in = False
    if settings.mode_effective == "auth":
        logged_in = bootstrap_client.login()
        if not logged_in and args.mode == "auth":
            raise RuntimeError("Не удалось авторизоваться для parallel search-index")
    auth_cookies = bootstrap_client.session.cookies.get_dict()

    status_counts: Counter[int] = Counter()
    entity_counts: Counter[str] = Counter()
    planned_pages_total = 0
    planned_extra_pages = 0
    errors_count = 0
    interrupted = False
    planner_rows: list[dict] = []

    storage = CrawlStorage(Path(args.output_dir))
    visited_pages = 0
    try:

        def persist_page_result(
            *,
            parent_url: str | None,
            depth: int,
            fetched: Any,
            extracted: dict,
        ) -> None:
            nonlocal visited_pages

            html_ref = (
                storage.save_html(fetched.final_url, fetched.text) if settings.save_html else None
            )
            page_record = {
                "url": fetched.final_url,
                "requested_url": fetched.requested_url,
                "depth": depth,
                "parent_url": parent_url,
                "status": fetched.status_code,
                "mirror": fetched.mirror,
                "elapsed_sec": round(fetched.elapsed_sec, 3),
                "truncated": fetched.truncated,
                "html_ref": html_ref,
                "title": extracted.get("title", ""),
                "page_type": extracted.get("page_type", "generic"),
                "breadcrumbs": extracted.get("breadcrumbs", []),
                "breadcrumb_links": extracted.get("breadcrumb_links", []),
                "pagination": extracted.get("pagination", {}),
                "signals": extracted.get("page_signals", {}),
                "forms": extracted.get("forms", []),
                "ts": utc_now_iso(),
            }
            storage.write_page(page_record)
            visited_pages += 1

            links = extracted.get("links", [])
            storage.write_edges(links)
            entity_counts["edges"] += len(links)

            forums = list(extracted.get("forums", []))
            topics = list(extracted.get("topics", []))
            users = list(extracted.get("users", []))
            torrents = list(extracted.get("torrents", []))
            categories = list(extracted.get("categories", []))
            posts = list(extracted.get("posts", []))

            tracker_details = extracted.get("tracker_details")
            if tracker_details:
                tracker_row = dict(tracker_details)
                tracker_row["kind"] = "tracker_details"
                topics.append(tracker_row)

            forum_details = extracted.get("forum_details")
            if forum_details:
                forum_row = dict(forum_details)
                forum_row["kind"] = "forum_details"
                forums.append(forum_row)

            storage.write_forums(forums)
            storage.write_topics(topics)
            storage.write_users(users)
            storage.write_torrents(torrents)
            storage.write_categories(categories)
            storage.write_posts(posts)
            entity_counts["forums"] += len(forums)
            entity_counts["topics"] += len(topics)
            entity_counts["users"] += len(users)
            entity_counts["torrents"] += len(torrents)
            entity_counts["categories"] += len(categories)
            entity_counts["posts"] += len(posts)

            topic_meta = extracted.get("topic_meta")
            if topic_meta:
                storage.write_topic_meta(topic_meta)
                entity_counts["topic_meta"] += 1

            profile_details = extracted.get("profile_details")
            if profile_details:
                storage.write_profile(profile_details)
                entity_counts["profiles"] += 1

        tasks: list[dict] = []
        task_urls_seen: set[str] = set()
        for forum_id, start_url in zip(forum_ids, start_urls, strict=False):
            try:
                fetched = bootstrap_client.request("GET", start_url)
                status_counts[fetched.status_code] += 1
                extracted = extract_page(
                    url=fetched.final_url,
                    html=fetched.text,
                    allowed_hosts=allowed_hosts,
                    allow_path_prefixes=DEFAULT_ALLOWED_PATH_PREFIXES,
                    allow_endpoints=frozenset({"tracker.php"}),
                )
                persist_page_result(
                    parent_url=None,
                    depth=0,
                    fetched=fetched,
                    extracted=extracted,
                )
            except KeyboardInterrupt:
                interrupted = True
                break
            except Exception as error:
                errors_count += 1
                storage.write_error(
                    {
                        "url": start_url,
                        "error": str(error),
                        "kind": "seed_request",
                        "depth": 0,
                        "ts": utc_now_iso(),
                    }
                )
                continue

            total_pages = _resolve_tracker_total_pages(extracted)
            page_size = _infer_tracker_page_size(fetched.final_url, extracted)
            planned_pages = _plan_pages_for_forum(
                total_pages=total_pages,
                max_pages_per_forum=max_pages_per_forum,
                incremental_mode=incremental_mode,
                incremental_pages_window=incremental_pages_window,
                hard_cap=hard_cap,
            )
            planned_pages_total += planned_pages

            tracker_details = extracted.get("tracker_details") or {}
            search_id = str(tracker_details.get("search_id") or "").strip()
            sort_by = str(tracker_details.get("sort_by") or "").strip()
            sort_dir = str(tracker_details.get("sort_dir") or "").strip()
            planner_rows.append(
                {
                    "forum_id": forum_id,
                    "seed_url": fetched.final_url,
                    "total_pages_detected": total_pages,
                    "planned_pages": planned_pages,
                    "page_size": page_size,
                    "search_id": search_id,
                    "sort_by": sort_by,
                    "sort_dir": sort_dir,
                }
            )

            for page_no in range(2, planned_pages + 1):
                start_offset = (page_no - 1) * page_size
                page_url = _replace_url_query(
                    fetched.final_url,
                    {
                        "start": start_offset,
                        "search_id": search_id or None,
                        "o": sort_by or None,
                        "s": sort_dir or None,
                    },
                )
                if page_url in task_urls_seen:
                    continue
                task_urls_seen.add(page_url)
                planned_extra_pages += 1
                tasks.append(
                    {
                        "forum_id": forum_id,
                        "page_no": page_no,
                        "url": page_url,
                        "referer": fetched.final_url,
                    }
                )

        if not interrupted and tasks:
            worker_settings = replace(
                settings,
                request_interval_sec=max(settings.request_interval_sec * float(workers), 0.2),
                jitter_sec=max(settings.jitter_sec * float(workers), 0.0),
            )
            local_state = threading.local()

            def get_worker_client() -> RutrackerHttpClient:
                client = getattr(local_state, "client", None)
                if client is None:
                    client = RutrackerHttpClient(worker_settings)
                    if auth_cookies:
                        client.session.cookies.update(auth_cookies)
                    elif worker_settings.mode_effective == "auth":
                        ok = client.login()
                        if not ok and args.mode == "auth":
                            raise RuntimeError("Worker login failed in parallel search-index")
                    local_state.client = client
                return client

            def run_task(task: dict) -> dict:
                try:
                    client = get_worker_client()
                    fetched = client.request("GET", task["url"], referer=task.get("referer"))
                    extracted = extract_page(
                        url=fetched.final_url,
                        html=fetched.text,
                        allowed_hosts=allowed_hosts,
                        allow_path_prefixes=DEFAULT_ALLOWED_PATH_PREFIXES,
                        allow_endpoints=frozenset({"tracker.php"}),
                    )
                    return {"ok": True, "task": task, "fetched": fetched, "extracted": extracted}
                except Exception as error:
                    return {"ok": False, "task": task, "error": str(error)}

            with ThreadPoolExecutor(max_workers=workers) as executor:
                futures = [executor.submit(run_task, task) for task in tasks]
                for future in as_completed(futures):
                    result = future.result()
                    task = result.get("task", {})
                    if not result.get("ok"):
                        errors_count += 1
                        storage.write_error(
                            {
                                "url": task.get("url", ""),
                                "error": result.get("error", "parallel request failed"),
                                "kind": "parallel_request",
                                "depth": 0,
                                "ts": utc_now_iso(),
                            }
                        )
                        continue
                    fetched = result["fetched"]
                    extracted = result["extracted"]
                    status_counts[fetched.status_code] += 1
                    persist_page_result(
                        parent_url=str(task.get("referer") or ""),
                        depth=0,
                        fetched=fetched,
                        extracted=extracted,
                    )

        summary = {
            "requested_mode": settings.mode_requested,
            "effective_mode": settings.mode_effective,
            "logged_in": logged_in,
            "mirrors": settings.mirrors,
            "visited_pages": visited_pages,
            "pending_pages": 0,
            "status_counts": dict(sorted(status_counts.items())),
            "entity_counts": dict(entity_counts),
            "errors": errors_count,
            "interrupted": interrupted,
            "harvest_mode": "parallel_tracker",
            "workers": workers,
            "planner": {
                "incremental_mode": incremental_mode,
                "max_pages_per_forum": max_pages_per_forum,
                "incremental_pages_window": incremental_pages_window,
                "hard_max_pages_per_forum": hard_cap,
                "planned_pages_total": planned_pages_total,
                "planned_extra_pages": planned_extra_pages,
                "forums": planner_rows,
            },
            "run_dir": str(storage.run_dir),
            "ts": utc_now_iso(),
        }
        storage.write_meta("summary.json", summary)
        storage.write_meta("search_plan.json", summary.get("planner", {}))
        storage.write_meta(
            "mode_capabilities.json",
            {
                "matrix": feature_matrix(),
                "guest": mode_summary("guest"),
                "auth": mode_summary("auth"),
                "effective": mode_summary(settings.mode_effective),
            },
        )
        return summary
    finally:
        storage.close()


def _read_jsonl(path: Path) -> list[dict]:
    if not path.exists():
        return []
    rows: list[dict] = []
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        rows.append(json.loads(line))
    return rows


def _jsonl_write(path: Path, rows: list[dict]) -> None:
    payload = "\n".join(json.dumps(row, ensure_ascii=False) for row in rows)
    if payload:
        payload += "\n"
    path.write_text(payload, encoding="utf-8")


def _as_dict_list(value: Any) -> list[dict]:
    if not isinstance(value, list):
        return []
    result: list[dict] = []
    for item in value:
        if isinstance(item, dict):
            result.append(item)
    return result


def _as_int_list(value: Any) -> list[int]:
    if not isinstance(value, list):
        return []
    result: list[int] = []
    seen: set[int] = set()
    for item in value:
        casted = _coerce_int(item)
        if casted is None or casted in seen:
            continue
        seen.add(casted)
        result.append(casted)
    return result


def _as_text_list(value: Any) -> list[str]:
    if not isinstance(value, list):
        return []
    result: list[str] = []
    seen: set[str] = set()
    for item in value:
        text = str(item or "").strip()
        if not text or text in seen:
            continue
        seen.add(text)
        result.append(text)
    return result


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


def _is_minimal_index_record(record: dict) -> bool:
    topic_id = _coerce_int(record.get("topic_id"))
    title = str(record.get("title") or "").strip()
    topic_url = str(record.get("topic_url") or "").strip()
    if topic_id is None:
        return False
    if len(title) < 3:
        return False
    if "viewtopic.php" not in topic_url or "t=" not in topic_url:
        return False

    has_payload = any(
        record.get(field) not in (None, "", [], {})
        for field in ("torrent_url", "size_bytes", "size_text", "seeders", "leechers", "downloads")
    )
    return has_payload


def _clamp_quality_threshold(raw_value: Any, default: float = 0.0) -> float:
    try:
        value = float(raw_value)
    except (TypeError, ValueError):
        return default
    if math.isnan(value) or math.isinf(value):
        return default
    return min(max(value, 0.0), 1.0)


def _record_quality_score(row: dict) -> float:
    score = 0.0
    total = 0.0

    title = str(row.get("title") or "").strip()
    topic_url = str(row.get("topic_url") or "").strip()
    forum_title = str(row.get("forum_title") or "").strip()
    uploader = str(row.get("uploader") or "").strip()
    torrent_url = str(row.get("torrent_url") or "").strip()
    status_text = str(row.get("status_text") or "").strip()
    size_bytes = _coerce_int(row.get("size_bytes"))
    seeders = _coerce_int(row.get("seeders"))
    leechers = _coerce_int(row.get("leechers"))
    downloads = _coerce_int(row.get("downloads"))
    added_ts = _coerce_int(row.get("added_ts"))
    added_at = str(row.get("added_at") or "").strip()

    total += 2.0
    if len(title) >= 8:
        score += 2.0
    elif title:
        score += 1.0

    total += 1.7
    if "viewtopic.php" in topic_url and "t=" in topic_url:
        score += 1.7

    total += 1.0
    if forum_title:
        score += 1.0

    total += 1.0
    if uploader:
        score += 1.0

    total += 1.6
    if torrent_url:
        score += 1.6

    total += 1.2
    if isinstance(size_bytes, int) and size_bytes > 0:
        score += 1.2
    elif str(row.get("size_text") or "").strip():
        score += 0.6

    total += 2.0
    if any(isinstance(value, int) and value >= 0 for value in (seeders, leechers, downloads)):
        score += 2.0

    total += 1.0
    if isinstance(added_ts, int) and added_ts > 0:
        score += 1.0
    elif added_at:
        score += 0.5

    total += 0.5
    if status_text:
        score += 0.5

    if total <= 0.0:
        return 0.0
    return max(0.0, min(1.0, score / total))


def _record_quality_penalty(row: dict) -> float:
    quality = _record_quality_score(row)
    return (1.0 - quality) * 28.0


def _normalize_existing_index_record(
    row: dict, query: str, *, min_quality_score: float = 0.0
) -> dict | None:
    topic_id = _coerce_int(row.get("topic_id"))
    if topic_id is None:
        return None
    normalized = dict(row)
    normalized["topic_id"] = topic_id
    normalized["query"] = str(normalized.get("query") or query)

    rank = normalized.get("rank")
    if not isinstance(rank, (int, float)):
        rank = _record_rank(normalized)
    normalized["rank"] = round(float(rank), 6)

    quality_score = normalized.get("quality_score")
    if not isinstance(quality_score, (int, float)):
        quality_score = _record_quality_score(normalized)
    normalized["quality_score"] = round(float(quality_score), 4)

    quality_penalty = normalized.get("quality_penalty")
    if not isinstance(quality_penalty, (int, float)):
        quality_penalty = _record_quality_penalty(normalized)
    normalized["quality_penalty"] = round(float(quality_penalty), 6)

    normalized["rank_base"] = round(_record_rank(normalized), 6)
    normalized["rank"] = round(
        max(float(normalized.get("rank_base") or 0.0) - float(normalized["quality_penalty"]), 0.0),
        6,
    )

    if float(normalized.get("quality_score") or 0.0) < min_quality_score:
        return None
    if not _is_minimal_index_record(normalized):
        return None
    return normalized


def _merge_incremental_records(
    *,
    current_records: list[dict],
    base_records: list[dict],
    query: str,
    min_quality_score: float = 0.0,
) -> tuple[list[dict], dict]:
    base_map: dict[int, dict] = {}
    base_filtered_out = 0
    for row in base_records:
        normalized = _normalize_existing_index_record(
            row, query, min_quality_score=min_quality_score
        )
        if not normalized:
            base_filtered_out += 1
            continue
        base_map[normalized["topic_id"]] = normalized

    base_topic_ids = set(base_map.keys())
    new_count = 0
    updated_count = 0
    new_topic_ids_sample: list[int] = []
    updated_topic_ids_sample: list[int] = []

    for row in current_records:
        topic_id = _coerce_int(row.get("topic_id"))
        if topic_id is None:
            continue
        if topic_id in base_topic_ids:
            updated_count += 1
            if len(updated_topic_ids_sample) < 50:
                updated_topic_ids_sample.append(topic_id)
        else:
            new_count += 1
            if len(new_topic_ids_sample) < 50:
                new_topic_ids_sample.append(topic_id)
        base_map[topic_id] = row

    carried_over = max(len(base_topic_ids) - updated_count, 0)
    merged = sorted(
        base_map.values(),
        key=lambda item: (
            float(item.get("rank") or 0.0),
            _coerce_int(item.get("seeders")) or 0,
            _coerce_int(item.get("downloads")) or 0,
            _coerce_int(item.get("added_ts")) or 0,
            float(item.get("quality_score") or 0.0),
        ),
        reverse=True,
    )
    return merged, {
        "base_records_total": len(base_topic_ids),
        "base_records_filtered_out": base_filtered_out,
        "new_records": new_count,
        "updated_records": updated_count,
        "carried_over_records": carried_over,
        "merged_records_total": len(merged),
        "new_topic_ids_sample": new_topic_ids_sample,
        "updated_topic_ids_sample": updated_topic_ids_sample,
    }


def _load_index_records(index_path: Path) -> tuple[str, list[dict]]:
    payload = json.loads(index_path.read_text(encoding="utf-8"))
    if isinstance(payload, dict):
        query = str(payload.get("query") or "").strip()
        records = payload.get("records", [])
        if isinstance(records, list):
            return query, [row for row in records if isinstance(row, dict)]
    elif isinstance(payload, list):
        return "", [row for row in payload if isinstance(row, dict)]
    return "", []


def _compact_record_payload(row: dict) -> dict:
    required = {
        "topic_id": row.get("topic_id"),
        "title": row.get("title", ""),
        "topic_url": row.get("topic_url", ""),
        "rank": row.get("rank", 0.0),
        "quality_score": row.get("quality_score", 0.0),
        "search_text": row.get("search_text", ""),
    }
    optional = {
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
        "torrent_url": row.get("torrent_url", ""),
        "quality_penalty": row.get("quality_penalty", 0.0),
        "rank_base": row.get("rank_base", row.get("rank", 0.0)),
    }
    payload = dict(required)
    for key, value in optional.items():
        if value in (None, "", [], {}):
            continue
        payload[key] = value
    return payload


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


def _comment_payload_richness(row: dict) -> int:
    formatting = row.get("formatting_stats")
    if not isinstance(formatting, dict):
        formatting = {}
    return sum(
        1
        for item in (
            row.get("text"),
            row.get("topic_title"),
            row.get("poster"),
            row.get("post_url"),
            row.get("links_detailed"),
            row.get("images_detailed"),
            row.get("quotes"),
            row.get("spoilers"),
            row.get("reply_to_post_ids"),
            row.get("reply_to_users"),
            row.get("signature_text"),
            formatting,
        )
        if item not in (None, "", [], {})
    )


def _is_minimal_comment_record(record: dict) -> bool:
    topic_id = _coerce_int(record.get("topic_id"))
    post_id = _coerce_int(record.get("post_id"))
    post_url = str(record.get("post_url") or "").strip()
    text = str(record.get("text") or "").strip()
    links_detailed = _as_dict_list(record.get("links_detailed"))
    images_detailed = _as_dict_list(record.get("images_detailed"))
    quotes = _as_dict_list(record.get("quotes"))
    spoilers = _as_dict_list(record.get("spoilers"))
    if topic_id is None or post_id is None:
        return False
    if not post_url:
        return False
    if len(text) >= 3:
        return True
    return bool(links_detailed or images_detailed or quotes or spoilers)


def _comment_quality_score(row: dict) -> float:
    text = str(row.get("text") or "").strip()
    topic_title = str(row.get("topic_title") or "").strip()
    post_url = str(row.get("post_url") or "").strip()
    topic_url = str(row.get("topic_url") or "").strip()
    poster = str(row.get("poster") or "").strip()
    posted_at = str(row.get("posted_at") or "").strip()

    links = _as_dict_list(row.get("links_detailed"))
    images = _as_dict_list(row.get("images_detailed"))
    quotes = _as_dict_list(row.get("quotes"))
    spoilers = _as_dict_list(row.get("spoilers"))
    reply_post_ids = _as_int_list(row.get("reply_to_post_ids"))
    reply_users = _as_text_list(row.get("reply_to_users"))
    formatting = row.get("formatting_stats")
    if not isinstance(formatting, dict):
        formatting = {}

    text_len = len(text)
    score = 0.0
    total = 0.0

    total += 2.6
    if text_len >= 220:
        score += 2.6
    elif text_len >= 100:
        score += 2.0
    elif text_len >= 40:
        score += 1.3
    elif text_len >= 15:
        score += 0.7
    elif text_len > 0:
        score += 0.3

    total += 1.9
    if topic_title:
        score += 0.7
    if post_url and "viewtopic.php" in post_url and "#" in post_url:
        score += 0.8
    elif post_url:
        score += 0.4
    if topic_url and "viewtopic.php" in topic_url:
        score += 0.4

    total += 1.0
    if poster:
        score += 0.5
    if posted_at:
        score += 0.5

    total += 2.5
    structure_raw = (
        min(len(quotes), 4) * 0.35
        + min(len(spoilers), 3) * 0.45
        + min(len(links), 8) * 0.14
        + min(len(images), 6) * 0.12
        + min(len(reply_post_ids), 6) * 0.18
        + min(len(reply_users), 6) * 0.16
    )
    if formatting:
        structure_raw += (
            min(
                float(formatting.get("bold_count") or 0)
                + float(formatting.get("italic_count") or 0)
                + float(formatting.get("code_count") or 0)
                + float(formatting.get("list_item_count") or 0)
                + float(formatting.get("quote_count") or 0)
                + float(formatting.get("spoiler_count") or 0),
                12.0,
            )
            * 0.06
        )
    score += min(structure_raw, 2.5)

    total += 1.3
    signature_text = str(row.get("signature_text") or "").strip()
    if signature_text:
        score += 0.2
    if _as_text_list(row.get("signature_links")):
        score += 0.6
    if _as_text_list(row.get("signature_images")):
        score += 0.5

    if total <= 0.0:
        return 0.0

    quality = max(0.0, min(1.0, score / total))

    normalized_text = _normalize_text(text)
    text_tokens = set(_tokenize(normalized_text, stem=False))
    only_low_signal = bool(text_tokens) and text_tokens.issubset(COMMENT_LOW_SIGNAL_TOKENS)
    low_signal = only_low_signal or (
        text_len <= 70 and any(token in COMMENT_LOW_SIGNAL_TOKENS for token in text_tokens)
    )
    if text_len < 20 and not (links or images or quotes or spoilers):
        quality *= 0.42
    if low_signal:
        quality *= 0.72
    if len(links) >= 6 and text_len < 120:
        quality *= 0.84

    return max(0.0, min(1.0, quality))


def _comment_quality_penalty(row: dict) -> float:
    return (1.0 - _comment_quality_score(row)) * 24.0


def _comment_rank_base(row: dict) -> float:
    quality = float(row.get("quality_score") or 0.0)
    text_len = max(len(str(row.get("text") or "").strip()), 0)
    reply_count = len(_as_int_list(row.get("reply_to_post_ids")))
    quote_count = len(_as_dict_list(row.get("quotes")))
    spoiler_count = len(_as_dict_list(row.get("spoilers")))
    link_count = len(_as_dict_list(row.get("links_detailed")))
    image_count = len(_as_dict_list(row.get("images_detailed")))
    formatting = row.get("formatting_stats")
    if not isinstance(formatting, dict):
        formatting = {}
    style_signal = min(
        float(formatting.get("bold_count") or 0)
        + float(formatting.get("italic_count") or 0)
        + float(formatting.get("code_count") or 0)
        + float(formatting.get("list_item_count") or 0),
        10.0,
    )

    return (
        quality * 45.0
        + min(float(text_len), 1600.0) / 42.0
        + float(reply_count) * 1.5
        + float(quote_count) * 1.2
        + float(spoiler_count) * 1.05
        + float(link_count) * 0.42
        + float(image_count) * 0.35
        + style_signal * 0.45
    )


def _normalize_existing_comment_index_record(
    row: dict, query: str, *, min_quality_score: float = 0.0
) -> dict | None:
    topic_id = _coerce_int(row.get("topic_id"))
    post_id = _coerce_int(row.get("post_id"))
    if topic_id is None or post_id is None:
        return None

    normalized = dict(row)
    normalized["topic_id"] = topic_id
    normalized["post_id"] = post_id
    normalized["comment_key"] = f"{topic_id}:{post_id}"
    normalized["query"] = str(normalized.get("query") or query)

    quality_score = normalized.get("quality_score")
    if not isinstance(quality_score, (int, float)):
        quality_score = _comment_quality_score(normalized)
    normalized["quality_score"] = round(float(quality_score), 4)

    quality_penalty = normalized.get("quality_penalty")
    if not isinstance(quality_penalty, (int, float)):
        quality_penalty = _comment_quality_penalty(normalized)
    normalized["quality_penalty"] = round(float(quality_penalty), 6)

    rank_base = normalized.get("rank_base")
    if not isinstance(rank_base, (int, float)):
        rank_base = _comment_rank_base(normalized)
    normalized["rank_base"] = round(float(rank_base), 6)
    normalized["rank"] = round(
        max(float(normalized["rank_base"]) - float(normalized["quality_penalty"]), 0.0), 6
    )
    normalized["text_len"] = len(str(normalized.get("text") or "").strip())

    if float(normalized.get("quality_score") or 0.0) < min_quality_score:
        return None
    if not _is_minimal_comment_record(normalized):
        return None
    return normalized


def _merge_incremental_comment_records(
    *,
    current_records: list[dict],
    base_records: list[dict],
    query: str,
    min_quality_score: float = 0.0,
) -> tuple[list[dict], dict]:
    base_map: dict[str, dict] = {}
    base_filtered_out = 0
    for row in base_records:
        normalized = _normalize_existing_comment_index_record(
            row, query, min_quality_score=min_quality_score
        )
        if not normalized:
            base_filtered_out += 1
            continue
        base_map[str(normalized["comment_key"])] = normalized

    base_keys = set(base_map.keys())
    new_count = 0
    updated_count = 0
    new_sample: list[str] = []
    updated_sample: list[str] = []
    for row in current_records:
        key = str(row.get("comment_key") or "")
        if not key:
            continue
        if key in base_keys:
            updated_count += 1
            if len(updated_sample) < 50:
                updated_sample.append(key)
        else:
            new_count += 1
            if len(new_sample) < 50:
                new_sample.append(key)
        base_map[key] = row

    carried_over = max(len(base_keys) - updated_count, 0)
    merged = sorted(
        base_map.values(),
        key=lambda item: (
            float(item.get("rank") or 0.0),
            float(item.get("quality_score") or 0.0),
            int(item.get("text_len") or 0),
            _coerce_int(item.get("post_id")) or 0,
        ),
        reverse=True,
    )
    return merged, {
        "base_records_total": len(base_keys),
        "base_records_filtered_out": base_filtered_out,
        "new_records": new_count,
        "updated_records": updated_count,
        "carried_over_records": carried_over,
        "merged_records_total": len(merged),
        "new_comment_keys_sample": new_sample,
        "updated_comment_keys_sample": updated_sample,
    }


def _build_topic_context_map(topics_rows: list[dict]) -> dict[int, dict]:
    by_topic: dict[int, dict] = {}
    for row in topics_rows:
        topic_id = _coerce_int(row.get("topic_id"))
        if topic_id is None:
            continue
        if str(row.get("kind") or "").strip().lower() == "tracker_details":
            continue
        candidate = {
            "topic_title": str(row.get("title") or "").strip(),
            "topic_url": str(row.get("url") or row.get("topic_url") or "").strip(),
            "forum_id": _coerce_int(row.get("forum_id")),
            "forum_title": str(row.get("forum_title") or "").strip(),
            "forum_url": str(row.get("forum_url") or "").strip(),
        }
        current = by_topic.get(topic_id)
        if not current:
            by_topic[topic_id] = candidate
            continue
        if _comment_payload_richness(candidate) >= _comment_payload_richness(current):
            by_topic[topic_id] = candidate
    return by_topic


def _load_comments_index_records(index_path: Path) -> tuple[str, list[dict]]:
    rows = _read_jsonl(index_path)
    query = ""
    normalized: list[dict] = []
    for row in rows:
        if not query:
            query = str(row.get("query") or "").strip()
        if isinstance(row, dict):
            normalized.append(row)
    return query, normalized


def build_comments_index(
    run_dir: Path,
    query: str,
    *,
    base_records: list[dict] | None = None,
    base_index_path: Path | None = None,
    min_quality_score: float = 0.0,
) -> dict:
    posts_rows = _read_jsonl(run_dir / "entities" / "posts.jsonl")
    topics_rows = _read_jsonl(run_dir / "entities" / "topics.jsonl")
    quality_threshold = _clamp_quality_threshold(min_quality_score, default=0.0)
    drop_stats: dict[str, int] = {
        "skipped_without_topic_or_post_id": 0,
        "skipped_empty_payload": 0,
        "skipped_low_quality": 0,
    }

    topic_context = _build_topic_context_map(topics_rows)
    chosen: dict[str, dict] = {}
    for row in posts_rows:
        topic_id = _coerce_int(row.get("topic_id"))
        post_id = _coerce_int(row.get("post_id"))
        if topic_id is None or post_id is None:
            drop_stats["skipped_without_topic_or_post_id"] += 1
            continue

        context = topic_context.get(topic_id, {})
        text = str(row.get("text") or "").strip()
        text_lines = _as_text_list(row.get("text_lines"))
        if not text and text_lines:
            text = " ".join(text_lines).strip()

        links_detailed = _as_dict_list(row.get("links_detailed"))
        if not links_detailed:
            links_detailed = [
                {"url": link, "anchor_text": "", "is_external": False}
                for link in _as_text_list(row.get("links"))
            ]

        images_detailed = _as_dict_list(row.get("images_detailed"))
        if not images_detailed:
            images_detailed = [
                {"url": image, "alt": ""} for image in _as_text_list(row.get("image_urls"))
            ]

        quotes = _as_dict_list(row.get("quotes"))
        spoilers = _as_dict_list(row.get("spoilers"))
        reply_to_post_ids = _as_int_list(row.get("reply_to_post_ids"))
        reply_to_users = _as_text_list(row.get("reply_to_users"))
        formatting_stats = row.get("formatting_stats")
        if not isinstance(formatting_stats, dict):
            formatting_stats = {}

        quote_texts = [
            str(item.get("text") or "").strip()
            for item in quotes
            if str(item.get("text") or "").strip()
        ]
        spoiler_texts = [
            str(item.get("text") or "").strip()
            for item in spoilers
            if str(item.get("text") or "").strip()
        ]
        link_texts = [
            str(item.get("anchor_text") or item.get("url") or "").strip()
            for item in links_detailed
            if str(item.get("anchor_text") or item.get("url") or "").strip()
        ]

        topic_title = str(row.get("topic_title") or context.get("topic_title") or "").strip()
        topic_url = str(row.get("topic_url") or context.get("topic_url") or "").strip()
        forum_title = str(row.get("forum_title") or context.get("forum_title") or "").strip()
        forum_id = _coerce_int(row.get("forum_id")) or _coerce_int(context.get("forum_id"))
        forum_url = str(row.get("forum_url") or context.get("forum_url") or "").strip()
        post_url = str(row.get("url") or "").strip()
        poster = str(row.get("poster") or "").strip()
        signature_text = str(row.get("signature_text") or "").strip()
        signature_links = _as_text_list(row.get("signature_links"))
        signature_images = _as_text_list(row.get("signature_images"))

        search_blob = " ".join(
            item
            for item in (
                topic_title,
                forum_title,
                poster,
                text,
                " ".join(text_lines),
                " ".join(quote_texts),
                " ".join(spoiler_texts),
                " ".join(link_texts),
                " ".join(reply_to_users),
                signature_text,
            )
            if item
        )
        search_text = _normalize_text(search_blob[:9000])

        record = {
            "comment_key": f"{topic_id}:{post_id}",
            "topic_id": topic_id,
            "post_id": post_id,
            "post_url": post_url,
            "topic_title": topic_title,
            "topic_url": topic_url,
            "forum_id": forum_id,
            "forum_title": forum_title,
            "forum_url": forum_url,
            "poster": poster,
            "poster_profile_url": str(row.get("poster_profile_url") or "").strip(),
            "poster_avatar_url": str(row.get("poster_avatar_url") or "").strip(),
            "poster_pm_url": str(row.get("poster_pm_url") or "").strip(),
            "posted_at": str(row.get("posted_at") or "").strip(),
            "edited_info": str(row.get("edited_info") or "").strip(),
            "quote_action_url": str(row.get("quote_action_url") or "").strip(),
            "quote_action_post_id": _coerce_int(row.get("quote_action_post_id")),
            "text": text,
            "text_len": len(text),
            "text_lines": text_lines,
            "quote_texts": quote_texts,
            "spoiler_texts": spoiler_texts,
            "links_detailed": links_detailed,
            "images_detailed": images_detailed,
            "quotes": quotes,
            "spoilers": spoilers,
            "reply_to_post_ids": reply_to_post_ids,
            "reply_to_users": reply_to_users,
            "formatting_stats": formatting_stats,
            "signature_text": signature_text,
            "signature_links": signature_links,
            "signature_images": signature_images,
            "source_url": str(row.get("source_url") or "").strip(),
            "query": query,
            "search_text": search_text,
            "ts": str(row.get("ts") or utc_now_iso()),
        }

        if not _is_minimal_comment_record(record):
            drop_stats["skipped_empty_payload"] += 1
            continue

        quality_score = _comment_quality_score(record)
        if quality_score < quality_threshold:
            drop_stats["skipped_low_quality"] += 1
            continue

        quality_penalty = _comment_quality_penalty(record)
        rank_base = _comment_rank_base({**record, "quality_score": quality_score})
        record["quality_score"] = round(quality_score, 4)
        record["quality_penalty"] = round(quality_penalty, 6)
        record["rank_base"] = round(rank_base, 6)
        record["rank"] = round(max(rank_base - quality_penalty, 0.0), 6)

        existing = chosen.get(record["comment_key"])
        if not existing:
            chosen[record["comment_key"]] = record
            continue
        if _comment_payload_richness(record) > _comment_payload_richness(existing):
            chosen[record["comment_key"]] = record
            continue
        if float(record.get("rank") or 0.0) > float(existing.get("rank") or 0.0):
            chosen[record["comment_key"]] = record

    current_records = sorted(
        chosen.values(),
        key=lambda item: (
            float(item.get("rank") or 0.0),
            float(item.get("quality_score") or 0.0),
            int(item.get("text_len") or 0),
            _coerce_int(item.get("post_id")) or 0,
        ),
        reverse=True,
    )
    records = current_records

    incremental_summary = {
        "enabled": False,
        "base_index_path": "",
        "min_quality_score": quality_threshold,
        "base_records_total": 0,
        "base_records_filtered_out": 0,
        "new_records": len(current_records),
        "updated_records": 0,
        "carried_over_records": 0,
        "merged_records_total": len(current_records),
        "new_comment_keys_sample": [],
        "updated_comment_keys_sample": [],
    }
    if base_records:
        records, merge_stats = _merge_incremental_comment_records(
            current_records=current_records,
            base_records=base_records,
            query=query,
            min_quality_score=quality_threshold,
        )
        incremental_summary = {
            "enabled": True,
            "base_index_path": str(base_index_path) if base_index_path else "",
            "min_quality_score": quality_threshold,
            **merge_stats,
        }

    search_dir = run_dir / "search"
    search_dir.mkdir(parents=True, exist_ok=True)

    comments_index_path = search_dir / "comments_index.jsonl"
    comments_stats_path = search_dir / "comments_index_stats.json"
    comments_top_path = search_dir / "comments_top50.json"

    _jsonl_write(comments_index_path, records)
    comments_top_path.write_text(
        json.dumps(
            {
                "query": query,
                "generated_at": utc_now_iso(),
                "top": records[:50],
            },
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )
    comments_stats_path.write_text(
        json.dumps(
            {
                "query": query,
                "generated_at": utc_now_iso(),
                "records_from_crawl": len(current_records),
                "records_total": len(records),
                "min_quality_score": quality_threshold,
                "quality_avg": round(
                    (
                        sum(float(item.get("quality_score") or 0.0) for item in records)
                        / max(len(records), 1)
                    ),
                    4,
                ),
                "drop_stats": drop_stats,
                "incremental": incremental_summary,
            },
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )
    return {
        "records_total": len(records),
        "records_from_crawl": len(current_records),
        "comments_index": str(comments_index_path),
        "comments_top50": str(comments_top_path),
        "comments_index_stats": str(comments_stats_path),
        "drop_stats": drop_stats,
        "min_quality_score": quality_threshold,
        "incremental": incremental_summary,
    }


def build_search_index(
    run_dir: Path,
    query: str,
    *,
    base_records: list[dict] | None = None,
    base_index_path: Path | None = None,
    min_quality_score: float = 0.0,
) -> dict:
    topics_rows = _read_jsonl(run_dir / "entities" / "topics.jsonl")
    torrents_rows = _read_jsonl(run_dir / "entities" / "torrents.jsonl")
    forums_rows = _read_jsonl(run_dir / "entities" / "forums.jsonl")
    pages_rows = _read_jsonl(run_dir / "pages" / "pages.jsonl")
    quality_threshold = _clamp_quality_threshold(min_quality_score, default=0.0)
    drop_stats: dict[str, int] = {
        "skipped_kind_rows": 0,
        "skipped_non_tracker_rows": 0,
        "skipped_without_topic_id": 0,
        "skipped_low_quality": 0,
    }

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
            drop_stats["skipped_kind_rows"] += 1
            continue
        source_url = str(row.get("source_url") or "")
        if "tracker.php" not in source_url:
            drop_stats["skipped_non_tracker_rows"] += 1
            continue

        topic_id = _coerce_int(row.get("topic_id"))
        if topic_id is None:
            drop_stats["skipped_without_topic_id"] += 1
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
        quality_score = _record_quality_score(record)
        quality_penalty = _record_quality_penalty(record)
        rank_base = _record_rank(record)
        record["quality_score"] = round(quality_score, 4)
        record["quality_penalty"] = round(quality_penalty, 6)
        record["rank_base"] = round(rank_base, 6)
        record["rank"] = round(max(rank_base - quality_penalty, 0.0), 6)

        if quality_score < quality_threshold:
            drop_stats["skipped_low_quality"] += 1
            continue
        if not _is_minimal_index_record(record):
            drop_stats["skipped_low_quality"] += 1
            continue

        old = chosen.get(topic_id)
        if old is None:
            chosen[topic_id] = record
            continue
        if _topic_richness(record) > _topic_richness(old):
            chosen[topic_id] = record
            continue
        if (record.get("rank") or 0.0) > (old.get("rank") or 0.0):
            chosen[topic_id] = record

    current_records = sorted(
        chosen.values(),
        key=lambda item: (
            float(item.get("rank") or 0.0),
            _coerce_int(item.get("seeders")) or 0,
            _coerce_int(item.get("downloads")) or 0,
            _coerce_int(item.get("added_ts")) or 0,
            float(item.get("quality_score") or 0.0),
        ),
        reverse=True,
    )
    records = current_records

    incremental_summary = {
        "enabled": False,
        "base_index_path": "",
        "min_quality_score": quality_threshold,
        "base_records_total": 0,
        "base_records_filtered_out": 0,
        "new_records": len(current_records),
        "updated_records": 0,
        "carried_over_records": 0,
        "merged_records_total": len(current_records),
        "new_topic_ids_sample": [],
        "updated_topic_ids_sample": [],
    }
    if base_records:
        records, merge_stats = _merge_incremental_records(
            current_records=current_records,
            base_records=base_records,
            query=query,
            min_quality_score=quality_threshold,
        )
        incremental_summary = {
            "enabled": True,
            "base_index_path": str(base_index_path) if base_index_path else "",
            "min_quality_score": quality_threshold,
            **merge_stats,
        }

    compact_records = [
        _compact_record_payload(
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
                "quality_score": row.get("quality_score", 0.0),
                "quality_penalty": row.get("quality_penalty", 0.0),
                "rank_base": row.get("rank_base", row.get("rank", 0.0)),
                "rank": row.get("rank", 0.0),
                "search_text": _normalize_text(
                    f"{row.get('title', '')} {row.get('forum_title', '')} {row.get('uploader', '')}"
                ),
            }
        )
        for row in records
    ]

    search_dir = run_dir / "search"
    search_dir.mkdir(parents=True, exist_ok=True)

    full_payload = {
        "query": query,
        "generated_at": utc_now_iso(),
        "run_dir": str(run_dir),
        "pages_crawled": len(pages_rows),
        "records_from_crawl": len(current_records),
        "records_total": len(records),
        "min_quality_score": quality_threshold,
        "drop_stats": drop_stats,
        "incremental": incremental_summary,
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
                "records_from_crawl": len(current_records),
                "records_total": len(records),
                "min_quality_score": quality_threshold,
                "forums_unique": len(
                    {item.get("forum_id") for item in records if item.get("forum_id") is not None}
                ),
                "with_torrent_url": sum(1 for item in records if item.get("torrent_url")),
                "quality_avg": round(
                    (
                        sum(float(item.get("quality_score") or 0.0) for item in records)
                        / max(len(records), 1)
                    ),
                    4,
                ),
                "drop_stats": drop_stats,
                "incremental": incremental_summary,
            },
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )

    delta_path = search_dir / "index_delta.json"
    if incremental_summary.get("enabled"):
        delta_payload = {
            "query": query,
            "generated_at": utc_now_iso(),
            "base_index_path": incremental_summary.get("base_index_path", ""),
            "min_quality_score": quality_threshold,
            "new_records": incremental_summary.get("new_records", 0),
            "updated_records": incremental_summary.get("updated_records", 0),
            "carried_over_records": incremental_summary.get("carried_over_records", 0),
            "new_topic_ids_sample": incremental_summary.get("new_topic_ids_sample", []),
            "updated_topic_ids_sample": incremental_summary.get("updated_topic_ids_sample", []),
        }
        delta_path.write_text(
            json.dumps(delta_payload, ensure_ascii=False, indent=2), encoding="utf-8"
        )

    return {
        "run_dir": str(run_dir),
        "query": query,
        "records_total": len(records),
        "index_full": str(full_path),
        "index_compact": str(compact_path),
        "index_top50": str(top_path),
        "index_stats": str(stats_path),
        "records_from_crawl": len(current_records),
        "min_quality_score": quality_threshold,
        "drop_stats": drop_stats,
        "incremental": incremental_summary,
        "index_delta": str(delta_path) if delta_path.exists() else "",
    }


def run_search_index(args: Any) -> dict:
    force_reindex = bool(getattr(args, "force", False))
    incremental_enabled = bool(getattr(args, "incremental", False)) and not force_reindex
    base_index_value = str(getattr(args, "base_index", "") or "").strip()
    min_quality_score = _clamp_quality_threshold(
        getattr(args, "min_quality_score", 0.0), default=0.0
    )
    max_base_records = max(int(getattr(args, "max_base_records", 200_000) or 0), 0)
    min_comment_quality_score = _clamp_quality_threshold(
        getattr(args, "min_comment_quality_score", 0.0), default=0.0
    )
    base_index_path: Path | None = None
    base_comments_index_path: Path | None = None
    base_query = ""
    base_records: list[dict] = []
    base_comment_query = ""
    base_comment_records: list[dict] = []

    if incremental_enabled:
        candidate: Path | None = None
        if base_index_value:
            candidate = Path(base_index_value).expanduser().resolve()
        else:
            latest_full = _find_latest_index_full_file(Path(args.output_dir))
            candidate = latest_full.resolve() if latest_full else None

        if candidate is not None and candidate.exists():
            if not candidate.is_file():
                raise FileNotFoundError(f"Base index is not a file: {candidate}")
            base_index_path = candidate
            base_query, loaded = _load_index_records(base_index_path)
            if base_query and _normalize_text(base_query) != _normalize_text(args.query):
                base_records = []
            else:
                base_records = loaded
            if max_base_records > 0 and len(base_records) > max_base_records:
                base_records = sorted(
                    base_records,
                    key=lambda item: (
                        float(item.get("rank") or 0.0),
                        _coerce_int(item.get("seeders")) or 0,
                        _coerce_int(item.get("downloads")) or 0,
                    ),
                    reverse=True,
                )[:max_base_records]

            candidate_comments = base_index_path.parent / "comments_index.jsonl"
            if candidate_comments.exists() and candidate_comments.is_file():
                base_comments_index_path = candidate_comments
                base_comment_query, loaded_comments = _load_comments_index_records(
                    base_comments_index_path
                )
                if base_comment_query and _normalize_text(base_comment_query) != _normalize_text(
                    args.query
                ):
                    base_comment_records = []
                else:
                    base_comment_records = loaded_comments
                if max_base_records > 0 and len(base_comment_records) > max_base_records:
                    base_comment_records = sorted(
                        base_comment_records,
                        key=lambda item: (
                            float(item.get("rank") or 0.0),
                            float(item.get("quality_score") or 0.0),
                            int(item.get("text_len") or 0),
                        ),
                        reverse=True,
                    )[:max_base_records]
        elif base_index_value:
            raise FileNotFoundError(f"Base index not found: {candidate}")

    forum_ids = [value for value in args.forum_id if value > 0]
    if not forum_ids:
        forum_ids = discover_audiobooks_forum_ids(args)

    limit_forums = max(args.limit_forums, 0)
    if limit_forums:
        forum_ids = forum_ids[:limit_forums]
    if not forum_ids:
        raise RuntimeError("Не удалось определить форумы аудиокниг для поиска")

    settings = _build_search_settings(args, forum_ids)
    indexer = str(getattr(args, "indexer", "parallel") or "parallel").strip().lower()
    if indexer not in {"parallel", "crawl"}:
        indexer = "parallel"
    if indexer == "crawl":
        engine = CrawlEngine(settings)
        crawl_summary = engine.run()
    else:
        crawl_summary = _run_parallel_tracker_harvest(args, settings, forum_ids)

    run_dir = Path(crawl_summary["run_dir"])
    index_summary = build_search_index(
        run_dir,
        args.query,
        base_records=base_records,
        base_index_path=base_index_path,
        min_quality_score=min_quality_score,
    )
    comments_summary = build_comments_index(
        run_dir,
        args.query,
        base_records=base_comment_records,
        base_index_path=base_comments_index_path,
        min_quality_score=min_comment_quality_score,
    )
    return {
        "query": args.query,
        "forum_ids": forum_ids,
        "indexer": indexer,
        "force_reindex": force_reindex,
        "crawl": crawl_summary,
        "index": index_summary,
        "comments_index": comments_summary,
        "incremental": {
            "enabled": incremental_enabled,
            "base_index_path": str(base_index_path) if base_index_path else "",
            "base_query": base_query,
            "base_records_loaded": len(base_records),
            "base_comments_index_path": (
                str(base_comments_index_path) if base_comments_index_path else ""
            ),
            "base_comment_query": base_comment_query,
            "base_comment_records_loaded": len(base_comment_records),
            "max_base_records": max_base_records,
            "min_quality_score": min_quality_score,
            "min_comment_quality_score": min_comment_quality_score,
            "force_reindex": force_reindex,
        },
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


def search_compact_index(
    index_path: Path, query: str, limit: int, *, min_quality_score: float = 0.0
) -> dict:
    payload = json.loads(index_path.read_text(encoding="utf-8"))
    if isinstance(payload, dict):
        records = payload.get("records", [])
    elif isinstance(payload, list):
        records = payload
    else:
        records = []

    quality_threshold = _clamp_quality_threshold(min_quality_score, default=0.0)
    raw_records_count = len(records)
    if quality_threshold > 0.0:
        records = [
            row for row in records if float(row.get("quality_score") or 0.0) >= quality_threshold
        ]

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
        quality_score = float(row.get("quality_score") or 0.0)
        quality_penalty = float(row.get("quality_penalty") or 0.0)
        freshness = (_coerce_int(row.get("added_ts")) or 0) / 100_000_000.0
        score = (
            lexical * 120.0
            + phrase_bonus
            + fuzzy_bonus
            + base_rank * 2.0
            + quality_score * 18.0
            - quality_penalty * 1.3
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
            float(row.get("quality_score") or 0.0),
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
        "min_quality_score": quality_threshold,
        "records_before_quality_filter": raw_records_count,
        "index_records": len(records),
        "results": best,
    }


def search_comments_index(
    index_path: Path, query: str, limit: int, *, min_quality_score: float = 0.0
) -> dict:
    records = _read_jsonl(index_path)
    quality_threshold = _clamp_quality_threshold(min_quality_score, default=0.0)
    raw_records_count = len(records)
    if quality_threshold > 0.0:
        records = [
            row for row in records if float(row.get("quality_score") or 0.0) >= quality_threshold
        ]

    query_normalized = _normalize_text(query)
    query_tokens = _tokenize(query_normalized, stem=False)
    query_stems = set(_tokenize(query_normalized, stem=True))
    idf_map = _build_idf(records)

    strict_ranked: list[tuple[float, dict]] = []
    fuzzy_ranked: list[tuple[float, dict]] = []
    for row in records:
        text = _normalize_text(str(row.get("text") or ""))
        topic_title = _normalize_text(str(row.get("topic_title") or ""))
        poster = _normalize_text(str(row.get("poster") or ""))
        forum_title = _normalize_text(str(row.get("forum_title") or ""))
        blob = _normalize_text(
            str(
                row.get("search_text")
                or (
                    f"{row.get('topic_title', '')} {row.get('forum_title', '')} "
                    f"{row.get('poster', '')} {row.get('text', '')}"
                )
            )
        )

        text_stems = set(_tokenize(text, stem=True))
        topic_stems = set(_tokenize(topic_title, stem=True))
        poster_stems = set(_tokenize(poster, stem=True))
        forum_stems = set(_tokenize(forum_title, stem=True))
        all_stems = text_stems | topic_stems | poster_stems | forum_stems

        lexical = 0.0
        for token in query_stems:
            token_idf = idf_map.get(token, 1.0)
            if token in text_stems:
                lexical += token_idf * 8.0
            if token in topic_stems:
                lexical += token_idf * 4.0
            if token in poster_stems:
                lexical += token_idf * 2.5
            if token in forum_stems:
                lexical += token_idf * 1.6

        phrase_bonus = 0.0
        if query_normalized and query_normalized in text:
            phrase_bonus += 34.0
        elif query_normalized and query_normalized in blob:
            phrase_bonus += 18.0

        fuzzy_text = (
            SequenceMatcher(None, query_normalized, text).ratio()
            if query_normalized and text
            else 0.0
        )
        fuzzy_topic = (
            SequenceMatcher(None, query_normalized, topic_title).ratio()
            if query_normalized and topic_title
            else 0.0
        )
        fuzzy_blob = (
            SequenceMatcher(None, query_normalized, blob).ratio()
            if query_normalized and blob
            else 0.0
        )
        fuzzy_score = max(fuzzy_text, fuzzy_topic, fuzzy_blob)
        fuzzy_bonus = max(0.0, fuzzy_score - 0.46) * 42.0

        quality_score = float(row.get("quality_score") or 0.0)
        quality_penalty = float(row.get("quality_penalty") or 0.0)
        base_rank = float(row.get("rank") or 0.0)
        reply_count = len(_as_int_list(row.get("reply_to_post_ids")))
        quote_count = len(_as_dict_list(row.get("quotes")))
        spoiler_count = len(_as_dict_list(row.get("spoilers")))
        score = (
            lexical * 115.0
            + phrase_bonus
            + fuzzy_bonus
            + base_rank * 1.8
            + quality_score * 20.0
            - quality_penalty * 1.2
            + float(reply_count) * 1.3
            + float(quote_count) * 0.8
            + float(spoiler_count) * 0.6
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
            float(row.get("quality_score") or 0.0),
            int(row.get("text_len") or 0),
            _coerce_int(row.get("post_id")) or 0,
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
        "min_quality_score": quality_threshold,
        "records_before_quality_filter": raw_records_count,
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
        incremental=bool(getattr(args, "incremental", False)),
        force=bool(getattr(args, "force", False)),
        indexer=str(getattr(args, "indexer", "parallel") or "parallel"),
        workers=int(getattr(args, "workers", DEFAULT_PARALLEL_INDEX_WORKERS) or 0),
        incremental_pages_window=int(
            getattr(args, "incremental_pages_window", DEFAULT_INCREMENTAL_PAGES_WINDOW) or 0
        ),
        hard_max_pages_per_forum=int(
            getattr(args, "hard_max_pages_per_forum", DEFAULT_HARD_MAX_PAGES_PER_FORUM) or 0
        ),
        base_index=str(getattr(args, "base_index", "") or ""),
        min_quality_score=float(getattr(args, "min_quality_score", 0.0) or 0.0),
        min_comment_quality_score=float(getattr(args, "min_comment_quality_score", 0.0) or 0.0),
        max_base_records=int(getattr(args, "max_base_records", 200_000) or 0),
    )


def _build_relaxed_refresh_args(args: Any) -> Any:
    refreshed = _build_search_index_namespace_from_find(args, query=args.query)
    refreshed.force = False
    refreshed.indexer = str(getattr(refreshed, "indexer", "parallel") or "parallel")
    if not refreshed.forum_id and refreshed.limit_forums and refreshed.limit_forums <= 2:
        refreshed.limit_forums = 8
    refreshed.max_pages_per_forum = max(2, int(refreshed.max_pages_per_forum))
    refreshed.max_retries = max(2, int(refreshed.max_retries))
    refreshed.min_quality_score = min(
        _clamp_quality_threshold(getattr(refreshed, "min_quality_score", 0.0), default=0.0), 0.35
    )
    refreshed.min_comment_quality_score = min(
        _clamp_quality_threshold(getattr(refreshed, "min_comment_quality_score", 0.0), default=0.0),
        0.2,
    )
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

    auto_build_allowed = args.auto_index_if_missing or args.auto_refresh_on_empty
    if auto_build_allowed:
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


def _resolve_comments_index_for_find(args: Any) -> tuple[Path, dict | None]:
    index_summary: dict | None = None
    explicit_index = bool(getattr(args, "comments_index", ""))
    if explicit_index:
        index_path = Path(args.comments_index).expanduser().resolve()
    else:
        latest = _find_latest_comments_index_file(Path(args.output_dir))
        index_path = latest.resolve() if latest else Path("")

    if index_path and index_path.is_file():
        return index_path, index_summary
    if explicit_index and index_path and index_path.exists() and not index_path.is_file():
        raise FileNotFoundError(f"Comments index path is not a file: {index_path}")

    auto_build_allowed = args.auto_index_if_missing or args.auto_refresh_on_empty
    if auto_build_allowed:
        build_args = _build_search_index_namespace_from_find(args, query=args.query)
        index_summary = run_search_index(build_args)
        comments_path = (
            index_summary.get("comments_index", {}).get("comments_index")
            if isinstance(index_summary.get("comments_index"), dict)
            else ""
        )
        index_path = Path(str(comments_path)).expanduser().resolve() if comments_path else Path("")
        if index_path and index_path.is_file():
            return index_path, index_summary

    if explicit_index:
        raise FileNotFoundError(
            f"Comments index not found: {index_path}. "
            "Use --auto-index-if-missing to build it automatically."
        )
    raise FileNotFoundError(
        f"Comments index not found in {Path(args.output_dir).resolve()}. "
        "Provide --comments-index or use --auto-index-if-missing."
    )


def _merge_scope_results(topic_result: dict, comment_result: dict, limit: int) -> list[dict]:
    merged: list[dict] = []
    for row in topic_result.get("results", []):
        payload = dict(row)
        payload["entity_type"] = "topic"
        payload["score_hint"] = (
            float(row.get("rank") or 0.0) + float(row.get("quality_score") or 0.0) * 4.0
        )
        merged.append(payload)
    for row in comment_result.get("results", []):
        payload = dict(row)
        payload["entity_type"] = "comment"
        payload["score_hint"] = (
            float(row.get("rank") or 0.0) + float(row.get("quality_score") or 0.0) * 6.0
        )
        merged.append(payload)
    merged.sort(key=lambda item: float(item.get("score_hint") or 0.0), reverse=True)
    return merged[: max(limit, 1)]


def run_search_find(args: Any) -> dict:
    min_quality_score = _clamp_quality_threshold(
        getattr(args, "min_quality_score", 0.0), default=0.0
    )
    min_comment_quality_score = _clamp_quality_threshold(
        getattr(args, "min_comment_quality_score", 0.0), default=0.0
    )
    scope = str(getattr(args, "scope", "topics") or "topics").strip().lower()
    if scope not in {"topics", "comments", "all"}:
        scope = "topics"

    topic_result: dict | None = None
    comment_result: dict | None = None
    topic_build_summary: dict | None = None
    comments_build_summary: dict | None = None

    if scope in {"topics", "all"}:
        try:
            index_path, topic_build_summary = _resolve_index_for_find(args)
            topic_result = search_compact_index(
                index_path=index_path,
                query=args.query,
                limit=args.limit,
                min_quality_score=min_quality_score,
            )
            topic_result["index_path"] = str(index_path)
            if topic_build_summary:
                topic_result["index_built"] = topic_build_summary.get("index", {})
        except FileNotFoundError as error:
            if scope == "topics":
                raise
            topic_result = {
                "query": args.query,
                "limit": max(int(args.limit), 1),
                "matched": 0,
                "strategy": "missing_index",
                "index_records": 0,
                "records_before_quality_filter": 0,
                "results": [],
                "warning": str(error),
            }

    if scope in {"comments", "all"}:
        try:
            comments_index_path, comments_build_summary = _resolve_comments_index_for_find(args)
            comment_result = search_comments_index(
                index_path=comments_index_path,
                query=args.query,
                limit=args.limit,
                min_quality_score=min_comment_quality_score,
            )
            comment_result["index_path"] = str(comments_index_path)
            if comments_build_summary:
                comment_result["index_built"] = comments_build_summary.get("comments_index", {})
        except FileNotFoundError as error:
            if scope == "comments":
                raise
            comment_result = {
                "query": args.query,
                "limit": max(int(args.limit), 1),
                "matched": 0,
                "strategy": "missing_index",
                "index_records": 0,
                "records_before_quality_filter": 0,
                "results": [],
                "warning": str(error),
            }

    if scope == "topics":
        result = dict(topic_result or {})
    elif scope == "comments":
        result = dict(comment_result or {})
    else:
        result = {
            "query": args.query,
            "limit": max(int(args.limit), 1),
            "scope": "all",
            "topic": topic_result or {},
            "comments": comment_result or {},
            "results": _merge_scope_results(topic_result or {}, comment_result or {}, args.limit),
            "matched": int((topic_result or {}).get("matched", 0))
            + int((comment_result or {}).get("matched", 0)),
            "index_records": int((topic_result or {}).get("index_records", 0))
            + int((comment_result or {}).get("index_records", 0)),
            "records_before_quality_filter": int(
                (topic_result or {}).get("records_before_quality_filter", 0)
            )
            + int((comment_result or {}).get("records_before_quality_filter", 0)),
        }

    def _needs_refresh(block: dict | None) -> bool:
        if not block:
            return False
        return bool(args.auto_refresh_on_empty) and (
            int(block.get("matched", 0)) == 0 or int(block.get("index_records", 0)) == 0
        )

    if scope in {"topics", "all"} and _needs_refresh(topic_result):
        refresh_args = _build_relaxed_refresh_args(args)
        refresh_summary = run_search_index(refresh_args)
        refreshed_path = Path(refresh_summary["index"]["index_compact"]).expanduser().resolve()
        refreshed_topic = search_compact_index(
            index_path=refreshed_path,
            query=args.query,
            limit=args.limit,
            min_quality_score=min_quality_score,
        )
        refreshed_topic["index_path"] = str(refreshed_path)
        refreshed_topic["index_refreshed"] = refresh_summary.get("index", {})
        topic_result = refreshed_topic

    if scope in {"comments", "all"} and _needs_refresh(comment_result):
        refresh_args = _build_relaxed_refresh_args(args)
        refresh_summary = run_search_index(refresh_args)
        comments_path_value = (
            refresh_summary.get("comments_index", {}).get("comments_index")
            if isinstance(refresh_summary.get("comments_index"), dict)
            else ""
        )
        if comments_path_value:
            refreshed_path = Path(str(comments_path_value)).expanduser().resolve()
            refreshed_comments = search_comments_index(
                index_path=refreshed_path,
                query=args.query,
                limit=args.limit,
                min_quality_score=min_comment_quality_score,
            )
            refreshed_comments["index_path"] = str(refreshed_path)
            refreshed_comments["index_refreshed"] = refresh_summary.get("comments_index", {})
            comment_result = refreshed_comments

    if scope == "topics":
        result = dict(topic_result or {})
    elif scope == "comments":
        result = dict(comment_result or {})
    else:
        result = {
            "query": args.query,
            "limit": max(int(args.limit), 1),
            "scope": "all",
            "topic": topic_result or {},
            "comments": comment_result or {},
            "results": _merge_scope_results(topic_result or {}, comment_result or {}, args.limit),
            "matched": int((topic_result or {}).get("matched", 0))
            + int((comment_result or {}).get("matched", 0)),
            "index_records": int((topic_result or {}).get("index_records", 0))
            + int((comment_result or {}).get("index_records", 0)),
            "records_before_quality_filter": int(
                (topic_result or {}).get("records_before_quality_filter", 0)
            )
            + int((comment_result or {}).get("records_before_quality_filter", 0)),
        }

    if scope == "topics":
        if (
            result.get("records_before_quality_filter", 0) > 0
            and result.get("index_records", 0) == 0
        ):
            result["hint"] = (
                "Все topic-записи отфильтрованы по quality. Снизьте --min-quality-score "
                "или обновите индекс с более широким покрытием."
            )
        elif result.get("index_records", 0) == 0:
            result["hint"] = (
                "Topic-индекс пуст: расширьте покрытие (больше форумов/страниц) или включите "
                "--auto-refresh-on-empty для автопересборки."
            )
        elif result.get("matched", 0) == 0:
            result["hint"] = (
                "Точных topic-совпадений нет в текущем индексе. Можно перезапросить с более "
                "широким query или обновить индекс (--auto-refresh-on-empty)."
            )
    elif scope == "comments":
        if (
            result.get("records_before_quality_filter", 0) > 0
            and result.get("index_records", 0) == 0
        ):
            result["hint"] = (
                "Все comment-записи отфильтрованы по quality. Снизьте "
                "--min-comment-quality-score или обновите индекс."
            )
        elif result.get("index_records", 0) == 0:
            result["hint"] = (
                "Comment-индекс пуст: нужно обойти темы (viewtopic) и пересобрать индекс."
            )
        elif result.get("matched", 0) == 0:
            result["hint"] = (
                "Точных совпадений в комментариях нет. Попробуйте расширить query "
                "или обновить индекс (--auto-refresh-on-empty)."
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
    parser.add_argument("--indexer", choices=["parallel", "crawl"], default="parallel")
    parser.add_argument("--workers", type=int, default=DEFAULT_PARALLEL_INDEX_WORKERS)
    parser.add_argument("--forum-id", action="append", type=int, default=[])
    parser.add_argument("--limit-forums", type=int, default=0)
    parser.add_argument("--max-pages-per-forum", type=int, default=2)
    parser.add_argument(
        "--incremental-pages-window", type=int, default=DEFAULT_INCREMENTAL_PAGES_WINDOW
    )
    parser.add_argument(
        "--hard-max-pages-per-forum", type=int, default=DEFAULT_HARD_MAX_PAGES_PER_FORUM
    )
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
    parser.add_argument("--min-quality-score", type=float, default=0.4)
    parser.add_argument("--min-comment-quality-score", type=float, default=0.15)
    parser.add_argument("--incremental", action="store_true")
    parser.add_argument("--force", action="store_true")
    parser.add_argument("--base-index", default="")
    parser.add_argument("--max-base-records", type=int, default=200_000)
    return parser


def build_search_find_parser() -> Any:
    import argparse

    parser = argparse.ArgumentParser(prog="python -m rutracker_parser search-find")
    parser.add_argument("--index", default="")
    parser.add_argument("--comments-index", default="")
    parser.add_argument("--scope", choices=["topics", "comments", "all"], default="topics")
    parser.add_argument("--query", required=True)
    parser.add_argument("--limit", type=int, default=20)
    parser.add_argument("--write", default="")
    parser.add_argument("--output-dir", default=DEFAULT_OUTPUT_DIR)
    parser.add_argument("--auto-index-if-missing", action="store_true")
    parser.add_argument("--auto-refresh-on-empty", action="store_true")
    parser.add_argument("--min-quality-score", type=float, default=0.4)
    parser.add_argument("--min-comment-quality-score", type=float, default=0.15)
    parser.add_argument("--incremental", action="store_true")
    parser.add_argument("--force", action="store_true")
    parser.add_argument("--base-index", default="")
    parser.add_argument("--max-base-records", type=int, default=200_000)

    parser.add_argument("--mode", choices=["auto", "guest", "auth"], default="auto")
    parser.add_argument("--mirror", default=DEFAULT_PRIMARY_MIRROR)
    parser.add_argument("--fallback-mirror", default=DEFAULT_FALLBACK_MIRROR)
    parser.add_argument("--extra-mirror", action="append", default=[])
    parser.add_argument("--no-fallback", action="store_true")
    parser.add_argument("--indexer", choices=["parallel", "crawl"], default="parallel")
    parser.add_argument("--workers", type=int, default=DEFAULT_PARALLEL_INDEX_WORKERS)
    parser.add_argument("--forum-id", action="append", type=int, default=[])
    parser.add_argument("--limit-forums", type=int, default=0)
    parser.add_argument("--max-pages-per-forum", type=int, default=1)
    parser.add_argument(
        "--incremental-pages-window", type=int, default=DEFAULT_INCREMENTAL_PAGES_WINDOW
    )
    parser.add_argument(
        "--hard-max-pages-per-forum", type=int, default=DEFAULT_HARD_MAX_PAGES_PER_FORUM
    )
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
