from __future__ import annotations

from fastapi import APIRouter, Depends, Request
from fastapi.responses import FileResponse
from sqlalchemy import text

from app import __version__
from app.api.deps import get_registry, get_storage
from app.core.config import Settings, get_settings
from app.core.errors import NotFoundError
from app.core.security import require_client_key
from app.database.session import session_factory
from app.providers.registry import ProviderRegistry
from app.schemas.api import HealthOut, ProviderInfo, ProvidersResponse
from app.services.temp_storage import TempStorage

router = APIRouter(tags=["system"])


@router.get("/health", response_model=HealthOut)
async def health(request: Request, settings: Settings = Depends(get_settings)) -> HealthOut:
    db_state = "ok"
    try:
        async with session_factory()() as db:
            await db.execute(text("SELECT 1"))
    except Exception:  # noqa: BLE001
        db_state = "unavailable"
    return HealthOut(status="ok" if db_state == "ok" else "degraded", version=__version__, database=db_state,
                     embedding_model=request.app.state.embedder.name, mock_mode=settings.provider_mock_mode,
                     environment=settings.environment)


@router.get("/providers", response_model=ProvidersResponse, dependencies=[Depends(require_client_key)])
async def providers(request: Request, registry: ProviderRegistry = Depends(get_registry),
                    settings: Settings = Depends(get_settings)) -> ProvidersResponse:
    return ProvidersResponse(mock_mode=settings.provider_mock_mode, embedding_model=request.app.state.embedder.name,
                             providers=[ProviderInfo(**p.describe(), globally_disabled=p.key in registry.disabled)
                                        for p in registry.all()])


@router.get("/public/temp/{token}", include_in_schema=False)
async def public_temp(token: str, storage: TempStorage = Depends(get_storage)) -> FileResponse:
    """Short-lived signed link used only by URL-based reverse image providers."""
    name = storage.verify(token)
    path = storage.resolve(name) if name else None
    if path is None:
        raise NotFoundError("Link expired.")
    return FileResponse(path, media_type="image/jpeg", headers={"Cache-Control": "no-store"})
