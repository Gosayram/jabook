from __future__ import annotations

import argparse
import json
import logging
import sys
from pathlib import Path

from .mode_matrix import feature_matrix, mode_summary
from .scenarios import SCENARIOS, apply_scenario
from .settings import build_crawl_parser, load_settings_from_args


def _build_capabilities_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="python -m rutracker_parser capabilities")
    parser.add_argument("--mode", choices=["guest", "auth", "both"], default="both")
    parser.add_argument("--write", default="")
    return parser


def _print_capabilities(mode: str, write_path: str) -> int:
    payload: dict
    if mode == "both":
        payload = {
            "matrix": feature_matrix(),
            "guest": mode_summary("guest"),
            "auth": mode_summary("auth"),
        }
    else:
        payload = {
            "matrix": feature_matrix(),
            mode: mode_summary(mode),
        }

    print(json.dumps(payload, ensure_ascii=False, indent=2))
    if write_path:
        path = Path(write_path)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    return 0


def _run_crawl(argv: list[str]) -> int:
    parser = build_crawl_parser()
    args = parser.parse_args(argv)
    from .crawl_engine import CrawlEngine

    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")

    settings = load_settings_from_args(args)
    if settings.scenario not in SCENARIOS:
        raise ValueError(f"Unknown scenario: {settings.scenario}. Available: {', '.join(sorted(SCENARIOS))}")
    settings = apply_scenario(settings)

    engine = CrawlEngine(settings)
    summary = engine.run()
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0


def main(argv: list[str] | None = None) -> int:
    values = list(argv if argv is not None else sys.argv[1:])
    if values and values[0] == "crawl":
        values = values[1:]
    if values and values[0] == "search-index":
        from .search_index import build_search_index_parser, run_search_index

        parser = build_search_index_parser()
        args = parser.parse_args(values[1:])
        payload = run_search_index(args)
        print(json.dumps(payload, ensure_ascii=False, indent=2))
        return 0
    if values and values[0] == "search-find":
        from .search_index import build_search_find_parser, run_search_find

        parser = build_search_find_parser()
        args = parser.parse_args(values[1:])
        payload = run_search_find(args)
        print(json.dumps(payload, ensure_ascii=False, indent=2))
        return 0
    if values and values[0] == "capabilities":
        cap_parser = _build_capabilities_parser()
        cap_args = cap_parser.parse_args(values[1:])
        return _print_capabilities(mode=cap_args.mode, write_path=cap_args.write)
    return _run_crawl(values)


if __name__ == "__main__":
    raise SystemExit(main())
