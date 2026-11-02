from __future__ import annotations

import uuid
from typing import Literal

from fastapi import APIRouter, Depends, Query
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.errors import NotFoundError
from app.core.security import get_current_user
from app.database.session import get_db
from app.models import SearchHistory, User
from app.schemas.api import SearchSummary

router = APIRouter(tags=["history"])


@router.get("/history")
async def list_history(
    type: Literal["all", "image", "video", "text"] = "all",  # noqa: A002
    page: int = Query(1, ge=1),
    page_size: int = Query(20, ge=1, le=100),
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> dict:
    stmt = select(SearchHistory).where(SearchHistory.user_id == user.id)
    if type != "all":
        stmt = stmt.where(SearchHistory.search_type == type)
    total = await db.scalar(select(func.count()).select_from(stmt.subquery())) or 0
    rows = (await db.scalars(stmt.order_by(SearchHistory.created_at.desc())
                             .offset((page - 1) * page_size).limit(page_size))).all()
    return {"total": total, "page": page, "page_size": page_size,
            "items": [SearchSummary.model_validate(r).model_dump(mode="json") for r in rows]}


@router.delete("/history/{search_id}", status_code=204)
async def delete_history_item(search_id: uuid.UUID, user: User = Depends(get_current_user),
                              db: AsyncSession = Depends(get_db)) -> None:
    search = await db.get(SearchHistory, search_id)
    if search is None or search.user_id != user.id:
        raise NotFoundError("Search not found.")
    await db.delete(search)


@router.delete("/history", status_code=204)
async def clear_history(user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)) -> None:
    ids = (await db.scalars(select(SearchHistory.id).where(SearchHistory.user_id == user.id))).all()
    for sid in ids:
        obj = await db.get(SearchHistory, sid)
        if obj is not None:
            await db.delete(obj)  # ORM cascade removes jobs, results and reports
