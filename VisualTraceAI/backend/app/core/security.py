"""Authentication & request-guard dependencies.

* Client API key (``X-API-Key``): identifies trusted client builds. Optional in development.
* Firebase ID token (``Authorization: Bearer <token>``): verified with firebase-admin.
* Guest mode (``X-Guest-Id: <uuid>``): anonymous device-scoped identity for basic search.
"""
from __future__ import annotations

import hmac
import logging
import re
import uuid
from dataclasses import dataclass
from functools import lru_cache

from fastapi import Depends, Header, Request
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import Settings, get_settings
from app.core.errors import AuthError, ForbiddenError
from app.core.rate_limit import limiter
from app.database.base import utcnow
from app.database.session import get_db
from app.models import User, UserSettings

log = logging.getLogger("visualtrace.auth")
_GUEST_RE = re.compile(r"^[0-9a-fA-F-]{32,36}$")


@dataclass(frozen=True)
class VerifiedToken:
    uid: str
    email: str | None
    name: str | None
    provider: str


class TokenVerifier:
    """Wraps firebase-admin; isolated so tests can substitute it."""

    def __init__(self, settings: Settings):
        self.settings = settings
        self._app = None

    def _firebase(self):  # noqa: ANN202
        if self._app is not None:
            return self._app
        import firebase_admin
        from firebase_admin import credentials

        if self.settings.firebase_credentials_file:
            cred = credentials.Certificate(self.settings.firebase_credentials_file)
            self._app = firebase_admin.initialize_app(cred, name="visualtrace")
        elif self.settings.firebase_project_id:
            self._app = firebase_admin.initialize_app(options={"projectId": self.settings.firebase_project_id},
                                                      name="visualtrace")
        else:
            raise AuthError("Account sign-in is not configured on this server.", code="auth_not_configured",
                            status_code=503)
        return self._app

    def verify(self, token: str) -> VerifiedToken:
        if token.startswith("dev:"):
            if self.settings.auth_dev_tokens and not self.settings.is_production:
                uid = token[4:][:128]
                if not uid:
                    raise AuthError("Invalid development token.")
                return VerifiedToken(uid=f"dev-{uid}", email=f"{uid}@dev.local", name=uid, provider="dev")
            raise AuthError("Development tokens are disabled.")
        from firebase_admin import auth

        try:
            decoded = auth.verify_id_token(token, app=self._firebase(), check_revoked=True)
        except AuthError:
            raise
        except Exception as exc:  # firebase raises several distinct error types
            log.info("token verification failed: %s", type(exc).__name__)
            raise AuthError("Your session has expired. Please sign in again.") from exc
        provider = (decoded.get("firebase") or {}).get("sign_in_provider", "password")
        return VerifiedToken(uid=decoded["uid"], email=decoded.get("email"), name=decoded.get("name"),
                             provider=provider)


@lru_cache
def get_token_verifier() -> TokenVerifier:
    return TokenVerifier(get_settings())


async def require_client_key(
    x_api_key: str | None = Header(default=None, alias="X-API-Key"),
    settings: Settings = Depends(get_settings),
) -> None:
    if not settings.client_api_keys:
        return
    if not x_api_key or not any(hmac.compare_digest(x_api_key, k) for k in settings.client_api_keys):
        raise AuthError("Invalid or missing client key.", code="invalid_client_key")


async def _ensure_settings(db: AsyncSession, user: User) -> None:
    existing = await db.scalar(select(UserSettings).where(UserSettings.user_id == user.id))
    if existing is None:
        db.add(UserSettings(user_id=user.id))


async def get_current_user(
    request: Request,
    authorization: str | None = Header(default=None),
    x_guest_id: str | None = Header(default=None, alias="X-Guest-Id"),
    db: AsyncSession = Depends(get_db),
    settings: Settings = Depends(get_settings),
    verifier: TokenVerifier = Depends(get_token_verifier),
    _: None = Depends(require_client_key),
) -> User:
    user: User | None = None
    if authorization:
        scheme, _, token = authorization.partition(" ")
        if scheme.lower() != "bearer" or not token:
            raise AuthError("Malformed Authorization header.")
        verified = verifier.verify(token.strip())
        user = await db.scalar(select(User).where(User.firebase_uid == verified.uid))
        if user is None:
            user = User(firebase_uid=verified.uid, email=verified.email, display_name=verified.name,
                        is_guest=False, auth_provider=verified.provider)
            db.add(user)
            await db.flush()
            await _ensure_settings(db, user)
        else:
            user.email = verified.email or user.email
            user.display_name = verified.name or user.display_name
    elif x_guest_id:
        if not settings.allow_guest:
            raise AuthError("Guest access is disabled. Please sign in.")
        if not _GUEST_RE.match(x_guest_id):
            raise AuthError("Invalid guest identifier.")
        guest_key = x_guest_id.lower()
        user = await db.scalar(select(User).where(User.guest_id == guest_key))
        if user is None:
            user = User(guest_id=guest_key, is_guest=True, auth_provider="guest")
            db.add(user)
            await db.flush()
            await _ensure_settings(db, user)
    else:
        raise AuthError("Sign in or continue as guest to use VisualTrace AI.")

    user.last_seen_at = utcnow()
    await db.flush()
    await limiter.hit(f"user:{user.id}", settings.rate_limit_per_minute, 60)
    request.state.user_id = str(user.id)
    return user


async def require_account(user: User = Depends(get_current_user)) -> User:
    if user.is_guest:
        raise ForbiddenError("This feature requires an account. Please sign in.", code="account_required")
    return user


async def search_rate_limit(user: User = Depends(get_current_user),
                            settings: Settings = Depends(get_settings)) -> User:
    await limiter.hit(f"search:{user.id}", settings.search_rate_limit_per_hour, 3600)
    return user


def new_guest_id() -> str:
    return str(uuid.uuid4())
