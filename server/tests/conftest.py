import os, sys, tempfile
import pytest

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
from app import create_app          # noqa: E402
from db import init_db, seed        # noqa: E402


@pytest.fixture
def app_client():
    fd, path = tempfile.mkstemp(suffix=".db")
    os.close(fd)
    init_db(path)
    seed(path)
    app = create_app(db_path=path)
    app.testing = True
    with app.test_client() as client:
        yield client
    os.unlink(path)
