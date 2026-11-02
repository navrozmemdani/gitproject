from __future__ import annotations

import asyncio
import uuid
from typing import Literal

import cv2
import httpx
import numpy as np
from fastapi import APIRouter, Depends, File, Form, Query, Request, UploadFile
from sqlalchemy import case, func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.deps import get_registry, get_runner, get_storage
from app.core.config import Settings, get_settings
from app.core.errors import (
    AppError,
    ConsentRequiredError,
    FileTooLargeError,
    NotFoundError,
)
from app.core.security import get_current_user, search_rate_limit
from app.database.session import get_db
from app.models import ResultReport, SearchHistory, SearchJob, SearchResult, User, UserSettings
from app.providers.registry import ProviderRegistry
from app.schemas.api import (
    JobStatus,
    ReportRequest,
    ResultsPage,
    SearchAccepted,
    SearchDetail,
    SearchResultOut,
    TextSearchRequest,
)
from app.services.file_validation import clamav_scan, validate_image_bytes, validate_video_header
from app.services.safe_http import fetch_limited
from app.services.search_service import make_thumbnail_b64
from app.services.temp_storage import TempStorage
from app.services.video_frames import extract_frames
from app.workers.runner import JobRunner

router = APIRouter(tags=["search"])


async def read_limited(upload: UploadFile, limit: int) -> bytes:
    buf = bytearray()
    while chunk := await upload.read(1024 * 1024):
        buf.extend(chunk)
        if len(buf) > limit:
            raise FileTooLargeError(f"The file is larger than the {limit // (1024 * 1024)} MB limit.")
    return bytes(buf)


def _parse_providers(raw: str | None, registry: ProviderRegistry) -> list[str] | None:
    if not raw:
        return None
    keys = [k.strip() for k in raw.split(",") if k.strip()]
    unknown = [k for k in keys if registry.get(k) is None]
    if unknown:
        raise AppError(f"Unknown provider(s): {', '.join(unknown)}", code="unknown_provider", status_code=422)
    return keys


async def _user_settings(db: AsyncSession, user: User) -> UserSettings | None:
    return await db.scalar(select(UserSettings).where(UserSettings.user_id == user.id))


async def _create_search(db: AsyncSession, request: Request, runner: JobRunner, user: User, *, search_type: str,
                         inputs: list[dict], requested: list[str] | None, consent: bool, query_text: str | None,
                         filename: str | None, mime: str | None, metadata: dict | None, thumb: str | None,
                         frame_interval: float | None = None) -> SearchAccepted:
    us = await _user_settings(db, user)
    search = SearchHistory(user_id=user.id, search_type=search_type, status="queued", query_text=query_text,
                           original_filename=(filename or "")[:255] or None, mime_type=mime,
                           preview_thumbnail_b64=thumb if (us is None or us.history_enabled) else None,
                           media_metadata=metadata, providers_requested=requested,
                           third_party_consent=consent, frame_interval_seconds=frame_interval)
    db.add(search)
    await db.flush()
    db.add(SearchJob(search_id=search.id, status="queued", stage="queued", input_files=inputs,
                     options={"user_enabled_providers": us.enabled_providers if us else None}))
    await db.commit()
    runner.submit(search.id)
    base = str(request.url_for("get_search", search_id=str(search.id)))
    return SearchAccepted(search_id=search.id, status="queued", status_url=base, results_url=base + "/results")


@router.post("/search/image", response_model=SearchAccepted, status_code=202)
async def search_image(
    request: Request,
    file: UploadFile | None = File(default=None, description="JPG, JPEG, PNG or WEBP"),
    image_url: str | None = Form(default=None, max_length=2048),
    providers: str | None = Form(default=None),
    third_party_consent: bool = Form(default=False),
    query_text: str | None = Form(default=None, max_length=300),
    save_preview: bool = Form(default=True),
    user: User = Depends(search_rate_limit),
    db: AsyncSession = Depends(get_db),
    settings: Settings = Depends(get_settings),
    storage: TempStorage = Depends(get_storage),
    registry: ProviderRegistry = Depends(get_registry),
    runner: JobRunner = Depends(get_runner),
) -> SearchAccepted:
    requested = _parse_providers(providers, registry)
    if file is None and not image_url:
        raise AppError("Choose an image or paste an image link.", code="missing_image", status_code=422)
    if file is not None:
        data = await read_limited(file, settings.max_image_bytes)
        filename, declared = file.filename, file.content_type
    else:
        async with httpx.AsyncClient(timeout=15, headers={"User-Agent": settings.http_user_agent}) as client:
            data, declared = await fetch_limited(client, image_url, settings.max_image_bytes)
        filename = None
        declared = declared if declared and declared.startswith("image/") else None
    validated = await asyncio.to_thread(validate_image_bytes, data, filename, declared, settings)
    await clamav_scan(data, settings)
    path = storage.save(validated.sanitized_jpeg, "jpg")
    img = cv2.imdecode(np.frombuffer(validated.sanitized_jpeg, dtype=np.uint8), cv2.IMREAD_COLOR)
    metadata = {"width": validated.width, "height": validated.height, "format": validated.kind,
                "file_size_bytes": validated.original_size, "exif": validated.exif,
                "source": "url" if image_url and file is None else "upload"}
    thumb = make_thumbnail_b64(img) if save_preview else None
    return await _create_search(db, request, runner, user, search_type="image",
                                inputs=[{"name": path.name, "timestamp_ms": None}], requested=requested,
                                consent=third_party_consent, query_text=query_text, filename=filename or image_url,
                                mime=validated.mime, metadata=metadata, thumb=thumb)


@router.post("/search/video", response_model=SearchAccepted, status_code=202)
async def search_video(
    request: Request,
    frames: list[UploadFile] | None = File(default=None, description="Representative frames extracted on-device"),
    frame_timestamps_ms: str | None = Form(default=None, description="Comma separated, one per frame"),
    video: UploadFile | None = File(default=None, description="Full video (requires upload_consent=true)"),
    upload_consent: bool = Form(default=False),
    frame_interval_seconds: float = Form(default=3.0, ge=0.5, le=60),
    max_duration_seconds: int = Form(default=120, ge=5, le=600),
    duration_ms: int | None = Form(default=None, ge=0),
    original_filename: str | None = Form(default=None, max_length=255),
    query_text: str | None = Form(default=None, max_length=300),
    providers: str | None = Form(default=None),
    third_party_consent: bool = Form(default=False),
    save_preview: bool = Form(default=True),
    user: User = Depends(search_rate_limit),
    db: AsyncSession = Depends(get_db),
    settings: Settings = Depends(get_settings),
    storage: TempStorage = Depends(get_storage),
    registry: ProviderRegistry = Depends(get_registry),
    runner: JobRunner = Depends(get_runner),
) -> SearchAccepted:
    requested = _parse_providers(providers, registry)
    max_duration = min(max_duration_seconds, settings.max_video_duration_seconds)
    interval = max(frame_interval_seconds, settings.min_frame_interval_seconds)
    inputs: list[dict] = []
    first_img = None
    metadata: dict = {"frame_interval_seconds": interval, "max_duration_seconds": max_duration}

    if frames:
        if len(frames) > settings.max_frames_per_video:
            raise AppError(f"At most {settings.max_frames_per_video} frames can be searched.", code="too_many_frames",
                           status_code=422)
        stamps = [int(s) for s in (frame_timestamps_ms or "").split(",") if s.strip().lstrip("-").isdigit()]
        if len(stamps) != len(frames):
            raise AppError("frame_timestamps_ms must contain one timestamp per frame.", code="validation_error",
                           status_code=422)
        try:
            for f, ts in zip(frames, stamps, strict=True):
                data = await read_limited(f, settings.max_frame_bytes)
                v = await asyncio.to_thread(validate_image_bytes, data, f.filename, f.content_type, settings)
                inputs.append({"name": storage.save(v.sanitized_jpeg, "jpg").name, "timestamp_ms": max(0, ts)})
                if first_img is None:
                    first_img = cv2.imdecode(np.frombuffer(v.sanitized_jpeg, np.uint8), cv2.IMREAD_COLOR)
        except Exception:
            for item in inputs:
                storage.delete(storage.resolve(item["name"]))
            raise
        metadata.update({"source": "device_frames", "duration_ms": duration_ms, "frames_received": len(inputs)})
        mime, filename = "video/*", original_filename
    elif video is not None:
        if not upload_consent:
            raise ConsentRequiredError("Uploading a full video requires your explicit consent. "
                                       "Alternatively, let the app extract frames on your device.")
        head = await video.read(64)
        rest = await read_limited(video, settings.max_video_bytes)
        data = head + rest
        vv = validate_video_header(head, video.filename, video.content_type, len(data), settings)
        await clamav_scan(data, settings)
        vpath = storage.save(data, vv.kind)
        del data
        try:
            info, extracted = await asyncio.to_thread(extract_frames, vpath, interval, max_duration,
                                                      settings.max_frames_per_video)
        finally:
            storage.delete(vpath)  # the full video is never kept after frame extraction
        for fr in extracted:
            ok, buf = cv2.imencode(".jpg", fr.image, [cv2.IMWRITE_JPEG_QUALITY, 90])
            if ok:
                inputs.append({"name": storage.save(buf.tobytes(), "jpg").name, "timestamp_ms": fr.timestamp_ms})
        first_img = extracted[0].image
        metadata.update({"source": "server_extracted", "duration_ms": int(info.duration_seconds * 1000),
                         "fps": info.fps, "width": info.width, "height": info.height, "format": vv.kind,
                         "frames_extracted": len(inputs)})
        mime, filename = vv.mime, video.filename
    else:
        raise AppError("Provide extracted frames or a video file.", code="missing_video", status_code=422)

    thumb = make_thumbnail_b64(first_img) if (save_preview and first_img is not None) else None
    return await _create_search(db, request, runner, user, search_type="video", inputs=inputs, requested=requested,
                                consent=third_party_consent, query_text=query_text, filename=filename, mime=mime,
                                metadata=metadata, thumb=thumb, frame_interval=interval)


@router.post("/search/text", response_model=SearchAccepted, status_code=202)
async def search_text(
    request: Request,
    body: TextSearchRequest,
    user: User = Depends(search_rate_limit),
    db: AsyncSession = Depends(get_db),
    registry: ProviderRegistry = Depends(get_registry),
    runner: JobRunner = Depends(get_runner),
) -> SearchAccepted:
    requested = _parse_providers(",".join(body.providers) if body.providers else None, registry)
    return await _create_search(db, request, runner, user, search_type="text", inputs=[], requested=requested,
                                consent=False, query_text=body.query, filename=None, mime=None, metadata=None,
                                thumb=None)


async def _owned_search(db: AsyncSession, search_id: uuid.UUID, user: User) -> SearchHistory:
    search = await db.get(SearchHistory, search_id)
    if search is None or search.user_id != user.id:
        raise NotFoundError("Search not found.")
    return search


@router.get("/search/{search_id}", response_model=SearchDetail, name="get_search")
async def get_search(search_id: uuid.UUID, user: User = Depends(get_current_user),
                     db: AsyncSession = Depends(get_db)) -> SearchDetail:
    search = await _owned_search(db, search_id, user)
    job = await db.scalar(select(SearchJob).where(SearchJob.search_id == search.id))
    detail = SearchDetail.model_validate(search)
    if job:
        detail.job_status = JobStatus(status=job.status, stage=job.stage, progress=job.progress, error=job.error)
    return detail


FILTERS = {
    "all": None,
    "exact": SearchResult.match_type == "exact",
    "similar": SearchResult.match_type.in_(("similar", "cropped")),
    "social": SearchResult.category == "social",
    "websites": SearchResult.category == "website",
    "news": SearchResult.category == "news",
}


@router.get("/search/{search_id}/results", response_model=ResultsPage)
async def get_results(
    search_id: uuid.UUID,
    filter: Literal["all", "exact", "similar", "social", "websites", "news"] = "all",  # noqa: A002
    sort: Literal["similarity", "recent", "relevance"] = "similarity",
    page: int = Query(1, ge=1),
    page_size: int = Query(30, ge=1, le=100),
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> ResultsPage:
    search = await _owned_search(db, search_id, user)
    base = select(SearchResult).where(SearchResult.search_id == search.id)
    if FILTERS[filter] is not None:
        base = base.where(FILTERS[filter])
    if sort == "similarity":
        order = [SearchResult.similarity_score.desc().nulls_last(), SearchResult.relevance_rank]
    elif sort == "recent":
        order = [SearchResult.published_at.desc().nulls_last(), SearchResult.similarity_score.desc().nulls_last()]
    else:
        order = [SearchResult.relevance_rank, SearchResult.similarity_score.desc().nulls_last()]
    total = await db.scalar(select(func.count()).select_from(base.subquery())) or 0
    rows = (await db.scalars(base.order_by(*order).offset((page - 1) * page_size).limit(page_size))).all()
    count_cols = [func.count().label("all")] + [
        func.sum(case((cond, 1), else_=0)).label(name) for name, cond in FILTERS.items() if cond is not None]
    counts_row = (await db.execute(select(*count_cols).where(SearchResult.search_id == search.id))).one()
    counts = {k: int(v or 0) for k, v in counts_row._mapping.items()}
    return ResultsPage(search_id=search.id, status=search.status, total=total, page=page, page_size=page_size,
                       filter=filter, sort=sort, items=[SearchResultOut.model_validate(r) for r in rows],
                       counts=counts)


@router.post("/results/{result_id}/report", status_code=204)
async def report_result(result_id: uuid.UUID, body: ReportRequest, user: User = Depends(get_current_user),
                        db: AsyncSession = Depends(get_db)) -> None:
    result = await db.get(SearchResult, result_id)
    if result is None:
        raise NotFoundError("Result not found.")
    await _owned_search(db, result.search_id, user)
    db.add(ResultReport(result_id=result.id, user_id=user.id, reason=body.reason, comment=body.comment))
    result.report_count += 1
