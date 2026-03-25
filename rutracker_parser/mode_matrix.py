from __future__ import annotations

from .constants import FEATURE_SUPPORT


def feature_matrix() -> dict[str, str]:
    return dict(FEATURE_SUPPORT)


def features_for_mode(mode: str) -> dict[str, bool]:
    resolved = mode.lower().strip()
    if resolved not in {"guest", "auth"}:
        raise ValueError(f"Unsupported mode: {mode}")
    result: dict[str, bool] = {}
    for name, support in FEATURE_SUPPORT.items():
        result[name] = support == "both" or support == resolved
    return result


def mode_summary(mode: str) -> dict[str, list[str]]:
    flags = features_for_mode(mode)
    working = sorted([name for name, value in flags.items() if value])
    blocked = sorted([name for name, value in flags.items() if not value])
    return {
        "mode": mode,
        "working": working,
        "blocked": blocked,
    }
