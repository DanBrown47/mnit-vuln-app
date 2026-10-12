import os, tempfile, pytest
import sys
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
from db import (init_db, seed, get_user_by_login, list_users,
                user_exists, list_tea_for, create_user, add_tea)
import config


@pytest.fixture
def db():
    fd, path = tempfile.mkstemp(suffix=".db"); os.close(fd)
    init_db(path); seed(path)
    yield path
    os.unlink(path)


def test_admin_is_user_1_with_flag(db):
    teas = list_tea_for(db, 1)
    assert any(config.FLAG in t["body"] for t in teas)
    users = {u["id"]: u["username"] for u in list_users(db)}
    assert users[1] == "admin"


def test_login_and_duplicate(db):
    uid = create_user(db, "mallory", "pw")
    assert get_user_by_login(db, "mallory", "pw")["id"] == uid
    assert get_user_by_login(db, "mallory", "wrong") is None
    with pytest.raises(Exception):
        create_user(db, "mallory", "pw2")


def test_tea_flow(db):
    uid = create_user(db, "bob", "pw")
    assert list_tea_for(db, uid) == []
    add_tea(db, 1, uid, "hi bob")
    assert user_exists(db, uid) is True
    assert user_exists(db, 999) is False
    assert list_tea_for(db, uid)[0]["body"] == "hi bob"
