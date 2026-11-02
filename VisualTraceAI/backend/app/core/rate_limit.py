"""Sliding-window rate limiter.

The in-memory implementation is per-process; for multi-instance deployments put a
shared limiter (e.g. Redis, API gateway) in front, as described in docs/DEPLOYMENT.md.
"""
from __future__ import annotations

import asyncio
import time
from collections import defaultdict, deque

from app.core.errors import RateLimitedError


class SlidingWindowLimiter:
    def __init__(self) -> None:
        self._hits: dict[str, deque[float]] = defaultdict(deque)
        self._lock = asyncio.Lock()

    async def hit(self, key: str, limit: int, window_seconds: float) -> None:
        if limit <= 0:
            return
        now = time.monotonic()
        async with self._lock:
            q = self._hits[key]
            while q and now - q[0] > window_seconds:
                q.popleft()
            if len(q) >= limit:
                retry_after = max(1, int(window_seconds - (now - q[0])) + 1)
                raise RateLimitedError(
                    "Too many requests. Please wait before trying again.",
                    details={"retry_after": retry_after},
                )
            q.append(now)

    def reset(self) -> None:
        self._hits.clear()


limiter = SlidingWindowLimiter()
