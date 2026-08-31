#!/usr/bin/env python3
"""
download_m3_docs.py

Downloads and parses the Material Design 3 documentation from
https://m3.material.io/ into test_results/docs/material3/.

The site is an Angular SPA; page content is served as "carbon" JSON via
/_dsm/content/m3/<carbonVersion>/<fileId>.json. The route map (slug ->
fileId) and the carbon version are embedded in the site's main.js bundle.
Raw page JSON (100% of the data) plus a rendered Markdown version are
saved for every page, along with the sitemap, route map and blog index.
"""

import os
import sys
from pathlib import Path

SCRIPT_PATH = Path(__file__).resolve()
VENV_DIR = SCRIPT_PATH.parent / ".venv"
VENV_PYTHON = VENV_DIR / ("Scripts/python.exe" if os.name == "nt" else "bin/python3")
REQUIREMENTS_FILE = SCRIPT_PATH.parent / "requirements.txt"


def _running_in_target_venv() -> bool:
    return (
        sys.prefix != sys.base_prefix
        and Path(sys.prefix).resolve() == VENV_DIR.resolve()
    )


if not _running_in_target_venv() and VENV_PYTHON.exists():
    try:
        os.execv(str(VENV_PYTHON), [str(VENV_PYTHON), str(SCRIPT_PATH), *sys.argv[1:]])
    except OSError as exc:
        sys.stderr.write(
            f"error: could not launch venv interpreter {VENV_PYTHON}: {exc}\n"
        )
        raise SystemExit(1) from exc

MIN_PYTHON = (3, 11)

if sys.version_info < MIN_PYTHON:
    sys.stderr.write(
        f"error: Python {MIN_PYTHON[0]}.{MIN_PYTHON[1]}+ is required, "
        f"got {sys.version.split()[0]} ({sys.executable})\n"
        f"Create the venv and install requirements:\n"
        f"  python3 -m venv {VENV_DIR}\n"
        f"  {VENV_PYTHON} -m pip install -r {REQUIREMENTS_FILE}\n"
    )
    raise SystemExit(1)

import argparse
import concurrent.futures
import json
import os
import re
import time
from html.parser import HTMLParser
from urllib.parse import urlsplit

import requests

BASE = "https://m3.material.io"
OUT_DEFAULT = Path("test_results/docs/material3")
MAIN_JS_RE = re.compile(r'src="(/static/angular/main\.[0-9a-f]+\.js)"')
CARBON_VERSION_RE = re.compile(r'carbonVersion:"([^"]+)"')
JSON_PARSE_RE = re.compile(r"JSON\.parse\('((?:[^'\\]|\\.)*)'\)")
SITEMAP_LOC_RE = re.compile(r"<loc>([^<]+)</loc>")


def build_session() -> requests.Session:
    s = requests.Session()
    s.headers.update(
        {
            "User-Agent": (
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"
            ),
            "Accept": "*/*",
        }
    )
    return s


def get_with_retry(
    session: requests.Session, url: str, attempts: int = 4, timeout: int = 30
) -> requests.Response:
    last_exc: Exception | None = None
    for i in range(attempts):
        try:
            r = session.get(url, timeout=timeout)
            if r.status_code == 200:
                return r
            last_exc = RuntimeError(f"HTTP {r.status_code} for {url}")
        except requests.RequestException as exc:
            last_exc = exc
        time.sleep(1.5 * (i + 1))
    raise RuntimeError(f"failed after {attempts} attempts: {url}") from last_exc


def extract_json_parse_blobs(js: str) -> list:
    """Parse webpack JSON.parse('...') single-quoted string literals."""
    blobs = []
    for m in JSON_PARSE_RE.finditer(js):
        raw = m.group(1)
        # single-quoted JS string: \' -> ', everything else is JSON-compatible
        txt = raw.replace("\\'", "'")
        try:
            blobs.append(json.loads(txt))
        except json.JSONDecodeError:
            continue
    return blobs


def collect_routes(main_js: str) -> tuple[str, list[dict], list[dict]]:
    version_m = CARBON_VERSION_RE.search(main_js)
    if not version_m:
        raise RuntimeError("carbonVersion not found in main.js")
    carbon_version = version_m.group(1)

    routes: list[dict] = []
    blog_posts: list[dict] = []
    for blob in extract_json_parse_blobs(main_js):
        if not isinstance(blob, dict):
            continue
        for key, value in blob.items():
            if not isinstance(value, list):
                continue
            for entry in value:
                if not isinstance(entry, dict) or "slug" not in entry:
                    continue
                if entry.get("exportedCarbonFileId") and entry not in routes:
                    routes.append(entry)
                elif (key == "Z" or "document_id" in entry) and entry not in blog_posts:
                    blog_posts.append(entry)
    return carbon_version, routes, blog_posts


def slug_to_dir(slug: str) -> Path:
    return Path(slug) if slug else Path("index")


# ---------------------------------------------------------------------------
# Internal link rewriting (site URLs -> local page.md paths)
# ---------------------------------------------------------------------------


def _norm_tab(label: str) -> str:
    return re.sub(r"[^a-z0-9]+", "-", label.lower().replace("&", " ")).strip("-")


def build_link_index(routes: list[dict]) -> dict:
    alias_to_slug: dict[str, str] = {}
    tab_slugs: dict[str, set[str]] = {}
    for route in routes:
        slug = route["slug"].strip("/")
        aliases = {slug, (route.get("carbonPath") or "").strip("/")}
        aliases |= {a.strip("/") for a in route.get("alternateSlugs") or []}
        aliases.discard("")
        labels = {
            _norm_tab(t["label"]) for t in route.get("tabs") or [] if t.get("label")
        }
        if labels:
            tab_slugs[slug] = labels
        for alias in aliases:
            alias_to_slug[alias] = slug
    seg_targets: dict[str, str | None] = {}

    def add_seg(seg: str, slug: str) -> None:
        if not seg:
            return
        if seg not in seg_targets:
            seg_targets[seg] = slug
        elif seg_targets[seg] != slug:
            seg_targets[seg] = None  # ambiguous

    for alias, slug in alias_to_slug.items():
        add_seg(alias.rsplit("/", 1)[-1], slug)
    for slug, labels in tab_slugs.items():
        for label in labels:
            add_seg(label, slug)
    return {
        "alias_to_slug": alias_to_slug,
        "tab_slugs": tab_slugs,
        "seg_targets": seg_targets,
    }


UUID_RE = re.compile(r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")


def _internal_target(href: str) -> tuple[str, str] | None:
    """Split href into (path, fragment) if it is site-internal, else None."""
    if href.startswith("#"):
        return None
    parts = urlsplit(href)
    if parts.scheme and parts.scheme not in ("http", "https"):
        return None
    if parts.netloc and parts.netloc != "m3.material.io":
        return None
    return parts.path, parts.fragment


def _resolve_path(path: str, link_index: dict) -> str | None:
    alias_to_slug: dict[str, str] = link_index["alias_to_slug"]
    tab_slugs: dict[str, set[str]] = link_index["tab_slugs"]
    seg_targets: dict[str, str | None] = link_index["seg_targets"]
    segs = [s for s in path.split("/") if s and not UUID_RE.match(s)]
    if not segs:
        return None
    clean = "/".join(segs)
    candidates = [clean]
    for prefix in ("m3/pages/", "google-material-3/pages/"):
        if clean.startswith(prefix):
            candidates.append(clean[len(prefix):])
    for p in candidates:
        if p in alias_to_slug:
            return alias_to_slug[p]
    for p in candidates:
        for alias, slug in alias_to_slug.items():
            if p.startswith(alias + "/"):
                rest = p[len(alias) + 1:]
                if rest in tab_slugs.get(slug, set()) or rest.startswith("tab"):
                    return slug
    for p in candidates:
        target = seg_targets.get(p.rsplit("/", 1)[-1])
        if target:
            return target
    p = candidates[0]
    while "/" in p:
        p = p.rsplit("/", 1)[0]
        hit = alias_to_slug.get(p) or seg_targets.get(p.rsplit("/", 1)[-1])
        if hit:
            return hit
    return None


def rewrite_internal_links(md: str, current_slug: str, link_index: dict) -> str:
    base_dir = slug_to_dir(current_slug)

    def repl(m: re.Match) -> str:
        href = m.group(1)
        target = _internal_target(href)
        if target is None:
            return m.group(0)
        path, fragment = target
        path = path.strip("/")
        if not path:
            return m.group(0)
        resolved = _resolve_path(path, link_index)
        if not resolved:
            return m.group(0)
        rel = Path(
            os.path.relpath(slug_to_dir(resolved) / "page.md", base_dir)
        ).as_posix()
        if fragment and not UUID_RE.match(fragment):
            rel += f"#{fragment}"
        return f"]({rel})"

    return re.sub(r"\]\(([^)\s]+)\)", repl, md)


# ---------------------------------------------------------------------------
# HTML -> Markdown conversion (stdlib only)
# ---------------------------------------------------------------------------

BLOCK_TAGS = {
    "p", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "li", "table",
    "thead", "tbody", "tr", "th", "td", "blockquote", "pre", "hr", "br",
    "div", "section", "figure", "figcaption",
}

HEADING = {"h1": "#", "h2": "##", "h3": "###", "h4": "####", "h5": "#####", "h6": "######"}


class HtmlToMarkdown(HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.out: list[str] = []
        self.list_stack: list[str] = []  # 'ul' | 'ol'
        self.ol_counters: list[int] = []
        self.in_pre = False
        self.in_table_cell = False
        self.table_rows: list[list[str]] = []
        self.current_row: list[str] | None = None
        self.current_cell: list[str] | None = None
        self.href: str | None = None
        self.img_alt = ""
        self.img_src = ""
        self.cell_is_header = False    # -- helpers ----------------------------------------------------------
    @staticmethod
    def _clean(text: str) -> str:
        return re.sub(r"[ \t\r\n]+", " ", text)

    def _append_cell(self) -> None:
        if self.current_cell is None or self.current_row is None:
            return
        self.current_row.append(self._clean("".join(self.current_cell)).strip())
        self.current_cell = None

    def _flush_table(self) -> None:
        if not self.table_rows:
            return
        rows = self.table_rows
        width = max(len(r) for r in rows)
        for r in rows:
            r.extend([""] * (width - len(r)))
        header = rows[0] if rows[0] and any(rows[0]) else [""] * width
        body = rows[1:] if rows[0] and any(rows[0]) else rows
        self.out.append("\n")
        self.out.append("| " + " | ".join(header) + " |\n")
        self.out.append("|" + "|".join([" --- "] * width) + "|\n")
        for r in body:
            self.out.append("| " + " | ".join(r) + " |\n")
        self.out.append("\n")
        self.table_rows = []

    # -- parser hooks ------------------------------------------------------
    def handle_starttag(self, tag: str, attrs) -> None:
        a = dict(attrs)
        if self.in_table_cell and tag in ("div", "p", "section", "li"):
            self.current_cell.append(" ")
        if tag == "pre":
            self.in_pre = True
            self.out.append("\n```\n")
        elif tag in ("ul", "ol"):
            self.list_stack.append(tag)
            self.ol_counters.append(0)
        elif tag == "li":
            indent = "  " * (len(self.list_stack) - 1)
            if self.list_stack and self.list_stack[-1] == "ol":
                self.ol_counters[-1] += 1
                self.out.append(f"\n{indent}{self.ol_counters[-1]}. ")
            else:
                self.out.append(f"\n{indent}- ")
        elif tag in HEADING:
            self.out.append(f"\n\n{HEADING[tag]} ")
        elif tag == "p":
            if not self.list_stack:
                self.out.append("\n\n")
        elif tag == "br":
            if self.in_table_cell and self.current_cell is not None:
                self.current_cell.append(" / ")
            else:
                self.out.append("\n" + "  " * max(len(self.list_stack) - 1, 0))
        elif tag == "hr":
            self.out.append("\n\n---\n\n")
        elif tag == "blockquote":
            self.out.append("\n\n> ")
        elif tag == "strong" or tag == "b":
            self.out.append("**")
        elif tag in ("em", "i"):
            self.out.append("*")
        elif tag == "code" and not self.in_pre:
            self.out.append("`")
        elif tag == "a":
            self.href = a.get("href")
            if self.href:
                self.out.append("[")
        elif tag == "img":
            alt = self._clean(a.get("alt") or "image").strip()
            self.out.append(f"![{alt}]({a.get('src', '')})")
        elif tag == "tr":
            self.current_row = []
        elif tag in ("th", "td"):
            self.in_table_cell = True
            self.cell_is_header = tag == "th"
            self.current_cell = []
        elif tag == "table":
            self.table_rows = []

    def handle_endtag(self, tag: str) -> None:
        if tag == "pre":
            self.in_pre = False
            self.out.append("\n```\n")
        elif tag in ("ul", "ol"):
            if self.list_stack:
                self.list_stack.pop()
            if self.ol_counters:
                self.ol_counters.pop()
        elif tag in HEADING:
            self.out.append("\n\n")
        elif tag == "p":
            if not self.list_stack:
                self.out.append("\n\n")
        elif tag == "strong" or tag == "b":
            self.out.append("**")
        elif tag in ("em", "i"):
            self.out.append("*")
        elif tag == "code" and not self.in_pre:
            self.out.append("`")
        elif tag == "a":
            if self.href:
                self.out.append(f"]({self.href})")
            self.href = None
        elif tag in ("th", "td"):
            self.in_table_cell = False
            self._append_cell()
        elif tag == "tr":
            if self.current_row is not None:
                if self.current_cell:
                    self._append_cell()
                self.table_rows.append(self.current_row)
                self.current_row = None
        elif tag == "table":
            self._flush_table()
        elif tag == "blockquote":
            self.out.append("\n")

    def handle_data(self, data: str) -> None:
        if self.in_table_cell and self.current_cell is not None:
            self.current_cell.append(self._clean(data))
            return
        if self.in_pre:
            self.out.append(data)
            return
        if self.current_row is not None and self.current_cell is None:
            return  # stray text between cells
        text = self._clean(data)
        if not text.strip() and not self.list_stack:
            return
        self.out.append(text)

    def result(self) -> str:
        text = "".join(self.out)
        text = re.sub(r"\n{3,}", "\n\n", text)
        text = re.sub(r"[ \t]+\n", "\n", text)
        return text.strip() + "\n"


def html_to_markdown(html: str) -> str:
    parser = HtmlToMarkdown()
    try:
        parser.feed(html)
        parser.close()
    except Exception:  # noqa: BLE001
        # ponytail: raw JSON is always saved alongside, conversion is best-effort
        return html
    return parser.result()


def render_page_markdown(page: dict, source_slug: str = "") -> str:
    lines: list[str] = []
    title = page.get("headerTitle") or page.get("title") or ""
    if title:
        lines += [f"# {title}", ""]
    if page.get("description"):
        lines += [page["description"].strip(), ""]
    lines += [f"> Source: {BASE}/{source_slug}", ""]
    if page.get("updatedTimestamp"):
        lines += [f"> Updated: {page['updatedTimestamp']}", ""]
    for section in page.get("sections") or []:
        name = (section.get("name") or "").strip()
        if name and name.lower() != "tab 1":
            lines += ["", f"## {name}", ""]
        for block in section.get("contentBlocks") or []:
            if block.get("isHidden"):
                continue
            block_title = block.get("title")
            if block_title:
                lines += ["", f"### {block_title}", ""]
            for chunk in block.get("contentChunks") or []:
                html_value = chunk.get("htmlValue") or ""
                if not html_value:
                    continue
                lines.append(html_to_markdown(html_value))
                lines.append("")
    return re.sub(r"\n{3,}", "\n\n", "\n".join(lines)).strip() + "\n"


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------


def download_page(
    session: requests.Session,
    route: dict,
    carbon_version: str,
    out_dir: Path,
    link_index: dict,
) -> tuple[str, str]:
    slug = route["slug"]
    file_id = route["exportedCarbonFileId"]
    url = f"{BASE}/_dsm/content/m3/{carbon_version}/{file_id}"
    try:
        page = get_with_retry(session, url).json()
    except Exception as exc:  # noqa: BLE001
        return slug, f"FAILED: {exc}"
    page_dir = out_dir / slug_to_dir(slug)
    page_dir.mkdir(parents=True, exist_ok=True)
    (page_dir / "page.json").write_text(
        json.dumps(page, indent=2, ensure_ascii=False), encoding="utf-8"
    )
    try:
        md = render_page_markdown(page, source_slug=slug)
        md = rewrite_internal_links(md, slug, link_index)
    except Exception as exc:  # noqa: BLE001
        return slug, f"FAILED (render): {exc}"
    (page_dir / "page.md").write_text(md, encoding="utf-8")
    n_blocks = sum(
        len(s.get("contentBlocks") or []) for s in page.get("sections") or []
    )
    return slug, f"OK ({n_blocks} blocks)"


def main() -> None:
    ap = argparse.ArgumentParser(description="Download Material Design 3 docs")
    ap.add_argument(
        "--out", type=Path, default=OUT_DEFAULT, help=f"output dir (default: {OUT_DEFAULT})"
    )
    ap.add_argument("--workers", type=int, default=6)
    args = ap.parse_args()
    out_dir = args.out
    meta_dir = out_dir / "_meta"
    meta_dir.mkdir(parents=True, exist_ok=True)

    session = build_session()

    print("Fetching homepage...")
    home = get_with_retry(session, f"{BASE}/")
    main_js_path_m = MAIN_JS_RE.search(home.text)
    if not main_js_path_m:
        raise RuntimeError("main.js reference not found on homepage")
    main_js_url = BASE + main_js_path_m.group(1)
    print(f"Fetching bundle: {main_js_url}")
    main_js = get_with_retry(session, main_js_url).text
    (meta_dir / "main.js").write_text(main_js, encoding="utf-8")

    carbon_version, routes, blog_posts = collect_routes(main_js)
    print(f"Carbon version: {carbon_version}")
    print(f"Doc pages: {len(routes)}, blog posts (metadata): {len(blog_posts)}")

    (meta_dir / "routes.json").write_text(
        json.dumps(
            {"carbonVersion": carbon_version, "mainJs": main_js_url, "routes": routes},
            indent=2,
            ensure_ascii=False,
        ),
        encoding="utf-8",
    )
    (meta_dir / "blog_index.json").write_text(
        json.dumps(blog_posts, indent=2, ensure_ascii=False), encoding="utf-8"
    )

    print("Fetching sitemap...")
    sitemap = get_with_retry(session, f"{BASE}/sitemap.xml").text
    (meta_dir / "sitemap.xml").write_text(sitemap, encoding="utf-8")
    sitemap_urls = [u.rstrip("/") for u in SITEMAP_LOC_RE.findall(sitemap)]

    print(f"Downloading {len(routes)} pages with {args.workers} workers...")
    link_index = build_link_index(routes)
    results: list[tuple[str, str]] = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers) as pool:
        futures = [
            pool.submit(download_page, session, route, carbon_version, out_dir, link_index)
            for route in routes
        ]
        for fut in concurrent.futures.as_completed(futures):
            results.append(fut.result())
    results.sort()

    failed = [(slug, status) for slug, status in results if status.startswith("FAILED")]
    for slug, status in results:
        print(f"  {slug or '(home)':60s} {status}")

    # Coverage check: every sitemap URL must map to a downloaded page or blog post
    doc_slugs = [r["slug"].rstrip("/") for r in routes]
    blog_slugs = {("blog/" + p["slug"]).rstrip("/") for p in blog_posts}
    tab_slugs = set()
    for r in routes:
        for tab in r.get("tabs") or []:
            label = tab.get("label", "").lower().replace(" ", "-")
            tab_slugs.add((r["slug"].rstrip("/") + "/" + label).rstrip("/"))
    uncovered = []
    for url in sitemap_urls:
        path = url[len(BASE):]
        if path in doc_slugs or path in tab_slugs or path in blog_slugs:
            continue
        if any(path.startswith(s + "/") for s in doc_slugs):
            continue  # tab page of a doc route
        uncovered.append(url)
    coverage_pct = 100 * (len(sitemap_urls) - len(uncovered)) / max(len(sitemap_urls), 1)

    print()
    print(f"Sitemap URLs: {len(sitemap_urls)}")
    print(f"Coverage:     {coverage_pct:.1f}% ({len(sitemap_urls) - len(uncovered)}/{len(sitemap_urls)})")
    if uncovered:
        (meta_dir / "uncovered_urls.txt").write_text("\n".join(uncovered), encoding="utf-8")
        print(f"Uncovered:    {len(uncovered)} (see {meta_dir / 'uncovered_urls.txt'})")

    # INDEX.md
    titles: dict[str, str] = {}
    for route in routes:
        page_file = out_dir / slug_to_dir(route["slug"]) / "page.json"
        title = route["slug"]
        if page_file.exists():
            try:
                data = json.loads(page_file.read_text(encoding="utf-8"))
                title = data.get("headerTitle") or data.get("title") or title
            except Exception:  # noqa: BLE001, S110
                pass
        titles[route["slug"]] = title
    index_lines = [
        "# Material Design 3 — Downloaded Documentation",
        "",
        f"- Source: {BASE}",
        f"- Carbon version: `{carbon_version}`",
        f"- Pages: {len(routes)} | Sitemap coverage: {coverage_pct:.1f}%",
        ("- Blog posts (metadata only, bodies are not publicly served): "
         f"{len(blog_posts)} — see `_meta/blog_index.json`"),
        "",
        "## Pages",
        "",
    ]
    for slug in sorted(titles):
        indent = "  " * (slug.count("/"))
        index_lines.append(f"- {indent}[{titles[slug]}]({slug_to_dir(slug).as_posix()}/page.md)")
    index_lines += [
        "",
        "## Meta files",
        "",
        "- `_meta/sitemap.xml`",
        "- `_meta/routes.json`",
        "- `_meta/blog_index.json`",
        "- `_meta/main.js`",
        "",
    ]
    (out_dir / "INDEX.md").write_text("\n".join(index_lines), encoding="utf-8")

    print()
    if failed:
        print(f"DONE WITH ERRORS: {len(failed)} pages failed:")
        for slug, status in failed:
            print(f"  {slug}: {status}")
        raise SystemExit(2)
    print(f"Done. Output: {out_dir}")


if __name__ == "__main__":
    main()
