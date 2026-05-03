#!/usr/bin/env python3
"""Synchronize library source archives based on direct app dependencies.

Source of truth:
- android/app/build.gradle.kts dependencies block
- android/gradle/libs.versions.toml version catalog
- android/settings.gradle.kts repositories order

Behavior:
- Resolves direct dependencies (implementation/api/ksp/debug/test/androidTest)
- Expands `libs.bundles.*`
- Resolves explicit versions from catalog
- Resolves versionless artifacts via project BOMs declared with platform(libs.*)
- Downloads `*-sources.jar`, unpacks into src/
- Updates index files and removes stale folders (unless --keep-stale)
"""

from __future__ import annotations

import argparse
import io
import re
import shutil
import sys
import tempfile
import time
import tomllib
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable
import zipfile

ROOT = Path(__file__).resolve().parents[1]
APP_BUILD = ROOT / "android/app/build.gradle.kts"
CATALOG = ROOT / "android/gradle/libs.versions.toml"
SETTINGS = ROOT / "android/settings.gradle.kts"
TARGET_DIR = ROOT / "test_results/source_codes/jabook_libs_sources"
INDEX_FILE = TARGET_DIR / "_INDEX.txt"
MISSING_FILE = TARGET_DIR / "_MISSING_SOURCES.txt"

GOOGLE_MAVEN = "https://dl.google.com/dl/android/maven2"
MAVEN_CENTRAL = "https://repo1.maven.org/maven2"

SUPPORTED_CONFIGS = {
    "implementation",
    "api",
    "ksp",
    "debugImplementation",
    "testImplementation",
    "androidTestImplementation",
}

TEST_CONFIGS = {"testImplementation", "androidTestImplementation"}

POM_NAMESPACE = {"m": "http://maven.apache.org/POM/4.0.0"}


@dataclass(frozen=True)
class Coordinates:
    group: str
    artifact: str
    version: str

    @property
    def gav(self) -> str:
        return f"{self.group}:{self.artifact}:{self.version}"

    @property
    def folder_name(self) -> str:
        return f"{self.group}_{self.artifact}_{self.version}"

    @property
    def sources_jar_name(self) -> str:
        return f"{self.artifact}-{self.version}-sources.jar"


@dataclass(frozen=True)
class MissingEntry:
    gav: str
    reason: str


class SyncError(RuntimeError):
    pass


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dry-run", action="store_true", help="Print planned actions without writing files")
    parser.add_argument(
        "--include-tests",
        action=argparse.BooleanOptionalAction,
        default=True,
        help="Include testImplementation and androidTestImplementation dependencies (default: true)",
    )
    parser.add_argument("--keep-stale", action="store_true", help="Do not delete stale directories")
    parser.add_argument("--strict-missing", action="store_true", help="Fail if any sources were not downloaded")
    parser.add_argument("--verbose", action="store_true", help="Verbose logging")
    return parser.parse_args()


def log(msg: str, *, verbose: bool = False, enabled: bool = True) -> None:
    if enabled:
        print(msg)


def v_log(msg: str, *, args: argparse.Namespace) -> None:
    if args.verbose:
        print(msg)


def read_text(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8")
    except FileNotFoundError as exc:
        raise SyncError(f"Required file is missing: {path}") from exc


def load_catalog(path: Path) -> dict:
    data = tomllib.loads(read_text(path))
    if not isinstance(data, dict):
        raise SyncError("Invalid version catalog format")
    return data


def parse_repository_order(settings_text: str) -> list[str]:
    repos: list[str] = []

    if re.search(r"\bgoogle\(\)", settings_text):
        repos.append(GOOGLE_MAVEN)
    if re.search(r"\bmavenCentral\(\)", settings_text):
        repos.append(MAVEN_CENTRAL)

    uri_pattern = re.compile(r"url\s*=\s*uri\(\s*\"([^\"]+)\"\s*\)")
    raw_url_pattern = re.compile(r"url\s*=\s*\"([^\"]+)\"")

    repos.extend(uri_pattern.findall(settings_text))
    repos.extend(raw_url_pattern.findall(settings_text))

    deduped: list[str] = []
    seen: set[str] = set()
    for repo in repos:
        normalized = repo.strip().rstrip("/")
        if not normalized or normalized in seen:
            continue
        seen.add(normalized)
        deduped.append(normalized)

    for fallback in (GOOGLE_MAVEN, MAVEN_CENTRAL, "https://jitpack.io"):
        if fallback not in seen:
            deduped.append(fallback)
            seen.add(fallback)

    return deduped


def extract_dependencies_block(text: str) -> str:
    match = re.search(r"\bdependencies\s*\{", text)
    if not match:
        raise SyncError("Could not find dependencies { ... } block in app/build.gradle.kts")

    start = match.end() - 1
    depth = 0
    for idx in range(start, len(text)):
        ch = text[idx]
        if ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
            if depth == 0:
                return text[start + 1 : idx]

    raise SyncError("Unbalanced braces in dependencies block")


def _extract_call_arg_strings(block: str, call_name: str) -> list[str]:
    pattern = re.compile(rf"\b{re.escape(call_name)}\s*\(")
    args: list[str] = []

    for match in pattern.finditer(block):
        idx = match.end()
        depth = 1
        arg_start = idx
        while idx < len(block):
            ch = block[idx]
            if ch == "(":
                depth += 1
            elif ch == ")":
                depth -= 1
                if depth == 0:
                    args.append(block[arg_start:idx].strip())
                    break
            idx += 1

    return args


def _accessor_to_alias(accessor: str) -> str:
    # libs.androidx.compose.bom -> androidx-compose-bom
    return accessor.replace(".", "-")


def parse_dependency_accessors(dependencies_block: str, include_tests: bool) -> tuple[set[str], set[str], set[str]]:
    direct_aliases: set[str] = set()
    bundle_aliases: set[str] = set()
    bom_aliases: set[str] = set()

    for config in SUPPORTED_CONFIGS:
        if not include_tests and config in TEST_CONFIGS:
            continue

        for arg in _extract_call_arg_strings(dependencies_block, config):
            # Direct alias
            for accessor in re.findall(r"\blibs\.([A-Za-z0-9_.]+)", arg):
                if accessor.startswith("bundles."):
                    bundle_aliases.add(_accessor_to_alias(accessor[len("bundles.") :]))
                else:
                    direct_aliases.add(_accessor_to_alias(accessor))

            # BOM platform aliases
            for accessor in re.findall(r"\b(?:platform|enforcedPlatform)\s*\(\s*libs\.([A-Za-z0-9_.]+)\s*\)", arg):
                bom_aliases.add(_accessor_to_alias(accessor))

    return direct_aliases, bundle_aliases, bom_aliases


def resolve_library_entry(alias: str, catalog: dict) -> tuple[str, str, str | None]:
    libs = catalog.get("libraries", {})
    versions = catalog.get("versions", {})

    if alias not in libs:
        raise SyncError(f"Unknown library alias in version catalog: {alias}")

    entry = libs[alias]

    if isinstance(entry, str):
        parts = entry.split(":")
        if len(parts) != 3:
            raise SyncError(f"Unsupported string library notation for {alias}: {entry}")
        return parts[0], parts[1], parts[2]

    if not isinstance(entry, dict):
        raise SyncError(f"Unsupported library entry type for {alias}: {type(entry)}")

    group = entry.get("group")
    name = entry.get("name")

    if (not group or not name) and "module" in entry:
        module = entry["module"]
        if isinstance(module, str) and ":" in module:
            group, name = module.split(":", 1)

    if not group or not name:
        raise SyncError(f"Library alias {alias} is missing group/name")

    version: str | None = None
    if "version" in entry:
        version_value = entry["version"]
        if isinstance(version_value, str):
            version = version_value
        elif isinstance(version_value, dict):
            if "ref" in version_value:
                ref = version_value["ref"]
                if ref not in versions:
                    raise SyncError(f"version.ref '{ref}' not found for alias {alias}")
                version = str(versions[ref])
    elif "version.ref" in entry:
        ref = entry["version.ref"]
        if ref not in versions:
            raise SyncError(f"version.ref '{ref}' not found for alias {alias}")
        version = str(versions[ref])

    return str(group), str(name), version


def expand_bundle_aliases(bundle_aliases: Iterable[str], catalog: dict) -> set[str]:
    bundles = catalog.get("bundles", {})
    result: set[str] = set()

    for bundle_alias in bundle_aliases:
        if bundle_alias not in bundles:
            raise SyncError(f"Unknown bundle alias in version catalog: {bundle_alias}")
        entries = bundles[bundle_alias]
        if not isinstance(entries, list):
            raise SyncError(f"Bundle {bundle_alias} must be a list")
        for lib_alias in entries:
            result.add(str(lib_alias))

    return result


def maven_path(group: str, artifact: str, version: str, filename: str) -> str:
    return f"{group.replace('.', '/')}/{artifact}/{version}/{filename}"


def fetch_url_bytes(url: str, *, timeout: int = 30, retries: int = 3) -> bytes:
    last_exc: Exception | None = None
    for attempt in range(1, retries + 1):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "jabook-lib-sources-sync/1.0"})
            with urllib.request.urlopen(req, timeout=timeout) as response:  # noqa: S310 (controlled URLs)
                return response.read()
        except Exception as exc:  # noqa: BLE001
            last_exc = exc
            if attempt < retries:
                time.sleep(0.8 * attempt)
    assert last_exc is not None
    raise last_exc


def resolve_from_repositories(
    repos: list[str],
    rel_path: str,
    *,
    timeout: int = 30,
    retries: int = 3,
    args: argparse.Namespace,
) -> tuple[bytes, str] | tuple[None, None]:
    for repo in repos:
        url = f"{repo}/{rel_path}"
        try:
            v_log(f"  ↳ GET {url}", args=args)
            payload = fetch_url_bytes(url, timeout=timeout, retries=retries)
            return payload, url
        except urllib.error.HTTPError as exc:
            v_log(f"    HTTP {exc.code} at {url}", args=args)
        except Exception as exc:  # noqa: BLE001
            v_log(f"    error at {url}: {exc}", args=args)
    return None, None


def parse_bom_versions(bom_pom_bytes: bytes) -> dict[tuple[str, str], str]:
    root = ET.fromstring(bom_pom_bytes)

    def ns_xpath(path: str) -> str:
        return path if root.tag.startswith("{") else path.replace("m:", "")

    properties: dict[str, str] = {}
    properties_node = root.find(ns_xpath("m:properties"), POM_NAMESPACE)
    if properties_node is not None:
        for child in list(properties_node):
            tag = child.tag
            if "}" in tag:
                tag = tag.split("}", 1)[1]
            properties[tag] = (child.text or "").strip()

    dep_versions: dict[tuple[str, str], str] = {}

    deps = root.findall(ns_xpath("m:dependencyManagement/m:dependencies/m:dependency"), POM_NAMESPACE)
    for dep in deps:
        g = dep.findtext(ns_xpath("m:groupId"), default="", namespaces=POM_NAMESPACE).strip()
        a = dep.findtext(ns_xpath("m:artifactId"), default="", namespaces=POM_NAMESPACE).strip()
        v = dep.findtext(ns_xpath("m:version"), default="", namespaces=POM_NAMESPACE).strip()
        if not g or not a or not v:
            continue

        def repl(match: re.Match[str]) -> str:
            key = match.group(1)
            return properties.get(key, match.group(0))

        resolved_v = re.sub(r"\$\{([^}]+)\}", repl, v)
        dep_versions[(g, a)] = resolved_v

    return dep_versions


def resolve_bom_maps(bom_aliases: set[str], catalog: dict, repos: list[str], args: argparse.Namespace) -> dict[tuple[str, str], str]:
    bom_map: dict[tuple[str, str], str] = {}

    for bom_alias in sorted(bom_aliases):
        group, artifact, version = resolve_library_entry(bom_alias, catalog)
        if not version:
            raise SyncError(f"BOM alias {bom_alias} has no explicit version")

        pom_name = f"{artifact}-{version}.pom"
        rel_path = maven_path(group, artifact, version, pom_name)
        payload, source_url = resolve_from_repositories(repos, rel_path, args=args)
        if payload is None:
            raise SyncError(f"Failed to resolve BOM POM for {group}:{artifact}:{version}")

        v_log(f"Loaded BOM {group}:{artifact}:{version} from {source_url}", args=args)
        bom_entries = parse_bom_versions(payload)
        bom_map.update(bom_entries)

    return bom_map


def safe_replace_dir(src_dir: Path, dst_dir: Path) -> None:
    parent = dst_dir.parent
    with tempfile.TemporaryDirectory(prefix="jabook-libsync-", dir=parent) as temp_dir:
        temp_path = Path(temp_dir) / dst_dir.name
        if temp_path.exists():
            shutil.rmtree(temp_path)
        shutil.copytree(src_dir, temp_path)

        backup = dst_dir.with_name(f"{dst_dir.name}.bak")
        if backup.exists():
            shutil.rmtree(backup)
        if dst_dir.exists():
            dst_dir.rename(backup)
        temp_path.rename(dst_dir)
        if backup.exists():
            shutil.rmtree(backup)


def write_text_atomic(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile("w", encoding="utf-8", delete=False, dir=str(path.parent)) as tmp:
        tmp.write(content)
        tmp_path = Path(tmp.name)
    tmp_path.replace(path)


def main() -> int:
    args = parse_args()

    TARGET_DIR.mkdir(parents=True, exist_ok=True)

    catalog = load_catalog(CATALOG)
    settings_text = read_text(SETTINGS)
    app_build_text = read_text(APP_BUILD)

    repositories = parse_repository_order(settings_text)
    v_log(f"Repository order: {repositories}", args=args)

    dependencies_block = extract_dependencies_block(app_build_text)
    direct_aliases, bundle_aliases, bom_aliases = parse_dependency_accessors(
        dependencies_block,
        include_tests=args.include_tests,
    )

    expanded_from_bundles = expand_bundle_aliases(bundle_aliases, catalog)
    # platform(libs.*bom) is used only for version alignment and generally has no sources.jar.
    # Keep BOM aliases for version resolution, but exclude them from download candidates.
    all_lib_aliases = sorted((direct_aliases | expanded_from_bundles) - bom_aliases)

    v_log(f"Direct aliases ({len(direct_aliases)}): {sorted(direct_aliases)}", args=args)
    v_log(f"Bundle aliases ({len(bundle_aliases)}): {sorted(bundle_aliases)}", args=args)
    v_log(f"Expanded bundle libs ({len(expanded_from_bundles)}): {sorted(expanded_from_bundles)}", args=args)
    v_log(f"BOM aliases ({len(bom_aliases)}): {sorted(bom_aliases)}", args=args)

    bom_versions = resolve_bom_maps(bom_aliases, catalog, repositories, args)

    coords: list[Coordinates] = []
    missing: list[MissingEntry] = []

    for alias in all_lib_aliases:
        try:
            group, artifact, version = resolve_library_entry(alias, catalog)
        except SyncError as exc:
            missing.append(MissingEntry(alias, str(exc)))
            continue

        if not version:
            version = bom_versions.get((group, artifact))
            if not version:
                missing.append(
                    MissingEntry(
                        f"{group}:{artifact}",
                        "No explicit version and no matching BOM entry found",
                    )
                )
                continue

        coords.append(Coordinates(group=group, artifact=artifact, version=version))

    # De-duplicate and stabilize order
    uniq = {c.gav: c for c in coords}
    resolved = [uniq[k] for k in sorted(uniq)]

    desired_folders = {c.folder_name for c in resolved}

    # Plan stale directories
    existing_dirs = {p.name for p in TARGET_DIR.iterdir() if p.is_dir()}
    stale_dirs = sorted(d for d in existing_dirs if d not in desired_folders)

    downloaded_count = 0

    for c in resolved:
        rel_path = maven_path(c.group, c.artifact, c.version, c.sources_jar_name)
        payload, source_url = resolve_from_repositories(repositories, rel_path, args=args)
        if payload is None:
            missing.append(
                MissingEntry(
                    c.gav,
                    "sources.jar not found in configured repositories",
                )
            )
            continue

        folder = TARGET_DIR / c.folder_name
        jar_path = folder / c.sources_jar_name

        if args.dry_run:
            log(f"[DRY] sync {c.gav} <- {source_url}")
            downloaded_count += 1
            continue

        with tempfile.TemporaryDirectory(prefix="jabook-libsync-item-", dir=str(TARGET_DIR)) as td:
            temp_dir = Path(td) / c.folder_name
            temp_dir.mkdir(parents=True, exist_ok=True)

            temp_jar = temp_dir / c.sources_jar_name
            temp_jar.write_bytes(payload)

            src_dir = temp_dir / "src"
            src_dir.mkdir(parents=True, exist_ok=True)
            with zipfile.ZipFile(io.BytesIO(payload)) as zf:
                zf.extractall(src_dir)

            # Keep only current jar + src in destination folder
            safe_replace_dir(temp_dir, folder)

        downloaded_count += 1
        log(f"✅ Synced {c.gav}")

    # Remove stale folders unless keep-stale
    if stale_dirs and not args.keep_stale:
        for stale in stale_dirs:
            stale_path = TARGET_DIR / stale
            if args.dry_run:
                log(f"[DRY] delete stale {stale}")
            else:
                shutil.rmtree(stale_path)
                log(f"🗑️  Deleted stale {stale}")

    # Write indexes
    index_lines = [
        "Auto-synced library sources from project direct dependencies\n",
        f"Total resolved libs: {len(resolved)}\n",
        f"Downloaded/available sources: {downloaded_count}\n",
        "\n",
    ]
    index_lines.extend(f"{name}\n" for name in sorted(desired_folders))

    missing_lines = [
        "Missing or unresolved sources\n",
        f"Total: {len(missing)}\n",
        "\n",
    ]
    for m in sorted(missing, key=lambda x: x.gav):
        missing_lines.append(f"{m.gav} :: {m.reason}\n")

    if args.dry_run:
        log(f"[DRY] would write {INDEX_FILE}")
        log(f"[DRY] would write {MISSING_FILE}")
    else:
        write_text_atomic(INDEX_FILE, "".join(index_lines))
        write_text_atomic(MISSING_FILE, "".join(missing_lines))

    if missing:
        log("⚠️ Some sources are missing/unresolved. See _MISSING_SOURCES.txt")

    log(f"Done. Resolved libs: {len(resolved)}, downloaded: {downloaded_count}, missing: {len(missing)}")

    if args.strict_missing and missing:
        return 2
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except SyncError as exc:
        print(f"❌ {exc}")
        raise SystemExit(1)
