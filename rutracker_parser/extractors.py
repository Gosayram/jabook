from __future__ import annotations

import re
from urllib.parse import parse_qs, urljoin, urlparse

from bs4 import BeautifulSoup

from .constants import BLOCKED_QUERY_KEYS
from .utils import normalize_url, query_value_as_int, utc_now_iso

SEED_RE = re.compile(r"Сиды\s*[:\-]?\s*(\d+)", re.IGNORECASE)
LEECH_RE = re.compile(r"Личи\s*[:\-]?\s*(\d+)", re.IGNORECASE)
DOWNLOADS_RE = re.compile(r"(?:\.torrent\s+)?скачан\w*\s*[:\-]?\s*([\d\s,]+)", re.IGNORECASE)
REGISTERED_RE = re.compile(r"Зарегистрирован\s*:\s*([^|\n\r]+)", re.IGNORECASE)

SIZE_MULTIPLIER = {
    "B": 1,
    "KB": 1024,
    "MB": 1024**2,
    "GB": 1024**3,
    "TB": 1024**4,
}


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
    if endpoint == "bookmarks.php":
        return "bookmarks"
    if endpoint == "watching.php":
        return "watching"
    if endpoint == "privmsg.php":
        return "privmsg"
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


def _parse_size_to_bytes(text: str) -> int | None:
    cleaned = _clean_text(text).upper().replace(",", ".")
    match = re.search(r"(\d+(?:\.\d+)?)\s*(B|KB|MB|GB|TB)", cleaned)
    if not match:
        return None
    try:
        value = float(match.group(1))
    except ValueError:
        return None
    unit = match.group(2)
    multiplier = SIZE_MULTIPLIER.get(unit)
    if not multiplier:
        return None
    return int(value * multiplier)


def _is_internal_page(url: str, allowed_hosts: set[str]) -> bool:
    parsed = urlparse(url)
    return parsed.scheme in {"http", "https"} and parsed.netloc in allowed_hosts


def _is_crawl_allowed(
    url: str,
    *,
    allow_path_prefixes: tuple[str, ...],
    allow_endpoints: frozenset[str],
) -> bool:
    parsed = urlparse(url)
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
    if "bookmarks.php" in url:
        return "bookmarks"
    if "watching.php" in url:
        return "watching"
    if "privmsg.php" in url:
        return "privmsg"
    if "memberlist.php" in url:
        return "memberlist"
    return "page"


def _dedupe(items: list[dict], key_fields: tuple[str, ...]) -> list[dict]:
    seen: set[tuple] = set()
    unique: list[dict] = []
    for item in items:
        key = tuple(item.get(field) for field in key_fields)
        if key in seen:
            continue
        seen.add(key)
        unique.append(item)
    return unique


def _extract_forum_rows(url: str, soup: BeautifulSoup) -> tuple[list[dict], list[dict], list[dict]]:
    topics: list[dict] = []
    users: list[dict] = []
    torrents: list[dict] = []

    rows = soup.select("tr.hl-tr[data-topic_id], tr.hl-tr[id^='tr-']")
    for row in rows:
        topic_id = _extract_numeric(row.get("data-topic_id", ""))
        if topic_id is None:
            topic_id = _extract_numeric(row.get("id", ""))
        if topic_id is None:
            continue

        title_anchor = row.select_one("a.torTopic.bold.tt-text, a[id^='tt-'], a[href*='viewtopic.php?t=']")
        title = _clean_text(title_anchor.get_text(" ", strip=True)) if title_anchor else ""
        topic_url = normalize_url(urljoin(url, title_anchor["href"])) if title_anchor and title_anchor.get("href") else ""

        author_anchor = row.select_one("div.topicAuthor a[href*='profile.php']")
        author_name = _clean_text(author_anchor.get_text(" ", strip=True)) if author_anchor else ""
        author_url = (
            normalize_url(urljoin(url, author_anchor["href"]))
            if author_anchor and author_anchor.get("href")
            else ""
        )

        seed_node = row.select_one("span.seedmed b, span.seed b")
        leech_node = row.select_one("span.leechmed b, span.leech b")
        replies_node = row.select_one("td.vf-col-replies span[title*='Ответов']")
        downloads_node = row.select_one("td.vf-col-replies p[title*='скачан'] b")

        size_anchor = row.select_one("a.f-dl[href*='dl.php?t=']")
        size_text = _clean_text(size_anchor.get_text(" ", strip=True)) if size_anchor else ""
        size_bytes = _parse_size_to_bytes(size_text)
        torrent_url = normalize_url(urljoin(url, size_anchor["href"])) if size_anchor and size_anchor.get("href") else ""

        last_post_time = ""
        last_post_user = ""
        last_post_user_url = ""
        last_post_link = ""

        last_post_col = row.select_one("td.vf-col-last-post")
        if last_post_col:
            first_line = last_post_col.select_one("p")
            if first_line:
                last_post_time = _clean_text(first_line.get_text(" ", strip=True))

            user_anchor = last_post_col.select_one("a[href*='profile.php']")
            if user_anchor and user_anchor.get("href"):
                last_post_user = _clean_text(user_anchor.get_text(" ", strip=True))
                last_post_user_url = normalize_url(urljoin(url, user_anchor["href"]))

            post_anchor = last_post_col.select_one("a[href*='viewtopic.php?p=']")
            if post_anchor and post_anchor.get("href"):
                last_post_link = normalize_url(urljoin(url, post_anchor["href"]))

        topics.append(
            {
                "topic_id": topic_id,
                "title": title,
                "url": topic_url,
                "source_url": url,
                "author": author_name,
                "author_url": author_url,
                "seeders": _extract_numeric(seed_node.get_text(" ", strip=True)) if seed_node else None,
                "leechers": _extract_numeric(leech_node.get_text(" ", strip=True)) if leech_node else None,
                "replies": _extract_numeric(replies_node.get_text(" ", strip=True)) if replies_node else None,
                "downloads": _extract_numeric(downloads_node.get_text(" ", strip=True)) if downloads_node else None,
                "size_text": size_text,
                "size_bytes": size_bytes,
                "torrent_url": torrent_url,
                "last_post_at": last_post_time,
                "last_post_user": last_post_user,
                "last_post_user_url": last_post_user_url,
                "last_post_url": last_post_link,
                "ts": utc_now_iso(),
            }
        )

        if author_url:
            users.append(
                {
                    "user_id": query_value_as_int(author_url, "u"),
                    "name": author_name,
                    "url": author_url,
                    "source_url": url,
                    "kind": "topic_author",
                    "ts": utc_now_iso(),
                }
            )

        if last_post_user_url:
            users.append(
                {
                    "user_id": query_value_as_int(last_post_user_url, "u"),
                    "name": last_post_user,
                    "url": last_post_user_url,
                    "source_url": url,
                    "kind": "last_poster",
                    "ts": utc_now_iso(),
                }
            )

        if torrent_url:
            torrents.append(
                {
                    "source_url": url,
                    "topic_id": topic_id,
                    "torrent_url": torrent_url,
                    "size_text": size_text,
                    "size_bytes": size_bytes,
                    "ts": utc_now_iso(),
                }
            )

    return topics, users, torrents


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

    breadcrumbs = [_clean_text(node.get_text()) for node in soup.select("td.nav a, .nav a, .t-breadcrumb-top a")]

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

        if not _is_internal_page(absolute, allowed_hosts):
            continue

        crawlable = _is_crawl_allowed(
            absolute,
            allow_path_prefixes=allow_path_prefixes,
            allow_endpoints=allow_endpoints,
        )

        label = _clean_text(anchor.get_text(" ", strip=True))
        relation = _relation(absolute)
        links.append(
            {
                "from": url,
                "to": absolute,
                "label": label,
                "relation": relation,
                "crawlable": crawlable,
                "ts": utc_now_iso(),
            }
        )

        forum_id = query_value_as_int(absolute, "f")
        if forum_id is not None and "viewforum.php" in absolute:
            forums.append(
                {
                    "forum_id": forum_id,
                    "title": label,
                    "url": absolute,
                    "source_url": url,
                    "ts": utc_now_iso(),
                }
            )

        topic_id = query_value_as_int(absolute, "t")
        if topic_id is not None and "viewtopic.php" in absolute:
            topics.append(
                {
                    "topic_id": topic_id,
                    "title": label,
                    "url": absolute,
                    "source_url": url,
                    "ts": utc_now_iso(),
                }
            )

        user_id = query_value_as_int(absolute, "u")
        if user_id is not None and "profile.php" in absolute:
            users.append(
                {
                    "user_id": user_id,
                    "name": label,
                    "url": absolute,
                    "source_url": url,
                    "ts": utc_now_iso(),
                }
            )

        if "dl.php" in absolute and "t=" in absolute:
            torrents.append(
                {
                    "source_url": url,
                    "torrent_url": absolute,
                    "label": label,
                    "topic_id": query_value_as_int(absolute, "t"),
                    "ts": utc_now_iso(),
                }
            )

    forum_topics, forum_users, forum_torrents = _extract_forum_rows(url, soup)
    topics.extend(forum_topics)
    users.extend(forum_users)
    torrents.extend(forum_torrents)

    topic_details: dict | None = None
    if page_type == "topic":
        topic_id = query_value_as_int(url, "t")

        stats_node = soup.select_one("#t-tor-stats")
        stats_text = _clean_text(stats_node.get_text(" ", strip=True)) if stats_node else _clean_text(
            soup.get_text(" ", strip=True)
        )

        size_bytes = None
        size_human = ""
        size_node = soup.select_one("#tor-size-humn")
        if size_node:
            size_human = _clean_text(size_node.get_text(" ", strip=True))
            size_bytes = _extract_numeric(size_node.get("title", ""))
        if size_bytes is None:
            size_bytes = _parse_size_to_bytes(stats_text)

        seeders = None
        leechers = None
        downloads = None

        seed_node = soup.select_one("#t-tor-stats span.seed b, span.seed b")
        if seed_node:
            seeders = _extract_numeric(seed_node.get_text(" ", strip=True))
        else:
            seed_match = SEED_RE.search(stats_text)
            if seed_match:
                seeders = _extract_numeric(seed_match.group(1))

        leech_node = soup.select_one("#t-tor-stats span.leech b, span.leech b")
        if leech_node:
            leechers = _extract_numeric(leech_node.get_text(" ", strip=True))
        else:
            leech_match = LEECH_RE.search(stats_text)
            if leech_match:
                leechers = _extract_numeric(leech_match.group(1))

        downloads_match = DOWNLOADS_RE.search(stats_text)
        if downloads_match:
            downloads = _extract_numeric(downloads_match.group(1))

        registered_value = None
        registered_match = REGISTERED_RE.search(stats_text)
        if registered_match:
            registered_value = _clean_text(registered_match.group(1))

        magnet_urls = [
            normalize_url(urljoin(url, a.get("href", "")))
            for a in soup.select("a.magnet-link[href], a[href^='magnet:?xt=urn:btih:']")
            if a.get("href")
        ]
        torrent_urls = [
            normalize_url(urljoin(url, a.get("href", "")))
            for a in soup.select("a[href*='dl.php?t=']")
            if a.get("href")
        ]

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
            "title": title,
            "url": url,
            "size_bytes": size_bytes,
            "size_human": size_human,
            "seeders": seeders,
            "leechers": leechers,
            "downloads": downloads,
            "registered": registered_value,
            "magnets": magnet_urls,
            "torrent_links": torrent_urls,
            "posts": post_items,
            "ts": utc_now_iso(),
        }

        for magnet in magnet_urls:
            torrents.append({"source_url": url, "topic_id": topic_id, "magnet": magnet, "ts": utc_now_iso()})
        for torrent_url in torrent_urls:
            torrents.append(
                {
                    "source_url": url,
                    "topic_id": topic_id,
                    "torrent_url": torrent_url,
                    "ts": utc_now_iso(),
                }
            )

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

    forums = _dedupe(forums, ("forum_id", "url"))
    topics = _dedupe(topics, ("topic_id", "url"))
    users = _dedupe(users, ("user_id", "url", "kind"))
    torrents = _dedupe(torrents, ("topic_id", "torrent_url", "magnet", "source_url"))
    links = _dedupe(links, ("from", "to"))

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
