from __future__ import annotations

import gzip
import json
from pathlib import Path

from .utils import hash_url, utc_now_iso


class CrawlStorage:
    def __init__(self, output_root: Path) -> None:
        stamp = utc_now_iso().replace(":", "-")
        self.run_dir = output_root / f"run_{stamp}"
        self.pages_dir = self.run_dir / "pages"
        self.raw_dir = self.run_dir / "raw"
        self.entities_dir = self.run_dir / "entities"
        self.graph_dir = self.run_dir / "graph"
        self.meta_dir = self.run_dir / "meta"

        for path in (
            self.pages_dir,
            self.raw_dir,
            self.entities_dir,
            self.graph_dir,
            self.meta_dir,
        ):
            path.mkdir(parents=True, exist_ok=True)

        self._handles: dict[str, object] = {
            "pages": (self.pages_dir / "pages.jsonl").open("a", encoding="utf-8"),
            "forums": (self.entities_dir / "forums.jsonl").open("a", encoding="utf-8"),
            "topics": (self.entities_dir / "topics.jsonl").open("a", encoding="utf-8"),
            "users": (self.entities_dir / "users.jsonl").open("a", encoding="utf-8"),
            "torrents": (self.entities_dir / "torrents.jsonl").open("a", encoding="utf-8"),
            "categories": (self.entities_dir / "categories.jsonl").open("a", encoding="utf-8"),
            "posts": (self.entities_dir / "posts.jsonl").open("a", encoding="utf-8"),
            "topic_meta": (self.entities_dir / "topic_meta.jsonl").open("a", encoding="utf-8"),
            "profiles": (self.entities_dir / "profiles.jsonl").open("a", encoding="utf-8"),
            "edges": (self.graph_dir / "edges.jsonl").open("a", encoding="utf-8"),
            "errors": (self.meta_dir / "errors.jsonl").open("a", encoding="utf-8"),
        }

    def close(self) -> None:
        for handle in self._handles.values():
            handle.close()

    def _write_jsonl(self, stream: str, payload: dict) -> None:
        handle = self._handles[stream]
        handle.write(json.dumps(payload, ensure_ascii=False) + "\n")
        handle.flush()

    def write_page(self, payload: dict) -> None:
        self._write_jsonl("pages", payload)

    def write_forums(self, payloads: list[dict]) -> None:
        for payload in payloads:
            self._write_jsonl("forums", payload)

    def write_topics(self, payloads: list[dict]) -> None:
        for payload in payloads:
            self._write_jsonl("topics", payload)

    def write_users(self, payloads: list[dict]) -> None:
        for payload in payloads:
            self._write_jsonl("users", payload)

    def write_torrents(self, payloads: list[dict]) -> None:
        for payload in payloads:
            self._write_jsonl("torrents", payload)

    def write_categories(self, payloads: list[dict]) -> None:
        for payload in payloads:
            self._write_jsonl("categories", payload)

    def write_posts(self, payloads: list[dict]) -> None:
        for payload in payloads:
            self._write_jsonl("posts", payload)

    def write_topic_meta(self, payload: dict) -> None:
        self._write_jsonl("topic_meta", payload)

    def write_profile(self, payload: dict) -> None:
        self._write_jsonl("profiles", payload)

    def write_edges(self, payloads: list[dict]) -> None:
        for payload in payloads:
            self._write_jsonl("edges", payload)

    def write_error(self, payload: dict) -> None:
        self._write_jsonl("errors", payload)

    def save_html(self, url: str, html: str) -> str:
        digest = hash_url(url)
        relative = Path("raw") / f"{digest}.html.gz"
        target = self.run_dir / relative
        with gzip.open(target, "wt", encoding="utf-8") as handle:
            handle.write(html)
        return str(relative)

    def write_meta(self, name: str, payload: dict) -> None:
        target = self.meta_dir / name
        target.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
