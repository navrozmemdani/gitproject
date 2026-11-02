"""Search orchestration.

Pipeline for each query unit (one image, or one representative video frame):
  analysis -> perceptual hashes -> embedding -> providers (concurrently, with timeouts
  and retries) -> candidate download (SSRF-safe) -> visual similarity -> de-duplication
  -> persistence.  Temporary inputs are deleted when the job ends, whatever the outcome.
"""
from __future__ import annotations

import asyncio
import base64
import logging
import uuid
from dataclasses import dataclass, field
from pathlib import Path

import cv2
import httpx
import numpy as np
from sqlalchemy import select

from app.core.config import Settings
from app.database.base import utcnow
from app.database.session import session_factory
from app.models import SearchHistory, SearchJob, SearchResult
from app.providers.base import ProviderHit, ProviderOutcome, ProviderQuery, ProviderStatus, run_provider
from app.providers.registry import ProviderRegistry
from app.providers.util import platform_for
from app.services.aggregation import better, dedupe_key
from app.services.embeddings import ClipEmbedder, Embedder, cosine
from app.services.hashing import compute_hashes, decode_image
from app.services.image_analysis import analyze_image
from app.services.provider_sync import record_outcomes
from app.services.safe_http import fetch_limited
from app.services.similarity import QueryFeatures, build_query_features, score_candidate
from app.services.temp_storage import TempStorage

log = logging.getLogger("visualtrace.search")

PROVENANCE_FIELDS = ("page_title", "description", "published_at", "author_display_name", "author_username",
                     "profile_url", "profile_bio", "website_url", "caption", "image_url", "thumbnail_url")


@dataclass
class QueryUnit:
    image: np.ndarray | None
    jpeg: bytes | None
    temp_name: str | None
    timestamp_ms: int | None


@dataclass
class ScoredHit:
    provider_key: str
    hit: ProviderHit
    score: float | None
    match_type: str
    method: str | None
    hash_distance: int | None
    phash_hex: str | None
    frame_ts: int | None
    found_by: set[str] = field(default_factory=set)


def make_thumbnail_b64(img: np.ndarray, size: int = 160) -> str:
    h, w = img.shape[:2]
    scale = size / max(h, w)
    small = cv2.resize(img, (max(1, int(w * scale)), max(1, int(h * scale))), interpolation=cv2.INTER_AREA)
    ok, buf = cv2.imencode(".jpg", small, [cv2.IMWRITE_JPEG_QUALITY, 70])
    return base64.b64encode(buf.tobytes()).decode() if ok else ""


class SearchService:
    def __init__(self, settings: Settings, registry: ProviderRegistry, storage: TempStorage, embedder: Embedder,
                 http_client_factory=None):  # noqa: ANN001
        self.settings = settings
        self.registry = registry
        self.storage = storage
        self.embedder = embedder
        self._client_factory = http_client_factory or self._default_client

    def _default_client(self) -> httpx.AsyncClient:
        return httpx.AsyncClient(timeout=httpx.Timeout(self.settings.provider_timeout_seconds, connect=5.0),
                                 headers={"User-Agent": self.settings.http_user_agent},
                                 limits=httpx.Limits(max_connections=30))

    # ------------------------------------------------------------------ job state
    async def _update_job(self, search_id: uuid.UUID, **fields) -> None:  # noqa: ANN003
        async with session_factory()() as db:
            job = await db.scalar(select(SearchJob).where(SearchJob.search_id == search_id))
            if job:
                for k, v in fields.items():
                    setattr(job, k, v)
                await db.commit()

    # ------------------------------------------------------------------ main entry
    async def run(self, search_id: uuid.UUID) -> None:
        async with session_factory()() as db:
            search = await db.get(SearchHistory, search_id)
            job = await db.scalar(select(SearchJob).where(SearchJob.search_id == search_id))
            if search is None or job is None:
                return
            if job.status in ("completed", "failed", "partial"):
                return
            job.status, job.stage, job.started_at = "running", "analyzing", utcnow()
            job.attempts += 1
            search.status = "running"
            inputs = list(job.input_files or [])
            options = dict(job.options or {})
            search_type, query_text = search.search_type, search.query_text
            consent, requested = search.third_party_consent, search.providers_requested
            await db.commit()

        try:
            await self._execute(search_id, search_type, query_text, inputs, options, consent, requested)
        except Exception as exc:  # noqa: BLE001
            log.exception("search %s failed", search_id)
            async with session_factory()() as db:
                search = await db.get(SearchHistory, search_id)
                job = await db.scalar(select(SearchJob).where(SearchJob.search_id == search_id))
                if search and job:
                    search.status, search.error_message, search.completed_at = "failed", "The search failed. Please retry.", utcnow()
                    job.status, job.stage, job.error, job.finished_at = "failed", "failed", type(exc).__name__, utcnow()
                    await db.commit()
        finally:
            for item in inputs:
                path = self.storage.resolve(item.get("name", ""))
                self.storage.delete(path)
            await self._update_job(search_id, input_files=[])

    async def _execute(self, search_id: uuid.UUID, search_type: str, query_text: str | None, inputs: list[dict],
                       options: dict, consent: bool, requested: list[str] | None) -> None:
        units: list[QueryUnit] = []
        for item in inputs:
            path = self.storage.resolve(item["name"])
            if path is None:
                continue
            data = Path(path).read_bytes()
            img = decode_image(data)
            if img is not None:
                units.append(QueryUnit(img, data, item["name"], item.get("timestamp_ms")))
        if search_type != "text" and not units:
            raise RuntimeError("no decodable inputs")
        if search_type == "text":
            units = [QueryUnit(None, None, None, None)]

        providers = self.registry.select(requested, options.get("user_enabled_providers"))
        analysis_dict: dict | None = None
        hashes_dict: dict | None = None
        text_embedding = None
        if search_type == "text" and isinstance(self.embedder, ClipEmbedder) and query_text:
            text_embedding = (await asyncio.to_thread(self.embedder.embed_text, [query_text]))[0]

        reports: dict[str, ProviderOutcome] = {}
        merged: dict[str, ScoredHit] = {}
        budget = {"downloads": self.settings.candidate_download_limit * max(1, min(len(units), 4))}

        async with self._client_factory() as client:
            for idx, unit in enumerate(units):
                await self._update_job(search_id, stage=f"searching ({idx + 1}/{len(units)})",
                                       progress=round(0.05 + 0.85 * idx / len(units), 3))
                features: QueryFeatures | None = None
                keywords: list[str] = []
                ocr_text = None
                if unit.image is not None:
                    analysis = await asyncio.to_thread(analyze_image, unit.image, self.embedder)
                    features = await asyncio.to_thread(build_query_features, unit.image, self.embedder)
                    keywords = analysis.keywords
                    ocr_text = analysis.ocr.get("text")
                    if analysis_dict is None:
                        analysis_dict = analysis.to_dict()
                        analysis_dict["embedding_model"] = self.embedder.name
                        hashes_dict = features.hashes.to_dict()
                public_url = None
                if unit.temp_name and self.settings.public_base_url and consent:
                    token = self.storage.sign(unit.temp_name)
                    public_url = f"{self.settings.public_base_url.rstrip('/')}{self.settings.api_prefix}/public/temp/{token}"
                query = ProviderQuery(
                    image_jpeg=unit.jpeg, keywords=keywords, text_query=query_text, ocr_text=ocr_text,
                    public_image_url=public_url, third_party_consent=consent, frame_timestamp_ms=unit.timestamp_ms,
                    max_results=self.settings.max_results_per_provider,
                    phash_hex=features.hashes.to_dict()["phash"] if features else None,
                    exclude_search_id=str(search_id))
                if not query.search_terms() and query.image_jpeg is None:
                    continue
                outcomes = await asyncio.gather(*(run_provider(p, query, client, self.settings) for p in providers))
                for outcome in outcomes:
                    self._merge_report(reports, outcome)
                    for hit in outcome.hits:
                        scored = await self._score(hit, outcome.key, features, text_embedding, client, budget,
                                                   unit.timestamp_ms)
                        key = dedupe_key(hit.source_url)
                        existing = merged.get(key)
                        if existing is None:
                            scored.found_by.add(outcome.key)
                            merged[key] = scored
                        else:
                            existing.found_by.add(outcome.key)
                            if better(scored.score, scored.match_type, existing.score, existing.match_type):
                                scored.found_by = existing.found_by
                                merged[key] = scored

        await self._update_job(search_id, stage="saving", progress=0.95)
        await self._persist(search_id, units, merged, reports, analysis_dict, hashes_dict)

    @staticmethod
    def _merge_report(reports: dict[str, ProviderOutcome], outcome: ProviderOutcome) -> None:
        prev = reports.get(outcome.key)
        if prev is None:
            reports[outcome.key] = ProviderOutcome(outcome.key, outcome.name, outcome.status, list(outcome.hits),
                                                   outcome.message, outcome.duration_ms, outcome.attempts)
            return
        prev.hits.extend(outcome.hits)
        prev.duration_ms += outcome.duration_ms
        prev.attempts += outcome.attempts
        rank = [ProviderStatus.OK, ProviderStatus.MOCK, ProviderStatus.NO_RESULTS]
        if outcome.status in rank and (prev.status not in rank or rank.index(outcome.status) < rank.index(prev.status)):
            prev.status, prev.message = outcome.status, outcome.message

    async def _score(self, hit: ProviderHit, provider_key: str, features: QueryFeatures | None, text_embedding,
                     client: httpx.AsyncClient, budget: dict, frame_ts: int | None) -> ScoredHit:  # noqa: ANN001
        fallback_type = {"exact": "exact", "visual_match": "similar"}.get(hit.provider_match_type or "", "related")
        result = ScoredHit(provider_key, hit, None, fallback_type, None, None, None, frame_ts)
        data = hit.candidate_bytes
        if data is None and (features is not None or text_embedding is not None):
            url = hit.thumbnail_url or hit.image_url
            if url and url.startswith(("http://", "https://")) and budget["downloads"] > 0:
                budget["downloads"] -= 1
                try:
                    data, _ = await fetch_limited(client, url, self.settings.candidate_max_bytes)
                except Exception as exc:  # noqa: BLE001 - unreachable thumbnails just stay unscored
                    log.debug("candidate fetch failed: %s", type(exc).__name__)
        if data is None:
            return result
        img = decode_image(data)
        if img is None:
            return result
        result.phash_hex = compute_hashes(img).to_dict()["phash"]
        if features is not None:
            s = await asyncio.to_thread(score_candidate, features, img, self.embedder)
            result.score, result.match_type, result.method, result.hash_distance = s.score, s.match_type, s.method, s.hash_distance
        elif text_embedding is not None:
            emb = await asyncio.to_thread(self.embedder.embed_image, img)
            sim = cosine(text_embedding, emb)
            result.score = round(max(0.0, min(1.0, (sim - 0.15) / 0.2)), 4)
            result.match_type = "similar" if result.score >= 0.5 else "related"
            result.method = f"{self.embedder.name}:text-image"
        return result

    async def _persist(self, search_id: uuid.UUID, units: list[QueryUnit], merged: dict[str, ScoredHit],
                       reports: dict[str, ProviderOutcome], analysis: dict | None, hashes: dict | None) -> None:
        ordered = sorted(merged.items(), key=lambda kv: (-(kv[1].score if kv[1].score is not None else -1),
                                                         kv[1].hit.relevance_rank))
        async with session_factory()() as db:
            search = await db.get(SearchHistory, search_id)
            job = await db.scalar(select(SearchJob).where(SearchJob.search_id == search_id))
            for key, sh in ordered:
                hit = sh.hit
                platform, category = platform_for(hit.source_url)
                provenance = {f: ("provider" if getattr(hit, f) else "unavailable") for f in PROVENANCE_FIELDS}
                provenance["similarity_score"] = "measured" if sh.score is not None else "unavailable"
                provenance["category"] = "provider" if hit.category else "inferred"
                provenance["platform"] = "provider" if hit.platform else "inferred"
                if hit.via:
                    provenance["via"] = hit.via
                db.add(SearchResult(
                    search_id=search_id, provider_key=sh.provider_key, also_found_by=sorted(sh.found_by),
                    platform=(hit.platform or platform)[:64], category=hit.category or category,
                    match_type=sh.match_type, source_url=hit.source_url, image_url=hit.image_url,
                    thumbnail_url=hit.thumbnail_url, page_title=hit.page_title, description=hit.description,
                    similarity_score=sh.score, similarity_method=sh.method, hash_distance=sh.hash_distance,
                    published_at=hit.published_at, author_display_name=hit.author_display_name,
                    author_username=hit.author_username, profile_url=hit.profile_url, profile_bio=hit.profile_bio,
                    website_url=hit.website_url, caption=hit.caption, frame_timestamp_ms=sh.frame_ts,
                    field_provenance=provenance, relevance_rank=hit.relevance_rank, is_mock=hit.is_mock,
                    dedupe_key=key, image_phash=sh.phash_hex))
            report_list = [o.report() for o in reports.values()]
            statuses = {r["status"] for r in report_list}
            failed = {ProviderStatus.ERROR.value, ProviderStatus.TIMEOUT.value, ProviderStatus.RATE_LIMITED.value}
            searched = any(r["searched"] or r["is_mock"] for r in report_list)
            if not searched and statuses & failed:
                status = "failed"
                search.error_message = "No provider could complete the search. Please try again later."
            elif statuses & failed:
                status = "partial"
            else:
                status = "completed"
            search.status = status
            search.result_count = len(ordered)
            search.provider_reports = report_list
            search.analysis = analysis
            search.perceptual_hashes = hashes
            search.frames_processed = len([u for u in units if u.image is not None]) or None
            search.completed_at = utcnow()
            if job:
                job.status, job.stage, job.progress, job.finished_at = status, "done", 1.0, utcnow()
            await db.commit()
        try:
            await record_outcomes(report_list)
        except Exception:  # noqa: BLE001 - bookkeeping must not fail the search
            log.warning("could not record provider outcomes")
