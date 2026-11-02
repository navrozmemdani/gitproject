"""Typed API errors rendered as a stable JSON envelope:

    {"error": {"code": "unsupported_media_type", "message": "...", "retryable": false}}
"""
from __future__ import annotations

import logging

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException

log = logging.getLogger("visualtrace.errors")


class AppError(Exception):
    status_code = 400
    code = "bad_request"
    retryable = False

    def __init__(self, message: str, *, code: str | None = None, status_code: int | None = None,
                 retryable: bool | None = None, details: dict | None = None):
        super().__init__(message)
        self.message = message
        if code:
            self.code = code
        if status_code:
            self.status_code = status_code
        if retryable is not None:
            self.retryable = retryable
        self.details = details or {}


class UnsupportedMediaError(AppError):
    status_code = 415
    code = "unsupported_media_type"


class FileTooLargeError(AppError):
    status_code = 413
    code = "file_too_large"


class SuspiciousFileError(AppError):
    status_code = 422
    code = "suspicious_file"


class AuthError(AppError):
    status_code = 401
    code = "authentication_failed"


class ForbiddenError(AppError):
    status_code = 403
    code = "forbidden"


class NotFoundError(AppError):
    status_code = 404
    code = "not_found"


class RateLimitedError(AppError):
    status_code = 429
    code = "rate_limited"
    retryable = True


class ConsentRequiredError(AppError):
    status_code = 428
    code = "consent_required"


def _body(code: str, message: str, retryable: bool, details: dict | None = None) -> dict:
    payload = {"code": code, "message": message, "retryable": retryable}
    if details:
        payload["details"] = details
    return {"error": payload}


def install_error_handlers(app: FastAPI) -> None:
    @app.exception_handler(AppError)
    async def _app_error(request: Request, exc: AppError):  # noqa: ANN202
        headers = {}
        if isinstance(exc, RateLimitedError) and "retry_after" in exc.details:
            headers["Retry-After"] = str(exc.details["retry_after"])
        return JSONResponse(status_code=exc.status_code,
                            content=_body(exc.code, exc.message, exc.retryable, exc.details), headers=headers)

    @app.exception_handler(RequestValidationError)
    async def _validation(request: Request, exc: RequestValidationError):  # noqa: ANN202
        errors = [{"loc": list(e.get("loc", [])), "msg": e.get("msg", "")} for e in exc.errors()]
        return JSONResponse(status_code=422, content=_body("validation_error", "The request is invalid.", False,
                                                           {"errors": errors}))

    @app.exception_handler(StarletteHTTPException)
    async def _http(request: Request, exc: StarletteHTTPException):  # noqa: ANN202
        code = {404: "not_found", 405: "method_not_allowed"}.get(exc.status_code, "http_error")
        return JSONResponse(status_code=exc.status_code, content=_body(code, str(exc.detail), False))

    @app.exception_handler(Exception)
    async def _unhandled(request: Request, exc: Exception):  # noqa: ANN202
        log.exception("Unhandled error on %s %s", request.method, request.url.path)
        return JSONResponse(status_code=500, content=_body("internal_error",
                                                           "Something went wrong on our side. Please try again.", True))
