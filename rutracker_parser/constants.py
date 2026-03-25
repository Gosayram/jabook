from __future__ import annotations

from typing import Final

DEFAULT_PRIMARY_MIRROR: Final[str] = "https://rutracker.net"
DEFAULT_FALLBACK_MIRROR: Final[str] = "https://rutracker.me"

DEFAULT_USER_AGENT: Final[str] = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
)
DEFAULT_ACCEPT_LANGUAGE: Final[str] = "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7"

DEFAULT_HEADERS: Final[dict[str, str]] = {
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    "Accept-Language": DEFAULT_ACCEPT_LANGUAGE,
    "Accept-Encoding": "gzip, deflate",
    "Connection": "keep-alive",
    "Upgrade-Insecure-Requests": "1",
    "DNT": "1",
}

DEFAULT_START_PATHS: Final[tuple[str, ...]] = (
    "/forum/index.php",
    "/forum/tracker.php",
)

DEFAULT_ALLOWED_PATH_PREFIXES: Final[tuple[str, ...]] = (
    "/forum",
)

SAFE_ENDPOINTS: Final[frozenset[str]] = frozenset(
    {
        "index.php",
        "viewforum.php",
        "viewtopic.php",
        "tracker.php",
        "profile.php",
        "memberlist.php",
        "faq.php",
        "search.php",
        "login.php",
        "groupcp.php",
        "bookmarks.php",
        "privmsg.php",
        "watching.php",
    }
)

BLOCKED_QUERY_KEYS: Final[frozenset[str]] = frozenset(
    {
        "logout",
        "delete",
        "confirm",
        "mark",
        "post",
        "vote",
        "attach",
        "d",
        "action",
    }
)

DEFAULT_CONNECT_TIMEOUT_SEC: Final[float] = 12.0
DEFAULT_READ_TIMEOUT_SEC: Final[float] = 35.0
DEFAULT_REQUEST_INTERVAL_SEC: Final[float] = 3.2
DEFAULT_JITTER_SEC: Final[float] = 1.1
DEFAULT_MAX_RETRIES: Final[int] = 4
DEFAULT_BACKOFF_BASE_SEC: Final[float] = 2.0
DEFAULT_BACKOFF_MAX_SEC: Final[float] = 180.0
DEFAULT_COOLDOWN_SEC: Final[float] = 160.0
DEFAULT_MAX_PAGES: Final[int] = 3000
DEFAULT_MAX_DEPTH: Final[int] = 10
DEFAULT_MAX_HTML_BYTES: Final[int] = 6_000_000
DEFAULT_MAX_ANTIBOT_EVENTS: Final[int] = 3

RETRY_STATUSES: Final[frozenset[int]] = frozenset({408, 425, 429, 500, 502, 503, 504, 520, 521, 522, 524})

CF_MARKERS: Final[tuple[str, ...]] = (
    "cloudflare",
    "cf-ray",
    "attention required",
    "checking your browser",
    "captcha",
    "challenge-platform",
)

FORUM_LOGIN_PATH: Final[str] = "/forum/login.php"

FEATURE_SUPPORT: Final[dict[str, str]] = {
    "forum_tree": "both",
    "forum_pagination": "both",
    "topic_listing": "both",
    "topic_content": "both",
    "topic_magnet_links": "both",
    "public_user_links": "both",
    "site_graph_edges": "both",
    "torrent_download_links": "auth",
    "user_bookmarks": "auth",
    "watched_topics": "auth",
    "private_profile_fields": "auth",
    "pm_sections": "auth",
}
