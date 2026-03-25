from __future__ import annotations

from dataclasses import dataclass

from .constants import DEFAULT_START_PATHS
from .settings import CrawlSettings
from .utils import to_absolute_url


@dataclass(frozen=True, slots=True)
class ScenarioPreset:
    name: str
    start_paths: tuple[str, ...]
    max_depth: int
    max_pages: int


SCENARIOS: dict[str, ScenarioPreset] = {
    "full": ScenarioPreset(
        name="full",
        start_paths=DEFAULT_START_PATHS,
        max_depth=10,
        max_pages=3000,
    ),
    "structure": ScenarioPreset(
        name="structure",
        start_paths=("/forum/index.php",),
        max_depth=6,
        max_pages=1200,
    ),
    "topics": ScenarioPreset(
        name="topics",
        start_paths=("/forum/tracker.php", "/forum/index.php"),
        max_depth=12,
        max_pages=6000,
    ),
    "auth_surface": ScenarioPreset(
        name="auth_surface",
        start_paths=("/forum/index.php", "/forum/profile.php", "/forum/tracker.php"),
        max_depth=8,
        max_pages=1800,
    ),
    "audiobooks": ScenarioPreset(
        name="audiobooks",
        start_paths=(
            "/forum/index.php?c=33",
            "/forum/viewforum.php?f=2332",
            "/forum/viewforum.php?f=2326",
            "/forum/viewforum.php?f=2389",
            "/forum/viewforum.php?f=2327",
            "/forum/viewforum.php?f=2324",
            "/forum/viewforum.php?f=2328",
        ),
        max_depth=4,
        max_pages=2200,
    ),
}


def apply_scenario(settings: CrawlSettings) -> CrawlSettings:
    preset = SCENARIOS.get(settings.scenario)
    if not preset:
        return settings

    if settings.start_urls == [to_absolute_url(settings.mirrors[0], p) for p in DEFAULT_START_PATHS]:
        settings.start_urls = [to_absolute_url(settings.mirrors[0], p) for p in preset.start_paths]

    settings.max_depth = min(settings.max_depth, preset.max_depth)
    settings.max_pages = min(settings.max_pages, preset.max_pages)
    return settings
