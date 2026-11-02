"""SQLAlchemy ORM models. Primary keys are UUIDs; all tables carry timestamps."""
from __future__ import annotations

import uuid
from datetime import datetime

from sqlalchemy import (
    Boolean,
    DateTime,
    Float,
    ForeignKey,
    Index,
    Integer,
    String,
    Text,
    UniqueConstraint,
    Uuid,
)
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database.base import Base, JSONType, TimestampMixin, UUIDPrimaryKeyMixin


class User(UUIDPrimaryKeyMixin, TimestampMixin, Base):
    __tablename__ = "users"

    firebase_uid: Mapped[str | None] = mapped_column(String(128), unique=True, index=True)
    guest_id: Mapped[str | None] = mapped_column(String(64), unique=True, index=True)
    email: Mapped[str | None] = mapped_column(String(320), index=True)
    display_name: Mapped[str | None] = mapped_column(String(200))
    is_guest: Mapped[bool] = mapped_column(Boolean, default=True, nullable=False)
    auth_provider: Mapped[str] = mapped_column(String(32), default="guest", nullable=False)
    last_seen_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))

    settings: Mapped[UserSettings | None] = relationship(back_populates="user", cascade="all, delete-orphan", uselist=False)
    searches: Mapped[list[SearchHistory]] = relationship(back_populates="user", cascade="all, delete-orphan")
    collections: Mapped[list[Collection]] = relationship(back_populates="user", cascade="all, delete-orphan")
    favorites: Mapped[list[Favorite]] = relationship(back_populates="user", cascade="all, delete-orphan")


class UserSettings(UUIDPrimaryKeyMixin, TimestampMixin, Base):
    __tablename__ = "user_settings"

    user_id: Mapped[uuid.UUID] = mapped_column(Uuid, ForeignKey("users.id", ondelete="CASCADE"), unique=True, nullable=False)
    theme: Mapped[str] = mapped_column(String(16), default="system", nullable=False)
    language: Mapped[str] = mapped_column(String(16), default="en", nullable=False)
    enabled_providers: Mapped[list[str] | None] = mapped_column(JSONType)
    search_quality: Mapped[str] = mapped_column(String(16), default="balanced", nullable=False)
    frame_interval_seconds: Mapped[float] = mapped_column(Float, default=3.0, nullable=False)
    max_video_duration_seconds: Mapped[int] = mapped_column(Integer, default=120, nullable=False)
    history_enabled: Mapped[bool] = mapped_column(Boolean, default=True, nullable=False)
    cloud_sync_enabled: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    third_party_consent: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    consent_updated_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))

    user: Mapped[User] = relationship(back_populates="settings")


class SearchProvider(UUIDPrimaryKeyMixin, TimestampMixin, Base):
    __tablename__ = "search_providers"

    key: Mapped[str] = mapped_column(String(64), unique=True, nullable=False)
    name: Mapped[str] = mapped_column(String(120), nullable=False)
    category: Mapped[str] = mapped_column(String(32), nullable=False)  # reverse_image | web | social | index
    enabled: Mapped[bool] = mapped_column(Boolean, default=True, nullable=False)
    requires_credentials: Mapped[bool] = mapped_column(Boolean, default=True, nullable=False)
    supports_reverse_image: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    last_status: Mapped[str] = mapped_column(String(32), default="unknown", nullable=False)
    last_error: Mapped[str | None] = mapped_column(Text)
    last_checked_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    limitations: Mapped[str | None] = mapped_column(Text)


class SearchHistory(UUIDPrimaryKeyMixin, TimestampMixin, Base):
    __tablename__ = "search_history"
    __table_args__ = (Index("ix_search_history_user_created", "user_id", "created_at"),)

    user_id: Mapped[uuid.UUID] = mapped_column(Uuid, ForeignKey("users.id", ondelete="CASCADE"), nullable=False)
    search_type: Mapped[str] = mapped_column(String(16), nullable=False)  # image | video | text
    status: Mapped[str] = mapped_column(String(16), default="queued", nullable=False, index=True)
    query_text: Mapped[str | None] = mapped_column(Text)
    original_filename: Mapped[str | None] = mapped_column(String(255))
    mime_type: Mapped[str | None] = mapped_column(String(64))
    preview_thumbnail_b64: Mapped[str | None] = mapped_column(Text)
    media_metadata: Mapped[dict | None] = mapped_column(JSONType)
    analysis: Mapped[dict | None] = mapped_column(JSONType)
    perceptual_hashes: Mapped[dict | None] = mapped_column(JSONType)
    providers_requested: Mapped[list[str] | None] = mapped_column(JSONType)
    provider_reports: Mapped[list[dict] | None] = mapped_column(JSONType)
    result_count: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    frame_interval_seconds: Mapped[float | None] = mapped_column(Float)
    frames_processed: Mapped[int | None] = mapped_column(Integer)
    third_party_consent: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    completed_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    error_message: Mapped[str | None] = mapped_column(Text)

    user: Mapped[User] = relationship(back_populates="searches")
    job: Mapped[SearchJob | None] = relationship(back_populates="search", cascade="all, delete-orphan", uselist=False)
    results: Mapped[list[SearchResult]] = relationship(back_populates="search", cascade="all, delete-orphan")


class SearchJob(UUIDPrimaryKeyMixin, TimestampMixin, Base):
    __tablename__ = "search_jobs"

    search_id: Mapped[uuid.UUID] = mapped_column(Uuid, ForeignKey("search_history.id", ondelete="CASCADE"), unique=True, nullable=False)
    status: Mapped[str] = mapped_column(String(16), default="queued", nullable=False, index=True)
    stage: Mapped[str] = mapped_column(String(64), default="queued", nullable=False)
    progress: Mapped[float] = mapped_column(Float, default=0.0, nullable=False)
    attempts: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    started_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    finished_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    error: Mapped[str | None] = mapped_column(Text)
    # Temporary input files (sanitised images / extracted frames). Deleted after processing.
    input_files: Mapped[list[dict] | None] = mapped_column(JSONType)
    options: Mapped[dict | None] = mapped_column(JSONType)

    search: Mapped[SearchHistory] = relationship(back_populates="job")


class SearchResult(UUIDPrimaryKeyMixin, TimestampMixin, Base):
    __tablename__ = "search_results"
    __table_args__ = (
        Index("ix_search_results_search_score", "search_id", "similarity_score"),
        UniqueConstraint("search_id", "dedupe_key", name="uq_search_results_search_dedupe"),
    )

    search_id: Mapped[uuid.UUID] = mapped_column(Uuid, ForeignKey("search_history.id", ondelete="CASCADE"), nullable=False)
    provider_key: Mapped[str] = mapped_column(String(64), nullable=False, index=True)
    also_found_by: Mapped[list[str] | None] = mapped_column(JSONType)
    platform: Mapped[str] = mapped_column(String(64), nullable=False)
    category: Mapped[str] = mapped_column(String(16), nullable=False)  # social | website | news
    match_type: Mapped[str] = mapped_column(String(16), nullable=False)  # exact | similar | related
    source_url: Mapped[str] = mapped_column(Text, nullable=False)
    image_url: Mapped[str | None] = mapped_column(Text)
    thumbnail_url: Mapped[str | None] = mapped_column(Text)
    page_title: Mapped[str | None] = mapped_column(Text)
    description: Mapped[str | None] = mapped_column(Text)
    similarity_score: Mapped[float | None] = mapped_column(Float)
    similarity_method: Mapped[str | None] = mapped_column(String(64))
    hash_distance: Mapped[int | None] = mapped_column(Integer)
    published_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    author_display_name: Mapped[str | None] = mapped_column(String(255))
    author_username: Mapped[str | None] = mapped_column(String(255))
    profile_url: Mapped[str | None] = mapped_column(Text)
    profile_bio: Mapped[str | None] = mapped_column(Text)
    website_url: Mapped[str | None] = mapped_column(Text)
    caption: Mapped[str | None] = mapped_column(Text)
    frame_timestamp_ms: Mapped[int | None] = mapped_column(Integer)
    field_provenance: Mapped[dict | None] = mapped_column(JSONType)
    relevance_rank: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    is_mock: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    dedupe_key: Mapped[str] = mapped_column(String(128), nullable=False)
    image_phash: Mapped[str | None] = mapped_column(String(16), index=True)
    report_count: Mapped[int] = mapped_column(Integer, default=0, nullable=False)

    search: Mapped[SearchHistory] = relationship(back_populates="results")


class ResultReport(UUIDPrimaryKeyMixin, TimestampMixin, Base):
    __tablename__ = "result_reports"

    result_id: Mapped[uuid.UUID] = mapped_column(Uuid, ForeignKey("search_results.id", ondelete="CASCADE"), nullable=False, index=True)
    user_id: Mapped[uuid.UUID] = mapped_column(Uuid, ForeignKey("users.id", ondelete="CASCADE"), nullable=False)
    reason: Mapped[str] = mapped_column(String(32), nullable=False)
    comment: Mapped[str | None] = mapped_column(Text)


class Collection(UUIDPrimaryKeyMixin, TimestampMixin, Base):
    __tablename__ = "collections"
    __table_args__ = (UniqueConstraint("user_id", "name", name="uq_collections_user_name"),)

    user_id: Mapped[uuid.UUID] = mapped_column(Uuid, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    name: Mapped[str] = mapped_column(String(120), nullable=False)
    description: Mapped[str | None] = mapped_column(Text)

    user: Mapped[User] = relationship(back_populates="collections")
    favorites: Mapped[list[Favorite]] = relationship(back_populates="collection")


class Favorite(UUIDPrimaryKeyMixin, TimestampMixin, Base):
    __tablename__ = "favorites"
    __table_args__ = (Index("ix_favorites_user_created", "user_id", "created_at"),)

    user_id: Mapped[uuid.UUID] = mapped_column(Uuid, ForeignKey("users.id", ondelete="CASCADE"), nullable=False)
    collection_id: Mapped[uuid.UUID | None] = mapped_column(Uuid, ForeignKey("collections.id", ondelete="SET NULL"), index=True)
    search_result_id: Mapped[uuid.UUID | None] = mapped_column(Uuid, ForeignKey("search_results.id", ondelete="SET NULL"))
    search_id: Mapped[uuid.UUID | None] = mapped_column(Uuid, ForeignKey("search_history.id", ondelete="SET NULL"))
    # A frozen copy of the result so favourites survive history deletion.
    snapshot: Mapped[dict] = mapped_column(JSONType, nullable=False)
    note: Mapped[str | None] = mapped_column(Text)

    user: Mapped[User] = relationship(back_populates="favorites")
    collection: Mapped[Collection | None] = relationship(back_populates="favorites")
