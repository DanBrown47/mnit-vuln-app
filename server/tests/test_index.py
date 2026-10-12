import config


def test_index_has_heading(app_client):
    r = app_client.get("/")
    assert r.status_code == 200
    assert config.HEADING.encode() in r.data
