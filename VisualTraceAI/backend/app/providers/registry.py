from __future__ import annotations

from app.core.config import Settings
from app.providers.base import ImageSearchProvider
from app.providers.local_index import LocalIndexProvider
from app.providers.social.platforms import (
    FacebookProvider,
    InstagramProvider,
    PinterestProvider,
    PublicWebForumsProvider,
    RedditProvider,
    TikTokProvider,
    XProvider,
)
from app.providers.web import (
    BingImageSearchProvider,
    GoogleImageSearchProvider,
    OpenverseProvider,
    SerpApiLensProvider,
    TinEyeProvider,
    WikimediaCommonsProvider,
)

PROVIDER_CLASSES: list[type[ImageSearchProvider]] = [
    SerpApiLensProvider, TinEyeProvider, BingImageSearchProvider, GoogleImageSearchProvider,
    OpenverseProvider, WikimediaCommonsProvider, LocalIndexProvider, PublicWebForumsProvider,
    InstagramProvider, FacebookProvider, TikTokProvider, XProvider, RedditProvider, PinterestProvider,
]


class ProviderRegistry:
    def __init__(self, settings: Settings):
        self.settings = settings
        self.providers: dict[str, ImageSearchProvider] = {cls.key: cls(settings) for cls in PROVIDER_CLASSES}
        self.disabled: set[str] = set()  # globally disabled by an operator (search_providers.enabled)

    def all(self) -> list[ImageSearchProvider]:
        return list(self.providers.values())

    def get(self, key: str) -> ImageSearchProvider | None:
        return self.providers.get(key)

    def select(self, requested: list[str] | None, user_enabled: list[str] | None) -> list[ImageSearchProvider]:
        """Requested keys win; else the user's saved preference; else defaults."""
        if requested:
            keys = [k for k in requested if k in self.providers]
        elif user_enabled is not None:
            keys = [k for k in user_enabled if k in self.providers]
        else:
            keys = [p.key for p in self.providers.values() if p.enabled_by_default]
        return [self.providers[k] for k in keys if k not in self.disabled]
