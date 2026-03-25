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
from .utils import normalize_url, utc_now_iso

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

    def _enqueue(self, url: str, depth: int, parent_url: str | None) -> None:
        normalized = normalize_url(url)
        if depth > self.settings.max_depth:
            return
        if normalized in self.visited or normalized in self.pending:
            return
        self.pending.add(normalized)
        self.queue.append(CrawlTask(url=normalized, depth=depth, parent_url=parent_url))

    def _emit_entities(self, extracted: dict) -> None:
        forums = extracted["forums"]
        topics = extracted["topics"]
        users = extracted["users"]
        torrents = extracted["torrents"]

        topic_details = extracted.get("topic_details")
        if topic_details:
            details_row = dict(topic_details)
            details_row["kind"] = "topic_details"
            topics.append(details_row)

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

        self.entity_counts["forums"] += len(forums)
        self.entity_counts["topics"] += len(topics)
        self.entity_counts["users"] += len(users)
        self.entity_counts["torrents"] += len(torrents)

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
                        "title": extracted.get("title", ""),
                        "page_type": extracted.get("page_type", "generic"),
                        "breadcrumbs": extracted.get("breadcrumbs", []),
                        "forms": extracted.get("forms", []),
                        "ts": utc_now_iso(),
                    }
                    self.storage.write_page(page_record)
                    self.storage.write_edges(extracted["links"])
                    self.entity_counts["edges"] += len(extracted["links"])

                    self._emit_entities(extracted)

                    for link in extracted["links"]:
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
