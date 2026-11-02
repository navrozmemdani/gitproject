from __future__ import annotations

from fastapi import APIRouter, Depends
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.deps import get_registry
from app.core.errors import AppError
from app.core.security import get_current_user
from app.database.base import utcnow
from app.database.session import get_db
from app.models import User, UserSettings
from app.providers.registry import ProviderRegistry
from app.schemas.api import UserOut, UserSettingsOut, UserSettingsUpdate

router = APIRouter(tags=["account"])


async def _settings(db: AsyncSession, user: User) -> UserSettings:
    s = await db.scalar(select(UserSettings).where(UserSettings.user_id == user.id))
    if s is None:
        s = UserSettings(user_id=user.id)
        db.add(s)
        await db.flush()
    return s


@router.get("/me", response_model=UserOut)
async def me(user: User = Depends(get_current_user)) -> User:
    return user


@router.delete("/me", status_code=204)
async def delete_me(user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)) -> None:
    """Deletes the account's server-side data: history, results, favourites, collections, settings."""
    await db.delete(user)


@router.get("/settings", response_model=UserSettingsOut)
async def get_user_settings(user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)) -> UserSettings:
    return await _settings(db, user)


@router.put("/settings", response_model=UserSettingsOut)
async def update_user_settings(body: UserSettingsUpdate, user: User = Depends(get_current_user),
                               db: AsyncSession = Depends(get_db),
                               registry: ProviderRegistry = Depends(get_registry)) -> UserSettings:
    s = await _settings(db, user)
    data = body.model_dump(exclude_unset=True)
    if "enabled_providers" in data and data["enabled_providers"] is not None:
        unknown = [k for k in data["enabled_providers"] if registry.get(k) is None]
        if unknown:
            raise AppError(f"Unknown provider(s): {', '.join(unknown)}", code="unknown_provider", status_code=422)
    if data.get("cloud_sync_enabled") and user.is_guest:
        raise AppError("Cloud sync requires an account.", code="account_required", status_code=403)
    if "third_party_consent" in data and data["third_party_consent"] != s.third_party_consent:
        s.consent_updated_at = utcnow()
    for k, v in data.items():
        setattr(s, k, v)
    await db.flush()
    return s
