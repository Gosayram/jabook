#!/usr/bin/env python3
"""Move open checklist tasks from .closed-arch-docs.md to .reborn-arch-docs.md.

Rules:
- Source of truth for "open task": markdown checklist item with "- [ ]".
- Open tasks are removed from `.closed-arch-docs.md`.
- Missing open tasks are appended to `.reborn-arch-docs.md` under an auto-sync section.
- Duplicates in `.reborn-arch-docs.md` are avoided using normalized task text.
"""

from __future__ import annotations

import argparse
import re
from collections import OrderedDict
from datetime import datetime
from pathlib import Path

CHECKBOX_RE = re.compile(r"^(?P<indent>\s*)- \[(?P<state>[ xX])\] (?P<text>.+?)\s*$")
HEADING_RE = re.compile(r"^(#{2,6})\s+(.+?)\s*$")


def normalize_task_text(text: str) -> str:
    value = text.strip().lower()
    value = value.replace("**", "")
    value = value.replace("`", "")
    value = re.sub(r"\s+", " ", value)
    return value


def load_lines(path: Path) -> list[str]:
    return path.read_text(encoding="utf-8").splitlines(keepends=True)


def write_lines(path: Path, lines: list[str]) -> None:
    path.write_text("".join(lines), encoding="utf-8")


def current_source(headings: dict[int, str]) -> str:
    for level in sorted(headings.keys(), reverse=True):
        title = headings[level].strip()
        if title:
            return title
    return "Uncategorized"


def collect_and_remove_open_tasks(closed_lines: list[str]) -> tuple[list[tuple[str, str]], list[str]]:
    headings: dict[int, str] = {}
    moved: list[tuple[str, str]] = []
    remove_ranges: list[tuple[int, int]] = []

    i = 0
    n = len(closed_lines)
    while i < n:
        line = closed_lines[i]

        hmatch = HEADING_RE.match(line.rstrip("\n"))
        if hmatch:
            level = len(hmatch.group(1))
            headings[level] = hmatch.group(2)
            for lvl in list(headings):
                if lvl > level:
                    del headings[lvl]

        cmatch = CHECKBOX_RE.match(line.rstrip("\n"))
        if not cmatch or cmatch.group("state") != " ":
            i += 1
            continue

        indent = len(cmatch.group("indent"))
        task_text = cmatch.group("text").strip()
        moved.append((current_source(headings), task_text))

        end = i
        j = i + 1
        while j < n:
            nxt = closed_lines[j]
            if HEADING_RE.match(nxt.rstrip("\n")):
                break
            if nxt.strip() == "":
                break

            nxt_indent = len(nxt) - len(nxt.lstrip(" "))
            stripped = nxt.lstrip(" ")

            if stripped.startswith("- ") and nxt_indent <= indent:
                break
            if nxt_indent <= indent:
                break

            end = j
            j += 1

        remove_ranges.append((i, end))
        i = end + 1

    to_remove = [False] * n
    for start, end in remove_ranges:
        for idx in range(start, end + 1):
            to_remove[idx] = True

    filtered_closed = [line for idx, line in enumerate(closed_lines) if not to_remove[idx]]
    return moved, filtered_closed


def collect_existing_tasks(reborn_lines: list[str]) -> set[str]:
    existing: set[str] = set()
    for line in reborn_lines:
        cmatch = CHECKBOX_RE.match(line.rstrip("\n"))
        if cmatch:
            existing.add(normalize_task_text(cmatch.group("text")))
    return existing


def append_to_reborn(
    reborn_lines: list[str],
    moved_tasks: list[tuple[str, str]],
    existing_normalized: set[str],
) -> tuple[list[str], int]:
    unique_new: OrderedDict[str, list[str]] = OrderedDict()
    seen_run: set[str] = set()

    for source, text in moved_tasks:
        normalized = normalize_task_text(text)
        if normalized in existing_normalized or normalized in seen_run:
            continue
        seen_run.add(normalized)
        unique_new.setdefault(source, []).append(text)

    if not unique_new:
        return reborn_lines, 0

    if reborn_lines and not reborn_lines[-1].endswith("\n"):
        reborn_lines[-1] += "\n"
    if reborn_lines and reborn_lines[-1].strip() != "":
        reborn_lines.append("\n")

    reborn_lines.append("## Auto-Moved Open Tasks From Closed\n")
    reborn_lines.append(f"Synced at: {datetime.now().strftime('%Y-%m-%d %H:%M')}\n")
    reborn_lines.append("\n")

    added = 0
    for source, tasks in unique_new.items():
        reborn_lines.append(f"### {source}\n")
        reborn_lines.append("\n")
        for task in tasks:
            reborn_lines.append(f"- [ ] {task}\n")
            added += 1
        reborn_lines.append("\n")

    return reborn_lines, added


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--closed",
        default=".closed-arch-docs.md",
        help="Path to closed architecture docs",
    )
    parser.add_argument(
        "--reborn",
        default=".reborn-arch-docs.md",
        help="Path to reborn architecture docs",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    closed_path = Path(args.closed)
    reborn_path = Path(args.reborn)

    if not closed_path.exists():
        raise SystemExit(f"Closed docs file not found: {closed_path}")
    if not reborn_path.exists():
        raise SystemExit(f"Reborn docs file not found: {reborn_path}")

    closed_lines = load_lines(closed_path)
    reborn_lines = load_lines(reborn_path)

    moved_tasks, updated_closed = collect_and_remove_open_tasks(closed_lines)
    existing = collect_existing_tasks(reborn_lines)
    updated_reborn, added_count = append_to_reborn(reborn_lines, moved_tasks, existing)

    write_lines(closed_path, updated_closed)
    write_lines(reborn_path, updated_reborn)

    print(f"Detected open tasks in closed: {len(moved_tasks)}")
    print(f"Added to reborn (deduplicated): {added_count}")
    print(f"Removed from closed: {len(moved_tasks)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
