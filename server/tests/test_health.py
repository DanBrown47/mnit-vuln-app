def test_health_ok(app_client):
    resp = app_client.get("/health")
    assert resp.status_code == 200
    assert resp.get_json()["status"] == "ok"
