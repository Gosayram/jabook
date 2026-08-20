#!/usr/bin/env python3
"""Check latest stable and available versions for dependencies in libs.versions.toml."""

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

import concurrent.futures
import io
import json
import re
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from enum import Enum

import tomllib

try:
    import typer
    from rich.console import Console
    from rich.markup import escape
    from rich.panel import Panel
    from rich.progress import (
        BarColumn,
        Progress,
        SpinnerColumn,
        TaskProgressColumn,
        TextColumn,
        TimeElapsedColumn,
    )
    from rich.table import Table
except ImportError as exc:
    sys.stderr.write(
        f"error: missing dependency '{exc.name}'\n"
        f"Currently running: {sys.executable}\n"
        f"Install the requirements into your venv:\n"
        f"  {VENV_PYTHON} -m pip install -r {REQUIREMENTS_FILE}\n"
    )
    raise SystemExit(1) from exc

DEFAULT_TOML_PATH = (
    SCRIPT_PATH.parent.parent / "android" / "gradle" / "libs.versions.toml"
)

MAVEN_REPOSITORIES = (
    "https://repo1.maven.org/maven2",
    "https://dl.google.com/dl/android/maven2",
)
GRADLE_PLUGIN_PORTAL_M2 = ("https://plugins.gradle.org/m2",)

PRERELEASE_PATTERN = re.compile(
    r"(alpha|beta|rc|dev|snapshot|eap|preview|m\d+)", re.IGNORECASE
)
REQUEST_TIMEOUT = 10
USER_AGENT = "libs-version-checker/2.0"

ROW_KEYS = ("name", "kind", "current", "latest_stable", "latest", "status")
ROW_HEADERS = ("Name", "Kind", "Current", "Stable", "Latest", "Status")

STATUS_STYLES = {
    "up-to-date": "green",
    "update available": "yellow bold",
}


class OutputFormat(str, Enum):
    table = "table"
    plain_text = "plain-text"
    json = "json"
    yaml = "yaml"


class VersionCheckError(Exception):
    """Base error for problems this tool can explain to the user."""


class TomlParseError(VersionCheckError):
    def __init__(self, path: Path, cause: tomllib.TOMLDecodeError) -> None:
        super().__init__(f"Could not parse {path}: {cause}")
        self.path = path
        self.cause = cause


class NoComponentsFoundError(VersionCheckError):
    def __init__(self, path: Path) -> None:
        super().__init__(f"No [libraries] or [plugins] entries found in {path}")
        self.path = path


@dataclass
class Component:
    name: str
    kind: str
    group: str
    artifact: str
    current: str
    repos: tuple[str, ...]
    latest_stable: str | None = None
    latest_any: str | None = None
    status: str = "unknown"
    error: str | None = None


def load_toml(path: Path) -> dict:
    with path.open("rb") as handle:
        try:
            return tomllib.load(handle)
        except tomllib.TOMLDecodeError as exc:
            raise TomlParseError(path, exc) from exc


def _extract_version(entry: dict) -> tuple[str | None, str | None]:
    version = entry.get("version")
    if isinstance(version, dict):
        return version.get("ref"), None
    if isinstance(version, str):
        return None, version
    return None, None


def collect_components(data: dict, source: Path) -> list[Component]:
    versions_table = data.get("versions", {})
    seen_refs: set[str] = set()
    components: list[Component] = []

    for key, entry in data.get("libraries", {}).items():
        group, artifact = entry.get("group"), entry.get("name")
        if not group or not artifact:
            continue
        ref, direct_version = _extract_version(entry)
        if ref:
            if ref in seen_refs:
                continue
            seen_refs.add(ref)
            name, current = ref, versions_table.get(ref, "")
        elif direct_version:
            name, current = key, direct_version
        else:
            continue
        components.append(
            Component(name, "library", group, artifact, current, MAVEN_REPOSITORIES)
        )

    for key, entry in data.get("plugins", {}).items():
        plugin_id = entry.get("id")
        if not plugin_id:
            continue
        ref, direct_version = _extract_version(entry)
        if ref:
            if ref in seen_refs:
                continue
            seen_refs.add(ref)
            name, current = ref, versions_table.get(ref, "")
        elif direct_version:
            name, current = key, direct_version
        else:
            continue
        artifact = f"{plugin_id}.gradle.plugin"
        components.append(
            Component(
                name, "plugin", plugin_id, artifact, current, GRADLE_PLUGIN_PORTAL_M2
            )
        )

    if not components:
        raise NoComponentsFoundError(source)

    return sorted(components, key=lambda c: c.name.lower())


def fetch_versions(repo: str, group: str, artifact: str) -> list[str] | None:
    url = f"{repo}/{group.replace('.', '/')}/{artifact}/maven-metadata.xml"
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    try:
        with urllib.request.urlopen(request, timeout=REQUEST_TIMEOUT) as response:
            payload = response.read()
    except (urllib.error.URLError, OSError, TimeoutError):
        return None
    try:
        root = ET.fromstring(payload)
    except ET.ParseError:
        return None
    versions_el = root.find("./versioning/versions")
    if versions_el is None:
        return None
    return [v.text for v in versions_el.findall("version") if v.text]


def parse_version(version: str) -> tuple[tuple[int, ...], str]:
    match = re.match(r"^(\d+(?:\.\d+)*)(.*)$", version)
    if not match:
        return (0,), version
    numeric, suffix = match.groups()
    return tuple(int(part) for part in numeric.split(".")), suffix


def version_sort_key(version: str) -> tuple:
    numeric, suffix = parse_version(version)
    return numeric, suffix == "", suffix.lower()


def resolve_component(component: Component) -> Component:
    versions: list[str] | None = None
    for repo in component.repos:
        versions = fetch_versions(repo, component.group, component.artifact)
        if versions:
            break
    if not versions:
        component.error = "not found"
        component.status = "not found"
        return component

    ordered = sorted(versions, key=version_sort_key)
    component.latest_any = ordered[-1]
    stable = [v for v in ordered if not PRERELEASE_PATTERN.search(v)]
    component.latest_stable = stable[-1] if stable else None

    if component.current and component.latest_stable:
        if version_sort_key(component.current) >= version_sort_key(
            component.latest_stable
        ):
            component.status = "up-to-date"
        else:
            component.status = "update available"
    return component


def resolve_all(
    components: list[Component], jobs: int, *, quiet: bool
) -> list[Component]:
    resolved: list[Component] = []
    progress_columns = (
        SpinnerColumn(),
        TextColumn("[progress.description]{task.description}"),
        BarColumn(),
        TaskProgressColumn(),
        TimeElapsedColumn(),
    )
    with Progress(
        *progress_columns, console=Console(stderr=True), disable=quiet
    ) as progress:
        task = progress.add_task("Checking versions...", total=len(components))
        with concurrent.futures.ThreadPoolExecutor(max_workers=jobs) as pool:
            futures = [pool.submit(resolve_component, c) for c in components]
            for future in concurrent.futures.as_completed(futures):
                resolved.append(future.result())
                progress.advance(task)
    return sorted(resolved, key=lambda c: c.name.lower())


def build_rows(components: list[Component]) -> list[dict[str, str]]:
    return [
        {
            "name": c.name,
            "kind": c.kind,
            "current": c.current,
            "latest_stable": c.latest_stable or "",
            "latest": c.latest_any or "",
            "status": c.error or c.status,
        }
        for c in components
    ]


def make_table(rows: list[dict[str, str]]) -> Table:
    table = Table(header_style="bold cyan")
    for header in ROW_HEADERS:
        table.add_column(header)
    for row in rows:
        style = STATUS_STYLES.get(row["status"], "red")
        table.add_row(
            row["name"],
            row["kind"],
            row["current"] or "-",
            row["latest_stable"] or "-",
            row["latest"] or "-",
            f"[{style}]{row['status']}[/{style}]",
        )
    return table


def render_table_plain(rows: list[dict[str, str]]) -> str:
    buffer = io.StringIO()
    Console(file=buffer, no_color=True, width=120).print(make_table(rows))
    return buffer.getvalue()


def to_plain_text(rows: list[dict[str, str]]) -> str:
    lines = [
        f"{row['name']} ({row['kind']}): current={row['current'] or '-'} "
        f"stable={row['latest_stable'] or '-'} latest={row['latest'] or '-'} status={row['status']}"
        for row in rows
    ]
    return "\n".join(lines) + "\n"


def to_json(rows: list[dict[str, str]]) -> str:
    return json.dumps(rows, indent=2, ensure_ascii=False) + "\n"


def to_yaml(rows: list[dict[str, str]]) -> str:
    lines = []
    for row in rows:
        lines.append(f"- name: {row['name']}")
        lines.extend(f"  {key}: {row[key] or 'null'}" for key in ROW_KEYS[1:])
    return "\n".join(lines) + "\n"


TEXT_FORMATTERS = {
    OutputFormat.plain_text: to_plain_text,
    OutputFormat.json: to_json,
    OutputFormat.yaml: to_yaml,
}


def print_summary(rows: list[dict[str, str]], console: Console) -> None:
    up_to_date = sum(1 for r in rows if r["status"] == "up-to-date")
    updates = sum(1 for r in rows if r["status"] == "update available")
    failed = len(rows) - up_to_date - updates
    console.print(
        f"\n[bold]{len(rows)}[/bold] checked  "
        f"[green]{up_to_date} up-to-date[/green]  "
        f"[yellow]{updates} update(s) available[/yellow]  "
        f"[red]{failed} unresolved[/red]"
    )


app = typer.Typer(
    add_completion=False,
    rich_markup_mode="rich",
    no_args_is_help=False,
    help=(
        "Check latest [bold]stable[/bold] and [bold]available[/bold] versions for every "
        "library and plugin declared in [cyan]libs.versions.toml[/cyan].\n\n"
        "[bold]Examples[/bold]\n"
        "  [dim]$[/dim] scripts/check_versions.py\n"
        "  [dim]$[/dim] scripts/check_versions.py --format json -f versions.json\n"
        "  [dim]$[/dim] scripts/check_versions.py -i path/to/libs.versions.toml --format yaml"
    ),
)


@app.command()
def main(
    input_path: Path = typer.Option(  # noqa: B008
        DEFAULT_TOML_PATH,
        "--input",
        "-i",
        exists=True,
        dir_okay=False,
        readable=True,
        help="Path to libs.versions.toml.",
    ),
    file: Path | None = typer.Option(  # noqa: B008
        None,
        "--file",
        "-f",
        dir_okay=False,
        help="Write the report to this file instead of printing it to the terminal.",
    ),
    output_format: OutputFormat = typer.Option(  # noqa: B008
        OutputFormat.table,
        "--format",
        case_sensitive=False,
        help="Output format.",
    ),
    jobs: int = typer.Option(
        16, "--jobs", "-j", min=1, max=64, help="Parallel network requests."
    ),
    quiet: bool = typer.Option(False, "--quiet", "-q", help="Hide the progress bar."),
) -> None:
    console = Console()
    err_console = Console(stderr=True)

    try:
        components = collect_components(load_toml(input_path), input_path)
    except TomlParseError as exc:
        err_console.print(
            Panel(escape(str(exc)), title="TOML parse error", border_style="red")
        )
        raise typer.Exit(code=1) from exc
    except NoComponentsFoundError as exc:
        err_console.print(
            Panel(escape(str(exc)), title="Nothing to check", border_style="yellow")
        )
        raise typer.Exit(code=1) from exc

    components = resolve_all(components, jobs, quiet=quiet)
    rows = build_rows(components)

    if output_format is OutputFormat.table:
        output = render_table_plain(rows) if file else None
    else:
        output = TEXT_FORMATTERS[output_format](rows)

    if file:
        try:
            file.write_text(output, encoding="utf-8")
        except OSError as exc:
            err_console.print(
                Panel(
                    escape(f"Could not write to {file}: {exc}"),
                    title="Write error",
                    border_style="red",
                )
            )
            raise typer.Exit(code=1) from exc
        console.print(
            f"[green]:heavy_check_mark:[/green] Written to [bold]{file}[/bold]"
        )
    elif output_format is OutputFormat.table:
        console.print(make_table(rows))
    else:
        print(output, end="")

    print_summary(rows, err_console)


if __name__ == "__main__":
    try:
        app()
    except KeyboardInterrupt:
        Console(stderr=True).print("\n[red]Interrupted.[/red]")
        raise SystemExit(130) from None
