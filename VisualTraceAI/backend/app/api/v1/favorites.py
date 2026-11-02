from __future__ import annotations

import uuid

from fastapi import APIRouter, Depends
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.errors import AppError, NotFoundError
from app.core.security import require_account
from app.database.session import get_db
from app.models import Collection, Favorite, SearchHistory, SearchResult, User
from app.schemas.api import (
    CollectionCreate,
    CollectionOut,
    FavoriteCreate,
    FavoriteOut,
    FavoriteUpdate,
    SearchResultOut,
)

router = APIRouter(tags=["favorites"])


async def _own_collection(db: AsyncSession, cid: uuid.UUID | None, user: User) -> None:
    if cid is None:
        return
    col = await db.get(Collection, cid)
    if col is None or col.user_id != user.id:
        raise NotFoundError("Collection not found.")


@router.post("/favorites", response_model=FavoriteOut, status_code=201)
async def create_favorite(body: FavoriteCreate, user: User = Depends(require_account),
                          db: AsyncSession = Depends(get_db)) -> Favorite:
    await _own_collection(db, body.collection_id, user)
    snapshot = body.snapshot
    search_id = body.search_id
    if body.search_result_id:
        result = await db.get(SearchResult, body.search_result_id)
        search = await db.get(SearchHistory, result.search_id) if result else None
        if result is None or search is None or search.user_id != user.id:
            raise NotFoundError("Result not found.")
        snapshot = SearchResultOut.model_validate(result).model_dump(mode="json")
        search_id = result.search_id
    elif search_id:
        search = await db.get(SearchHistory, search_id)
        if search is None or search.user_id != user.id:
            raise NotFoundError("Search not found.")
        snapshot = snapshot or {"type": "search", "search_type": search.search_type,
                                "result_count": search.result_count, "created_at": search.created_at.isoformat()}
    if not snapshot:
        raise AppError("Provide a search_result_id, search_id or snapshot.", code="validation_error", status_code=422)
    fav = Favorite(user_id=user.id, collection_id=body.collection_id, search_result_id=body.search_result_id,
                   search_id=search_id, snapshot=snapshot, note=body.note)
    db.add(fav)
    await db.flush()
    await db.refresh(fav)
    return fav


@router.get("/favorites", response_model=list[FavoriteOut])
async def list_favorites(collection_id: uuid.UUID | None = None, user: User = Depends(require_account),
                         db: AsyncSession = Depends(get_db)) -> list[Favorite]:
    stmt = select(Favorite).where(Favorite.user_id == user.id)
    if collection_id:
        stmt = stmt.where(Favorite.collection_id == collection_id)
    return list((await db.scalars(stmt.order_by(Favorite.created_at.desc()))).all())


@router.patch("/favorites/{favorite_id}", response_model=FavoriteOut)
async def update_favorite(favorite_id: uuid.UUID, body: FavoriteUpdate, user: User = Depends(require_account),
                          db: AsyncSession = Depends(get_db)) -> Favorite:
    fav = await db.get(Favorite, favorite_id)
    if fav is None or fav.user_id != user.id:
        raise NotFoundError("Saved result not found.")
    if body.note is not None:
        fav.note = body.note
    if body.clear_collection:
        fav.collection_id = None
    elif body.collection_id is not None:
        await _own_collection(db, body.collection_id, user)
        fav.collection_id = body.collection_id
    await db.flush()
    await db.refresh(fav)
    return fav


@router.delete("/favorites/{favorite_id}", status_code=204)
async def delete_favorite(favorite_id: uuid.UUID, user: User = Depends(require_account),
                          db: AsyncSession = Depends(get_db)) -> None:
    fav = await db.get(Favorite, favorite_id)
    if fav is None or fav.user_id != user.id:
        raise NotFoundError("Saved result not found.")
    await db.delete(fav)


@router.get("/collections", response_model=list[CollectionOut])
async def list_collections(user: User = Depends(require_account), db: AsyncSession = Depends(get_db)):  # noqa: ANN201
    return list((await db.scalars(select(Collection).where(Collection.user_id == user.id)
                                  .order_by(Collection.name))).all())


@router.post("/collections", response_model=CollectionOut, status_code=201)
async def create_collection(body: CollectionCreate, user: User = Depends(require_account),
                            db: AsyncSession = Depends(get_db)) -> Collection:
    col = Collection(user_id=user.id, name=body.name.strip(), description=body.description)
    db.add(col)
    try:
        await db.flush()
    except IntegrityError as exc:
        raise AppError("A collection with this name already exists.", code="duplicate", status_code=409) from exc
    await db.refresh(col)
    return col


@router.delete("/collections/{collection_id}", status_code=204)
async def delete_collection(collection_id: uuid.UUID, user: User = Depends(require_account),
                            db: AsyncSession = Depends(get_db)) -> None:
    col = await db.get(Collection, collection_id)
    if col is None or col.user_id != user.id:
        raise NotFoundError("Collection not found.")
    favs = (await db.scalars(select(Favorite).where(Favorite.collection_id == col.id))).all()
    for f in favs:
        f.collection_id = None
    await db.delete(col)
