from __future__ import annotations

import random
import time


class AdaptiveRateLimiter:
    def __init__(self, interval_sec: float, jitter_sec: float, cooldown_sec: float) -> None:
        self._interval_sec = max(interval_sec, 0.0)
        self._jitter_sec = max(jitter_sec, 0.0)
        self._cooldown_sec = max(cooldown_sec, 0.0)
        self._next_allowed_at = 0.0

    def wait_turn(self) -> None:
        now = time.monotonic()
        if now < self._next_allowed_at:
            time.sleep(self._next_allowed_at - now)
        delay = self._interval_sec + random.uniform(0.0, self._jitter_sec)
        self._next_allowed_at = max(self._next_allowed_at, time.monotonic()) + delay

    def apply_cooldown(self, multiplier: float = 1.0) -> None:
        pause = self._cooldown_sec * max(multiplier, 0.0)
        self._next_allowed_at = max(self._next_allowed_at, time.monotonic() + pause)
