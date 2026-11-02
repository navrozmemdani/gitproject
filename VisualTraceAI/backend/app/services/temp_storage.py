"""Short-lived storage for uploads while a search is processed.

Files are written with 0600 permissions under random names, deleted as soon as the
job finishes, and swept by TTL in case of crashes."""
from __future__ import annotations

import base64
import hashlib
import hmac
import logging
import os
import secrets
import time
from pathlib import Path

from app.core.config import Settings

log = logging.getLogger("visualtrace.temp")


class TempStorage:
    def __init__(self, settings: Settings):
        self.settings = settings
        self.root = Path(settings.temp_dir).resolve()
        self.root.mkdir(parents=True, exist_ok=True)
        os.chmod(self.root, 0o700)

    def save(self, data: bytes, suffix: str) -> Path:
        name = secrets.token_hex(16) + "." + suffix.lstrip(".")
        path = self.root / name
        fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(fd, "wb") as fh:
            fh.write(data)
        return path

    def resolve(self, name: str) -> Path | None:
        if "/" in name or "\\" in name or name.startswith("."):
            return None
        path = (self.root / name).resolve()
        if path.parent != self.root or not path.is_file():
            return None
        return path

    def delete(self, path: Path | str | None) -> None:
        if not path:
            return
        try:
            Path(path).unlink(missing_ok=True)
        except OSError as exc:
            log.warning("could not delete temp file: %s", exc)

    def sweep(self) -> int:
        cutoff = time.time() - self.settings.temp_file_ttl_seconds
        removed = 0
        for child in self.root.iterdir():
            try:
                if child.is_file() and child.stat().st_mtime < cutoff:
                    child.unlink()
                    removed += 1
            except OSError:
                continue
        return removed

    # Signed, expiring URLs let an authorised reverse-image provider fetch the query image.
    def sign(self, name: str, ttl_seconds: int = 300) -> str:
        expires = int(time.time()) + ttl_seconds
        msg = f"{name}:{expires}".encode()
        sig = hmac.new(self.settings.signed_url_secret.encode(), msg, hashlib.sha256).digest()
        token = base64.urlsafe_b64encode(msg + b":" + sig).decode().rstrip("=")
        return token

    def verify(self, token: str) -> str | None:
        try:
            raw = base64.urlsafe_b64decode(token + "=" * (-len(token) % 4))
            name, expires, sig = raw.split(b":", 2)
        except Exception:  # noqa: BLE001
            return None
        msg = name + b":" + expires
        expected = hmac.new(self.settings.signed_url_secret.encode(), msg, hashlib.sha256).digest()
        if not hmac.compare_digest(sig, expected) or int(expires) < time.time():
            return None
        return name.decode()
