from __future__ import annotations

import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app import __version__
from app.api.v1 import account, favorites, history, search, system
from app.core.config import Settings, get_settings
from app.core.errors import install_error_handlers
from app.core.logging import RequestLoggingMiddleware, configure_logging
from app.database.session import dispose_engine, init_engine
from app.providers.registry import ProviderRegistry
from app.services.embeddings import Embedder, get_embedder
from app.services.provider_sync import sync_providers
from app.services.search_service import SearchService
from app.services.temp_storage import TempStorage
from app.workers.runner import JobRunner

log = logging.getLogger("visualtrace")


def create_app(settings: Settings | None = None, *, embedder: Embedder | None = None,
               http_client_factory=None, init_db: bool = True) -> FastAPI:  # noqa: ANN001
    settings = settings or get_settings()
    problems = settings.validate_for_runtime()
    if problems:
        raise RuntimeError("Invalid production configuration: " + "; ".join(problems))

    @asynccontextmanager
    async def lifespan(app: FastAPI):  # noqa: ANN202
        if init_db:
            init_engine(settings.database_url)
        await sync_providers(app.state.registry)
        await app.state.runner.recover()
        app.state.runner.start_sweeper()
        if settings.provider_mock_mode:
            log.warning("PROVIDER_MOCK_MODE is ON: unconfigured providers return flagged mock results")
        yield
        await app.state.runner.shutdown()
        if init_db:
            await dispose_engine()

    configure_logging(settings.debug)
    app = FastAPI(title="VisualTrace AI API", version=__version__, lifespan=lifespan,
                  docs_url=None if settings.is_production else "/docs",
                  redoc_url=None, openapi_url=None if settings.is_production else "/openapi.json")
    storage = TempStorage(settings)
    registry = ProviderRegistry(settings)
    emb = embedder or get_embedder()
    service = SearchService(settings, registry, storage, emb, http_client_factory)
    app.state.settings = settings
    app.state.storage = storage
    app.state.registry = registry
    app.state.embedder = emb
    app.state.service = service
    app.state.runner = JobRunner(service, storage)

    app.add_middleware(RequestLoggingMiddleware)
    if settings.cors_origins:
        app.add_middleware(CORSMiddleware, allow_origins=settings.cors_origins, allow_methods=["*"],
                           allow_headers=["*"])
    install_error_handlers(app)
    for module in (system, search, history, favorites, account):
        app.include_router(module.router, prefix=settings.api_prefix)
    return app

