import config

TOKEN = config.STATIC_TOKEN


def test_no_token_401(app_client):
    assert app_client.get("/api/users").status_code == 401


def test_wrong_token_401(app_client):
    r = app_client.get("/api/users", headers={"Authorization": "Bearer nope"})
    assert r.status_code == 401


def test_good_token_not_401(app_client):
    r = app_client.get("/api/users", headers={"Authorization": f"Bearer {TOKEN}"})
    assert r.status_code == 200
