import config
H = {"Authorization": f"Bearer {config.STATIC_TOKEN}"}


def test_register_login(app_client):
    r = app_client.post("/api/register", json={"username": "zoe", "password": "p"}, headers=H)
    assert r.status_code == 201
    uid = r.get_json()["user_id"]
    r = app_client.post("/api/login", json={"username": "zoe", "password": "p"}, headers=H)
    assert r.status_code == 200 and r.get_json()["user_id"] == uid


def test_register_duplicate_409(app_client):
    app_client.post("/api/register", json={"username": "dup", "password": "p"}, headers=H)
    r = app_client.post("/api/register", json={"username": "dup", "password": "p"}, headers=H)
    assert r.status_code == 409


def test_register_missing_field_400(app_client):
    r = app_client.post("/api/register", json={"username": "x"}, headers=H)
    assert r.status_code == 400


def test_login_bad_creds_401(app_client):
    r = app_client.post("/api/login", json={"username": "admin", "password": "wrong"}, headers=H)
    assert r.status_code == 401
