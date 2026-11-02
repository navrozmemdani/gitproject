from pathlib import Path

import cv2
import numpy as np

from app.services.embeddings import OpenCvDescriptorEmbedder, cosine
from app.services.hashing import compute_hashes, decode_image, hash_distance
from app.services.image_analysis import analyze_image, dominant_colors
from app.services.similarity import build_query_features, score_candidate
from app.services.video_frames import extract_frames, probe
from tests.conftest import FIXTURES, fixture_bytes

EMB = OpenCvDescriptorEmbedder()


def img(name):
    return decode_image(fixture_bytes(name))


def test_hash_robust_to_resize_but_not_unrelated():
    base = compute_hashes(img("sample_car.jpg"))
    assert hash_distance(base, compute_hashes(img("sample_car_resized.jpg"))) <= 4
    assert hash_distance(base, compute_hashes(img("sample_unrelated.jpg"))) > 12


def test_embedding_similarity_ordering():
    q = EMB.embed_image(img("sample_car.jpg"))
    assert cosine(q, EMB.embed_image(img("sample_car_resized.jpg"))) > cosine(q, EMB.embed_image(img("sample_unrelated.jpg")))


def test_similarity_classification():
    q = build_query_features(img("sample_car.jpg"), EMB)
    exact = score_candidate(q, img("sample_car_resized.jpg"), EMB)
    cropped = score_candidate(q, img("sample_car_cropped.jpg"), EMB)
    other = score_candidate(q, img("sample_unrelated.jpg"), EMB)
    assert exact.match_type == "exact" and exact.score >= 0.95
    assert cropped.match_type in ("cropped", "similar", "exact") and cropped.score >= 0.55
    assert other.match_type == "related" and other.score < 0.55
    assert exact.score > cropped.score > other.score


def test_analysis_reports_sources_honestly():
    a = analyze_image(img("sample_car.jpg"), EMB)
    assert a.orientation == "landscape" and (a.width, a.height) == (640, 480)
    assert a.quality["source"] == "measured"
    assert a.colors["items"] and a.colors["source"] == "measured"
    # Without CLIP no objects are invented
    assert a.objects["items"] == [] and a.objects["source"] == "unavailable"


def test_dominant_colors_red():
    red = np.zeros((100, 100, 3), np.uint8)
    red[:] = (30, 30, 200)
    assert dominant_colors(red)[0]["name"] in ("red", "dark red")


def test_video_probe_and_frame_extraction():
    path = Path(FIXTURES / "sample_video.mp4")
    info = probe(path)
    assert 8.5 <= info.duration_seconds <= 9.5
    _, frames = extract_frames(path, interval=1.0, max_duration=60, max_frames=40)
    # 3 visually distinct segments -> near-duplicate consecutive frames are dropped
    assert 3 <= len(frames) <= 4
    assert frames[0].timestamp_ms == 0
    assert any(2900 <= f.timestamp_ms <= 3100 for f in frames)


def test_video_frame_limits():
    _, frames = extract_frames(Path(FIXTURES / "sample_video.mp4"), interval=3.0, max_duration=4, max_frames=40)
    assert all(f.timestamp_ms <= 4000 for f in frames)
    _, frames = extract_frames(Path(FIXTURES / "sample_video.mp4"), interval=0.5, max_duration=60, max_frames=2,
                               dedupe_distance=-1)
    assert len(frames) == 2


def test_cv2_can_write_jpeg():
    ok, _ = cv2.imencode(".jpg", img("sample_car.jpg"))
    assert ok
