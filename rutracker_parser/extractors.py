from __future__ import annotations

import re
from urllib.parse import parse_qs, urljoin, urlparse

from bs4 import BeautifulSoup

from .constants import BLOCKED_QUERY_KEYS
from .utils import normalize_url, query_value_as_int, utc_now_iso

TOPIC_RE = re.compile(r"viewtopic\.php\?[^\"' ]*t=(\d+)")
FORUM_RE = re.compile(r"viewforum\.php\?[^\"' ]*f=(\d+)")
USER_RE = re.compile(r"profile\.php\?[^\"' ]*u=(\d+)")
SEED_RE = re.compile(r"Сиды\s*[:\-]?\s*(\d+)", re.IGNORECASE)
LEECH_RE = re.compile(r"Личи\s*[:\-]?\s*(\d+)", re.IGNORECASE)
DOWNLOADS_RE = re.compile(r"скачан\w*\s*[:\-]?\s*(\d+)", re.IGNORECASE)


def detect_page_type(url: str) -> str:
    parsed = urlparse(url)
    endpoint = parsed.path.rsplit("/", 1)[-1].lower()
    if endpoint == "index.php":
        return "index"
    if endpoint == "viewforum.php":
        return "forum"
    if endpoint == "viewtopic.php":
        return "topic"
    if endpoint == "tracker.php":
        return "tracker"
    if endpoint == "profile.php":
        return "profile"
    if endpoint == "login.php":
        return "login"
    return "generic"


def _clean_text(value: str) -> str:
    return " ".join(value.replace("\xa0", " ").split())


def _extract_numeric(raw: str) -> int | None:
    digits = "".join(ch for ch in raw if ch.isdigit())
    if not digits:
        return None
    try:
        return int(digits)
    except ValueError:
        return None


def _is_link_allowed(
    url: str,
    *,
    allowed_hosts: set[str],
    allow_path_prefixes: tuple[str, ...],
    allow_endpoints: frozenset[str],
) -> bool:
    parsed = urlparse(url)
    if parsed.scheme not in {"http", "https"}:
        return False
    if parsed.netloc not in allowed_hosts:
        return False
    if not any(parsed.path.startswith(prefix) for prefix in allow_path_prefixes):
        return False
    endpoint = parsed.path.rsplit("/", 1)[-1]
    if endpoint and endpoint not in allow_endpoints:
        return False
    query = parse_qs(parsed.query)
    if any(key in BLOCKED_QUERY_KEYS for key in query):
        return False
    return True


def _relation(url: str) -> str:
    if "viewforum.php" in url:
        return "forum"
    if "viewtopic.php" in url:
        return "topic"
    if "tracker.php" in url:
        return "tracker"
    if "profile.php" in url:
        return "profile"
    if "memberlist.php" in url:
        return "memberlist"
    if url.startswith("magnet:"):
        return "magnet"
    return "page"


def extract_page(
    *,
    url: str,
    html: str,
    allowed_hosts: set[str],
    allow_path_prefixes: tuple[str, ...],
    allow_endpoints: frozenset[str],
) -> dict:
    page_type = detect_page_type(url)
    soup = BeautifulSoup(html, "lxml")

    title_node = soup.find("title")
    title = _clean_text(title_node.get_text()) if title_node else ""

    breadcrumbs = [_clean_text(node.get_text()) for node in soup.select("td.nav a, .nav a")]

    links: list[dict] = []
    forums: list[dict] = []
    topics: list[dict] = []
    users: list[dict] = []
    torrents: list[dict] = []

    for anchor in soup.select("a[href]"):
        href = anchor.get("href", "").strip()
        if not href:
            continue
        absolute = normalize_url(urljoin(url, href))

        if absolute.startswith("magnet:"):
            torrents.append(
                {
                    "source_url": url,
                    "magnet": absolute,
                    "label": _clean_text(anchor.get_text(" ", strip=True)),
                    "ts": utc_now_iso(),
                }
            )
            continue

        if not _is_link_allowed(
            absolute,
            allowed_hosts=allowed_hosts,
            allow_path_prefixes=allow_path_prefixes,
            allow_endpoints=allow_endpoints,
        ):
            continue

        label = _clean_text(anchor.get_text(" ", strip=True))
        relation = _relation(absolute)
        links.append({"from": url, "to": absolute, "label": label, "relation": relation, "ts": utc_now_iso()})

        forum_id = query_value_as_int(absolute, "f")
        if forum_id is not None:
            forums.append({"forum_id": forum_id, "title": label, "url": absolute, "source_url": url, "ts": utc_now_iso()})

        topic_id = query_value_as_int(absolute, "t")
        if topic_id is not None and "viewtopic.php" in absolute:
            topics.append({"topic_id": topic_id, "title": label, "url": absolute, "source_url": url, "ts": utc_now_iso()})

        user_id = query_value_as_int(absolute, "u")
        if user_id is not None:
            users.append({"user_id": user_id, "name": label, "url": absolute, "source_url": url, "ts": utc_now_iso()})

        if "dl.php" in absolute and "t=" in absolute:
            torrents.append({"source_url": url, "torrent_url": absolute, "label": label, "ts": utc_now_iso()})

    topic_details: dict | None = None
    if page_type == "topic":
        topic_id = query_value_as_int(url, "t")
        topic_title = title

        magnet_urls = [item["magnet"] for item in torrents if "magnet" in item]
        torrent_urls = [item["torrent_url"] for item in torrents if "torrent_url" in item]

        body_text = _clean_text(soup.get_text(" ", strip=True))
        seeders = None
        leechers = None
        downloads = None

        seed_match = SEED_RE.search(body_text)
        if seed_match:
            seeders = _extract_numeric(seed_match.group(1))

        leech_match = LEECH_RE.search(body_text)
        if leech_match:
            leechers = _extract_numeric(leech_match.group(1))

        downloads_match = DOWNLOADS_RE.search(body_text)
        if downloads_match:
            downloads = _extract_numeric(downloads_match.group(1))

        size_bytes = None
        size_node = soup.select_one("#tor-size-humn")
        if size_node:
            size_bytes = _extract_numeric(size_node.get("title", "") or size_node.get_text(" ", strip=True))

        post_items: list[dict] = []
        for node in soup.select("div.post_body"):
            post_items.append(
                {
                    "post_id": node.get("id", "") or None,
                    "text": _clean_text(node.get_text(" ", strip=True)),
                }
            )

        topic_details = {
            "topic_id": topic_id,
            "title": topic_title,
            "url": url,
            "size_bytes": size_bytes,
            "seeders": seeders,
            "leechers": leechers,
            "downloads": downloads,
            "magnets": magnet_urls,
            "torrent_links": torrent_urls,
            "posts": post_items,
            "ts": utc_now_iso(),
        }

    forum_details: dict | None = None
    if page_type == "forum":
        forum_id = query_value_as_int(url, "f")
        forum_details = {
            "forum_id": forum_id,
            "title": title,
            "url": url,
            "topics_discovered": len(topics),
            "subforums_discovered": len(forums),
            "ts": utc_now_iso(),
        }

    tracker_details: dict | None = None
    if page_type == "tracker":
        tracker_details = {
            "url": url,
            "title": title,
            "topics_discovered": len(topics),
            "ts": utc_now_iso(),
        }

    forms: list[dict] = []
    for form in soup.select("form"):
        action = form.get("action", "").strip()
        method = form.get("method", "GET").upper()
        forms.append(
            {
                "action": normalize_url(urljoin(url, action)) if action else url,
                "method": method,
                "fields": [inp.get("name", "") for inp in form.select("input[name]") if inp.get("name")],
            }
        )

    return {
        "page_type": page_type,
        "title": title,
        "breadcrumbs": breadcrumbs,
        "links": links,
        "forums": forums,
        "topics": topics,
        "users": users,
        "torrents": torrents,
        "topic_details": topic_details,
        "forum_details": forum_details,
        "tracker_details": tracker_details,
        "forms": forms,
    }
