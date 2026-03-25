from __future__ import annotations

import logging
from collections import Counter, deque
from dataclasses import dataclass
from urllib.parse import urlparse

from .client import AntiBotDetectedError, RutrackerHttpClient
from .extractors import extract_page
from .mode_matrix import feature_matrix, mode_summary
from .settings import CrawlSettings
from .storage import CrawlStorage
from .utils import normalize_url, query_value_as_int, utc_now_iso

logger = logging.getLogger(__name__)


@dataclass(slots=True, frozen=True)
class CrawlTask:
    url: str
    depth: int
    parent_url: str | None


class CrawlEngine:
    def __init__(self, settings: CrawlSettings) -> None:
        self.settings = settings
        self.client = RutrackerHttpClient(settings)
        self.storage = CrawlStorage(settings.output_root)
        self.allowed_hosts = {urlparse(mirror).netloc for mirror in settings.mirrors}

        self.queue: deque[CrawlTask] = deque()
        self.pending: set[str] = set()
        self.visited: set[str] = set()

        self.entity_counts: Counter[str] = Counter()
        self.status_counts: Counter[int] = Counter()
        self.error_count = 0
        self.logged_in = False
        self.interrupted = False
        self.is_audiobooks_mode = self.settings.scenario == "audiobooks"
        self.scope_category_ids: set[int] = set()
        self.scope_forum_ids: set[int] = set()
        self.scope_topic_ids: set[int] = set()
        if self.is_audiobooks_mode:
            for start_url in self.settings.start_urls:
                category_id = query_value_as_int(start_url, "c")
                if category_id is not None:
                    self.scope_category_ids.add(category_id)
                forum_id = query_value_as_int(start_url, "f")
                if forum_id is not None:
                    self.scope_forum_ids.add(forum_id)

    def _enqueue(self, url: str, depth: int, parent_url: str | None) -> None:
        normalized = normalize_url(url)
        if depth > self.settings.max_depth:
            return
        if not self._is_url_in_scope(normalized, parent_url):
            return
        if normalized in self.visited or normalized in self.pending:
            return
        self.pending.add(normalized)
        self.queue.append(CrawlTask(url=normalized, depth=depth, parent_url=parent_url))

    @staticmethod
    def _endpoint(url: str) -> str:
        return urlparse(url).path.rsplit("/", 1)[-1].lower()

    def _is_url_in_scope(self, url: str, parent_url: str | None) -> bool:
        if not self.is_audiobooks_mode:
            return True

        endpoint = self._endpoint(url)
        category_id = query_value_as_int(url, "c")
        forum_id = query_value_as_int(url, "f")
        topic_id = query_value_as_int(url, "t")
        parent_forum_id = query_value_as_int(parent_url or "", "f")
        parent_topic_id = query_value_as_int(parent_url or "", "t")

        if endpoint == "index.php":
            return category_id is not None and category_id in self.scope_category_ids
        if endpoint == "viewforum.php":
            return forum_id is not None and forum_id in self.scope_forum_ids
        if endpoint == "viewtopic.php":
            if topic_id is None:
                return False
            return topic_id in self.scope_topic_ids or parent_forum_id in self.scope_forum_ids
        if endpoint == "dl.php":
            if topic_id is None:
                return False
            return topic_id in self.scope_topic_ids or parent_topic_id in self.scope_topic_ids
        return False

    def _update_scope(self, extracted: dict, source_url: str) -> None:
        if not self.is_audiobooks_mode:
            return

        source_category_id = query_value_as_int(source_url, "c")
        source_forum_id = query_value_as_int(source_url, "f")
        source_topic_id = query_value_as_int(source_url, "t")
        if source_category_id is not None and source_category_id in self.scope_category_ids:
            if source_forum_id is not None:
                self.scope_forum_ids.add(source_forum_id)
        if source_forum_id is not None and source_forum_id in self.scope_forum_ids and source_topic_id is not None:
            self.scope_topic_ids.add(source_topic_id)

        for row in extracted.get("categories", []):
            category_id = row.get("category_id")
            if not isinstance(category_id, int) or category_id not in self.scope_category_ids:
                continue

            forum_id = row.get("forum_id")
            if isinstance(forum_id, int):
                self.scope_forum_ids.add(forum_id)
            for subforum in row.get("subforums", []):
                subforum_id = subforum.get("forum_id")
                if isinstance(subforum_id, int):
                    self.scope_forum_ids.add(subforum_id)

        for forum in extracted.get("forums", []):
            forum_id = forum.get("forum_id")
            if not isinstance(forum_id, int):
                continue
            forum_source = forum.get("source_url", "")
            forum_source_forum_id = query_value_as_int(forum_source, "f")
            if forum_source_forum_id in self.scope_forum_ids:
                self.scope_forum_ids.add(forum_id)

        for topic in extracted.get("topics", []):
            topic_id = topic.get("topic_id")
            if not isinstance(topic_id, int):
                continue
            topic_source = topic.get("source_url", "")
            topic_source_forum_id = query_value_as_int(topic_source, "f")
            if topic_source_forum_id in self.scope_forum_ids:
                self.scope_topic_ids.add(topic_id)

        topic_details = extracted.get("topic_details") or {}
        topic_details_id = topic_details.get("topic_id")
        if isinstance(topic_details_id, int):
            self.scope_topic_ids.add(topic_details_id)

        topic_meta = extracted.get("topic_meta") or {}
        topic_meta_id = topic_meta.get("topic_id")
        if isinstance(topic_meta_id, int):
            self.scope_topic_ids.add(topic_meta_id)

    def _apply_scope_filters(self, extracted: dict, source_url: str) -> dict:
        if not self.is_audiobooks_mode:
            return extracted

        scoped = dict(extracted)
        source_forum_id = query_value_as_int(source_url, "f")

        scoped["links"] = [
            link for link in extracted.get("links", []) if self._is_url_in_scope(link.get("to", ""), source_url)
        ]
        scoped["categories"] = [
            row for row in extracted.get("categories", []) if row.get("category_id") in self.scope_category_ids
        ]
        scoped["forums"] = [row for row in extracted.get("forums", []) if row.get("forum_id") in self.scope_forum_ids]
        scoped["topics"] = [
            row
            for row in extracted.get("topics", [])
            if row.get("topic_id") in self.scope_topic_ids or source_forum_id in self.scope_forum_ids
        ]
        scoped["users"] = [
            row
            for row in extracted.get("users", [])
            if row.get("topic_id") in self.scope_topic_ids or source_forum_id in self.scope_forum_ids
        ]
        scoped["torrents"] = [
            row
            for row in extracted.get("torrents", [])
            if row.get("topic_id") in self.scope_topic_ids or source_forum_id in self.scope_forum_ids
        ]

        topic_details = extracted.get("topic_details")
        if topic_details and topic_details.get("topic_id") not in self.scope_topic_ids:
            scoped["topic_details"] = None

        topic_meta = extracted.get("topic_meta")
        if topic_meta and topic_meta.get("topic_id") not in self.scope_topic_ids:
            scoped["topic_meta"] = None

        scoped["posts"] = [
            row for row in extracted.get("posts", []) if row.get("topic_id") in self.scope_topic_ids
        ]
        return scoped

    def _emit_entities(self, extracted: dict) -> None:
        forums = extracted["forums"]
        topics = extracted["topics"]
        users = extracted["users"]
        torrents = extracted["torrents"]
        categories = extracted.get("categories", [])
        posts = extracted.get("posts", [])

        topic_details = extracted.get("topic_details")
        if topic_details:
            details_row = dict(topic_details)
            details_row["kind"] = "topic_details"
            topics.append(details_row)

        topic_meta = extracted.get("topic_meta")
        if topic_meta:
            self.storage.write_topic_meta(topic_meta)
            self.entity_counts["topic_meta"] += 1

        forum_details = extracted.get("forum_details")
        if forum_details:
            details_row = dict(forum_details)
            details_row["kind"] = "forum_details"
            forums.append(details_row)

        tracker_details = extracted.get("tracker_details")
        if tracker_details:
            details_row = dict(tracker_details)
            details_row["kind"] = "tracker_details"
            topics.append(details_row)

        self.storage.write_forums(forums)
        self.storage.write_topics(topics)
        self.storage.write_users(users)
        self.storage.write_torrents(torrents)
        self.storage.write_categories(categories)
        self.storage.write_posts(posts)

        profile_details = extracted.get("profile_details")
        if profile_details:
            self.storage.write_profile(profile_details)
            self.entity_counts["profiles"] += 1

        self.entity_counts["forums"] += len(forums)
        self.entity_counts["topics"] += len(topics)
        self.entity_counts["users"] += len(users)
        self.entity_counts["torrents"] += len(torrents)
        self.entity_counts["categories"] += len(categories)
        self.entity_counts["posts"] += len(posts)

    def run(self) -> dict:
        try:
            if self.settings.mode_effective == "auth":
                try:
                    self.logged_in = self.client.login()
                except Exception as error:
                    if self.settings.mode_requested == "auth":
                        raise RuntimeError(f"Не удалось выполнить auth login: {error}") from error
                    logger.warning("Auth недоступен, продолжаю как guest: %s", error)
                    self.logged_in = False
                    self.settings.mode_effective = "guest"

            for item in self.settings.start_urls:
                self._enqueue(item, depth=0, parent_url=None)

            try:
                while self.queue and len(self.visited) < self.settings.max_pages:
                    task = self.queue.popleft()
                    self.pending.discard(task.url)

                    try:
                        fetched = self.client.request("GET", task.url, referer=task.parent_url)
                        self.status_counts[fetched.status_code] += 1
                        self.visited.add(task.url)

                        extracted = extract_page(
                            url=fetched.final_url,
                            html=fetched.text,
                            allowed_hosts=self.allowed_hosts,
                            allow_path_prefixes=self.settings.allow_path_prefixes,
                            allow_endpoints=self.settings.allow_endpoints,
                        )
                        self._update_scope(extracted, fetched.final_url)
                        scoped_extracted = self._apply_scope_filters(extracted, fetched.final_url)

                        html_ref = None
                        if self.settings.save_html:
                            html_ref = self.storage.save_html(fetched.final_url, fetched.text)

                        page_record = {
                            "url": fetched.final_url,
                            "requested_url": fetched.requested_url,
                            "depth": task.depth,
                            "parent_url": task.parent_url,
                            "status": fetched.status_code,
                            "mirror": fetched.mirror,
                            "elapsed_sec": round(fetched.elapsed_sec, 3),
                            "truncated": fetched.truncated,
                            "html_ref": html_ref,
                            "title": scoped_extracted.get("title", ""),
                            "page_type": scoped_extracted.get("page_type", "generic"),
                            "breadcrumbs": scoped_extracted.get("breadcrumbs", []),
                            "breadcrumb_links": scoped_extracted.get("breadcrumb_links", []),
                            "pagination": scoped_extracted.get("pagination", {}),
                            "signals": scoped_extracted.get("page_signals", {}),
                            "forms": scoped_extracted.get("forms", []),
                            "ts": utc_now_iso(),
                        }
                        self.storage.write_page(page_record)
                        self.storage.write_edges(scoped_extracted["links"])
                        self.entity_counts["edges"] += len(scoped_extracted["links"])

                        self._emit_entities(scoped_extracted)

                        for link in scoped_extracted["links"]:
                            if link.get("crawlable"):
                                self._enqueue(link["to"], depth=task.depth + 1, parent_url=fetched.final_url)

                    except AntiBotDetectedError as error:
                        self.error_count += 1
                        self.storage.write_error(
                            {
                                "url": task.url,
                                "error": str(error),
                                "kind": "antibot",
                                "depth": task.depth,
                                "ts": utc_now_iso(),
                            }
                        )
                        break
                    except Exception as error:
                        self.error_count += 1
                        self.storage.write_error(
                            {
                                "url": task.url,
                                "error": str(error),
                                "kind": "request",
                                "depth": task.depth,
                                "ts": utc_now_iso(),
                            }
                        )
                        continue
            except KeyboardInterrupt:
                self.interrupted = True
                self.storage.write_error(
                    {
                        "url": "",
                        "error": "Run interrupted by user",
                        "kind": "interrupted",
                        "depth": -1,
                        "ts": utc_now_iso(),
                    }
                )

            summary = {
                "requested_mode": self.settings.mode_requested,
                "effective_mode": self.settings.mode_effective,
                "logged_in": self.logged_in,
                "mirrors": self.settings.mirrors,
                "visited_pages": len(self.visited),
                "pending_pages": len(self.queue),
                "status_counts": dict(sorted(self.status_counts.items())),
                "entity_counts": dict(self.entity_counts),
                "errors": self.error_count,
                "interrupted": self.interrupted,
                "run_dir": str(self.storage.run_dir),
                "ts": utc_now_iso(),
            }

            self.storage.write_meta("summary.json", summary)
            self.storage.write_meta(
                "mode_capabilities.json",
                {
                    "matrix": feature_matrix(),
                    "guest": mode_summary("guest"),
                    "auth": mode_summary("auth"),
                    "effective": mode_summary(self.settings.mode_effective),
                },
            )
            return summary
        finally:
            self.storage.close()
