"""Keeps the ``search_providers`` table in sync with the provider registry.

Operators can disable a provider globally by setting ``enabled = false`` on its row."""
from __future__ import annotations

from sqlalchemy import select

from app.database.base import utcnow
from app.database.session import session_factory
from app.models import SearchProvider
from app.providers.registry import ProviderRegistry


async def sync_providers(registry: ProviderRegistry) -> None:
    async with session_factory()() as db:
        rows = {r.key: r for r in (await db.scalars(select(SearchProvider))).all()}
        for p in registry.all():
            row = rows.get(p.key)
            if row is None:
                row = SearchProvider(key=p.key, name=p.name, category=p.category.value, enabled=True)
                db.add(row)
            row.name, row.category = p.name, p.category.value
            row.requires_credentials = p.requires_credentials
            row.supports_reverse_image = p.supports_reverse_image
            row.limitations = p.limitations or None
            row.last_status = "configured" if p.is_configured() else "not_configured"
            row.last_checked_at = utcnow()
            if row.enabled is False:
                registry.disabled.add(p.key)
        await db.commit()


async def record_outcomes(reports: list[dict]) -> None:
    async with session_factory()() as db:
        rows = {r.key: r for r in (await db.scalars(select(SearchProvider))).all()}
        for rep in reports:
            row = rows.get(rep["key"])
            if row is not None:
                row.last_status = rep["status"]
                row.last_error = rep["message"] if rep["status"] in ("error", "timeout", "rate_limited") else None
                row.last_checked_at = utcnow()
        await db.commit()
