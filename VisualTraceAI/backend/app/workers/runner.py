"""In-process background job runner.

Jobs are persisted in ``search_jobs`` so status survives restarts; on startup any job
left ``running``/``queued`` by a crashed process is marked failed and its temporary
inputs are swept. For horizontal scaling, replace with a queue worker (see docs)."""
from __future__ import annotations

import asyncio
import logging
import uuid

from sqlalchemy import select

from app.database.base import utcnow
from app.database.session import session_factory
from app.models import SearchHistory, SearchJob
from app.services.search_service import SearchService
from app.services.temp_storage import TempStorage

log = logging.getLogger("visualtrace.worker")


class JobRunner:
    def __init__(self, service: SearchService, storage: TempStorage, concurrency: int = 4):
        self.service = service
        self.storage = storage
        self._sem = asyncio.Semaphore(concurrency)
        self._tasks: set[asyncio.Task] = set()
        self._sweeper: asyncio.Task | None = None

    def submit(self, search_id: uuid.UUID) -> None:
        task = asyncio.create_task(self._run(search_id))
        self._tasks.add(task)
        task.add_done_callback(self._tasks.discard)

    async def _run(self, search_id: uuid.UUID) -> None:
        async with self._sem:
            await self.service.run(search_id)

    async def drain(self) -> None:
        while self._tasks:
            await asyncio.gather(*list(self._tasks), return_exceptions=True)

    async def recover(self) -> None:
        async with session_factory()() as db:
            jobs = (await db.scalars(select(SearchJob).where(SearchJob.status.in_(("queued", "running"))))).all()
            for job in jobs:
                job.status, job.stage, job.error, job.finished_at = "failed", "interrupted", "server restarted", utcnow()
                job.input_files = []
                search = await db.get(SearchHistory, job.search_id)
                if search:
                    search.status = "failed"
                    search.error_message = "The search was interrupted. Please search again."
            await db.commit()
        removed = self.storage.sweep()
        if jobs or removed:
            log.info("recovered %d interrupted jobs, swept %d temp files", len(jobs), removed)

    def start_sweeper(self, interval: float = 60.0) -> None:
        async def loop() -> None:
            while True:
                await asyncio.sleep(interval)
                try:
                    self.storage.sweep()
                except Exception:  # noqa: BLE001
                    log.exception("temp sweep failed")

        self._sweeper = asyncio.create_task(loop())

    async def shutdown(self) -> None:
        if self._sweeper:
            self._sweeper.cancel()
        for t in list(self._tasks):
            t.cancel()
        await asyncio.gather(*list(self._tasks), return_exceptions=True)
