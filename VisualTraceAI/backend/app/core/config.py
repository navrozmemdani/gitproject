"""Application configuration.

All secrets (provider API keys, Firebase credentials, database passwords) are read
from environment variables / a local ``.env`` file. Nothing secret is ever sent to
or embedded in the Android client.
"""
from __future__ import annotations

from functools import lru_cache
from pathlib import Path
from typing import Annotated

from pydantic import Field, field_validator
from pydantic_settings import BaseSettings, NoDecode, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    # --- General -------------------------------------------------------------------
    app_name: str = "VisualTrace AI"
    environment: str = Field("development", description="development | staging | production")
    debug: bool = False
    api_prefix: str = "/api/v1"
    public_base_url: str = Field(
        "",
        description="Public HTTPS base URL of this backend. Needed by providers that fetch the "
        "query image by URL (e.g. SerpApi Google Lens). Leave empty to disable those providers.",
    )
    cors_origins: Annotated[list[str], NoDecode] = Field(default_factory=list)

    # --- Database ------------------------------------------------------------------
    database_url: str = "postgresql+asyncpg://visualtrace:visualtrace@localhost:5432/visualtrace"
    db_echo: bool = False

    # --- Security ------------------------------------------------------------------
    # Comma separated client API keys. When empty, API-key enforcement is disabled
    # (development only). These identify client builds; they are NOT provider secrets.
    client_api_keys: Annotated[list[str], NoDecode] = Field(default_factory=list)
    firebase_credentials_file: str = ""
    firebase_project_id: str = ""
    allow_guest: bool = True
    # Development-only auth bypass: accepts "Bearer dev:<uid>" tokens. Refused in production.
    auth_dev_tokens: bool = False
    signed_url_secret: str = "change-me"

    # --- Rate limiting ------------------------------------------------------------
    rate_limit_per_minute: int = 30
    search_rate_limit_per_hour: int = 60

    # --- Uploads -------------------------------------------------------------------
    temp_dir: Path = Path("./tmp")
    max_image_bytes: int = 15 * 1024 * 1024
    max_video_bytes: int = 200 * 1024 * 1024
    max_frame_bytes: int = 5 * 1024 * 1024
    max_frames_per_video: int = 40
    max_image_pixels: int = 50_000_000
    temp_file_ttl_seconds: int = 900
    clamav_host: str = ""
    clamav_port: int = 3310

    # --- Video ---------------------------------------------------------------------
    default_frame_interval_seconds: float = 3.0
    min_frame_interval_seconds: float = 0.5
    max_video_duration_seconds: int = 600

    # --- AI ------------------------------------------------------------------------
    embedding_backend: str = Field("auto", description="auto | clip | opencv")
    clip_model_name: str = "ViT-B-32"
    clip_pretrained: str = "laion2b_s34b_b79k"
    ocr_enabled: bool = True

    # --- Providers -----------------------------------------------------------------
    provider_timeout_seconds: float = 12.0
    provider_max_retries: int = 2
    provider_mock_mode: bool = Field(
        False,
        description="DEVELOPMENT ONLY. Providers without credentials return clearly flagged "
        "mock results (is_mock=true). Never enable in production.",
    )
    max_results_per_provider: int = 20
    candidate_download_limit: int = 40
    candidate_max_bytes: int = 4 * 1024 * 1024

    google_cse_api_key: str = ""
    google_cse_cx: str = ""
    bing_visual_search_key: str = ""
    bing_visual_search_endpoint: str = ""
    serpapi_api_key: str = ""
    tineye_api_key: str = ""
    tineye_api_url: str = "https://api.tineye.com/rest/"
    reddit_client_id: str = ""
    reddit_client_secret: str = ""
    reddit_user_agent: str = "VisualTraceAI/1.0 (by u/visualtrace)"
    x_bearer_token: str = ""
    pinterest_access_token: str = ""
    meta_graph_access_token: str = ""
    instagram_business_account_id: str = ""
    tiktok_research_client_key: str = ""
    tiktok_research_client_secret: str = ""
    openverse_enabled: bool = True
    wikimedia_enabled: bool = True
    http_user_agent: str = "VisualTraceAI/1.0 (+https://visualtrace.ai/bot; contact@visualtrace.ai)"

    @field_validator("cors_origins", "client_api_keys", mode="before")
    @classmethod
    def _split_csv(cls, value):  # noqa: ANN001
        if isinstance(value, str):
            value = value.strip()
            if value.startswith("["):
                import json

                return json.loads(value)
            return [v.strip() for v in value.split(",") if v.strip()]
        return value

    @property
    def is_production(self) -> bool:
        return self.environment.lower() == "production"

    def validate_for_runtime(self) -> list[str]:
        """Return a list of configuration problems that must block production startup."""
        problems: list[str] = []
        if self.is_production:
            if self.provider_mock_mode:
                problems.append("PROVIDER_MOCK_MODE must be false in production")
            if self.auth_dev_tokens:
                problems.append("AUTH_DEV_TOKENS must be false in production")
            if self.signed_url_secret == "change-me":
                problems.append("SIGNED_URL_SECRET must be set in production")
            if not self.firebase_credentials_file and not self.firebase_project_id:
                problems.append("Firebase credentials are required in production")
        return problems


@lru_cache
def get_settings() -> Settings:
    return Settings()
