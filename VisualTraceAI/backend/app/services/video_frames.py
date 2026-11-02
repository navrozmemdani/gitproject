"""Representative frame extraction (OpenCV; FFmpeg backend when available).

Frames are sampled every ``interval`` seconds up to ``max_duration`` seconds and at most
``max_frames``. Consecutive near-duplicate frames (dHash distance <= 6) are skipped so
that only visually distinct, representative frames are searched."""
from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

import cv2
import numpy as np

from app.core.errors import UnsupportedMediaError
from app.services.hashing import dhash, hamming


@dataclass
class ExtractedFrame:
    timestamp_ms: int
    image: np.ndarray


@dataclass
class VideoInfo:
    duration_seconds: float
    fps: float
    width: int
    height: int
    frame_count: int


def probe(path: Path) -> VideoInfo:
    cap = cv2.VideoCapture(str(path))
    try:
        if not cap.isOpened():
            raise UnsupportedMediaError("The video could not be decoded.", code="corrupt_video")
        fps = cap.get(cv2.CAP_PROP_FPS) or 0.0
        count = int(cap.get(cv2.CAP_PROP_FRAME_COUNT) or 0)
        width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH) or 0)
        height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT) or 0)
        duration = count / fps if fps > 0 and count > 0 else 0.0
        return VideoInfo(duration_seconds=round(duration, 2), fps=round(fps, 2), width=width, height=height,
                         frame_count=count)
    finally:
        cap.release()


def extract_frames(path: Path, interval: float, max_duration: float, max_frames: int,
                   dedupe_distance: int = 6) -> tuple[VideoInfo, list[ExtractedFrame]]:
    info = probe(path)
    cap = cv2.VideoCapture(str(path))
    frames: list[ExtractedFrame] = []
    try:
        limit = min(info.duration_seconds or max_duration, max_duration)
        t = 0.0
        last_hash: int | None = None
        while t <= limit + 1e-6 and len(frames) < max_frames:
            cap.set(cv2.CAP_PROP_POS_MSEC, t * 1000)
            ok, frame = cap.read()
            if not ok or frame is None:
                break
            h = dhash(frame)
            if last_hash is None or hamming(h, last_hash) > dedupe_distance:
                frames.append(ExtractedFrame(timestamp_ms=int(round(t * 1000)), image=frame))
                last_hash = h
            t += interval
    finally:
        cap.release()
    if not frames:
        raise UnsupportedMediaError("No frames could be extracted from this video.", code="no_frames")
    return info, frames
