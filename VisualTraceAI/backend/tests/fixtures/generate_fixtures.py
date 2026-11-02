"""Regenerates the deterministic sample media used by the test-suite.

    python tests/fixtures/generate_fixtures.py
"""
from __future__ import annotations

from pathlib import Path

import cv2
import numpy as np
from PIL import Image

HERE = Path(__file__).parent


def scene(seed: int = 1, size=(640, 480)) -> np.ndarray:
    rng = np.random.default_rng(seed)
    w, h = size
    img = np.zeros((h, w, 3), np.uint8)
    for y in range(h):  # sky-ish gradient
        img[y, :] = (200 - y // 4, 150 - y // 6, 60 + y // 5)
    cv2.rectangle(img, (80, 260), (560, 380), (30, 30, 200), -1)  # red "car" body
    cv2.rectangle(img, (180, 200), (440, 270), (40, 40, 170), -1)
    for cx in (170, 470):
        cv2.circle(img, (cx, 385), 45, (20, 20, 20), -1)
        cv2.circle(img, (cx, 385), 18, (180, 180, 180), -1)
    for _ in range(40):
        x, y = rng.integers(0, w), rng.integers(0, 200)
        cv2.circle(img, (int(x), int(y)), int(rng.integers(2, 8)), (255, 255, 255), -1)
    cv2.putText(img, "VT-2026", (230, 330), cv2.FONT_HERSHEY_SIMPLEX, 1.2, (255, 255, 255), 3)
    return img


def unrelated(seed: int = 9) -> np.ndarray:
    rng = np.random.default_rng(seed)
    img = np.full((400, 400, 3), (40, 160, 60), np.uint8)
    for _ in range(25):
        cv2.line(img, tuple(int(v) for v in rng.integers(0, 400, 2)), tuple(int(v) for v in rng.integers(0, 400, 2)),
                 tuple(int(v) for v in rng.integers(0, 255, 3)), 3)
    return img


def main() -> None:
    base = scene()
    cv2.imwrite(str(HERE / "sample_car.jpg"), base, [cv2.IMWRITE_JPEG_QUALITY, 90])
    Image.fromarray(cv2.cvtColor(base, cv2.COLOR_BGR2RGB)).save(HERE / "sample_car.png")
    Image.fromarray(cv2.cvtColor(base, cv2.COLOR_BGR2RGB)).save(HERE / "sample_car.webp", quality=85)
    cv2.imwrite(str(HERE / "sample_car_resized.jpg"), cv2.resize(base, (320, 240)), [cv2.IMWRITE_JPEG_QUALITY, 75])
    cv2.imwrite(str(HERE / "sample_car_cropped.jpg"), base[60:420, 60:580])
    cv2.imwrite(str(HERE / "sample_unrelated.jpg"), unrelated())
    # 9 second 10fps clip: 3 s car, 3 s unrelated, 3 s car shifted -> representative frames
    writer = cv2.VideoWriter(str(HERE / "sample_video.mp4"), cv2.VideoWriter_fourcc(*"mp4v"), 10, (640, 480))
    other = cv2.resize(unrelated(), (640, 480))
    for i in range(90):
        frame = base if i < 30 else other if i < 60 else np.roll(base, 40, axis=1)
        writer.write(frame)
    writer.release()
    # Polyglot: valid JPEG with an appended script payload (must be rejected)
    (HERE / "polyglot.jpg").write_bytes((HERE / "sample_car_resized.jpg").read_bytes() + b"<?php system($_GET['c']); ?>" * 50)
    (HERE / "not_an_image.jpg").write_bytes(b"<html><script>alert(1)</script></html>")


if __name__ == "__main__":
    main()
