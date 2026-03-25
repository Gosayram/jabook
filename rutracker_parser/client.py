from __future__ import annotations

import random
import time
from dataclasses import dataclass
from typing import Any
from urllib.parse import urlparse

import requests

from .constants import CF_MARKERS, DEFAULT_HEADERS, FORUM_LOGIN_PATH, RETRY_STATUSES
from .settings import CrawlSettings
from .throttle import AdaptiveRateLimiter
from .utils import normalize_url, to_absolute_url


class AntiBotDetectedError(RuntimeError):
    pass


@dataclass(slots=True)
class FetchResult:
    requested_url: str
    final_url: str
    status_code: int
    headers: dict[str, str]
    content: bytes
    text: str
    mirror: str
    elapsed_sec: float
    truncated: bool


class RutrackerHttpClient:
    def __init__(self, settings: CrawlSettings) -> None:
        self.settings = settings
        self.session = requests.Session()
        headers = dict(DEFAULT_HEADERS)
        headers["User-Agent"] = settings.user_agent
        self.session.headers.update(headers)
        self.limiter = AdaptiveRateLimiter(
            interval_sec=settings.request_interval_sec,
            jitter_sec=settings.jitter_sec,
            cooldown_sec=settings.cooldown_sec,
        )
        self._mirror_index = 0
        self.antibot_events = 0

    @property
    def current_mirror(self) -> str:
        return self.settings.mirrors[self._mirror_index]

    def _rotate_mirror(self) -> bool:
        if len(self.settings.mirrors) <= 1:
            return False
        self._mirror_index = (self._mirror_index + 1) % len(self.settings.mirrors)
        return True

    def _decode_html(self, response: requests.Response, content: bytes) -> str:
        encoding = response.encoding or response.apparent_encoding or "cp1251"
        return content.decode(encoding, errors="replace")

    def _is_antibot(self, response: requests.Response, text: str) -> bool:
        status = response.status_code
        if status not in {403, 429, 503}:
            return False
        marker_blob = "\n".join(
            [
                text.lower(),
                response.headers.get("server", "").lower(),
                response.headers.get("cf-ray", "").lower(),
            ]
        )
        return any(marker in marker_blob for marker in CF_MARKERS)

    def _sleep_backoff(self, attempt: int) -> None:
        exp = min(self.settings.backoff_base_sec * (2**attempt), self.settings.backoff_max_sec)
        jitter = random.uniform(0.0, min(exp * 0.25, 8.0))
        time.sleep(exp + jitter)

    def request(
        self,
        method: str,
        url_or_path: str,
        *,
        referer: str | None = None,
        data: dict[str, Any] | None = None,
        allow_redirects: bool = True,
    ) -> FetchResult:
        method_upper = method.upper()
        last_error: Exception | None = None

        for attempt in range(self.settings.max_retries + 1):
            target = to_absolute_url(self.current_mirror, url_or_path)
            headers: dict[str, str] = {}
            if referer:
                headers["Referer"] = referer

            self.limiter.wait_turn()
            started = time.monotonic()
            try:
                response = self.session.request(
                    method=method_upper,
                    url=target,
                    data=data,
                    headers=headers,
                    timeout=(self.settings.connect_timeout_sec, self.settings.read_timeout_sec),
                    allow_redirects=allow_redirects,
                )
            except requests.RequestException as error:
                last_error = error
                if attempt >= self.settings.max_retries:
                    break
                self._rotate_mirror()
                self.limiter.apply_cooldown(multiplier=1.0)
                self._sleep_backoff(attempt)
                continue

            elapsed = time.monotonic() - started
            content = response.content[: self.settings.max_html_bytes]
            truncated = len(response.content) > self.settings.max_html_bytes
            text = self._decode_html(response, content)

            if self._is_antibot(response, text):
                self.antibot_events += 1
                if self.antibot_events >= self.settings.max_antibot_events:
                    raise AntiBotDetectedError(
                        f"Cloudflare/anti-bot детектирован на {response.url} (status={response.status_code})"
                    )
                self._rotate_mirror()
                self.limiter.apply_cooldown(multiplier=2.0)
                self._sleep_backoff(attempt)
                continue

            if response.status_code in RETRY_STATUSES and attempt < self.settings.max_retries:
                self.limiter.apply_cooldown(multiplier=1.0)
                self._sleep_backoff(attempt)
                continue

            final_url = normalize_url(response.url)
            return FetchResult(
                requested_url=target,
                final_url=final_url,
                status_code=response.status_code,
                headers={k.lower(): v for k, v in response.headers.items()},
                content=content,
                text=text,
                mirror=f"{urlparse(target).scheme}://{urlparse(target).netloc}",
                elapsed_sec=elapsed,
                truncated=truncated,
            )

        if last_error:
            raise RuntimeError(
                f"Request failed after retries: {url_or_path}; error={last_error}"
            ) from last_error
        raise RuntimeError(f"Request failed after retries: {url_or_path}")

    def login(self) -> bool:
        if self.settings.mode_effective != "auth":
            return False

        self.request("GET", FORUM_LOGIN_PATH)
        payload = {
            "login_username": self.settings.login,
            "login_password": self.settings.password,
            "login": "Вход",
        }
        response = self.request("POST", FORUM_LOGIN_PATH, data=payload, allow_redirects=False)

        cookies = self.session.cookies.get_dict()
        if "bb_session" in cookies:
            return True
        if "logout.php" in response.text.lower():
            return True
        if "captcha" in response.text.lower() or "подтверждения" in response.text.lower():
            raise AntiBotDetectedError("Логин требует CAPTCHA/доп. подтверждение")
        return False
