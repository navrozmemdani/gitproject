from app.core.config import Settings
from tests.conftest import ACCOUNT, GUEST, fixture_bytes, wait_done

API = "/api/v1"


async def run_search(app, client, headers):
    r = await client.post(f"{API}/search/image", headers=headers, data={"providers": "google_cse", "query_text": "red car"},
                          files={"file": ("a.jpg", fixture_bytes("sample_car.jpg"), "image/jpeg")})
    sid = r.json()["search_id"]
    await wait_done(app, client, sid, headers)
    return sid


async def test_history_list_delete_clear(app, client):
    ids = [await run_search(app, client, GUEST) for _ in range(3)]
    h = (await client.get(f"{API}/history", headers=GUEST)).json()
    assert h["total"] == 3 and h["items"][0]["id"] == ids[-1]
    assert h["items"][0]["preview_thumbnail_b64"]
    assert (await client.get(f"{API}/history?type=video", headers=GUEST)).json()["total"] == 0
    assert (await client.delete(f"{API}/history/{ids[0]}", headers=GUEST)).status_code == 204
    assert (await client.get(f"{API}/search/{ids[0]}", headers=GUEST)).status_code == 404
    assert (await client.delete(f"{API}/history", headers=GUEST)).status_code == 204
    assert (await client.get(f"{API}/history", headers=GUEST)).json()["total"] == 0


async def test_history_disabled_skips_preview(app, client):
    await client.put(f"{API}/settings", headers=GUEST, json={"history_enabled": False})
    await run_search(app, client, GUEST)
    assert (await client.get(f"{API}/history", headers=GUEST)).json()["items"][0]["preview_thumbnail_b64"] is None


async def test_favorites_require_account(client):
    r = await client.post(f"{API}/favorites", headers=GUEST, json={"snapshot": {"a": 1}})
    assert r.status_code == 403 and r.json()["error"]["code"] == "account_required"


async def test_favorites_and_collections(app, client):
    sid = await run_search(app, client, ACCOUNT)
    result = (await client.get(f"{API}/search/{sid}/results", headers=ACCOUNT)).json()["items"][0]
    col = (await client.post(f"{API}/collections", headers=ACCOUNT, json={"name": "Cars"})).json()
    dup = await client.post(f"{API}/collections", headers=ACCOUNT, json={"name": "Cars"})
    assert dup.status_code == 409
    r = await client.post(f"{API}/favorites", headers=ACCOUNT,
                          json={"search_result_id": result["id"], "collection_id": col["id"], "note": "original?"})
    assert r.status_code == 201, r.text
    fav = r.json()
    assert fav["snapshot"]["source_url"] == result["source_url"]
    r = await client.patch(f"{API}/favorites/{fav['id']}", headers=ACCOUNT, json={"note": "checked"})
    assert r.json()["note"] == "checked"
    listed = (await client.get(f"{API}/favorites?collection_id={col['id']}", headers=ACCOUNT)).json()
    assert [f["id"] for f in listed] == [fav["id"]]
    # Favourite survives history deletion thanks to its snapshot
    await client.delete(f"{API}/history/{sid}", headers=ACCOUNT)
    listed = (await client.get(f"{API}/favorites", headers=ACCOUNT)).json()
    assert listed[0]["snapshot"]["source_url"] == result["source_url"]
    assert (await client.delete(f"{API}/collections/{col['id']}", headers=ACCOUNT)).status_code == 204
    assert (await client.get(f"{API}/favorites", headers=ACCOUNT)).json()[0]["collection_id"] is None
    assert (await client.delete(f"{API}/favorites/{fav['id']}", headers=ACCOUNT)).status_code == 204
    assert (await client.delete(f"{API}/favorites/{fav['id']}", headers=ACCOUNT)).status_code == 404


async def test_cannot_favorite_others_results(app, client):
    sid = await run_search(app, client, GUEST)
    rid = (await client.get(f"{API}/search/{sid}/results", headers=GUEST)).json()["items"][0]["id"]
    r = await client.post(f"{API}/favorites", headers=ACCOUNT, json={"search_result_id": rid})
    assert r.status_code == 404


async def test_settings_roundtrip_and_validation(client):
    s = (await client.get(f"{API}/settings", headers=ACCOUNT)).json()
    assert s["frame_interval_seconds"] == 3.0 and s["third_party_consent"] is False
    r = await client.put(f"{API}/settings", headers=ACCOUNT, json={
        "theme": "dark", "frame_interval_seconds": 5, "enabled_providers": ["google_cse", "reddit"],
        "third_party_consent": True, "cloud_sync_enabled": True})
    assert r.status_code == 200 and r.json()["theme"] == "dark" and r.json()["enabled_providers"] == ["google_cse", "reddit"]
    assert (await client.put(f"{API}/settings", headers=ACCOUNT, json={"frame_interval_seconds": 0.1})).status_code == 422
    assert (await client.put(f"{API}/settings", headers=ACCOUNT, json={"enabled_providers": ["nope"]})).status_code == 422
    assert (await client.put(f"{API}/settings", headers=GUEST, json={"cloud_sync_enabled": True})).status_code == 403


async def test_me_and_account_deletion(app, client):
    me = (await client.get(f"{API}/me", headers=ACCOUNT)).json()
    assert me["is_guest"] is False and me["email"] == "alice@dev.local" and me["auth_provider"] == "dev"
    await run_search(app, client, ACCOUNT)
    assert (await client.delete(f"{API}/me", headers=ACCOUNT)).status_code == 204
    assert (await client.get(f"{API}/history", headers=ACCOUNT)).json()["total"] == 0


async def test_dev_tokens_disabled(app, client, settings):
    settings.auth_dev_tokens = False
    r = await client.get(f"{API}/me", headers=ACCOUNT)
    assert r.status_code == 401


async def test_client_api_key_enforced(client, settings):
    settings.client_api_keys = ["secret-client-key"]
    assert (await client.get(f"{API}/me", headers=GUEST)).status_code == 401
    ok = await client.get(f"{API}/me", headers={**GUEST, "X-API-Key": "secret-client-key"})
    assert ok.status_code == 200
    assert (await client.get(f"{API}/providers")).status_code == 401
    assert (await client.get(f"{API}/health")).status_code == 200


async def test_rate_limiting(client, settings):
    settings.rate_limit_per_minute = 3
    codes = [(await client.get(f"{API}/me", headers=GUEST)).status_code for _ in range(5)]
    assert codes[:3] == [200, 200, 200] and codes[3] == 429
    r = await client.get(f"{API}/me", headers=GUEST)
    assert r.json()["error"]["retryable"] is True and "Retry-After" in r.headers


async def test_invalid_guest_id(client):
    assert (await client.get(f"{API}/me", headers={"X-Guest-Id": "../../etc"})).status_code == 401


def test_production_config_validation():
    s = Settings(_env_file=None, environment="production", provider_mock_mode=True, auth_dev_tokens=True)
    problems = s.validate_for_runtime()
    assert len(problems) == 4


def test_csv_env_parsing(monkeypatch):
    monkeypatch.setenv("CLIENT_API_KEYS", "a, b")
    assert Settings(_env_file=None).client_api_keys == ["a", "b"]


def test_signed_temp_urls(tmp_path):
    from app.services.temp_storage import TempStorage

    st = TempStorage(Settings(_env_file=None, temp_dir=tmp_path, signed_url_secret="s"))
    p = st.save(b"x", "jpg")
    token = st.sign(p.name)
    assert st.verify(token) == p.name
    assert st.verify(token[:-2] + "aa") is None
    assert st.resolve("../etc/passwd") is None
    assert st.sign(p.name, ttl_seconds=-1) and st.verify(st.sign(p.name, ttl_seconds=-1)) is None
