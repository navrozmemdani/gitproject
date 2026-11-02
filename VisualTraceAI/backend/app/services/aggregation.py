"""Result normalisation, de-duplication and ranking helpers."""
from __future__ import annotations

import hashlib
from urllib.parse import parse_qsl, urlencode, urlparse, urlunparse

TRACKING_PARAMS = {"fbclid", "gclid", "igshid", "igsh", "mc_cid", "mc_eid", "ref", "ref_src", "s", "si", "spm",
                   "_ga", "yclid", "msclkid"}


def normalize_url(url: str) -> str:
    p = urlparse(url.strip())
    host = (p.hostname or "").lower()
    if host.startswith("www."):
        host = host[4:]
    if host.startswith("m.") or host.startswith("mobile."):
        host = host.split(".", 1)[1]
    query = sorted((k, v) for k, v in parse_qsl(p.query, keep_blank_values=False)
                   if not k.lower().startswith("utm_") and k.lower() not in TRACKING_PARAMS)
    path = p.path.rstrip("/") or "/"
    return urlunparse(("https", host, path, "", urlencode(query), ""))


def dedupe_key(source_url: str) -> str:
    return hashlib.sha256(normalize_url(source_url).encode()).hexdigest()[:40]


MATCH_PRIORITY = {"exact": 3, "cropped": 2, "similar": 1, "related": 0}


def better(a_score: float | None, a_type: str, b_score: float | None, b_type: str) -> bool:
    """True when candidate *a* should replace *b*."""
    if MATCH_PRIORITY.get(a_type, 0) != MATCH_PRIORITY.get(b_type, 0):
        return MATCH_PRIORITY.get(a_type, 0) > MATCH_PRIORITY.get(b_type, 0)
    return (a_score or -1) > (b_score or -1)
