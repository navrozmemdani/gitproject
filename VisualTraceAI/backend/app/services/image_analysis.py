"""Image analysis: dimensions, orientation, quality, colours, background/environment,
objects, clothing/accessories and OCR.

Every section reports the ``source`` that produced it so the client can distinguish
model-inferred descriptions from measured properties, and sections that could not be
computed are returned with ``source="unavailable"`` instead of invented values."""
from __future__ import annotations

import logging
import shutil
from dataclasses import dataclass, field

import cv2
import numpy as np

from app.core.config import get_settings
from app.services.embeddings import ClipEmbedder, Embedder

log = logging.getLogger("visualtrace.analysis")

# Reference palette for naming dominant colours (RGB).
COLOR_NAMES: dict[str, tuple[int, int, int]] = {
    "black": (20, 20, 20), "white": (240, 240, 240), "gray": (128, 128, 128), "silver": (192, 192, 192),
    "red": (200, 30, 30), "dark red": (120, 10, 20), "orange": (240, 140, 20), "yellow": (240, 220, 40),
    "olive": (128, 128, 0), "green": (40, 160, 60), "dark green": (20, 80, 30), "teal": (0, 128, 128),
    "cyan": (40, 200, 220), "light blue": (150, 200, 240), "blue": (30, 80, 200), "navy": (20, 30, 90),
    "purple": (120, 50, 160), "violet": (170, 110, 220), "pink": (240, 150, 190), "magenta": (200, 40, 160),
    "brown": (120, 70, 30), "beige": (225, 205, 170), "gold": (210, 170, 50),
}

OBJECT_LABELS = [
    "a person", "a group of people", "a car", "a sports car", "a motorcycle", "a bicycle", "a bus", "a truck",
    "an airplane", "a boat", "a train", "a dog", "a cat", "a bird", "a horse", "food", "a drink", "a building",
    "a house", "a bridge", "a tree", "flowers", "a mountain", "a beach", "a city street", "a laptop",
    "a smartphone", "a book", "a chair", "a sofa", "a table", "a painting", "a logo", "a document",
    "a screenshot", "a meme", "a chart", "a watch", "a shoe", "a bag", "jewelry", "a toy", "a sports ball",
]
CLOTHING_LABELS = [
    "a t-shirt", "a shirt", "a jacket", "a coat", "a suit", "a dress", "a skirt", "jeans", "shorts",
    "a hoodie", "a sweater", "a uniform", "a hat", "a cap", "sunglasses", "glasses", "a scarf", "sneakers",
    "boots", "a handbag", "a backpack", "a watch", "a necklace", "earrings", "no clothing items visible",
]
ENVIRONMENT_LABELS = [
    "an indoor room", "an office", "a kitchen", "a restaurant", "a shop", "a stadium", "an outdoor city",
    "a street", "a park", "a forest", "a beach", "a mountain landscape", "a desert", "snow", "a road",
    "a studio background", "a plain background", "the sky", "underwater", "night time", "a stage",
]


@dataclass
class AnalysisResult:
    width: int
    height: int
    orientation: str
    aspect_ratio: float
    quality: dict
    colors: dict
    background: dict
    environment: dict
    objects: dict
    clothing: dict
    ocr: dict
    characteristics: list[str] = field(default_factory=list)
    keywords: list[str] = field(default_factory=list)

    def to_dict(self) -> dict:
        return {
            "width": self.width, "height": self.height, "orientation": self.orientation,
            "aspect_ratio": self.aspect_ratio, "quality": self.quality, "colors": self.colors,
            "background": self.background, "environment": self.environment, "objects": self.objects,
            "clothing": self.clothing, "ocr": self.ocr, "characteristics": self.characteristics,
            "keywords": self.keywords,
        }


def _nearest_color(rgb: np.ndarray) -> str:
    best, best_d = "gray", 1e9
    for name, ref in COLOR_NAMES.items():
        d = float(np.sum((rgb - np.array(ref)) ** 2))
        if d < best_d:
            best, best_d = name, d
    return best


def dominant_colors(img_bgr: np.ndarray, k: int = 5) -> list[dict]:
    small = cv2.resize(img_bgr, (96, 96), interpolation=cv2.INTER_AREA)
    data = cv2.cvtColor(small, cv2.COLOR_BGR2RGB).reshape(-1, 3).astype(np.float32)
    criteria = (cv2.TERM_CRITERIA_EPS + cv2.TERM_CRITERIA_MAX_ITER, 20, 1.0)
    cv2.setRNGSeed(7)
    _, labels, centers = cv2.kmeans(data, k, None, criteria, 3, cv2.KMEANS_PP_CENTERS)
    counts = np.bincount(labels.flatten(), minlength=k)
    merged: dict[str, dict] = {}
    for idx in np.argsort(-counts):
        rgb = centers[idx]
        name = _nearest_color(rgb)
        share = float(counts[idx] / counts.sum())
        if name in merged:
            merged[name]["share"] += share
        else:
            merged[name] = {"name": name, "hex": "#%02x%02x%02x" % tuple(int(c) for c in rgb), "share": share}
    out = sorted(merged.values(), key=lambda c: -c["share"])
    for c in out:
        c["share"] = round(c["share"], 3)
    return out


def assess_quality(img_bgr: np.ndarray) -> dict:
    gray = cv2.cvtColor(img_bgr, cv2.COLOR_BGR2GRAY)
    sharpness = float(cv2.Laplacian(gray, cv2.CV_64F).var())
    brightness = float(gray.mean())
    contrast = float(gray.std())
    h, w = gray.shape
    megapixels = round(h * w / 1_000_000, 2)
    issues = []
    if sharpness < 60:
        issues.append("blurry")
    if brightness < 50:
        issues.append("dark")
    elif brightness > 210:
        issues.append("overexposed")
    if contrast < 25:
        issues.append("low contrast")
    if min(h, w) < 300:
        issues.append("low resolution")
    score = 1.0 - 0.2 * len(issues)
    label = "high" if score >= 0.95 else "medium" if score >= 0.6 else "low"
    return {"label": label, "sharpness": round(sharpness, 1), "brightness": round(brightness, 1),
            "contrast": round(contrast, 1), "megapixels": megapixels, "issues": issues, "source": "measured"}


def describe_background(img_bgr: np.ndarray) -> dict:
    """Measure the border region: a uniform border suggests a plain/studio background."""
    h, w = img_bgr.shape[:2]
    b = max(4, min(h, w) // 10)
    border = np.concatenate([img_bgr[:b].reshape(-1, 3), img_bgr[-b:].reshape(-1, 3),
                             img_bgr[:, :b].reshape(-1, 3), img_bgr[:, -b:].reshape(-1, 3)])
    std = float(border.std(axis=0).mean())
    mean_rgb = border.mean(axis=0)[::-1]
    kind = "plain" if std < 18 else "moderately busy" if std < 45 else "busy / detailed"
    return {"type": kind, "dominant_color": _nearest_color(mean_rgb), "uniformity": round(max(0.0, 1 - std / 80), 2),
            "source": "measured"}


_TESSERACT_CHECKED: bool | None = None


def run_ocr(img_bgr: np.ndarray) -> dict:
    global _TESSERACT_CHECKED
    if not get_settings().ocr_enabled:
        return {"text": None, "source": "unavailable", "reason": "OCR disabled in configuration"}
    if _TESSERACT_CHECKED is None:
        try:
            import pytesseract  # noqa: F401

            _TESSERACT_CHECKED = shutil.which("tesseract") is not None
        except ImportError:
            _TESSERACT_CHECKED = False
    if not _TESSERACT_CHECKED:
        return {"text": None, "source": "unavailable", "reason": "Tesseract OCR is not installed on the server"}
    import pytesseract

    gray = cv2.cvtColor(img_bgr, cv2.COLOR_BGR2GRAY)
    if max(gray.shape) < 1000:
        scale = 1000 / max(gray.shape)
        gray = cv2.resize(gray, None, fx=scale, fy=scale, interpolation=cv2.INTER_CUBIC)
    try:
        text = pytesseract.image_to_string(gray, timeout=15)
    except Exception as exc:  # noqa: BLE001
        return {"text": None, "source": "unavailable", "reason": f"OCR failed: {type(exc).__name__}"}
    cleaned = " ".join(text.split())
    return {"text": cleaned[:2000] or None, "source": "tesseract-ocr"}


def _labels(embedder: Embedder, img: np.ndarray, labels: list[str], top_k: int, min_p: float) -> dict:
    if not isinstance(embedder, ClipEmbedder):
        return {"items": [], "source": "unavailable",
                "reason": "Semantic recognition requires the CLIP model (install requirements-ml.txt)"}
    items = [{"label": label.removeprefix("a ").removeprefix("an "), "confidence": round(p, 3)}
             for label, p in embedder.zero_shot(img, labels, top_k) if p >= min_p]
    return {"items": items, "source": f"inferred:{embedder.name}"}


def analyze_image(img_bgr: np.ndarray, embedder: Embedder) -> AnalysisResult:
    h, w = img_bgr.shape[:2]
    ratio = round(w / h, 3)
    orientation = "square" if 0.95 <= ratio <= 1.05 else "landscape" if ratio > 1 else "portrait"
    colors = dominant_colors(img_bgr)
    quality = assess_quality(img_bgr)
    background = describe_background(img_bgr)
    objects = _labels(embedder, img_bgr, OBJECT_LABELS, 5, 0.08)
    clothing = _labels(embedder, img_bgr, CLOTHING_LABELS, 4, 0.10)
    environment = _labels(embedder, img_bgr, ENVIRONMENT_LABELS, 3, 0.10)
    ocr = run_ocr(img_bgr)

    characteristics = [f"{orientation} image", f"{quality['label']} quality"]
    characteristics += [f"{c['name']} tones" for c in colors[:2] if c["share"] > 0.15]
    characteristics.append(f"{background['type']} background")
    keywords: list[str] = []
    for section in (objects, clothing, environment):
        for item in section.get("items", []):
            if item["label"] != "no clothing items visible":
                keywords.append(item["label"])
    if ocr.get("text"):
        keywords.extend(w for w in ocr["text"].split()[:6] if len(w) > 3)
    if colors and objects.get("items"):
        keywords.insert(0, f"{colors[0]['name']} {objects['items'][0]['label']}")
    seen: set[str] = set()
    keywords = [k for k in keywords if not (k.lower() in seen or seen.add(k.lower()))][:10]
    return AnalysisResult(width=w, height=h, orientation=orientation, aspect_ratio=ratio,
                          quality=quality, colors={"items": colors, "source": "measured"}, background=background,
                          environment=environment, objects=objects, clothing=clothing, ocr=ocr,
                          characteristics=characteristics, keywords=keywords)
