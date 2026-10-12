import config
H = {"Authorization": f"Bearer {config.STATIC_TOKEN}"}


def test_users_list(app_client):
    r = app_client.get("/api/users", headers=H)
    assert r.status_code == 200
    users = r.get_json()["users"]
    assert users[0] == {"id": 1, "username": "admin"}
    assert len(users) >= 4
