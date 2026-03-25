from __future__ import annotations

import argparse
import os
from dataclasses import dataclass
from pathlib import Path
from typing import Literal

from .constants import (
    DEFAULT_ALLOWED_PATH_PREFIXES,
    DEFAULT_BACKOFF_BASE_SEC,
    DEFAULT_BACKOFF_MAX_SEC,
    DEFAULT_CONNECT_TIMEOUT_SEC,
    DEFAULT_COOLDOWN_SEC,
    DEFAULT_FALLBACK_MIRROR,
    DEFAULT_JITTER_SEC,
    DEFAULT_MAX_ANTIBOT_EVENTS,
    DEFAULT_MAX_DEPTH,
    DEFAULT_MAX_HTML_BYTES,
    DEFAULT_MAX_PAGES,
    DEFAULT_MAX_RETRIES,
    DEFAULT_PRIMARY_MIRROR,
    DEFAULT_READ_TIMEOUT_SEC,
    DEFAULT_REQUEST_INTERVAL_SEC,
    DEFAULT_START_PATHS,
    DEFAULT_USER_AGENT,
    SAFE_ENDPOINTS,
)
from .utils import ensure_absolute_base, to_absolute_url, unique_preserve_order

Mode = Literal["auto", "guest", "auth"]


@dataclass(slots=True)
class CrawlSettings:
    mirrors: list[str]
    mode_requested: Mode
    mode_effective: Literal["guest", "auth"]
    login: str
    password: str
    user_agent: str
    output_root: Path
    start_urls: list[str]
    request_interval_sec: float
    jitter_sec: float
    connect_timeout_sec: float
    read_timeout_sec: float
    max_retries: int
    backoff_base_sec: float
    backoff_max_sec: float
    cooldown_sec: float
    max_pages: int
    max_depth: int
    max_html_bytes: int
    max_antibot_events: int
    save_html: bool
    allow_path_prefixes: tuple[str, ...]
    allow_endpoints: frozenset[str]
    scenario: str


def build_crawl_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="python -m rutracker_parser crawl")
    parser.add_argument("--mode", choices=["auto", "guest", "auth"], default="auto")
    parser.add_argument("--mirror", default=DEFAULT_PRIMARY_MIRROR)
    parser.add_argument("--fallback-mirror", default=DEFAULT_FALLBACK_MIRROR)
    parser.add_argument("--extra-mirror", action="append", default=[])
    parser.add_argument("--no-fallback", action="store_true")
    parser.add_argument("--start-url", action="append", default=[])
    parser.add_argument("--request-interval", type=float, default=DEFAULT_REQUEST_INTERVAL_SEC)
    parser.add_argument("--jitter", type=float, default=DEFAULT_JITTER_SEC)
    parser.add_argument("--connect-timeout", type=float, default=DEFAULT_CONNECT_TIMEOUT_SEC)
    parser.add_argument("--read-timeout", type=float, default=DEFAULT_READ_TIMEOUT_SEC)
    parser.add_argument("--max-retries", type=int, default=DEFAULT_MAX_RETRIES)
    parser.add_argument("--backoff-base", type=float, default=DEFAULT_BACKOFF_BASE_SEC)
    parser.add_argument("--backoff-max", type=float, default=DEFAULT_BACKOFF_MAX_SEC)
    parser.add_argument("--cooldown", type=float, default=DEFAULT_COOLDOWN_SEC)
    parser.add_argument("--max-pages", type=int, default=DEFAULT_MAX_PAGES)
    parser.add_argument("--max-depth", type=int, default=DEFAULT_MAX_DEPTH)
    parser.add_argument("--max-html-bytes", type=int, default=DEFAULT_MAX_HTML_BYTES)
    parser.add_argument("--max-antibot-events", type=int, default=DEFAULT_MAX_ANTIBOT_EVENTS)
    parser.add_argument("--output-dir", default="rutracker_parser/output")
    parser.add_argument("--login-env", default="RUTRACKER_LOGIN")
    parser.add_argument("--password-env", default="RUTRACKER_PASSWORD")
    parser.add_argument("--user-agent", default=DEFAULT_USER_AGENT)
    parser.add_argument("--allow-path-prefix", action="append", default=[])
    parser.add_argument("--allow-endpoint", action="append", default=[])
    parser.add_argument("--skip-html", action="store_true")
    parser.add_argument("--scenario", default="full")
    return parser


def load_settings_from_args(args: argparse.Namespace) -> CrawlSettings:
    primary = ensure_absolute_base(args.mirror)
    mirrors: list[str] = [primary]
    if not args.no_fallback and args.fallback_mirror:
        mirrors.append(ensure_absolute_base(args.fallback_mirror))
    mirrors.extend(ensure_absolute_base(m) for m in args.extra_mirror)
    mirrors = unique_preserve_order(mirrors)

    login = os.getenv(args.login_env, "").strip()
    password = os.getenv(args.password_env, "").strip()

    mode_requested: Mode = args.mode
    mode_effective: Literal["guest", "auth"]
    if mode_requested == "guest":
        mode_effective = "guest"
    elif mode_requested == "auth":
        if not login or not password:
            raise ValueError(
                f"Mode 'auth' выбран, но env-переменные {args.login_env}/{args.password_env} не заданы"
            )
        mode_effective = "auth"
    else:
        mode_effective = "auth" if login and password else "guest"

    start_values = args.start_url or list(DEFAULT_START_PATHS)
    start_urls = [to_absolute_url(mirrors[0], value) for value in start_values]

    allow_path_prefixes = tuple(
        unique_preserve_order(args.allow_path_prefix or list(DEFAULT_ALLOWED_PATH_PREFIXES))
    )
    allow_endpoints = frozenset(unique_preserve_order(args.allow_endpoint or list(SAFE_ENDPOINTS)))

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
        max_pages=max(args.max_pages, 1),
        max_depth=max(args.max_depth, 0),
        max_html_bytes=max(args.max_html_bytes, 64_000),
        max_antibot_events=max(args.max_antibot_events, 1),
        save_html=not args.skip_html,
        allow_path_prefixes=allow_path_prefixes,
        allow_endpoints=allow_endpoints,
        scenario=args.scenario,
    )
