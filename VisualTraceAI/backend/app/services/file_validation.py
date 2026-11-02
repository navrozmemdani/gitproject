"""Upload validation: size limits, extension/MIME/magic-byte agreement, decoder
verification, decompression-bomb limits, polyglot/trailing-payload detection and an
optional ClamAV scan. Images are re-encoded (metadata stripped) before any further
processing or third-party transmission."""
from __future__ import annotations

import asyncio
import io
import logging
import struct
from dataclasses import dataclass, field

from PIL import Image, ImageOps, UnidentifiedImageError

from app.core.config import Settings
from app.core.errors import FileTooLargeError, SuspiciousFileError, UnsupportedMediaError

log = logging.getLogger("visualtrace.validation")

IMAGE_EXTENSIONS = {"jpg": "jpeg", "jpeg": "jpeg", "png": "png", "webp": "webp"}
VIDEO_EXTENSIONS = {"mp4": "mp4", "mov": "mov", "webm": "webm"}
IMAGE_MIME = {"jpeg": "image/jpeg", "png": "image/png", "webp": "image/webp"}
VIDEO_MIME = {"mp4": "video/mp4", "mov": "video/quicktime", "webm": "video/webm"}

# Byte sequences that should never appear in an image payload that is then trailed by
# extra data (classic image/script polyglots and appended executables).
_SUSPICIOUS_MARKERS = (b"<?php", b"<script", b"MZ\x90\x00", b"\x7fELF", b"#!/bin/", b"<%@", b"PK\x03\x04")


@dataclass
class ValidatedImage:
    kind: str  # jpeg | png | webp
    mime: str
    width: int
    height: int
    sanitized_jpeg: bytes  # EXIF-orientation applied, metadata stripped
    exif: dict = field(default_factory=dict)
    original_size: int = 0


@dataclass
class ValidatedVideo:
    kind: str
    mime: str
    size: int


def sniff_image(data: bytes) -> str | None:
    if data[:3] == b"\xff\xd8\xff":
        return "jpeg"
    if data[:8] == b"\x89PNG\r\n\x1a\n":
        return "png"
    if data[:4] == b"RIFF" and data[8:12] == b"WEBP":
        return "webp"
    return None


def sniff_video(data: bytes) -> str | None:
    if data[:4] == b"\x1a\x45\xdf\xa3":
        return "webm"
    if len(data) >= 12 and data[4:8] == b"ftyp":
        brand = data[8:12]
        if brand == b"qt  ":
            return "mov"
        return "mp4"
    return None


def _extension(filename: str | None) -> str | None:
    if not filename or "." not in filename:
        return None
    return filename.rsplit(".", 1)[-1].lower().strip()


def _trailing_payload(kind: str, data: bytes) -> bytes:
    """Return any bytes after the logical end of the image container."""
    if kind == "jpeg":
        end = data.rfind(b"\xff\xd9")
        return data[end + 2:] if end != -1 else b""
    if kind == "png":
        end = data.rfind(b"IEND")
        return data[end + 8:] if end != -1 else b""
    if kind == "webp" and len(data) >= 8:
        riff_size = struct.unpack("<I", data[4:8])[0]
        return data[8 + riff_size:]
    return b""


def _check_suspicious(kind: str, data: bytes) -> None:
    trailing = _trailing_payload(kind, data)
    if len(trailing) > 1024 or any(m in trailing for m in _SUSPICIOUS_MARKERS):
        raise SuspiciousFileError("This file contains unexpected embedded data and was rejected for safety.")
    head = data[:4096].lower()
    if b"<script" in head or b"<?php" in head or b"<html" in head:
        raise SuspiciousFileError("This file does not look like a genuine image.")


SAFE_EXIF_TAGS = {271: "camera_make", 272: "camera_model", 305: "software", 306: "datetime",
                  36867: "datetime_original", 274: "orientation"}


def _exif_summary(img: Image.Image) -> dict:
    try:
        exif = img.getexif()
    except Exception:  # noqa: BLE001
        return {}
    summary: dict = {}
    for tag, name in SAFE_EXIF_TAGS.items():
        value = exif.get(tag)
        if value is not None:
            summary[name] = str(value)[:120]
    # Location is privacy sensitive: report only that it exists, never the coordinates.
    summary["has_gps"] = 34853 in exif
    return summary


def validate_image_bytes(data: bytes, filename: str | None, declared_mime: str | None, settings: Settings) -> ValidatedImage:
    if not data:
        raise UnsupportedMediaError("The file is empty.", code="empty_file")
    if len(data) > settings.max_image_bytes:
        raise FileTooLargeError(f"Images must be smaller than {settings.max_image_bytes // (1024 * 1024)} MB.")
    kind = sniff_image(data)
    if kind is None:
        raise UnsupportedMediaError("Unsupported image format. Use JPG, JPEG, PNG or WEBP.")
    ext = _extension(filename)
    if ext is not None and IMAGE_EXTENSIONS.get(ext) not in (kind,):
        if ext in IMAGE_EXTENSIONS:
            raise SuspiciousFileError("The file extension does not match its contents.")
        raise UnsupportedMediaError("Unsupported image format. Use JPG, JPEG, PNG or WEBP.")
    if declared_mime and declared_mime not in ("application/octet-stream", IMAGE_MIME[kind]):
        if not (kind == "jpeg" and declared_mime == "image/jpg"):
            raise SuspiciousFileError("The declared file type does not match its contents.")
    _check_suspicious(kind, data)

    Image.MAX_IMAGE_PIXELS = settings.max_image_pixels
    try:
        with Image.open(io.BytesIO(data)) as probe:
            probe.verify()
        img = Image.open(io.BytesIO(data))
        if img.width * img.height > settings.max_image_pixels:
            raise FileTooLargeError("Image resolution is too large.")
        exif = _exif_summary(img)
        img = ImageOps.exif_transpose(img)
        if getattr(img, "n_frames", 1) > 1:
            img.seek(0)
        rgb = img.convert("RGB")
    except (UnidentifiedImageError, Image.DecompressionBombError, OSError, SyntaxError) as exc:
        raise UnsupportedMediaError("The image could not be read. It may be corrupted.", code="corrupt_image") from exc

    out = io.BytesIO()
    rgb.save(out, format="JPEG", quality=92, optimize=True)  # new container => metadata stripped
    return ValidatedImage(kind=kind, mime=IMAGE_MIME[kind], width=rgb.width, height=rgb.height,
                          sanitized_jpeg=out.getvalue(), exif=exif, original_size=len(data))


def validate_video_header(head: bytes, filename: str | None, declared_mime: str | None, size: int,
                          settings: Settings) -> ValidatedVideo:
    if size == 0:
        raise UnsupportedMediaError("The file is empty.", code="empty_file")
    if size > settings.max_video_bytes:
        raise FileTooLargeError(f"Videos must be smaller than {settings.max_video_bytes // (1024 * 1024)} MB.")
    kind = sniff_video(head)
    if kind is None:
        raise UnsupportedMediaError("Unsupported video format. Use MP4, MOV or WEBM.")
    ext = _extension(filename)
    if ext is not None:
        if ext not in VIDEO_EXTENSIONS:
            raise UnsupportedMediaError("Unsupported video format. Use MP4, MOV or WEBM.")
        # MP4 and MOV share the ISO-BMFF container, so accept either extension for both.
        if (VIDEO_EXTENSIONS[ext] == "webm") != (kind == "webm"):
            raise SuspiciousFileError("The file extension does not match its contents.")
        kind = VIDEO_EXTENSIONS[ext] if kind != "webm" else kind
    if declared_mime and declared_mime not in ("application/octet-stream", *VIDEO_MIME.values()):
        raise SuspiciousFileError("The declared file type does not match its contents.")
    return ValidatedVideo(kind=kind, mime=VIDEO_MIME[kind], size=size)


async def clamav_scan(data: bytes, settings: Settings) -> None:
    """Scan with clamd's INSTREAM command when CLAMAV_HOST is configured."""
    if not settings.clamav_host:
        return
    try:
        reader, writer = await asyncio.wait_for(
            asyncio.open_connection(settings.clamav_host, settings.clamav_port), timeout=5)
        writer.write(b"zINSTREAM\0")
        for i in range(0, len(data), 64 * 1024):
            chunk = data[i:i + 64 * 1024]
            writer.write(struct.pack("!L", len(chunk)) + chunk)
        writer.write(struct.pack("!L", 0))
        await writer.drain()
        reply = (await asyncio.wait_for(reader.read(4096), timeout=30)).decode(errors="replace")
        writer.close()
    except Exception as exc:  # noqa: BLE001
        log.error("ClamAV scan unavailable: %s", exc)
        raise SuspiciousFileError("The file could not be scanned right now. Please try again.",
                                  code="scan_unavailable", status_code=503, retryable=True) from exc
    if "FOUND" in reply:
        raise SuspiciousFileError("The file was flagged by the malware scanner and rejected.")
