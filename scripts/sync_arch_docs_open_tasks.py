#!/usr/bin/env python3
"""Sync checklist tasks between `.closed-arch-docs.md` and `.reborn-arch-docs.md`.

Supported directions:
- `open-from-closed`: move open tasks (`- [ ]`) from closed -> reborn.
- `closed-from-reborn`: move closed tasks (`- [x]`) from reborn -> closed.
- `both`: run both directions in one pass.

Rules:
- Source of truth for task items: markdown checklist item with `- [ ]` or `- [x]`.
- A task is moved together with its trailing descriptive block (paragraphs/code fences)
  until the next sibling checklist item or heading.
- Duplicates are avoided using normalized checklist text.
"""

from __future__ import annotations

import argparse
import re
from collections import OrderedDict
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path

CHECKBOX_RE = re.compile(r"^(?P<indent>\s*)- \[(?P<state>[ xX])\] (?P<text>.+?)\s*$")
HEADING_RE = re.compile(r"^(#{2,6})\s+(.+?)\s*$")
HR_RE = re.compile(r"^\s{0,3}(-{3,}|\*{3,}|_{3,})\s*$")
FENCE_RE = re.compile(r"^\s*```")


@dataclass(frozen=True)
class TaskBlock:
    source: str
    text: str
    state: str
    lines: list[str]


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


def find_task_block_end(lines: list[str], start: int, indent: int) -> int:
    n = len(lines)
    j = start + 1
    in_fence = False

    while j < n:
        line = lines[j]
        stripped_nl = line.rstrip("\n")
        stripped = line.strip()

        if FENCE_RE.match(stripped_nl):
            in_fence = not in_fence
            j += 1
            continue

        if not in_fence:
            cmatch = CHECKBOX_RE.match(stripped_nl)
            if cmatch and len(cmatch.group("indent")) <= indent:
                break

            hmatch = HEADING_RE.match(stripped_nl)
            if hmatch:
                heading_indent = len(line) - len(line.lstrip(" "))
                if heading_indent <= indent:
                    break

        j += 1

    while j < n and lines[j].strip() == "":
        j += 1

    if j < n and HR_RE.match(lines[j].rstrip("\n")):
        j += 1
        while j < n and lines[j].strip() == "":
            j += 1

    return j


def collect_and_remove_tasks(lines: list[str], target_state: str) -> tuple[list[TaskBlock], list[str]]:
    headings: dict[int, str] = {}
    moved: list[TaskBlock] = []
    remove_ranges: list[tuple[int, int]] = []

    i = 0
    n = len(lines)
    while i < n:
        line = lines[i]

        hmatch = HEADING_RE.match(line.rstrip("\n"))
        if hmatch:
            level = len(hmatch.group(1))
            headings[level] = hmatch.group(2)
            for lvl in list(headings):
                if lvl > level:
                    del headings[lvl]

        cmatch = CHECKBOX_RE.match(line.rstrip("\n"))
        if not cmatch or cmatch.group("state").lower() != target_state.lower():
            i += 1
            continue

        indent = len(cmatch.group("indent"))
        task_text = cmatch.group("text").strip()
        end = find_task_block_end(lines, i, indent)
        moved.append(
            TaskBlock(
                source=current_source(headings),
                text=task_text,
                state=cmatch.group("state").lower(),
                lines=lines[i:end],
            )
        )
        remove_ranges.append((i, end - 1))
        i = end

    to_remove = [False] * n
    for start, end in remove_ranges:
        for idx in range(start, end + 1):
            to_remove[idx] = True

    filtered_lines = [line for idx, line in enumerate(lines) if not to_remove[idx]]
    return moved, filtered_lines


def collect_existing_tasks(lines: list[str]) -> set[str]:
    existing: set[str] = set()
    for line in lines:
        cmatch = CHECKBOX_RE.match(line.rstrip("\n"))
        if cmatch:
            existing.add(normalize_task_text(cmatch.group("text")))
    return existing


def block_with_state(block: TaskBlock, state: str | None) -> list[str]:
    if state is None:
        return block.lines
    if not block.lines:
        return block.lines

    first = block.lines[0].rstrip("\n")
    cmatch = CHECKBOX_RE.match(first)
    if not cmatch:
        return block.lines

    normalized_state = "x" if state.lower() == "x" else " "
    replacement = f"{cmatch.group('indent')}- [{normalized_state}] {cmatch.group('text')}"
    updated = [replacement + "\n"]
    updated.extend(block.lines[1:])
    return updated


def append_blocks(
    lines: list[str],
    moved_tasks: list[TaskBlock],
    existing_normalized: set[str],
    section_title: str,
    forced_state: str | None = None,
) -> tuple[list[str], int]:
    unique_new: OrderedDict[str, list[TaskBlock]] = OrderedDict()
    seen_run: set[str] = set()

    for block in moved_tasks:
        normalized = normalize_task_text(block.text)
        if normalized in existing_normalized or normalized in seen_run:
            continue
        seen_run.add(normalized)
        unique_new.setdefault(block.source, []).append(block)

    if not unique_new:
        return lines, 0

    if lines and not lines[-1].endswith("\n"):
        lines[-1] += "\n"
    if lines and lines[-1].strip() != "":
        lines.append("\n")

    lines.append(section_title + "\n")
    lines.append(f"Synced at: {datetime.now().strftime('%Y-%m-%d %H:%M')}\n")
    lines.append("\n")

    added = 0
    for source, tasks in unique_new.items():
        lines.append(f"### {source}\n")
        lines.append("\n")
        for task in tasks:
            block_lines = block_with_state(task, forced_state)
            lines.extend(block_lines)
            if block_lines and block_lines[-1].strip() != "":
                lines.append("\n")
            added += 1
        if lines and lines[-1].strip() != "":
            lines.append("\n")

    return lines, added


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
    parser.add_argument(
        "--direction",
        choices=("open-from-closed", "closed-from-reborn", "both"),
        default="open-from-closed",
        help="Sync direction (default keeps backward-compatible behavior)",
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

    moved_open_count = 0
    moved_closed_count = 0
    added_open_count = 0
    added_closed_count = 0

    if args.direction in ("open-from-closed", "both"):
        moved_open, closed_lines = collect_and_remove_tasks(closed_lines, target_state=" ")
        existing_in_reborn = collect_existing_tasks(reborn_lines)
        reborn_lines, added_open_count = append_blocks(
            reborn_lines,
            moved_open,
            existing_in_reborn,
            section_title="## Auto-Moved Open Tasks From Closed",
            forced_state=" ",
        )
        moved_open_count = len(moved_open)

    if args.direction in ("closed-from-reborn", "both"):
        moved_closed, reborn_lines = collect_and_remove_tasks(reborn_lines, target_state="x")
        existing_in_closed = collect_existing_tasks(closed_lines)
        closed_lines, added_closed_count = append_blocks(
            closed_lines,
            moved_closed,
            existing_in_closed,
            section_title="## Auto-Moved Closed Tasks From Reborn",
            forced_state="x",
        )
        moved_closed_count = len(moved_closed)

    write_lines(closed_path, closed_lines)
    write_lines(reborn_path, reborn_lines)

    print(f"Direction: {args.direction}")
    print(f"Detected open tasks in closed: {moved_open_count}")
    print(f"Added to reborn (deduplicated): {added_open_count}")
    print(f"Removed from closed: {moved_open_count}")
    print(f"Detected closed tasks in reborn: {moved_closed_count}")
    print(f"Added to closed (deduplicated): {added_closed_count}")
    print(f"Removed from reborn: {moved_closed_count}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
