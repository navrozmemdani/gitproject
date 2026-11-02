"""Visual embeddings.

* ``ClipEmbedder``  - OpenCLIP (default ViT-B-32). Installed via requirements-ml.txt.
* ``OpenCvDescriptorEmbedder`` - lightweight global descriptor (colour histogram +
  gradient-orientation grid + low-frequency DCT). Used automatically when CLIP is not
  installed; it is reported as such in every search response.
"""
from __future__ import annotations

import logging
import threading
from functools import lru_cache

import cv2
import numpy as np

from app.core.config import get_settings

log = logging.getLogger("visualtrace.embeddings")


class Embedder:
    name: str = "base"
    supports_text: bool = False

    def embed_image(self, img_bgr: np.ndarray) -> np.ndarray:
        raise NotImplementedError

    def embed_text(self, texts: list[str]) -> np.ndarray:
        raise NotImplementedError

    def zero_shot(self, img_bgr: np.ndarray, labels: list[str], top_k: int = 5) -> list[tuple[str, float]]:
        raise NotImplementedError


def _l2(v: np.ndarray) -> np.ndarray:
    n = float(np.linalg.norm(v))
    return v / n if n > 0 else v


class OpenCvDescriptorEmbedder(Embedder):
    name = "opencv-global-descriptor-v1"

    def embed_image(self, img_bgr: np.ndarray) -> np.ndarray:
        img = cv2.resize(img_bgr, (128, 128), interpolation=cv2.INTER_AREA)
        hsv = cv2.cvtColor(img, cv2.COLOR_BGR2HSV)
        hist = cv2.calcHist([hsv], [0, 1, 2], None, [8, 4, 4], [0, 180, 0, 256, 0, 256]).flatten()
        hist = _l2(np.sqrt(hist))
        gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY).astype(np.float32)
        gx = cv2.Sobel(gray, cv2.CV_32F, 1, 0)
        gy = cv2.Sobel(gray, cv2.CV_32F, 0, 1)
        mag, ang = cv2.cartToPolar(gx, gy, angleInDegrees=True)
        ang = np.mod(ang, 180)
        cells = []
        for i in range(4):
            for j in range(4):
                m = mag[i * 32:(i + 1) * 32, j * 32:(j + 1) * 32].flatten()
                a = ang[i * 32:(i + 1) * 32, j * 32:(j + 1) * 32].flatten()
                h, _ = np.histogram(a, bins=9, range=(0, 180), weights=m)
                cells.append(h)
        grad = _l2(np.sqrt(np.concatenate(cells).astype(np.float32)))
        dct = cv2.dct(cv2.resize(gray, (32, 32)) / 255.0)[:8, :8].flatten()
        dct = _l2(dct[1:])
        return _l2(np.concatenate([hist * 0.45, grad * 0.35, dct * 0.20]).astype(np.float32))


class ClipEmbedder(Embedder):
    supports_text = True

    def __init__(self, model_name: str, pretrained: str):
        import open_clip  # type: ignore
        import torch  # type: ignore

        self._torch = torch
        self.model, _, self.preprocess = open_clip.create_model_and_transforms(model_name, pretrained=pretrained)
        self.model.eval()
        self.tokenizer = open_clip.get_tokenizer(model_name)
        self.name = f"clip-{model_name}-{pretrained}"
        self._lock = threading.Lock()

    def embed_image(self, img_bgr: np.ndarray) -> np.ndarray:
        from PIL import Image

        pil = Image.fromarray(cv2.cvtColor(img_bgr, cv2.COLOR_BGR2RGB))
        with self._lock, self._torch.no_grad():
            feats = self.model.encode_image(self.preprocess(pil).unsqueeze(0))
        return _l2(feats[0].cpu().numpy().astype(np.float32))

    def embed_text(self, texts: list[str]) -> np.ndarray:
        with self._lock, self._torch.no_grad():
            feats = self.model.encode_text(self.tokenizer(texts))
        arr = feats.cpu().numpy().astype(np.float32)
        return arr / np.linalg.norm(arr, axis=1, keepdims=True)

    def zero_shot(self, img_bgr: np.ndarray, labels: list[str], top_k: int = 5) -> list[tuple[str, float]]:
        img = self.embed_image(img_bgr)
        txt = self.embed_text([f"a photo of {label}" for label in labels])
        logits = 100.0 * txt @ img
        probs = np.exp(logits - logits.max())
        probs = probs / probs.sum()
        order = np.argsort(-probs)[:top_k]
        return [(labels[i], float(probs[i])) for i in order]


_lock = threading.Lock()


@lru_cache
def get_embedder() -> Embedder:
    settings = get_settings()
    with _lock:
        if settings.embedding_backend in ("auto", "clip"):
            try:
                emb = ClipEmbedder(settings.clip_model_name, settings.clip_pretrained)
                log.info("Using CLIP embeddings: %s", emb.name)
                return emb
            except Exception as exc:  # noqa: BLE001
                if settings.embedding_backend == "clip":
                    raise
                log.warning("CLIP unavailable (%s); falling back to OpenCV descriptor", type(exc).__name__)
        return OpenCvDescriptorEmbedder()


def cosine(a: np.ndarray, b: np.ndarray) -> float:
    if a.shape != b.shape:
        return 0.0
    return float(np.clip(np.dot(_l2(a), _l2(b)), -1.0, 1.0))
