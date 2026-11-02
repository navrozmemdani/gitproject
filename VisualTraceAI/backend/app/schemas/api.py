from __future__ import annotations

import uuid
from datetime import datetime
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, HttpUrl, field_validator


class ORM(BaseModel):
    model_config = ConfigDict(from_attributes=True)


# ---------------------------------------------------------------------------- search
class ProviderReport(BaseModel):
    key: str
    name: str
    status: str
    message: str | None = None
    result_count: int = 0
    duration_ms: int = 0
    attempts: int = 0
    searched: bool = False
    is_mock: bool = False


class JobStatus(BaseModel):
    status: str
    stage: str
    progress: float
    error: str | None = None


class SearchSummary(ORM):
    id: uuid.UUID
    search_type: str
    status: str
    query_text: str | None = None
    original_filename: str | None = None
    preview_thumbnail_b64: str | None = None
    result_count: int
    created_at: datetime
    completed_at: datetime | None = None


class SearchDetail(SearchSummary):
    media_metadata: dict | None = None
    analysis: dict | None = None
    perceptual_hashes: dict | None = None
    providers_requested: list[str] | None = None
    provider_reports: list[ProviderReport] | None = None
    frame_interval_seconds: float | None = None
    frames_processed: int | None = None
    third_party_consent: bool = False
    error_message: str | None = None
    job_status: JobStatus | None = None
    disclaimer: str = ("Similarity scores estimate visual resemblance only. They do not prove that images share "
                       "an origin or owner, or that they depict the same person.")


class SearchAccepted(BaseModel):
    search_id: uuid.UUID
    status: str
    status_url: str
    results_url: str


class TextSearchRequest(BaseModel):
    query: str = Field(min_length=2, max_length=300)
    providers: list[str] | None = None

    @field_validator("query")
    @classmethod
    def _clean(cls, v: str) -> str:
        return " ".join(v.split())


class SearchResultOut(ORM):
    id: uuid.UUID
    search_id: uuid.UUID
    provider_key: str
    also_found_by: list[str] | None = None
    platform: str
    category: str
    match_type: str
    source_url: str
    image_url: str | None = None
    thumbnail_url: str | None = None
    page_title: str | None = None
    description: str | None = None
    similarity_score: float | None = None
    similarity_method: str | None = None
    hash_distance: int | None = None
    published_at: datetime | None = None
    author_display_name: str | None = None
    author_username: str | None = None
    profile_url: str | None = None
    profile_bio: str | None = None
    website_url: str | None = None
    caption: str | None = None
    frame_timestamp_ms: int | None = None
    field_provenance: dict | None = None
    relevance_rank: int
    is_mock: bool
    created_at: datetime


class ResultsPage(BaseModel):
    search_id: uuid.UUID
    status: str
    total: int
    page: int
    page_size: int
    filter: str
    sort: str
    items: list[SearchResultOut]
    counts: dict[str, int]


class ReportRequest(BaseModel):
    reason: Literal["not_similar", "wrong_source", "wrong_profile", "broken_link", "inappropriate", "other"]
    comment: str | None = Field(default=None, max_length=1000)


# ---------------------------------------------------------------------------- providers
class ProviderInfo(BaseModel):
    key: str
    name: str
    platform: str
    category: str
    supports_reverse_image: bool
    uploads_media: bool
    requires_credentials: bool
    configured: bool
    mode: str
    credential_env: list[str]
    limitations: str
    alternatives: str
    enabled_by_default: bool
    homepage: str
    native_api_configured: bool | None = None
    site_search_fallback: bool | None = None
    globally_disabled: bool = False


class ProvidersResponse(BaseModel):
    mock_mode: bool
    embedding_model: str
    providers: list[ProviderInfo]


# ---------------------------------------------------------------------------- favorites
class FavoriteCreate(BaseModel):
    search_result_id: uuid.UUID | None = None
    search_id: uuid.UUID | None = None
    collection_id: uuid.UUID | None = None
    note: str | None = Field(default=None, max_length=2000)
    # Allows saving a result that only exists locally on the device.
    snapshot: dict | None = None

    @field_validator("snapshot")
    @classmethod
    def _limit_snapshot(cls, v: dict | None) -> dict | None:
        if v is not None and len(str(v)) > 20_000:
            raise ValueError("snapshot too large")
        return v


class FavoriteUpdate(BaseModel):
    note: str | None = Field(default=None, max_length=2000)
    collection_id: uuid.UUID | None = None
    clear_collection: bool = False


class FavoriteOut(ORM):
    id: uuid.UUID
    collection_id: uuid.UUID | None
    search_result_id: uuid.UUID | None
    search_id: uuid.UUID | None
    snapshot: dict
    note: str | None
    created_at: datetime


class CollectionCreate(BaseModel):
    name: str = Field(min_length=1, max_length=120)
    description: str | None = Field(default=None, max_length=500)


class CollectionOut(ORM):
    id: uuid.UUID
    name: str
    description: str | None
    created_at: datetime


# ---------------------------------------------------------------------------- settings / user
class UserSettingsOut(ORM):
    theme: str
    language: str
    enabled_providers: list[str] | None
    search_quality: str
    frame_interval_seconds: float
    max_video_duration_seconds: int
    history_enabled: bool
    cloud_sync_enabled: bool
    third_party_consent: bool


class UserSettingsUpdate(BaseModel):
    theme: Literal["system", "light", "dark"] | None = None
    language: str | None = Field(default=None, pattern=r"^[a-z]{2}(-[A-Z]{2})?$")
    enabled_providers: list[str] | None = None
    search_quality: Literal["fast", "balanced", "thorough"] | None = None
    frame_interval_seconds: float | None = Field(default=None, ge=0.5, le=60)
    max_video_duration_seconds: int | None = Field(default=None, ge=5, le=600)
    history_enabled: bool | None = None
    cloud_sync_enabled: bool | None = None
    third_party_consent: bool | None = None


class UserOut(ORM):
    id: uuid.UUID
    email: str | None
    display_name: str | None
    is_guest: bool
    auth_provider: str
    created_at: datetime


class HealthOut(BaseModel):
    status: str
    version: str
    database: str
    embedding_model: str
    mock_mode: bool
    environment: str


class ImageUrlSearch(BaseModel):
    image_url: HttpUrl
