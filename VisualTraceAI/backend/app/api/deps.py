from __future__ import annotations

from fastapi import Request

from app.providers.registry import ProviderRegistry
from app.services.search_service import SearchService
from app.services.temp_storage import TempStorage
from app.workers.runner import JobRunner


def get_registry(request: Request) -> ProviderRegistry:
    return request.app.state.registry


def get_storage(request: Request) -> TempStorage:
    return request.app.state.storage


def get_runner(request: Request) -> JobRunner:
    return request.app.state.runner


def get_service(request: Request) -> SearchService:
    return request.app.state.service
