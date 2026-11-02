"""Searches VisualTrace's own index of public images previously retrieved from
authorised providers (perceptual-hash proximity). Only public result metadata is
indexed - never user uploads."""
from __future__ import annotations

import httpx
from sqlalchemy import select

from app.database.session import session_factory
from app.models import SearchResult
from app.providers.base import ImageSearchProvider, ProviderCategory, ProviderHit, ProviderQuery
from app.services.hashing import hamming


class LocalIndexProvider(ImageSearchProvider):
    key = "local_index"
    name = "VisualTrace public index"
    platform = "VisualTrace index"
    category = ProviderCategory.INDEX
    supports_reverse_image = True
    requires_credentials = False
    credential_env = ()
    limitations = "Contains only public images previously returned by other providers."
    max_distance = 10
    scan_limit = 5000

    def is_configured(self) -> bool:
        return True

    def mock_hits(self, query: ProviderQuery) -> list[ProviderHit]:
        return []

    async def search(self, query: ProviderQuery, client: httpx.AsyncClient) -> list[ProviderHit]:
        if not query.phash_hex:
            return []
        target = int(query.phash_hex, 16)
        async with session_factory()() as db:
            stmt = (select(SearchResult).where(SearchResult.image_phash.is_not(None), SearchResult.is_mock.is_(False))
                    .order_by(SearchResult.created_at.desc()).limit(self.scan_limit))
            rows = (await db.scalars(stmt)).all()
        best: dict[str, tuple[int, SearchResult]] = {}
        for r in rows:
            if query.exclude_search_id and str(r.search_id) == query.exclude_search_id:
                continue
            d = hamming(target, int(r.image_phash, 16))
            if d <= self.max_distance and (r.source_url not in best or d < best[r.source_url][0]):
                best[r.source_url] = (d, r)
        hits = []
        for rank, (_d, r) in enumerate(sorted(best.values(), key=lambda x: x[0])):
            hits.append(ProviderHit(
                source_url=r.source_url, platform=r.platform, category=r.category, image_url=r.image_url,
                thumbnail_url=r.thumbnail_url, page_title=r.page_title, description=r.description,
                published_at=r.published_at, author_display_name=r.author_display_name,
                author_username=r.author_username, profile_url=r.profile_url, relevance_rank=rank,
                via=f"VisualTrace index (originally from {r.provider_key})"))
        return hits
