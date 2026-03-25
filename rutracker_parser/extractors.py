from __future__ import annotations

import re
from urllib.parse import parse_qs, urljoin, urlparse

from bs4 import BeautifulSoup, Tag

from .constants import BLOCKED_QUERY_KEYS
from .utils import normalize_url, query_value_as_int, utc_now_iso

SEED_RE = re.compile(r"Сиды\s*[:\-]?\s*(\d+)", re.IGNORECASE)
LEECH_RE = re.compile(r"Личи\s*[:\-]?\s*(\d+)", re.IGNORECASE)
DOWNLOADS_RE = re.compile(r"(?:\.torrent\s+)?скачан\w*\s*[:\-]?\s*([\d\s,]+)", re.IGNORECASE)
REGISTERED_RE = re.compile(r"Зарегистрирован\s*:\s*([^|\n\r]+)", re.IGNORECASE)
BTIH_RE = re.compile(r"btih:([A-Fa-f0-9]{32,64})", re.IGNORECASE)

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


def _normalize_attach_field_value(raw_value: str) -> str:
    value = _clean_text(raw_value)
    value = re.sub(r"\s*Скачать\s+по\s+magnet[-\s]ссылке\s*$", "", value, flags=re.IGNORECASE)
    return _clean_text(value).strip()


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


def _dedupe_prefer_rich(items: list[dict], key_fields: tuple[str, ...], prefer_fields: tuple[str, ...]) -> list[dict]:
    chosen: dict[tuple, dict] = {}
    order: list[tuple] = []
    for item in items:
        key = tuple(item.get(field) for field in key_fields)
        score = sum(1 for field in prefer_fields if item.get(field) not in (None, "", [], {}))
        if key not in chosen:
            chosen[key] = dict(item)
            chosen[key]["__score"] = score
            order.append(key)
            continue
        if score > chosen[key].get("__score", -1):
            chosen[key] = dict(item)
            chosen[key]["__score"] = score

    result: list[dict] = []
    for key in order:
        payload = dict(chosen[key])
        payload.pop("__score", None)
        result.append(payload)
    return result


def _first_topic_anchor(row: Tag) -> Tag | None:
    candidates = row.select("a[href*='viewtopic.php?t=']")
    if not candidates:
        return None

    def score(anchor: Tag) -> tuple[int, int, int]:
        href = anchor.get("href", "")
        text = _clean_text(anchor.get_text(" ", strip=True))
        has_view_newest = 1 if "view=newest" in href else 0
        is_title_class = 1 if "tt-text" in (anchor.get("class") or []) else 0
        return (len(text), is_title_class, -has_view_newest)

    best = max(candidates, key=score)
    return best


def _extract_forum_rows(url: str, soup: BeautifulSoup) -> tuple[list[dict], list[dict], list[dict]]:
    topics: list[dict] = []
    users: list[dict] = []
    torrents: list[dict] = []

    forum_id = query_value_as_int(url, "f")
    rows = soup.select("tr.hl-tr[data-topic_id], tr.hl-tr[id^='tr-']")
    for row in rows:
        topic_id = _extract_numeric(row.get("data-topic_id", ""))
        if topic_id is None:
            topic_id = _extract_numeric(row.get("id", ""))
        if topic_id is None:
            continue

        title_anchor = _first_topic_anchor(row)
        title = _clean_text(title_anchor.get_text(" ", strip=True)) if title_anchor else ""
        topic_url = normalize_url(urljoin(url, title_anchor["href"])) if title_anchor and title_anchor.get("href") else ""

        newest_anchor = row.select_one("a.t-is-unread[href*='viewtopic.php?t=']")
        newest_url = normalize_url(urljoin(url, newest_anchor.get("href", ""))) if newest_anchor else ""

        author_anchor = row.select_one("div.topicAuthor a[href*='profile.php']")
        author_name = _clean_text(author_anchor.get_text(" ", strip=True)) if author_anchor else ""
        author_url = (
            normalize_url(urljoin(url, author_anchor["href"])) if author_anchor and author_anchor.get("href") else ""
        )

        seed_node = row.select_one("span.seedmed b, span.seed b")
        leech_node = row.select_one("span.leechmed b, span.leech b")
        replies_node = row.select_one("td.vf-col-replies span[title*='Ответов']")
        downloads_node = row.select_one("td.vf-col-replies p[title*='скачан'] b")

        size_anchor = row.select_one("a.f-dl[href*='dl.php?t=']")
        size_text = _clean_text(size_anchor.get_text(" ", strip=True)) if size_anchor else ""
        size_bytes = _parse_size_to_bytes(size_text)
        torrent_url = normalize_url(urljoin(url, size_anchor["href"])) if size_anchor and size_anchor.get("href") else ""

        icon_node = row.select_one("td.vf-topic-icon-cell img.topic_icon")
        icon_src = icon_node.get("src", "") if icon_node else ""
        topic_icon_name = icon_src.rsplit("/", 1)[-1] if icon_src else ""

        status_icon = row.select_one("span.tor-icon")
        status_classes = status_icon.get("class", []) if status_icon else []

        last_post_time = ""
        last_post_user = ""
        last_post_user_url = ""
        last_post_link = ""
        last_post_id = None

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
                last_post_id = query_value_as_int(last_post_link, "p")

        topic_payload = {
            "topic_id": topic_id,
            "forum_id": forum_id,
            "title": title,
            "url": topic_url,
            "newest_url": newest_url,
            "source_url": url,
            "author": author_name,
            "author_url": author_url,
            "author_id": query_value_as_int(author_url, "u") if author_url else None,
            "seeders": _extract_numeric(seed_node.get_text(" ", strip=True)) if seed_node else None,
            "leechers": _extract_numeric(leech_node.get_text(" ", strip=True)) if leech_node else None,
            "replies": _extract_numeric(replies_node.get_text(" ", strip=True)) if replies_node else None,
            "downloads": _extract_numeric(downloads_node.get_text(" ", strip=True)) if downloads_node else None,
            "size_text": size_text,
            "size_bytes": size_bytes,
            "torrent_url": torrent_url,
            "is_unread": bool(newest_anchor),
            "is_sticky": "sticky" in topic_icon_name,
            "topic_icon": topic_icon_name,
            "topic_status_classes": status_classes,
            "last_post_at": last_post_time,
            "last_post_user": last_post_user,
            "last_post_user_url": last_post_user_url,
            "last_post_user_id": query_value_as_int(last_post_user_url, "u") if last_post_user_url else None,
            "last_post_url": last_post_link,
            "last_post_id": last_post_id,
            "ts": utc_now_iso(),
        }
        topics.append(topic_payload)

        if author_url:
            users.append(
                {
                    "user_id": query_value_as_int(author_url, "u"),
                    "name": author_name,
                    "url": author_url,
                    "source_url": url,
                    "kind": "topic_author",
                    "topic_id": topic_id,
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
                    "topic_id": topic_id,
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


def _extract_forum_pagination(url: str, soup: BeautifulSoup) -> dict:
    links: list[dict] = []
    for anchor in soup.select("a.pg[href]"):
        href = anchor.get("href", "")
        if not href:
            continue
        normalized = normalize_url(urljoin(url, href))
        label = _clean_text(anchor.get_text(" ", strip=True))
        start = query_value_as_int(normalized, "start")
        links.append({"url": normalized, "label": label, "start": start})

    numeric_pages = [_extract_numeric(item["label"] or "") for item in links]
    numeric_pages = [p for p in numeric_pages if p is not None]

    return {
        "links": links,
        "has_next": any(item["label"].startswith("След") for item in links),
        "max_page_number": max(numeric_pages) if numeric_pages else None,
    }


def _extract_topic_field_map(post_body: Tag) -> dict[str, str]:
    fields: dict[str, str] = {}
    for label in post_body.select("span.post-b"):
        key = _clean_text(label.get_text(" ", strip=True)).strip(":")
        if not key:
            continue

        values: list[str] = []
        for sibling in label.next_siblings:
            if isinstance(sibling, Tag) and sibling.name == "br":
                break
            if isinstance(sibling, Tag):
                text = _clean_text(sibling.get_text(" ", strip=True))
            else:
                text = _clean_text(str(sibling))
            if text and text != ":":
                values.append(text)

        value = _clean_text(" ".join(values)).strip(":")
        if value:
            fields[key] = value

    return fields


def _extract_posts(url: str, soup: BeautifulSoup) -> tuple[list[dict], list[dict]]:
    posts: list[dict] = []
    users: list[dict] = []

    topic_id = query_value_as_int(url, "t")
    for body in soup.select("div.post_body[id^='p-']"):
        post_id = _extract_numeric(body.get("id", ""))
        tbody = body.find_parent("tbody")
        poster = tbody.select_one("td.poster_info") if tbody else None

        nickname = ""
        joined = ""
        message_count = None
        flag_title = ""

        if poster:
            nick_node = poster.select_one("p.nick")
            nickname = _clean_text(nick_node.get_text(" ", strip=True)) if nick_node else ""

            joined_node = poster.select_one("p.joined")
            joined = _clean_text(joined_node.get_text(" ", strip=True)) if joined_node else ""

            posts_node = poster.select_one("p.posts")
            message_count = _extract_numeric(posts_node.get_text(" ", strip=True)) if posts_node else None

            flag_node = poster.select_one("img.poster-flag")
            flag_title = _clean_text(flag_node.get("title", "")) if flag_node else ""

        head = body.find_parent("td", class_="message")
        posted_at = ""
        edited_info = ""
        if head:
            link = head.select_one("p.post-time a.p-link")
            posted_at = _clean_text(link.get_text(" ", strip=True)) if link else ""
            edited_node = head.select_one("span.posted_since")
            edited_info = _clean_text(edited_node.get_text(" ", strip=True)) if edited_node else ""

        fields = _extract_topic_field_map(body)
        body_text = _clean_text(body.get_text(" ", strip=True))
        image_urls = []
        for img_var in body.select("var.postImg[title]"):
            title = _clean_text(img_var.get("title", ""))
            if title:
                image_urls.append(title)
        for img in body.select("img[src]"):
            src = normalize_url(urljoin(url, img.get("src", "")))
            if src:
                image_urls.append(src)

        links = [
            normalize_url(urljoin(url, a.get("href", "")))
            for a in body.select("a[href]")
            if a.get("href")
        ]

        ext_meta = {}
        if body.has_attr("data-ext_link_data"):
            raw = body.get("data-ext_link_data", "")
            for part in raw.replace("{", "").replace("}", "").replace('"', "").split(","):
                if ":" not in part:
                    continue
                key, value = part.split(":", 1)
                ext_meta[key.strip()] = _clean_text(value)

        posts.append(
            {
                "topic_id": topic_id,
                "post_id": post_id,
                "url": f"{url}#{post_id}" if post_id else url,
                "source_url": url,
                "poster": nickname,
                "poster_joined": joined,
                "poster_message_count": message_count,
                "poster_flag": flag_title,
                "posted_at": posted_at,
                "edited_info": edited_info,
                "text": body_text,
                "fields": fields,
                "image_urls": _dedupe([{"u": x} for x in image_urls], ("u",)),
                "links": _dedupe([{"u": x} for x in links], ("u",)),
                "ext_meta": ext_meta,
                "ts": utc_now_iso(),
            }
        )

        user_id = None
        if ext_meta.get("u"):
            user_id = _extract_numeric(ext_meta.get("u", ""))
        if user_id is not None or nickname:
            users.append(
                {
                    "user_id": user_id,
                    "name": nickname,
                    "url": normalize_url(urljoin(url, f"profile.php?mode=viewprofile&u={user_id}")) if user_id else "",
                    "source_url": url,
                    "kind": "post_author",
                    "post_id": post_id,
                    "topic_id": topic_id,
                    "joined": joined,
                    "message_count": message_count,
                    "flag": flag_title,
                    "ts": utc_now_iso(),
                }
            )

    cleaned_posts = []
    for post in posts:
        item = dict(post)
        item["image_urls"] = [u["u"] for u in post["image_urls"] if u.get("u")]
        item["links"] = [u["u"] for u in post["links"] if u.get("u")]
        cleaned_posts.append(item)
    return cleaned_posts, users


def _extract_category_tree(url: str, soup: BeautifulSoup) -> list[dict]:
    categories: list[dict] = []
    for category in soup.select("div.category[id^='c-']"):
        category_id = _extract_numeric(category.get("id", ""))
        cat_anchor = category.select_one("h3.cat_title a[href]")
        if not cat_anchor:
            continue

        category_title = _clean_text(cat_anchor.get_text(" ", strip=True))
        category_url = normalize_url(urljoin(url, cat_anchor.get("href", "")))

        forums_table = category.select_one("table.forums")
        if not forums_table:
            categories.append(
                {
                    "category_id": category_id,
                    "category_title": category_title,
                    "category_url": category_url,
                    "forum_id": None,
                    "forum_title": "",
                    "forum_url": "",
                    "subforums": [],
                    "ts": utc_now_iso(),
                }
            )
            continue

        for row in forums_table.select("tr[id^='f-']"):
            forum_id = _extract_numeric(row.get("id", ""))
            forum_anchor = row.select_one("h4.forumlink a[href]")
            forum_title = _clean_text(forum_anchor.get_text(" ", strip=True)) if forum_anchor else ""
            forum_url = normalize_url(urljoin(url, forum_anchor.get("href", ""))) if forum_anchor else ""

            subforums: list[dict] = []
            for sf_anchor in row.select("p.subforums a[href]"):
                sf_url = normalize_url(urljoin(url, sf_anchor.get("href", "")))
                subforums.append(
                    {
                        "forum_id": query_value_as_int(sf_url, "f"),
                        "title": _clean_text(sf_anchor.get_text(" ", strip=True)),
                        "url": sf_url,
                    }
                )

            categories.append(
                {
                    "category_id": category_id,
                    "category_title": category_title,
                    "category_url": category_url,
                    "forum_id": forum_id,
                    "forum_title": forum_title,
                    "forum_url": forum_url,
                    "subforums": subforums,
                    "ts": utc_now_iso(),
                }
            )
    return categories


def _extract_profile_details(url: str, soup: BeautifulSoup) -> dict:
    profile_details: dict[str, str] = {}
    for row in soup.select("tr"):
        cells = row.find_all("td", recursive=False)
        if len(cells) < 2:
            continue
        label = _clean_text(cells[0].get_text(" ", strip=True)).strip(":")
        value = _clean_text(cells[1].get_text(" ", strip=True))
        if not label or not value:
            continue
        if len(label) > 80:
            continue
        if label in profile_details:
            continue
        profile_details[label] = value

    user_id = query_value_as_int(url, "u")
    header = soup.select_one("h1, .maintitle")
    display_name = _clean_text(header.get_text(" ", strip=True)) if header else ""

    return {
        "user_id": user_id,
        "url": url,
        "display_name": display_name,
        "fields": profile_details,
        "ts": utc_now_iso(),
    }


def _extract_topic_meta(url: str, soup: BeautifulSoup, stats_text: str, magnets: list[str], torrent_urls: list[str]) -> dict:
    meta: dict[str, object] = {}

    share_node = soup.select_one("#soc-container")
    if share_node:
        meta["share_title"] = _clean_text(share_node.get("data-share_title", ""))
        share_url = share_node.get("data-share_url", "")
        meta["share_url"] = normalize_url(urljoin(url, share_url)) if share_url else ""

    attach_table = soup.select_one("table.attach")
    field_rows: dict[str, str] = {}
    if attach_table:
        for row in attach_table.select("tr.row1"):
            cells = row.find_all("td", recursive=False)
            if len(cells) < 2:
                continue
            key = _clean_text(cells[0].get_text(" ", strip=True)).strip(":")
            value = _normalize_attach_field_value(cells[1].get_text(" ", strip=True))
            if key and value:
                field_rows[key] = value

        dl_topic = attach_table.select_one("a.dl-topic[href*='dl.php?t=']")
        if dl_topic:
            dl_url = normalize_url(urljoin(url, dl_topic.get("href", "")))
            meta["torrent_download_url"] = dl_url

        tor_file_size = attach_table.select_one("a.dl-topic + p.small")
        if tor_file_size:
            meta["torrent_file_size"] = _clean_text(tor_file_size.get_text(" ", strip=True))

    meta["fields"] = field_rows

    hash_values: list[str] = []
    for magnet in magnets:
        match = BTIH_RE.search(magnet)
        if match:
            hash_values.append(match.group(1).upper())
    for anchor in soup.select("a.magnet-link[title]"):
        title_hash = _clean_text(anchor.get("title", ""))
        if title_hash:
            hash_values.append(title_hash.upper())

    meta["hashes"] = sorted(set(hash_values))
    meta["magnets"] = magnets
    meta["torrent_links"] = torrent_urls

    seed_match = SEED_RE.search(stats_text)
    leech_match = LEECH_RE.search(stats_text)
    downloads_match = DOWNLOADS_RE.search(stats_text)
    registered_match = REGISTERED_RE.search(stats_text)
    if seed_match:
        meta["seeders"] = _extract_numeric(seed_match.group(1))
    if leech_match:
        meta["leechers"] = _extract_numeric(leech_match.group(1))
    if downloads_match:
        meta["downloads"] = _extract_numeric(downloads_match.group(1))
    if registered_match:
        meta["registered"] = _clean_text(registered_match.group(1))

    topic_status_node = soup.select_one("#tor-status-resp")
    if topic_status_node:
        meta["topic_status_text"] = _clean_text(topic_status_node.get_text(" ", strip=True))

    meta["topic_id"] = query_value_as_int(url, "t")
    meta["url"] = url
    meta["ts"] = utc_now_iso()
    return meta


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
    breadcrumb_links = [
        {
            "title": _clean_text(node.get_text(" ", strip=True)),
            "url": normalize_url(urljoin(url, node.get("href", ""))),
        }
        for node in soup.select("td.nav a[href], .nav a[href], .t-breadcrumb-top a[href]")
        if node.get("href")
    ]

    links: list[dict] = []
    forums: list[dict] = []
    topics: list[dict] = []
    users: list[dict] = []
    torrents: list[dict] = []
    categories: list[dict] = []
    posts: list[dict] = []

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
                    "kind": "profile_link",
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

    pagination = _extract_forum_pagination(url, soup) if page_type in {"forum", "tracker"} else {"links": [], "has_next": False, "max_page_number": None}

    topic_details: dict | None = None
    topic_meta: dict | None = None
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

        parsed_posts, post_users = _extract_posts(url, soup)
        posts.extend(parsed_posts)
        users.extend(post_users)

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
            "posts": parsed_posts,
            "ts": utc_now_iso(),
        }
        topic_meta = _extract_topic_meta(url, soup, stats_text, magnet_urls, torrent_urls)

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
        maintitle_anchor = soup.select_one("h1.maintitle a[href]")
        forum_details = {
            "forum_id": forum_id,
            "title": title,
            "maintitle": _clean_text(maintitle_anchor.get_text(" ", strip=True)) if maintitle_anchor else "",
            "maintitle_url": normalize_url(urljoin(url, maintitle_anchor.get("href", ""))) if maintitle_anchor else "",
            "description": _clean_text((soup.select_one(".forum-desc-in-title") or Tag(name="div")).get_text(" ", strip=True)) if soup.select_one(".forum-desc-in-title") else "",
            "url": url,
            "topics_discovered": len(topics),
            "subforums_discovered": len(forums),
            "pagination": pagination,
            "ts": utc_now_iso(),
        }

    tracker_details: dict | None = None
    if page_type == "tracker":
        tracker_details = {
            "url": url,
            "title": title,
            "topics_discovered": len(topics),
            "pagination": pagination,
            "ts": utc_now_iso(),
        }

    if page_type == "index":
        categories = _extract_category_tree(url, soup)

    profile_details: dict | None = None
    if page_type == "profile":
        profile_details = _extract_profile_details(url, soup)
        if profile_details.get("user_id") is not None:
            users.append(
                {
                    "user_id": profile_details.get("user_id"),
                    "name": profile_details.get("display_name", ""),
                    "url": profile_details.get("url", ""),
                    "source_url": url,
                    "kind": "profile_page",
                    "fields": profile_details.get("fields", {}),
                    "ts": utc_now_iso(),
                }
            )

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

    page_signals = {
        "contains_antiphishing_banner": "Похоже, вас пытаются обмануть" in html,
        "contains_login_form": bool(soup.select_one("form[action*='login.php']")),
        "contains_logout_action": "logout.php" in html,
        "has_magnet_links": bool(soup.select_one("a.magnet-link")),
        "has_torrent_download_link": bool(soup.select_one("a[href*='dl.php?t=']")),
    }

    forums = _dedupe(forums, ("forum_id", "url"))
    topics = _dedupe_prefer_rich(
        topics,
        ("topic_id", "url"),
        (
            "seeders",
            "leechers",
            "downloads",
            "size_text",
            "size_bytes",
            "torrent_url",
            "author",
            "title",
            "last_post_id",
        ),
    )
    users = _dedupe_prefer_rich(
        users,
        ("user_id", "url", "kind"),
        ("name", "joined", "message_count", "post_id", "topic_id", "fields"),
    )
    torrents = _dedupe(torrents, ("topic_id", "torrent_url", "magnet", "source_url"))
    links = _dedupe(links, ("from", "to"))
    categories = _dedupe(categories, ("category_id", "forum_id", "forum_url"))
    posts = _dedupe_prefer_rich(posts, ("topic_id", "post_id"), ("fields", "image_urls", "links", "poster", "text"))

    return {
        "page_type": page_type,
        "title": title,
        "breadcrumbs": breadcrumbs,
        "breadcrumb_links": breadcrumb_links,
        "links": links,
        "forums": forums,
        "topics": topics,
        "users": users,
        "torrents": torrents,
        "categories": categories,
        "posts": posts,
        "topic_details": topic_details,
        "topic_meta": topic_meta,
        "forum_details": forum_details,
        "tracker_details": tracker_details,
        "profile_details": profile_details,
        "pagination": pagination,
        "page_signals": page_signals,
        "forms": forms,
    }
