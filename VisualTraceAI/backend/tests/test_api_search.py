import pytest

from tests.conftest import ACCOUNT, GUEST, fixture_bytes, wait_done

API = "/api/v1"
PROVIDERS = "google_cse,openverse,reddit,tineye,instagram,facebook,x"


async def upload(client, name="sample_car.jpg", headers=GUEST, **form):
    data = {"providers": PROVIDERS, "query_text": "red sports car", **{k: str(v).lower() if isinstance(v, bool) else v for k, v in form.items()}}
    return await client.post(f"{API}/search/image", headers=headers, data=data,
                             files={"file": (name, fixture_bytes(name), "image/jpeg" if name.endswith("jpg") else None)})


async def test_health_and_providers(client):
    r = await client.get(f"{API}/health")
    assert r.status_code == 200 and r.json()["database"] == "ok"
    r = await client.get(f"{API}/providers")
    body = r.json()
    keys = {p["key"]: p for p in body["providers"]}
    assert keys["google_cse"]["mode"] == "live"
    assert keys["pinterest"]["mode"] == "live"  # site-search fallback through Google
    assert keys["serpapi_lens"]["mode"] == "unavailable"
    assert body["mock_mode"] is False


async def test_requires_identity(client):
    r = await client.post(f"{API}/search/image", files={"file": ("a.jpg", fixture_bytes("sample_car.jpg"))})
    assert r.status_code == 401 and r.json()["error"]["code"] == "authentication_failed"
    r = await client.get(f"{API}/history", headers={"Authorization": "Bearer garbage-token"})
    assert r.status_code in (401, 503)


async def test_image_search_end_to_end(app, client, transport):
    r = await upload(client)
    assert r.status_code == 202, r.text
    sid = r.json()["search_id"]
    detail = await wait_done(app, client, sid)
    assert detail["status"] == "completed", detail
    assert detail["media_metadata"]["width"] == 640
    assert detail["perceptual_hashes"]["phash"]
    assert detail["analysis"]["embedding_model"] == "opencv-global-descriptor-v1"
    assert "do not prove" in detail["disclaimer"]
    reports = {p["key"]: p for p in detail["provider_reports"]}
    assert reports["tineye"]["status"] == "skipped_no_consent"
    assert reports["google_cse"]["status"] == "ok" and reports["google_cse"]["searched"]
    assert reports["x"]["status"] == "ok" or reports["x"]["status"] in ("no_results", "error")
    assert not any("tineye" in c for c in transport.calls)

    res = (await client.get(f"{API}/search/{sid}/results", headers=GUEST)).json()
    items = res["items"]
    assert res["total"] == len(items) == detail["result_count"] > 0
    scores = [i["similarity_score"] for i in items if i["similarity_score"] is not None]
    assert scores == sorted(scores, reverse=True)
    top = items[0]
    assert top["match_type"] == "exact" and top["similarity_score"] >= 0.95
    # De-duplication: the autoblog page was returned by Google and Openverse
    autoblog = [i for i in items if "autoblog.test/posts/red-car" in i["source_url"]]
    assert len(autoblog) == 1 and set(autoblog[0]["also_found_by"]) >= {"google_cse", "openverse"}
    unrelated = next(i for i in items if "art.test" in i["source_url"])
    assert unrelated["match_type"] == "related"
    reddit = next(i for i in items if i["platform"] == "Reddit")
    assert reddit["author_username"] == "car_spotter"
    assert reddit["field_provenance"]["author_username"] == "provider"
    assert reddit["field_provenance"]["profile_bio"] == "unavailable"
    assert all(i["is_mock"] is False for i in items)
    assert res["counts"]["all"] == len(items) and res["counts"]["social"] >= 1

    social = (await client.get(f"{API}/search/{sid}/results?filter=social", headers=GUEST)).json()
    assert social["items"] and all(i["category"] == "social" for i in social["items"])
    exact = (await client.get(f"{API}/search/{sid}/results?filter=exact", headers=GUEST)).json()
    assert all(i["match_type"] == "exact" for i in exact["items"])
    recent = (await client.get(f"{API}/search/{sid}/results?sort=recent", headers=GUEST)).json()
    assert recent["items"][0]["published_at"] is not None


async def test_temp_files_deleted_after_search(app, client, settings):
    r = await upload(client)
    await wait_done(app, client, r.json()["search_id"])
    assert list(settings.temp_dir.iterdir()) == []


async def test_consent_enables_upload_providers(app, client, transport):
    r = await upload(client, third_party_consent=True)
    detail = await wait_done(app, client, r.json()["search_id"])
    reports = {p["key"]: p for p in detail["provider_reports"]}
    assert reports["tineye"]["status"] == "ok"
    res = (await client.get(f"{API}/search/{detail['id']}/results?filter=news", headers=GUEST)).json()
    assert any("example-times" in i["source_url"] for i in res["items"])


async def test_provider_failures_produce_partial_status(app, client, transport):
    transport.fail["customsearch.googleapis.com"] = 500
    r = await upload(client)
    detail = await wait_done(app, client, r.json()["search_id"])
    assert detail["status"] == "partial"
    reports = {p["key"]: p for p in detail["provider_reports"]}
    assert reports["google_cse"]["status"] == "error" and reports["openverse"]["status"] == "ok"


async def test_all_providers_failing_marks_failed(app, client, transport):
    for host in ("customsearch.googleapis.com", "api.openverse.org", "www.reddit.com", "oauth.reddit.com"):
        transport.fail[host] = 503
    r = await client.post(f"{API}/search/image", headers=GUEST, data={"providers": "google_cse,openverse,reddit", "query_text": "car"},
                          files={"file": ("a.jpg", fixture_bytes("sample_car.jpg"), "image/jpeg")})
    detail = await wait_done(app, client, r.json()["search_id"])
    assert detail["status"] == "failed" and detail["result_count"] == 0


@pytest.mark.parametrize("name,code,status", [
    ("polyglot.jpg", "suspicious_file", 422),
    ("not_an_image.jpg", "unsupported_media_type", 415),
])
async def test_rejects_bad_uploads(client, name, code, status):
    r = await upload(client, name)
    assert r.status_code == status and r.json()["error"]["code"] == code


async def test_rejects_unknown_provider(client):
    r = await client.post(f"{API}/search/image", headers=GUEST, data={"providers": "made_up"},
                          files={"file": ("a.jpg", fixture_bytes("sample_car.jpg"), "image/jpeg")})
    assert r.status_code == 422 and r.json()["error"]["code"] == "unknown_provider"


async def test_requires_image(client):
    r = await client.post(f"{API}/search/image", headers=GUEST, data={})
    assert r.status_code == 422 and r.json()["error"]["code"] == "missing_image"


async def test_private_image_url_rejected(client, monkeypatch):
    from app.services import safe_http

    monkeypatch.undo()  # restore real SSRF guard
    r = await client.post(f"{API}/search/image", headers=GUEST, data={"image_url": "http://127.0.0.1/admin.png"})
    assert r.status_code == 422 and r.json()["error"]["code"] == "unsafe_url"
    assert safe_http.assert_public_url.__name__ == "assert_public_url"


async def test_video_frames_search_reports_timestamps(app, client):
    files = [("frames", (f"f{i}.jpg", fixture_bytes(n), "image/jpeg"))
             for i, n in enumerate(["sample_car.jpg", "sample_unrelated.jpg"])]
    r = await client.post(f"{API}/search/video", headers=GUEST, files=files,
                          data={"frame_timestamps_ms": "0,3000", "providers": "google_cse", "query_text": "red car", "duration_ms": "9000",
                                "frame_interval_seconds": "3"})
    assert r.status_code == 202, r.text
    detail = await wait_done(app, client, r.json()["search_id"])
    assert detail["search_type"] == "video" and detail["frames_processed"] == 2
    items = (await client.get(f"{API}/search/{detail['id']}/results", headers=GUEST)).json()["items"]
    assert {i["frame_timestamp_ms"] for i in items} <= {0, 3000}
    assert items[0]["frame_timestamp_ms"] == 0  # the car frame produced the exact match


async def test_video_frame_count_mismatch(client):
    r = await client.post(f"{API}/search/video", headers=GUEST, data={"frame_timestamps_ms": "0,1"},
                          files=[("frames", ("f.jpg", fixture_bytes("sample_car.jpg"), "image/jpeg"))])
    assert r.status_code == 422


async def test_full_video_requires_consent(app, client):
    files = {"video": ("clip.mp4", fixture_bytes("sample_video.mp4"), "video/mp4")}
    r = await client.post(f"{API}/search/video", headers=GUEST, files=files, data={"providers": "google_cse"})
    assert r.status_code == 428 and r.json()["error"]["code"] == "consent_required"
    r = await client.post(f"{API}/search/video", headers=GUEST, files=files,
                          data={"providers": "google_cse,local_index", "upload_consent": "true", "frame_interval_seconds": "3"})
    assert r.status_code == 202, r.text
    detail = await wait_done(app, client, r.json()["search_id"])
    assert detail["media_metadata"]["frames_extracted"] >= 2
    assert detail["status"] == "completed"
    reports = {p["key"]: p for p in detail["provider_reports"]}
    assert reports["google_cse"]["status"] == "skipped_no_query"  # no CLIP keywords and no description


async def test_text_characteristics_search(app, client):
    r = await client.post(f"{API}/search/text", headers=GUEST,
                          json={"query": "red sports car", "providers": ["google_cse"]})
    assert r.status_code == 202
    detail = await wait_done(app, client, r.json()["search_id"])
    assert detail["status"] == "completed" and detail["result_count"] == 4


async def test_search_ownership(app, client):
    r = await upload(client)
    sid = r.json()["search_id"]
    await app.state.runner.drain()
    other = {"X-Guest-Id": "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"}
    assert (await client.get(f"{API}/search/{sid}", headers=other)).status_code == 404
    assert (await client.get(f"{API}/search/{sid}/results", headers=ACCOUNT)).status_code == 404


async def test_report_result(app, client):
    sid = (await upload(client)).json()["search_id"]
    await wait_done(app, client, sid)
    rid = (await client.get(f"{API}/search/{sid}/results", headers=GUEST)).json()["items"][0]["id"]
    r = await client.post(f"{API}/results/{rid}/report", headers=GUEST, json={"reason": "not_similar"})
    assert r.status_code == 204
    r = await client.post(f"{API}/results/{rid}/report", headers=GUEST, json={"reason": "bogus"})
    assert r.status_code == 422


async def test_local_index_finds_previous_public_results(app, client):
    sid1 = (await upload(client)).json()["search_id"]
    await wait_done(app, client, sid1)
    r = await client.post(f"{API}/search/image", headers=GUEST, data={"providers": "local_index"},
                          files={"file": ("a.jpg", fixture_bytes("sample_car_resized.jpg"), "image/jpeg")})
    detail = await wait_done(app, client, r.json()["search_id"])
    assert detail["result_count"] >= 1
    items = (await client.get(f"{API}/search/{detail['id']}/results", headers=GUEST)).json()["items"]
    assert all(i["provider_key"] == "local_index" for i in items)


async def test_provider_table_sync_and_global_disable(app, client):
    from sqlalchemy import select

    from app.database.session import session_factory
    from app.models import SearchProvider
    from app.services.provider_sync import sync_providers

    await sync_providers(app.state.registry)
    assert app.state.registry.disabled == set()  # fresh rows must not disable providers
    async with session_factory()() as db:
        row = await db.scalar(select(SearchProvider).where(SearchProvider.key == "google_cse"))
        assert row.last_status == "configured"
        row.enabled = False
        await db.commit()
    await sync_providers(app.state.registry)
    assert "google_cse" in app.state.registry.disabled
    body = (await client.get(f"{API}/providers")).json()
    assert next(p for p in body["providers"] if p["key"] == "google_cse")["globally_disabled"] is True
