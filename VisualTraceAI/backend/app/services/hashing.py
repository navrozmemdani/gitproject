"""Perceptual hashes (aHash, dHash, pHash) implemented with OpenCV/NumPy."""
from __future__ import annotations

from dataclasses import dataclass

import cv2
import numpy as np


@dataclass(frozen=True)
class PerceptualHashes:
    ahash: int
    dhash: int
    phash: int

    def to_dict(self) -> dict[str, str]:
        return {"ahash": f"{self.ahash:016x}", "dhash": f"{self.dhash:016x}", "phash": f"{self.phash:016x}"}

    @staticmethod
    def from_dict(d: dict[str, str]) -> PerceptualHashes:
        return PerceptualHashes(int(d["ahash"], 16), int(d["dhash"], 16), int(d["phash"], 16))


def _bits_to_int(bits: np.ndarray) -> int:
    value = 0
    for b in bits.flatten():
        value = (value << 1) | int(bool(b))
    return value


def _gray(img_bgr: np.ndarray) -> np.ndarray:
    return cv2.cvtColor(img_bgr, cv2.COLOR_BGR2GRAY) if img_bgr.ndim == 3 else img_bgr


def ahash(img_bgr: np.ndarray) -> int:
    small = cv2.resize(_gray(img_bgr), (8, 8), interpolation=cv2.INTER_AREA).astype(np.float32)
    return _bits_to_int(small > small.mean())


def dhash(img_bgr: np.ndarray) -> int:
    small = cv2.resize(_gray(img_bgr), (9, 8), interpolation=cv2.INTER_AREA).astype(np.float32)
    return _bits_to_int(small[:, 1:] > small[:, :-1])


def phash(img_bgr: np.ndarray) -> int:
    small = cv2.resize(_gray(img_bgr), (32, 32), interpolation=cv2.INTER_AREA).astype(np.float32)
    dct = cv2.dct(small)[:8, :8]
    median = np.median(dct.flatten()[1:])
    return _bits_to_int(dct > median)


def compute_hashes(img_bgr: np.ndarray) -> PerceptualHashes:
    return PerceptualHashes(ahash(img_bgr), dhash(img_bgr), phash(img_bgr))


def hamming(a: int, b: int) -> int:
    return (a ^ b).bit_count()


def hash_distance(a: PerceptualHashes, b: PerceptualHashes) -> int:
    """Combined distance dominated by pHash (robust to resizing/re-encoding)."""
    return round(0.6 * hamming(a.phash, b.phash) + 0.25 * hamming(a.dhash, b.dhash) + 0.15 * hamming(a.ahash, b.ahash))


def decode_image(data: bytes) -> np.ndarray | None:
    arr = np.frombuffer(data, dtype=np.uint8)
    img = cv2.imdecode(arr, cv2.IMREAD_COLOR)
    return img
