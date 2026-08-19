#!/usr/bin/env python3
"""
download_sources.py

Читает libs.versions.toml (формат Gradle Version Catalog), для каждой
библиотеки из секции [libraries] пытается скачать *-sources.jar
из Google Maven / Maven Central / JitPack (по очереди) и распаковывает
исходники в:

    test_results/<artifactId>-<version>/

Использование:
    pip install requests tomli   # tomli не нужен на Python 3.11+
    python download_sources.py libs.versions.toml

Требования: Python 3.9+, пакет `requests`.
"""

import sys
import os
import io
import zipfile
import concurrent.futures
import argparse
from dataclasses import dataclass

try:
    import tomllib  # Python 3.11+
except ModuleNotFoundError:
    import tomli as tomllib  # pip install tomli

import requests

OUTPUT_DIR = "test_results"

# Репозитории, которые пробуем по очереди для скачивания sources.jar.
# {group_path} -> group с точками, замененными на "/"
REPOS = [
    ("Google Maven",  "https://dl.google.com/dl/android/maven2/{group_path}/{artifact}/{version}/{artifact}-{version}-sources.jar"),
    ("Maven Central", "https://repo1.maven.org/maven2/{group_path}/{artifact}/{version}/{artifact}-{version}-sources.jar"),
    ("Gradle Plugin Portal", "https://plugins.gradle.org/m2/{group_path}/{artifact}/{version}/{artifact}-{version}-sources.jar"),
]

# JitPack — особый случай: координаты вида com.github.<user>
JITPACK_TEMPLATE = "https://jitpack.io/{group_path}/{artifact}/{version}/{artifact}-{version}-sources.jar"

TIMEOUT = 30


@dataclass
class Lib:
    key: str        # ключ из [libraries], например "androidx-room-runtime"
    group: str
    artifact: str
    version: str


def load_catalog(path: str) -> dict:
    with open(path, "rb") as f:
        return tomllib.load(f)


def resolve_version(versions: dict, entry: dict) -> str | None:
    if "version" in entry and isinstance(entry["version"], str):
        return entry["version"]
    ref = entry.get("version", {}).get("ref") if isinstance(entry.get("version"), dict) else None
    if ref is None and "version.ref" in entry:
        ref = entry["version.ref"]
    # tomllib парсит "version.ref" как вложенный dict {"version": {"ref": ...}}
    if ref is None and isinstance(entry.get("version"), dict):
        ref = entry["version"].get("ref")
    if ref is not None:
        return versions.get(ref)
    return None


def collect_libs(catalog: dict) -> list[Lib]:
    versions = catalog.get("versions", {})
    libs = []
    for key, entry in catalog.get("libraries", {}).items():
        if not isinstance(entry, dict):
            continue
        group = entry.get("group")
        artifact = entry.get("name")
        version = resolve_version(versions, entry)
        if not group or not artifact:
            continue
        if not version:
            # библиотека без явной версии (версия берётся из BOM) — пропускаем,
            # для неё нет фиксированной версии в каталоге
            continue
        libs.append(Lib(key=key, group=group, artifact=artifact, version=version))
    return libs


def group_path(group: str) -> str:
    return group.replace(".", "/")


def try_download(url: str) -> bytes | None:
    try:
        resp = requests.get(url, timeout=TIMEOUT)
        if resp.status_code == 200 and resp.content:
            return resp.content
    except requests.RequestException:
        pass
    return None


def download_one(lib: Lib) -> tuple[Lib, str, str | None]:
    """
    Возвращает (lib, статус, откуда_скачано_или_None)
    """
    gpath = group_path(lib.group)
    dest_dir = os.path.join(OUTPUT_DIR, f"{lib.artifact}-{lib.version}")

    if os.path.isdir(dest_dir) and os.listdir(dest_dir):
        return lib, "skip (already exists)", None

    # 1. Обычные репозитории
    for repo_name, template in REPOS:
        url = template.format(group_path=gpath, artifact=lib.artifact, version=lib.version)
        content = try_download(url)
        if content:
            _extract(content, dest_dir)
            return lib, "ok", repo_name

    # 2. JitPack — актуально для com.github.* координат (например ktaglib)
    if lib.group.startswith("com.github."):
        url = JITPACK_TEMPLATE.format(group_path=gpath, artifact=lib.artifact, version=lib.version)
        content = try_download(url)
        if content:
            _extract(content, dest_dir)
            return lib, "ok", "JitPack"

    return lib, "not found", None


def _extract(jar_bytes: bytes, dest_dir: str) -> None:
    os.makedirs(dest_dir, exist_ok=True)
    with zipfile.ZipFile(io.BytesIO(jar_bytes)) as zf:
        zf.extractall(dest_dir)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("toml_path", nargs="?", default="libs.versions.toml")
    parser.add_argument("--workers", type=int, default=8, help="Число параллельных загрузок")
    args = parser.parse_args()

    catalog = load_catalog(args.toml_path)
    libs = collect_libs(catalog)

    print(f"Найдено библиотек с версией: {len(libs)}")
    os.makedirs(OUTPUT_DIR, exist_ok=True)

    results = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers) as ex:
        futures = [ex.submit(download_one, lib) for lib in libs]
        for fut in concurrent.futures.as_completed(futures):
            lib, status, source = fut.result()
            results.append((lib, status, source))
            tag = f"[{source}]" if source else ""
            print(f"{status:20s} {lib.group}:{lib.artifact}:{lib.version} {tag}")

    not_found = [r for r in results if r[1] == "not found"]
    print("\n--- Итог ---")
    print(f"Успешно / пропущено: {len(results) - len(not_found)}")
    print(f"Не найдено sources.jar: {len(not_found)}")
    if not_found:
        print("\nБиблиотеки без исходников (проверьте вручную — возможно другой репозиторий/название артефакта):")
        for lib, _, _ in not_found:
            print(f"  - {lib.group}:{lib.artifact}:{lib.version}  (ключ: {lib.key})")


if __name__ == "__main__":
    main()
