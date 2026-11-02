"""Visual similarity scoring between the query image and candidate images.

The score blends three signals:
  * embedding cosine similarity (CLIP or OpenCV descriptor),
  * perceptual-hash distance (exact / resized / re-encoded copies),
  * ORB keypoint matching with RANSAC (cropped or partially overlapping copies).

Scores are a *visual* similarity estimate in [0, 1]. They are never proof that two
images share an origin, an owner, or depict the same person."""
from __future__ import annotations

from dataclasses import dataclass

import cv2
import numpy as np

from app.services.embeddings import Embedder, cosine
from app.services.hashing import PerceptualHashes, compute_hashes, hash_distance

EXACT_HASH_DISTANCE = 4
SIMILAR_THRESHOLD = 0.55


@dataclass
class QueryFeatures:
    image: np.ndarray
    hashes: PerceptualHashes
    embedding: np.ndarray
    keypoints: tuple
    descriptors: np.ndarray | None


@dataclass
class SimilarityScore:
    score: float
    match_type: str  # exact | cropped | similar | related
    method: str
    hash_distance: int
    embedding_similarity: float
    keypoint_inlier_ratio: float


_orb = cv2.ORB_create(nfeatures=800)


def _orb_features(img: np.ndarray):  # noqa: ANN202
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    scale = 640 / max(gray.shape) if max(gray.shape) > 640 else 1.0
    if scale != 1.0:
        gray = cv2.resize(gray, None, fx=scale, fy=scale, interpolation=cv2.INTER_AREA)
    return _orb.detectAndCompute(gray, None)


def build_query_features(img: np.ndarray, embedder: Embedder) -> QueryFeatures:
    kp, des = _orb_features(img)
    return QueryFeatures(image=img, hashes=compute_hashes(img), embedding=embedder.embed_image(img),
                         keypoints=kp, descriptors=des)


def keypoint_inlier_ratio(query: QueryFeatures, cand_img: np.ndarray) -> float:
    if query.descriptors is None or len(query.keypoints) < 12:
        return 0.0
    kp2, des2 = _orb_features(cand_img)
    if des2 is None or len(kp2) < 12:
        return 0.0
    matcher = cv2.BFMatcher(cv2.NORM_HAMMING)
    pairs = matcher.knnMatch(query.descriptors, des2, k=2)
    good = [p[0] for p in pairs if len(p) == 2 and p[0].distance < 0.75 * p[1].distance]
    if len(good) < 10:
        return 0.0
    src = np.float32([query.keypoints[m.queryIdx].pt for m in good]).reshape(-1, 1, 2)
    dst = np.float32([kp2[m.trainIdx].pt for m in good]).reshape(-1, 1, 2)
    _, mask = cv2.findHomography(src, dst, cv2.RANSAC, 5.0)
    if mask is None:
        return 0.0
    inliers = int(mask.sum())
    return min(1.0, inliers / max(30, min(len(query.keypoints), len(kp2)) * 0.25))


def score_candidate(query: QueryFeatures, cand_img: np.ndarray, embedder: Embedder) -> SimilarityScore:
    cand_hashes = compute_hashes(cand_img)
    dist = hash_distance(query.hashes, cand_hashes)
    emb = cosine(query.embedding, embedder.embed_image(cand_img))
    emb_norm = max(0.0, (emb - 0.5) / 0.5) if embedder.name.startswith("opencv") else max(0.0, (emb - 0.15) / 0.85)
    hash_sim = max(0.0, 1.0 - dist / 32.0)
    inlier = keypoint_inlier_ratio(query, cand_img) if dist > EXACT_HASH_DISTANCE else 1.0

    score = 0.45 * emb_norm + 0.30 * hash_sim + 0.25 * inlier
    if dist <= EXACT_HASH_DISTANCE:
        match_type, score = "exact", max(score, 0.95)
    elif inlier >= 0.5:
        match_type, score = "cropped", max(score, 0.75)
    elif score >= SIMILAR_THRESHOLD:
        match_type = "similar"
    else:
        match_type = "related"
    return SimilarityScore(score=round(float(min(score, 1.0)), 4), match_type=match_type,
                           method=f"{embedder.name}+phash+orb", hash_distance=dist,
                           embedding_similarity=round(emb, 4), keypoint_inlier_ratio=round(inlier, 3))
