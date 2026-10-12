import config
H = {"Authorization": f"Bearer {config.STATIC_TOKEN}"}


def uid_header(uid):
    h = dict(H); h["X-User-Id"] = str(uid); return h


def test_idor_reads_admin_flag(app_client):
    # The whole point: any caller sets X-User-Id: 1 and reads admin's tea.
    r = app_client.get("/api/tea", headers=uid_header(1))
    assert r.status_code == 200
    bodies = [t["body"] for t in r.get_json()["tea"]]
    assert config.FLAG in bodies


def test_tea_empty_for_new_user(app_client):
    reg = app_client.post("/api/register", json={"username": "newbie", "password": "p"}, headers=H)
    uid = reg.get_json()["user_id"]
    r = app_client.get("/api/tea", headers=uid_header(uid))
    assert r.status_code == 200 and r.get_json()["tea"] == []


def test_tea_missing_header_400(app_client):
    assert app_client.get("/api/tea", headers=H).status_code == 400


def test_tea_bad_header_400(app_client):
    h = dict(H); h["X-User-Id"] = "abc"
    assert app_client.get("/api/tea", headers=h).status_code == 400


def test_send_tea(app_client):
    r = app_client.post("/api/tea", json={"to_user_id": 2, "body": "hey"}, headers=uid_header(3))
    assert r.status_code == 201
    got = app_client.get("/api/tea", headers=uid_header(2)).get_json()["tea"]
    assert any(t["body"] == "hey" for t in got)


def test_send_tea_bad_recipient_400(app_client):
    r = app_client.post("/api/tea", json={"to_user_id": 999, "body": "x"}, headers=uid_header(3))
    assert r.status_code == 400
