"""Outbound fetching of third-party images with SSRF protection and size limits."""
from __future__ import annotations

import asyncio
import ipaddress
import socket
from urllib.parse import urlparse

import httpx

from app.core.errors import AppError


class UnsafeUrlError(AppError):
    status_code = 422
    code = "unsafe_url"


def _is_public_ip(ip: str) -> bool:
    addr = ipaddress.ip_address(ip)
    return not (addr.is_private or addr.is_loopback or addr.is_link_local or addr.is_multicast
                or addr.is_reserved or addr.is_unspecified)


async def assert_public_url(url: str) -> None:
    parsed = urlparse(url)
    if parsed.scheme not in ("http", "https") or not parsed.hostname:
        raise UnsafeUrlError("Only public http(s) image links are supported.")
    if parsed.username or parsed.password:
        raise UnsafeUrlError("Links containing credentials are not allowed.")
    loop = asyncio.get_running_loop()
    try:
        infos = await loop.getaddrinfo(parsed.hostname, parsed.port or (443 if parsed.scheme == "https" else 80),
                                       type=socket.SOCK_STREAM)
    except socket.gaierror as exc:
        raise UnsafeUrlError("The link's host could not be resolved.") from exc
    for info in infos:
        if not _is_public_ip(info[4][0]):
            raise UnsafeUrlError("Links to private or local network addresses are not allowed.")


async def fetch_limited(client: httpx.AsyncClient, url: str, max_bytes: int, *,
                        require_image: bool = True, max_redirects: int = 3) -> tuple[bytes, str]:
    """Fetch ``url`` following redirects manually so every hop is SSRF-checked."""
    current = url
    for _ in range(max_redirects + 1):
        await assert_public_url(current)
        async with client.stream("GET", current, follow_redirects=False) as resp:
            if resp.is_redirect:
                location = resp.headers.get("location")
                if not location:
                    raise UnsafeUrlError("Invalid redirect.")
                current = str(resp.url.join(location))
                continue
            if resp.status_code >= 400:
                raise AppError(f"The image link returned HTTP {resp.status_code}.", code="fetch_failed",
                               status_code=422)
            ctype = resp.headers.get("content-type", "").split(";")[0].strip().lower()
            if require_image and ctype and not ctype.startswith("image/") and ctype != "application/octet-stream":
                raise AppError("The link does not point to an image.", code="not_an_image", status_code=422)
            declared = resp.headers.get("content-length")
            if declared and declared.isdigit() and int(declared) > max_bytes:
                raise AppError("The linked image is too large.", code="file_too_large", status_code=413)
            buf = bytearray()
            async for chunk in resp.aiter_bytes():
                buf.extend(chunk)
                if len(buf) > max_bytes:
                    raise AppError("The linked image is too large.", code="file_too_large", status_code=413)
            return bytes(buf), ctype
    raise UnsafeUrlError("Too many redirects.")
