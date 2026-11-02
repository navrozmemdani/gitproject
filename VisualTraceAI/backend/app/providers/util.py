from __future__ import annotations

from datetime import datetime, timezone
from urllib.parse import urlparse

NEWS_HINTS = ("news", "times", "post", "herald", "guardian", "bbc", "cnn", "reuters", "apnews", "tribune",
              "journal", "gazette", "blog", "medium.com", "substack", "wordpress", "blogspot")
SOCIAL_DOMAINS = {
    "instagram.com": "Instagram", "facebook.com": "Facebook", "fb.com": "Facebook", "tiktok.com": "TikTok",
    "x.com": "X (Twitter)", "twitter.com": "X (Twitter)", "reddit.com": "Reddit", "redd.it": "Reddit",
    "pinterest.com": "Pinterest", "pin.it": "Pinterest", "youtube.com": "YouTube", "linkedin.com": "LinkedIn",
    "threads.net": "Threads", "tumblr.com": "Tumblr", "flickr.com": "Flickr", "vk.com": "VK",
}


def domain_of(url: str) -> str:
    host = (urlparse(url).hostname or "").lower()
    return host[4:] if host.startswith("www.") else host


def platform_for(url: str) -> tuple[str, str]:
    """Return (platform_name, category) for a URL."""
    host = domain_of(url)
    for dom, name in SOCIAL_DOMAINS.items():
        if host == dom or host.endswith("." + dom):
            return name, "social"
    if any(h in host for h in NEWS_HINTS):
        return host, "news"
    return host or "Website", "website"


def parse_date(value) -> datetime | None:  # noqa: ANN001
    if value in (None, ""):
        return None
    try:
        if isinstance(value, (int, float)):
            return datetime.fromtimestamp(float(value), tz=timezone.utc)
        text = str(value).strip().replace("Z", "+00:00")
        dt = datetime.fromisoformat(text)
        return dt if dt.tzinfo else dt.replace(tzinfo=timezone.utc)
    except (ValueError, OSError, OverflowError):
        return None


def clip(text: str | None, n: int = 600) -> str | None:
    if not text:
        return None
    text = " ".join(str(text).split())
    return text[:n] or None
