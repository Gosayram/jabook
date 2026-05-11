#!/usr/bin/env python3
# Copyright 2026 Jabook Contributors
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from dataclasses import dataclass
from typing import Any


CODERABBIT_LOGINS = {"coderabbitai[bot]", "coderabbitai"}


@dataclass
class Recommendation:
    kind: str
    author: str
    url: str
    body: str
    file_path: str | None = None
    line: int | None = None
    start_line: int | None = None
    diff_hunk: str | None = None
    resolved: bool = False

    @property
    def summary(self) -> str:
        lines = [line.strip() for line in self.body.splitlines() if line.strip()]
        if not lines:
            return "(empty comment)"
        for line in lines:
            if line.startswith("@coderabbitai"):
                continue
            if line.startswith("_⚠️") or line.startswith("_ℹ️") or line.startswith("_✅"):
                continue
            return line
        return lines[0]

    @property
    def prompt(self) -> str | None:
        marker = "Prompt for AI Agents"
        body = self.body
        if marker not in body:
            return None
        prompt_section = body[body.find(marker) :]
        fenced = re.findall(r"```(?:\w+)?\n(.*?)```", prompt_section, flags=re.DOTALL)
        if not fenced:
            return None
        for block in fenced:
            stripped = block.strip()
            if "Verify each finding against current code" in stripped or "In `" in stripped:
                return stripped
        return fenced[0].strip()


def run_gh_api(path: str) -> list[dict[str, Any]]:
    cmd = ["gh", "api", "--paginate", path]
    try:
        result = subprocess.run(
            cmd,
            check=True,
            capture_output=True,
            text=True,
        )
    except FileNotFoundError:
        raise RuntimeError("gh CLI not found in PATH")
    except subprocess.CalledProcessError as exc:
        stderr = (exc.stderr or "").strip()
        raise RuntimeError(f"gh api failed for {path}: {stderr}")

    chunks = [line for line in result.stdout.splitlines() if line.strip()]
    items: list[dict[str, Any]] = []
    for chunk in chunks:
        parsed = json.loads(chunk)
        if isinstance(parsed, list):
            items.extend(parsed)
        elif isinstance(parsed, dict):
            items.append(parsed)
    return items


def run_gh_graphql(query: str, variables: dict[str, Any]) -> dict[str, Any]:
    cmd = ["gh", "api", "graphql", "-f", f"query={query}"]
    for key, value in variables.items():
        cmd.extend(["-F", f"{key}={value}"])
    try:
        result = subprocess.run(
            cmd,
            check=True,
            capture_output=True,
            text=True,
        )
    except FileNotFoundError:
        raise RuntimeError("gh CLI not found in PATH")
    except subprocess.CalledProcessError as exc:
        stderr = (exc.stderr or "").strip()
        raise RuntimeError(f"gh graphql failed: {stderr}")
    return json.loads(result.stdout)


def fetch_review_thread_resolution_map(owner: str, repo: str, pr_number: int) -> dict[str, bool]:
    query = """
query($owner:String!, $repo:String!, $num:Int!, $after:String) {
  repository(owner:$owner, name:$repo) {
    pullRequest(number:$num) {
      reviewThreads(first:100, after:$after) {
        pageInfo { hasNextPage endCursor }
        nodes {
          isResolved
          comments(first:100) {
            nodes { url }
          }
        }
      }
    }
  }
}
"""

    after: str | None = None
    out: dict[str, bool] = {}
    while True:
        variables: dict[str, Any] = {
            "owner": owner,
            "repo": repo,
            "num": pr_number,
        }
        if after:
            variables["after"] = after
        payload = run_gh_graphql(query, variables)
        threads = payload["data"]["repository"]["pullRequest"]["reviewThreads"]
        for thread in threads["nodes"]:
            resolved = bool(thread["isResolved"])
            for comment in thread["comments"]["nodes"]:
                url = str(comment["url"])
                out[url] = resolved
        if not threads["pageInfo"]["hasNextPage"]:
            break
        after = threads["pageInfo"]["endCursor"]
    return out


def parse_pr_ref(raw: str) -> tuple[str, str, int]:
    ref = raw.strip()
    ref = ref.removeprefix("https://github.com/")
    ref = ref.removeprefix("http://github.com/")
    ref = ref.strip("/")

    m = re.fullmatch(r"([^/]+)/([^/]+)/pull/(\d+)", ref)
    if not m:
        raise ValueError(
            "Unsupported PR format. Use either full URL "
            "(https://github.com/owner/repo/pull/123) or owner/repo/pull/123"
        )
    owner, repo, number = m.group(1), m.group(2), int(m.group(3))
    return owner, repo, number


def is_coderabbit(item: dict[str, Any]) -> bool:
    user = item.get("user") or {}
    login = str(user.get("login") or "").lower()
    return login in CODERABBIT_LOGINS


def collect_recommendations(owner: str, repo: str, pr_number: int, debug: bool = False) -> list[Recommendation]:
    review_comments = run_gh_api(f"repos/{owner}/{repo}/pulls/{pr_number}/comments")
    issue_comments = run_gh_api(f"repos/{owner}/{repo}/issues/{pr_number}/comments")
    review_resolution: dict[str, bool] = {}
    if not debug:
        try:
            review_resolution = fetch_review_thread_resolution_map(owner, repo, pr_number)
        except RuntimeError:
            # Fallback to heuristic mode if GraphQL resolution fetch fails.
            review_resolution = {}

    out: list[Recommendation] = []
    for item in review_comments:
        if not is_coderabbit(item):
            continue
        url = str(item.get("html_url") or "")
        resolved_by_graph = review_resolution.get(url)
        resolved = resolved_by_graph if resolved_by_graph is not None else is_resolved_heuristic(str(item.get("body") or ""))
        out.append(
            Recommendation(
                kind="review",
                author=str((item.get("user") or {}).get("login") or ""),
                url=url,
                body=str(item.get("body") or ""),
                file_path=item.get("path"),
                line=item.get("line"),
                start_line=item.get("start_line"),
                diff_hunk=item.get("diff_hunk"),
                resolved=resolved,
            )
        )

    for item in issue_comments:
        if not is_coderabbit(item):
            continue
        out.append(
            Recommendation(
                kind="issue",
                author=str((item.get("user") or {}).get("login") or ""),
                url=str(item.get("html_url") or ""),
                body=str(item.get("body") or ""),
                resolved=is_resolved_heuristic(str(item.get("body") or "")),
            )
        )

    # Keep deterministic order by URL.
    out.sort(key=lambda rec: rec.url)
    return out


def is_resolved_heuristic(body: str) -> bool:
    normalized = body.lower()
    markers = [
        "✅ addressed in commits",
        "addressed in commits",
        "resolved in commits",
        "already fixed",
        "already addressed",
    ]
    return any(marker in normalized for marker in markers)


def render_markdown(owner: str, repo: str, pr_number: int, recs: list[Recommendation]) -> str:
    lines: list[str] = []
    lines.append(f"# CodeRabbit Recommendations: {owner}/{repo} PR #{pr_number}")
    lines.append("")
    lines.append(f"Total recommendations: **{len(recs)}**")
    lines.append("")

    if not recs:
        lines.append("No CodeRabbit recommendations found.")
        return "\n".join(lines)

    for idx, rec in enumerate(recs, start=1):
        lines.append(f"## {idx}. {rec.summary}")
        lines.append("")
        lines.append(f"- Type: `{rec.kind}`")
        if rec.file_path:
            lines.append(f"- File: `{rec.file_path}`")
        if rec.start_line and rec.line and rec.start_line != rec.line:
            lines.append(f"- Lines: `{rec.start_line}-{rec.line}`")
        elif rec.line:
            lines.append(f"- Line: `{rec.line}`")
        elif rec.start_line:
            lines.append(f"- Line: `{rec.start_line}`")
        lines.append(f"- Resolved: `{'yes' if rec.resolved else 'no'}`")
        lines.append(f"- URL: {rec.url}")
        lines.append("")
        lines.append("### Recommendation")
        lines.append("")
        lines.append("```text")
        lines.append(rec.body.strip() or "(empty)")
        lines.append("```")
        prompt = rec.prompt
        if prompt:
            lines.append("")
            lines.append("### Prompt for AI Agents")
            lines.append("")
            lines.append("```text")
            lines.append(prompt)
            lines.append("```")
        lines.append("")
    return "\n".join(lines).rstrip() + "\n"


def render_json(recs: list[Recommendation]) -> str:
    payload = []
    for rec in recs:
        payload.append(
            {
                "type": rec.kind,
                "file": rec.file_path,
                "line": rec.line,
                "start_line": rec.start_line,
                "url": rec.url,
                "summary": rec.summary,
                "resolved": rec.resolved,
                "recommendation": rec.body.strip(),
                "prompt_for_ai_agents": rec.prompt,
            }
        )
    return json.dumps(payload, ensure_ascii=False, indent=2) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(
        description=(
            "Extract actionable CodeRabbit recommendations from a GitHub PR "
            "using gh CLI."
        ),
    )
    parser.add_argument(
        "pr",
        help=(
            "PR reference: full URL "
            "(https://github.com/owner/repo/pull/123) or owner/repo/pull/123"
        ),
    )
    parser.add_argument(
        "--format",
        choices=("markdown", "json"),
        default="markdown",
        help="Output format (default: markdown)",
    )
    parser.add_argument(
        "--output",
        help="Write output to file instead of stdout",
    )
    parser.add_argument(
        "--debug",
        action="store_true",
        help="Include resolved/closed recommendations (default: only open recommendations)",
    )
    args = parser.parse_args()

    try:
        owner, repo, number = parse_pr_ref(args.pr)
        recommendations = collect_recommendations(owner, repo, number, debug=args.debug)
        if not args.debug:
            recommendations = [rec for rec in recommendations if not rec.resolved]
    except Exception as exc:  # noqa: BLE001 - script-level error surface
        print(f"Error: {exc}", file=sys.stderr)
        return 1

    if args.format == "json":
        rendered = render_json(recommendations)
    else:
        rendered = render_markdown(owner, repo, number, recommendations)

    if args.output:
        with open(args.output, "w", encoding="utf-8") as fh:
            fh.write(rendered)
    else:
        sys.stdout.write(rendered)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
