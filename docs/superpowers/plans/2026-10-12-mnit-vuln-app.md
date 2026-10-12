# MNIT Vuln App Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a deliberately vulnerable "Tea" Android app + Flask/sqlite backend for an MNIT security session, where the intended attack is: decompile the APK to find a hardcoded Bearer token, then forge `X-User-Id: 1` to read the admin's flag.

**Architecture:** Flask + sqlite3 backend trusts a static Bearer token (hardcoded in the app) as its only auth gate and trusts the `X-User-Id` header for identity with no ownership check. Native Kotlin Android app ships that token plus a set of insecure config flags so MobSF reports findings. Deployed to a VPS as an isolated systemd service behind its own nginx block, with a cron that stops only this service at session end.

**Tech Stack:** Python 3.12, Flask, sqlite3, pytest, gunicorn, nginx, certbot; Kotlin, Android Gradle (SDK at `~/Android/Sdk`, JDK 21), HttpURLConnection + org.json; dockerized MobSF.

**Spec:** `docs/superpowers/specs/2026-10-12-mnit-vuln-app-design.md`

## Global Constraints

- Heading text, exact, on every app screen and web page: `for MNIT Use - Vuln App`
- Flag, exact, stored as admin's tea: `flag{This is often harder than real world NGL.}`
- Static Bearer token, one value for all installs, hardcoded in app AND expected by server: `mnit_app_2026_bearer_9f3c1a` (throwaway, no real privilege)
- Admin is user id `1`, username `admin`.
- Identity header name: `X-User-Id`. Server trusts it with no verification — do not add an ownership check.
- Server base URL in app: `https://android.danwand.in`
- Passwords stored plaintext in sqlite (intentional finding).
- Neo-brutalist UI, palette: `--ink #0B0B0C`, `--paper #EDE7DC`, `--oxblood #8A1C1C`, `--crimson #C0271E`, `--blush #E9D6D1`, `--electric #2A2DF0`. Thick ink borders, hard offset shadows (no blur), no rounded corners.
- Do NOT touch the pre-existing app already running on the VPS. Vuln app gets its own port, own systemd unit (`mnit-vulnapp.service`), own nginx server block.
- Secrets (VPS root password, etc.) never enter the repo, committed config, `.env`, or logs. The APK's Bearer token is the exception — it is meant to be in the app.
- Teardown: stop + disable `mnit-vulnapp.service` at 2026-10-14 23:00 server wall-clock time (confirm TZ at deploy).

## Review Focus

- **Missing/empty `Authorization` header** on `/api/*` → server returns 401, not 500 or a stack trace. (Task 3)
- **Missing or non-integer `X-User-Id`** on `/api/tea` → server returns a clean 400, not a 500. The vuln is trusting the id, not crashing on a bad one. (Task 6)
- **Register with a duplicate username** → clean 409, not a sqlite IntegrityError 500. (Task 4)
- **`GET /api/tea` for a user with no tea** → `200` with empty list, not an error. (Task 6)
- **`POST /api/tea` to a non-existent `to_user_id`** → clean 400, not a dangling row or 500. (Task 6)

---

## Phase A — Backend (Flask + sqlite3)

### Task 1: Server scaffold + config + health endpoint

**Files:**
- Create: `server/app.py`
- Create: `server/config.py`
- Create: `server/requirements.txt`
- Create: `server/tests/test_health.py`
- Create: `server/tests/conftest.py`

**Interfaces:**
- Produces: `create_app(db_path=None) -> Flask`; config constants `STATIC_TOKEN`, `DEFAULT_DB_PATH`, `HEADING`, `FLAG`.

- [ ] **Step 1: Write `server/requirements.txt`**

```
Flask==3.0.3
gunicorn==23.0.0
pytest==8.3.3
```

- [ ] **Step 2: Write `server/config.py`**

```python
STATIC_TOKEN = "mnit_app_2026_bearer_9f3c1a"
DEFAULT_DB_PATH = "tea.db"
HEADING = "for MNIT Use - Vuln App"
FLAG = "flag{This is often harder than real world NGL.}"
```

- [ ] **Step 3: Write `server/tests/conftest.py`**

```python
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
```

- [ ] **Step 4: Write the failing test `server/tests/test_health.py`**

```python
def test_health_ok(app_client):
    resp = app_client.get("/health")
    assert resp.status_code == 200
    assert resp.get_json()["status"] == "ok"
```

- [ ] **Step 5: Run it to verify it fails**

Run: `cd server && python -m pytest tests/test_health.py -v`
Expected: FAIL (ImportError / create_app missing).

- [ ] **Step 6: Write minimal `server/app.py`**

```python
from flask import Flask, jsonify
import config


def create_app(db_path=None):
    app = Flask(__name__)
    app.config["DB_PATH"] = db_path or config.DEFAULT_DB_PATH

    @app.get("/health")
    def health():
        return jsonify(status="ok")

    return app


app = create_app()

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=8099)
```

Note: Task 2 creates `db.py` (imported by conftest). Run this task's test after Task 2's `db.py` exists, or stub it. To keep order simple, implement Task 2 immediately after Step 6, then run Step 7.

- [ ] **Step 7: Run the test to verify it passes** (after Task 2's `db.py` exists)

Run: `cd server && python -m pytest tests/test_health.py -v`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add server/ && git commit -m "feat(server): scaffold Flask app with health endpoint"
```

### Task 2: Database layer + schema + seed

**Files:**
- Create: `server/db.py`
- Create: `server/schema.sql`
- Create: `server/seed.py`
- Create: `server/tests/test_db.py`

**Interfaces:**
- Consumes: `config.STATIC_TOKEN`, `config.FLAG`.
- Produces: `get_db(path) -> sqlite3.Connection` (row_factory = Row); `init_db(path)`; `seed(path)`; `create_user(path, username, password) -> int`; `get_user_by_login(path, username, password) -> row|None`; `list_users(path) -> list[row]`; `user_exists(path, user_id) -> bool`; `list_tea_for(path, user_id) -> list[row]`; `add_tea(path, from_id, to_id, body)`.

- [ ] **Step 1: Write `server/schema.sql`**

```sql
CREATE TABLE IF NOT EXISTS users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    username TEXT UNIQUE NOT NULL,
    password TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS tea (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    from_user_id INTEGER NOT NULL,
    to_user_id INTEGER NOT NULL,
    body TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
);
```

- [ ] **Step 2: Write the failing test `server/tests/test_db.py`**

```python
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
```

- [ ] **Step 3: Run it to verify it fails**

Run: `cd server && python -m pytest tests/test_db.py -v`
Expected: FAIL (ImportError).

- [ ] **Step 4: Write `server/db.py`**

```python
import os, sqlite3


def get_db(path):
    conn = sqlite3.connect(path)
    conn.row_factory = sqlite3.Row
    return conn


def init_db(path):
    schema = os.path.join(os.path.dirname(__file__), "schema.sql")
    with open(schema) as f, get_db(path) as conn:
        conn.executescript(f.read())


def create_user(path, username, password):
    with get_db(path) as conn:
        cur = conn.execute(
            "INSERT INTO users(username, password) VALUES (?, ?)",
            (username, password),
        )
        return cur.lastrowid


def get_user_by_login(path, username, password):
    with get_db(path) as conn:
        return conn.execute(
            "SELECT id, username FROM users WHERE username=? AND password=?",
            (username, password),
        ).fetchone()


def list_users(path):
    with get_db(path) as conn:
        return conn.execute("SELECT id, username FROM users ORDER BY id").fetchall()


def user_exists(path, user_id):
    with get_db(path) as conn:
        return conn.execute("SELECT 1 FROM users WHERE id=?", (user_id,)).fetchone() is not None


def list_tea_for(path, user_id):
    with get_db(path) as conn:
        return conn.execute(
            "SELECT id, from_user_id, to_user_id, body, created_at "
            "FROM tea WHERE to_user_id=? ORDER BY id",
            (user_id,),
        ).fetchall()


def add_tea(path, from_id, to_id, body):
    with get_db(path) as conn:
        conn.execute(
            "INSERT INTO tea(from_user_id, to_user_id, body) VALUES (?, ?, ?)",
            (from_id, to_id, body),
        )


def seed(path):
    import config
    with get_db(path) as conn:
        if conn.execute("SELECT COUNT(*) c FROM users").fetchone()["c"]:
            return
    create_user(path, "admin", "admin_pw_2026")           # id 1
    create_user(path, "priya", "priya123")                # id 2
    create_user(path, "rahul", "rahul123")                # id 3
    create_user(path, "sneha", "sneha123")                # id 4
    add_tea(path, 2, 1, config.FLAG)
    add_tea(path, 3, 1, "admin, the deploy keys are under my desk.")
    add_tea(path, 1, 2, "priya, your demo slot is 3pm.")
    add_tea(path, 4, 3, "rahul, lunch?")
    add_tea(path, 2, 4, "sneha, loved your talk.")
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd server && python -m pytest tests/test_db.py tests/test_health.py -v`
Expected: PASS (all).

- [ ] **Step 6: Commit**

```bash
git add server/ && git commit -m "feat(server): db layer, schema, and seed with admin flag"
```

### Task 3: Bearer-token auth gate

**Files:**
- Modify: `server/app.py`
- Create: `server/auth.py`
- Create: `server/tests/test_auth.py`

**Interfaces:**
- Consumes: `config.STATIC_TOKEN`.
- Produces: `require_token(view)` decorator that returns 401 JSON `{"error": "unauthorized"}` when the `Authorization` header is missing or not `Bearer <STATIC_TOKEN>`.

- [ ] **Step 1: Write the failing test `server/tests/test_auth.py`**

```python
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
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd server && python -m pytest tests/test_auth.py -v`
Expected: FAIL (route /api/users missing → 404, not 401/200).

- [ ] **Step 3: Write `server/auth.py`**

```python
from functools import wraps
from flask import request, jsonify
import config


def require_token(view):
    @wraps(view)
    def wrapper(*args, **kwargs):
        header = request.headers.get("Authorization", "")
        if header != f"Bearer {config.STATIC_TOKEN}":
            return jsonify(error="unauthorized"), 401
        return view(*args, **kwargs)
    return wrapper
```

- [ ] **Step 4: Add a temporary guarded `/api/users` stub to `app.py`**

Inside `create_app`, add (Task 5 will replace the body with real data):

```python
    from auth import require_token
    from flask import jsonify

    @app.get("/api/users")
    @require_token
    def api_users():
        return jsonify(users=[])
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd server && python -m pytest tests/test_auth.py -v`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add server/ && git commit -m "feat(server): static Bearer token auth gate"
```

### Task 4: Register + login endpoints

**Files:**
- Modify: `server/app.py`
- Create: `server/tests/test_accounts.py`

**Interfaces:**
- Consumes: `db.create_user`, `db.get_user_by_login`, `require_token`.
- Produces: `POST /api/register {username,password}` → 201 `{user_id}` / 409 on dup / 400 on missing field; `POST /api/login {username,password}` → 200 `{user_id}` / 401 on bad creds.

- [ ] **Step 1: Write the failing test `server/tests/test_accounts.py`**

```python
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
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd server && python -m pytest tests/test_accounts.py -v`
Expected: FAIL (routes missing → 404).

- [ ] **Step 3: Implement in `app.py`** (inside `create_app`, using `app.config["DB_PATH"]`)

```python
    import sqlite3
    from flask import request
    import db

    @app.post("/api/register")
    @require_token
    def api_register():
        data = request.get_json(silent=True) or {}
        username, password = data.get("username"), data.get("password")
        if not username or not password:
            return jsonify(error="username and password required"), 400
        try:
            uid = db.create_user(app.config["DB_PATH"], username, password)
        except sqlite3.IntegrityError:
            return jsonify(error="username taken"), 409
        return jsonify(user_id=uid), 201

    @app.post("/api/login")
    @require_token
    def api_login():
        data = request.get_json(silent=True) or {}
        row = db.get_user_by_login(app.config["DB_PATH"],
                                   data.get("username"), data.get("password"))
        if row is None:
            return jsonify(error="invalid credentials"), 401
        return jsonify(user_id=row["id"])
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd server && python -m pytest tests/test_accounts.py -v`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add server/ && git commit -m "feat(server): register and login endpoints"
```

### Task 5: Users list endpoint

**Files:**
- Modify: `server/app.py` (replace the Task 3 stub body)
- Create: `server/tests/test_users.py`

**Interfaces:**
- Consumes: `db.list_users`.
- Produces: `GET /api/users` → 200 `{"users": [{"id", "username"}, ...]}` ordered by id.

- [ ] **Step 1: Write the failing test `server/tests/test_users.py`**

```python
import config
H = {"Authorization": f"Bearer {config.STATIC_TOKEN}"}


def test_users_list(app_client):
    r = app_client.get("/api/users", headers=H)
    assert r.status_code == 200
    users = r.get_json()["users"]
    assert users[0] == {"id": 1, "username": "admin"}
    assert len(users) >= 4
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd server && python -m pytest tests/test_users.py -v`
Expected: FAIL (stub returns empty list).

- [ ] **Step 3: Replace the `api_users` body in `app.py`**

```python
    @app.get("/api/users")
    @require_token
    def api_users():
        rows = db.list_users(app.config["DB_PATH"])
        return jsonify(users=[{"id": r["id"], "username": r["username"]} for r in rows])
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd server && python -m pytest tests/test_users.py -v`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add server/ && git commit -m "feat(server): users list endpoint"
```

### Task 6: Tea endpoints (the IDOR) + the exploit proof

**Files:**
- Modify: `server/app.py`
- Create: `server/tests/test_tea.py`

**Interfaces:**
- Consumes: `db.list_tea_for`, `db.add_tea`, `db.user_exists`.
- Produces:
  - `GET /api/tea` with header `X-User-Id` → 200 `{"tea": [...]}` for that id; **no ownership check** (the vuln). 400 if header missing/non-integer.
  - `POST /api/tea` `{to_user_id, body}` + `X-User-Id` → 201; 400 on missing fields / bad caller id / non-existent recipient.
- Helper: `caller_id()` parses `X-User-Id` → int or None.

- [ ] **Step 1: Write the failing test `server/tests/test_tea.py`**

```python
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
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd server && python -m pytest tests/test_tea.py -v`
Expected: FAIL (routes missing → 404).

- [ ] **Step 3: Implement in `app.py`**

```python
    def caller_id():
        raw = request.headers.get("X-User-Id")
        if raw is None:
            return None
        try:
            return int(raw)
        except ValueError:
            return None

    @app.get("/api/tea")
    @require_token
    def api_get_tea():
        uid = caller_id()
        if uid is None:
            return jsonify(error="X-User-Id required"), 400
        # INTENTIONAL VULN: trusts X-User-Id, no check the caller owns it.
        rows = db.list_tea_for(app.config["DB_PATH"], uid)
        return jsonify(tea=[dict(r) for r in rows])

    @app.post("/api/tea")
    @require_token
    def api_post_tea():
        uid = caller_id()
        if uid is None:
            return jsonify(error="X-User-Id required"), 400
        data = request.get_json(silent=True) or {}
        to_user_id, body = data.get("to_user_id"), data.get("body")
        if not isinstance(to_user_id, int) or not body:
            return jsonify(error="to_user_id (int) and body required"), 400
        if not db.user_exists(app.config["DB_PATH"], to_user_id):
            return jsonify(error="recipient not found"), 400
        db.add_tea(app.config["DB_PATH"], uid, to_user_id, body)
        return jsonify(status="sent"), 201
```

- [ ] **Step 4: Run all server tests to verify they pass**

Run: `cd server && python -m pytest -v`
Expected: PASS (all).

- [ ] **Step 5: Commit**

```bash
git add server/ && git commit -m "feat(server): tea endpoints with intentional X-User-Id IDOR"
```

### Task 7: Neo-brutalist web landing page

**Files:**
- Modify: `server/app.py`
- Create: `server/templates/index.html`
- Create: `server/static/brutal.css`
- Create: `server/tests/test_index.py`

**Interfaces:**
- Produces: `GET /` → 200 HTML containing the heading and a short "intentionally vulnerable — authorized MNIT training only" note. No auth required on `/`.

- [ ] **Step 1: Write the failing test `server/tests/test_index.py`**

```python
import config


def test_index_has_heading(app_client):
    r = app_client.get("/")
    assert r.status_code == 200
    assert config.HEADING.encode() in r.data
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd server && python -m pytest tests/test_index.py -v`
Expected: FAIL (no `/` route).

- [ ] **Step 3: Write `server/static/brutal.css`**

```css
:root{--ink:#0B0B0C;--paper:#EDE7DC;--oxblood:#8A1C1C;--crimson:#C0271E;--blush:#E9D6D1;--electric:#2A2DF0}
*{box-sizing:border-box;margin:0}
body{background:var(--paper);color:var(--ink);font-family:"Courier New",monospace;padding:24px}
.heading{background:var(--ink);color:var(--paper);border:4px solid var(--ink);
  box-shadow:10px 10px 0 var(--oxblood);padding:18px 22px;font-weight:900;
  letter-spacing:2px;text-transform:uppercase;font-size:clamp(20px,5vw,40px)}
.block{background:var(--blush);border:4px solid var(--ink);box-shadow:8px 8px 0 var(--ink);
  padding:18px;margin-top:24px;max-width:720px}
.warn{color:var(--oxblood);font-weight:900;text-transform:uppercase}
a{color:var(--electric)}
```

- [ ] **Step 4: Write `server/templates/index.html`**

```html
<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>MNIT Vuln App</title>
<link rel="stylesheet" href="{{ url_for('static', filename='brutal.css') }}"></head>
<body>
  <div class="heading">{{ heading }}</div>
  <div class="block">
    <p class="warn">Intentionally vulnerable.</p>
    <p>Authorized MNIT training target only. API under <code>/api</code>.</p>
  </div>
</body></html>
```

- [ ] **Step 5: Add the route in `app.py`**

```python
    from flask import render_template

    @app.get("/")
    def index():
        return render_template("index.html", heading=config.HEADING)
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `cd server && python -m pytest tests/test_index.py -v`
Expected: PASS.

- [ ] **Step 7: Run the full server suite + commit**

```bash
cd server && python -m pytest -v && cd .. && \
git add server/ && git commit -m "feat(server): neo-brutalist landing page"
```

---

## Phase B — Android app (Kotlin)

> Android tasks verify by Gradle build + grep assertions + MobSF, not pytest. The build uses the wrapper and `~/Android/Sdk`.

### Task 8: Gradle project scaffold + vulnerable manifest

**Files:**
- Create: `app/settings.gradle`, `app/build.gradle` (root), `app/gradle.properties`, `app/local.properties`
- Create: `app/app/build.gradle`
- Create: `app/app/src/main/AndroidManifest.xml`
- Create: `app/gradlew` + wrapper (via `gradle wrapper`)

**Interfaces:**
- Produces: a buildable project whose manifest declares `debuggable`, `allowBackup`, `usesCleartextTraffic`, and an exported activity.

- [ ] **Step 1: Write `app/local.properties`** (git-ignored; points at the SDK)

```
sdk.dir=/home/cat/Android/Sdk
```

- [ ] **Step 2: Write `app/settings.gradle`**

```groovy
pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories { google(); mavenCentral() } }
rootProject.name = "MnitVulnApp"
include ":app"
```

- [ ] **Step 3: Write root `app/build.gradle`**

```groovy
plugins {
    id 'com.android.application' version '8.5.2' apply false
    id 'org.jetbrains.kotlin.android' version '1.9.24' apply false
}
```

- [ ] **Step 4: Write `app/gradle.properties`**

```
org.gradle.jvmargs=-Xmx2048m
android.useAndroidX=true
kotlin.code.style=official
```

- [ ] **Step 5: Write `app/app/build.gradle`**

```groovy
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
}
android {
    namespace 'in.danwand.mnit.vulnapp'
    compileSdk 34
    defaultConfig {
        applicationId 'in.danwand.mnit.vulnapp'
        minSdk 24
        targetSdk 34
        versionCode 1
        versionName '1.0'
    }
    buildTypes {
        release { minifyEnabled false }   // intentional: no obfuscation
        debug   { minifyEnabled false }
    }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = '17' }
}
dependencies {
    implementation 'androidx.appcompat:appcompat:1.7.0'
    implementation 'androidx.constraintlayout:constraintlayout:2.1.4'
    implementation 'com.google.android.material:material:1.12.0'
}
```

- [ ] **Step 6: Write `app/app/src/main/AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET"/>
    <application
        android:allowBackup="true"
        android:debuggable="true"
        android:usesCleartextTraffic="true"
        android:networkSecurityConfig="@xml/network_security_config"
        android:label="MNIT Vuln App"
        android:theme="@style/Theme.MnitVuln">
        <activity android:name=".AuthActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN"/>
                <category android:name="android.intent.category.LAUNCHER"/>
            </intent-filter>
        </activity>
        <activity android:name=".UsersActivity" android:exported="false"/>
        <activity android:name=".MyTeaActivity" android:exported="true"/>  <!-- intentional: exported, no guard -->
        <activity android:name=".SendTeaActivity" android:exported="false"/>
    </application>
</manifest>
```

- [ ] **Step 7: Generate the Gradle wrapper**

Run: `cd app && gradle wrapper --gradle-version 8.9`
Expected: creates `gradlew`, `gradle/wrapper/*`.

- [ ] **Step 8: Verify the manifest carries the intended flags**

Run:
```bash
cd app && grep -q 'android:debuggable="true"' app/src/main/AndroidManifest.xml \
  && grep -q 'usesCleartextTraffic="true"' app/src/main/AndroidManifest.xml \
  && grep -q 'allowBackup="true"' app/src/main/AndroidManifest.xml \
  && echo "MANIFEST OK"
```
Expected: `MANIFEST OK`.

- [ ] **Step 9: Commit**

```bash
git add app/ && git commit -m "feat(app): gradle scaffold and deliberately insecure manifest"
```

### Task 9: Resources — network config, strings (hardcoded token), theme, layouts

**Files:**
- Create: `app/app/src/main/res/xml/network_security_config.xml`
- Create: `app/app/src/main/res/values/strings.xml`
- Create: `app/app/src/main/res/values/colors.xml`
- Create: `app/app/src/main/res/values/themes.xml`
- Create: layouts: `activity_auth.xml`, `activity_users.xml`, `activity_my_tea.xml`, `activity_send_tea.xml`, `row_item.xml` under `app/app/src/main/res/layout/`

**Interfaces:**
- Produces: `@xml/network_security_config` trusting user CAs with no pins; `@string/*` including the hardcoded token; a neo-brutalist theme/colors; the four screen layouts, each with a heading `TextView` showing `for MNIT Use - Vuln App`.

- [ ] **Step 1: Write `network_security_config.xml`** (trusts user CAs, no pins — intentional)

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="true">
        <trust-anchors>
            <certificates src="system"/>
            <certificates src="user"/>
        </trust-anchors>
    </base-config>
</network-security-config>
```

- [ ] **Step 2: Write `strings.xml`** (hardcoded token — intentional finding)

```xml
<resources>
    <string name="app_name">MNIT Vuln App</string>
    <string name="heading">for MNIT Use - Vuln App</string>
    <string name="base_url">https://android.danwand.in</string>
    <string name="api_token">mnit_app_2026_bearer_9f3c1a</string>
</resources>
```

- [ ] **Step 3: Write `colors.xml`**

```xml
<resources>
    <color name="ink">#0B0B0C</color>
    <color name="paper">#EDE7DC</color>
    <color name="oxblood">#8A1C1C</color>
    <color name="crimson">#C0271E</color>
    <color name="blush">#E9D6D1</color>
    <color name="electric">#2A2DF0</color>
</resources>
```

- [ ] **Step 4: Write `themes.xml`**

```xml
<resources>
    <style name="Theme.MnitVuln" parent="Theme.MaterialComponents.Light.NoActionBar">
        <item name="android:colorBackground">@color/paper</item>
        <item name="colorPrimary">@color/ink</item>
        <item name="colorAccent">@color/oxblood</item>
    </style>
</resources>
```

- [ ] **Step 5: Write a reusable heading + the four layouts**

Each layout is a vertical `LinearLayout` whose first child is the heading banner. Example `activity_auth.xml` (the others follow the same heading pattern with their own body widgets: `activity_users.xml` → a `ListView id=list`; `activity_my_tea.xml` → a `ListView id=list`; `activity_send_tea.xml` → `EditText id=recipient`, `EditText id=body`, `Button id=send`):

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:orientation="vertical" android:padding="20dp"
    android:background="@color/paper"
    android:layout_width="match_parent" android:layout_height="match_parent">
    <TextView android:text="@string/heading"
        android:background="@color/ink" android:textColor="@color/paper"
        android:textStyle="bold" android:textSize="22sp" android:padding="16dp"
        android:letterSpacing="0.1"
        android:layout_width="match_parent" android:layout_height="wrap_content"/>
    <EditText android:id="@+id/username" android:hint="username"
        android:layout_marginTop="20dp"
        android:layout_width="match_parent" android:layout_height="wrap_content"/>
    <EditText android:id="@+id/password" android:hint="password"
        android:inputType="textPassword"
        android:layout_width="match_parent" android:layout_height="wrap_content"/>
    <Button android:id="@+id/register" android:text="Register"
        android:backgroundTint="@color/oxblood"
        android:layout_width="match_parent" android:layout_height="wrap_content"/>
    <Button android:id="@+id/login" android:text="Login"
        android:backgroundTint="@color/ink" android:layout_marginTop="8dp"
        android:layout_width="match_parent" android:layout_height="wrap_content"/>
</LinearLayout>
```

Write `row_item.xml` as a single padded `TextView id=row_text` for list rows. Write `activity_users.xml`, `activity_my_tea.xml`, `activity_send_tea.xml` with the same heading `TextView` first child and the body widgets named above.

- [ ] **Step 6: Verify token + heading present in resources**

Run:
```bash
cd app && grep -q 'mnit_app_2026_bearer_9f3c1a' app/src/main/res/values/strings.xml \
  && grep -rq 'for MNIT Use - Vuln App' app/src/main/res/values/strings.xml \
  && echo "RES OK"
```
Expected: `RES OK`.

- [ ] **Step 7: Commit**

```bash
git add app/ && git commit -m "feat(app): neo-brutalist resources, network config, hardcoded token string"
```

### Task 10: API client (Bearer + X-User-Id, insecure logging, MD5)

**Files:**
- Create: `app/app/src/main/java/in/danwand/mnit/vulnapp/Api.kt`
- Create: `app/app/src/main/java/in/danwand/mnit/vulnapp/Store.kt`

**Interfaces:**
- Produces:
  - `Store(context)` — plaintext SharedPreferences: `saveCreds(username, password, userId)`, `userId: Int`, `username: String`.
  - `Api` — `register(u,p): Int`, `login(u,p): Int`, `users(): JSONArray`, `tea(userId): JSONArray`, `sendTea(fromId,toId,body)`. All attach `Authorization: Bearer <token>` and, where relevant, `X-User-Id`. Runs on a background thread; callers use a simple callback. Logs request/response including token via `Log.d` (intentional).

- [ ] **Step 1: Write `Store.kt`** (plaintext credential storage — intentional)

```kotlin
package in.danwand.mnit.vulnapp

import android.content.Context
import android.util.Log

class Store(ctx: Context) {
    private val p = ctx.getSharedPreferences("mnit_prefs", Context.MODE_PRIVATE)
    fun saveCreds(username: String, password: String, userId: Int) {
        p.edit().putString("username", username)
            .putString("password", password)      // plaintext, intentional
            .putInt("user_id", userId).apply()
        Log.d("MNIT", "stored creds $username / $password id=$userId")  // insecure log
    }
    val userId: Int get() = p.getInt("user_id", -1)
    val username: String get() = p.getString("username", "") ?: ""
}
```

- [ ] **Step 2: Write `Api.kt`**

```kotlin
package in.danwand.mnit.vulnapp

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

class Api(ctx: Context) {
    private val base = ctx.getString(R.string.base_url)
    private val token = ctx.getString(R.string.api_token)   // hardcoded Bearer
    private val pool = Executors.newSingleThreadExecutor()

    // intentional weak crypto, used trivially
    private fun weakId(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun open(path: String, method: String, userId: Int?): HttpURLConnection {
        val c = URL("$base$path").openConnection() as HttpURLConnection
        c.requestMethod = method
        c.setRequestProperty("Authorization", "Bearer $token")
        c.setRequestProperty("Content-Type", "application/json")
        if (userId != null) c.setRequestProperty("X-User-Id", userId.toString())
        Log.d("MNIT", "$method $path token=$token xuid=$userId")  // insecure log
        return c
    }

    private fun body(c: HttpURLConnection, json: JSONObject?) {
        if (json != null) {
            c.doOutput = true
            c.outputStream.use { it.write(json.toString().toByteArray()) }
        }
    }

    private fun readText(c: HttpURLConnection): String {
        val stream = if (c.responseCode in 200..299) c.inputStream else c.errorStream
        return stream.bufferedReader().use { it.readText() }
    }

    fun register(u: String, p: String, cb: (Int?, String) -> Unit) = pool.execute {
        try {
            val c = open("/api/register", "POST", null)
            body(c, JSONObject().put("username", u).put("password", p))
            val txt = readText(c)
            Log.d("MNIT", "register ${weakId(u)} -> $txt")
            val id = if (c.responseCode == 201) JSONObject(txt).getInt("user_id") else null
            cb(id, txt)
        } catch (e: Exception) { cb(null, e.message ?: "error") }
    }

    fun login(u: String, p: String, cb: (Int?, String) -> Unit) = pool.execute {
        try {
            val c = open("/api/login", "POST", null)
            body(c, JSONObject().put("username", u).put("password", p))
            val txt = readText(c)
            val id = if (c.responseCode == 200) JSONObject(txt).getInt("user_id") else null
            cb(id, txt)
        } catch (e: Exception) { cb(null, e.message ?: "error") }
    }

    fun users(cb: (JSONArray?) -> Unit) = pool.execute {
        try {
            val c = open("/api/users", "GET", null)
            cb(JSONObject(readText(c)).getJSONArray("users"))
        } catch (e: Exception) { cb(null) }
    }

    fun tea(userId: Int, cb: (JSONArray?) -> Unit) = pool.execute {
        try {
            val c = open("/api/tea", "GET", userId)
            cb(JSONObject(readText(c)).getJSONArray("tea"))
        } catch (e: Exception) { cb(null) }
    }

    fun sendTea(fromId: Int, toId: Int, text: String, cb: (Boolean) -> Unit) = pool.execute {
        try {
            val c = open("/api/tea", "POST", fromId)
            body(c, JSONObject().put("to_user_id", toId).put("body", text))
            cb(c.responseCode == 201)
        } catch (e: Exception) { cb(false) }
    }
}
```

- [ ] **Step 3: Verify it compiles (part of the Task 12 build)** — no standalone test. Proceed.

- [ ] **Step 4: Commit**

```bash
git add app/ && git commit -m "feat(app): API client with Bearer token, X-User-Id, insecure logging, MD5"
```

### Task 11: Activities (the four screens)

**Files:**
- Create under `app/app/src/main/java/in/danwand/mnit/vulnapp/`: `AuthActivity.kt`, `UsersActivity.kt`, `MyTeaActivity.kt`, `SendTeaActivity.kt`

**Interfaces:**
- Consumes: `Api`, `Store`, the layouts and ids from Task 9.
- Produces: launcher `AuthActivity` → on login/register success store id + go to `UsersActivity`; `UsersActivity` lists users and opens `SendTeaActivity` (passing recipient id) and has a button to `MyTeaActivity`; `MyTeaActivity` shows `tea(store.userId)`; `SendTeaActivity` posts tea.

- [ ] **Step 1: Write `AuthActivity.kt`**

```kotlin
package in.danwand.mnit.vulnapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class AuthActivity : AppCompatActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_auth)
        val api = Api(this); val store = Store(this)
        val u = findViewById<EditText>(R.id.username)
        val p = findViewById<EditText>(R.id.password)
        fun done(id: Int?, msg: String) = runOnUiThread {
            if (id != null) {
                store.saveCreds(u.text.toString(), p.text.toString(), id)
                startActivity(Intent(this, UsersActivity::class.java)); finish()
            } else Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        }
        findViewById<Button>(R.id.register).setOnClickListener {
            api.register(u.text.toString(), p.text.toString(), ::done)
        }
        findViewById<Button>(R.id.login).setOnClickListener {
            api.login(u.text.toString(), p.text.toString(), ::done)
        }
    }
}
```

- [ ] **Step 2: Write `UsersActivity.kt`**

```kotlin
package in.danwand.mnit.vulnapp

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity

class UsersActivity : AppCompatActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_users)
        val api = Api(this)
        val list = findViewById<ListView>(R.id.list)
        startActivity(Intent(this, MyTeaActivity::class.java))  // show my tea alongside
        api.users { arr ->
            if (arr == null) return@users
            val ids = IntArray(arr.length()); val labels = ArrayList<String>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                ids[i] = o.getInt("id"); labels.add("${o.getInt("id")}  ${o.getString("username")}")
            }
            runOnUiThread {
                list.adapter = ArrayAdapter(this, R.layout.row_item, R.id.row_text, labels)
                list.setOnItemClickListener { _, _, pos, _ ->
                    startActivity(Intent(this, SendTeaActivity::class.java)
                        .putExtra("to", ids[pos]))
                }
            }
        }
    }
}
```

Note: the `startActivity(MyTeaActivity)` line in Step 2 is a convenience jump; if it causes a loop during manual testing, remove it and add a button instead. Keep for now — harmless on first launch.

- [ ] **Step 3: Write `MyTeaActivity.kt`**

```kotlin
package in.danwand.mnit.vulnapp

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity

class MyTeaActivity : AppCompatActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_my_tea)
        val store = Store(this)
        val list = findViewById<ListView>(R.id.list)
        Api(this).tea(store.userId) { arr ->
            val rows = ArrayList<String>()
            if (arr != null) for (i in 0 until arr.length())
                rows.add(arr.getJSONObject(i).getString("body"))
            runOnUiThread {
                list.adapter = ArrayAdapter(this, R.layout.row_item, R.id.row_text, rows)
            }
        }
    }
}
```

- [ ] **Step 4: Write `SendTeaActivity.kt`**

```kotlin
package in.danwand.mnit.vulnapp

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SendTeaActivity : AppCompatActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_send_tea)
        val store = Store(this); val api = Api(this)
        val recipient = findViewById<EditText>(R.id.recipient)
        val body = findViewById<EditText>(R.id.body)
        intent.getIntExtra("to", -1).let { if (it > 0) recipient.setText(it.toString()) }
        findViewById<Button>(R.id.send).setOnClickListener {
            val to = recipient.text.toString().toIntOrNull() ?: return@setOnClickListener
            api.sendTea(store.userId, to, body.text.toString()) { ok ->
                runOnUiThread {
                    Toast.makeText(this, if (ok) "sent" else "failed", Toast.LENGTH_SHORT).show()
                    if (ok) finish()
                }
            }
        }
    }
}
```

- [ ] **Step 5: Commit**

```bash
git add app/ && git commit -m "feat(app): four activities (auth, users, my tea, send tea)"
```

### Task 12: Build the APK

**Files:** none new.

- [ ] **Step 1: Accept SDK licenses if needed**

Run: `yes | ~/Android/Sdk/cmdline-tools/*/bin/sdkmanager --licenses >/dev/null 2>&1 || true`

- [ ] **Step 2: Build debug APK**

Run: `cd app && ./gradlew assembleDebug --no-daemon`
Expected: `BUILD SUCCESSFUL`; APK at `app/app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 3: Verify the APK exists and carries the token**

Run:
```bash
cd app && test -f app/build/outputs/apk/debug/app-debug.apk && \
  ~/Android/Sdk/build-tools/*/aapt dump --values strings app/build/outputs/apk/debug/app-debug.apk 2>/dev/null \
  | grep -q 'mnit_app_2026_bearer_9f3c1a' && echo "APK OK (token embedded)"
```
Expected: `APK OK (token embedded)`.

- [ ] **Step 4: Commit the build note** (APK itself is git-ignored via `**/build/`)

```bash
git commit --allow-empty -m "build(app): debug APK builds and embeds the token"
```

### Task 13: MobSF scan verification

**Files:**
- Create: `docs/mobsf-expected-findings.md`

- [ ] **Step 1: Start dockerized MobSF**

Run: `docker run -d --rm -p 8000:8000 --name mobsf opensecurity/mobile-security-framework-mobsf:latest`
Wait ~40s for startup.

- [ ] **Step 2: Upload the APK and run the scan via the REST API**

Run (MobSF prints its API key in `docker logs mobsf`):
```bash
KEY=$(docker logs mobsf 2>&1 | grep -oE 'API Key: [a-f0-9]+' | tail -1 | awk '{print $3}')
APK=app/app/build/outputs/apk/debug/app-debug.apk
H=$(curl -s -F "file=@$APK" http://localhost:8000/api/v1/upload -H "Authorization:$KEY")
HASH=$(echo "$H" | python3 -c 'import sys,json;print(json.load(sys.stdin)["hash"])')
curl -s -X POST --data "hash=$HASH" http://localhost:8000/api/v1/scan -H "Authorization:$KEY" >/dev/null
curl -s -X POST --data "hash=$HASH" http://localhost:8000/api/v1/report_json -H "Authorization:$KEY" > /tmp/mobsf.json
echo "report bytes: $(wc -c < /tmp/mobsf.json)"
```

- [ ] **Step 2b: Assert the expected findings appear**

Run:
```bash
python3 - <<'PY'
import json
r = json.load(open("/tmp/mobsf.json"))
blob = json.dumps(r).lower()
for needle in ["debug", "cleartext", "backup", "exported"]:
    assert needle in blob, f"missing {needle}"
print("MOBSF findings present: debuggable, cleartext, backup, exported")
PY
```
Expected: the print line (adjust needles to the report's wording if MobSF phrases differ; the goal is confirming the flags surfaced).

- [ ] **Step 3: Write `docs/mobsf-expected-findings.md`** listing the intended findings (hardcoded token, cleartext traffic, trust user CAs/no pinning, debuggable, allowBackup, exported activity, plaintext prefs, insecure logging, no obfuscation, MD5) so the session facilitator can map MobSF output to lessons.

- [ ] **Step 4: Stop MobSF + commit**

```bash
docker stop mobsf; git add docs/mobsf-expected-findings.md && \
git commit -m "docs: MobSF expected findings for the session"
```

---

## Phase C — Deploy (VPS, isolated)

> Uses live SSH to `root@31.42.125.200`. The password is supplied at run time, never written to the repo. `sshpass` or an interactive prompt carries it.

### Task 14: Deploy artifacts

**Files:**
- Create: `deploy/mnit-vulnapp.service`
- Create: `deploy/nginx-mnit-vulnapp.conf`
- Create: `deploy/teardown.cron`
- Create: `deploy/deploy.sh`
- Create: `deploy/README.md`

**Interfaces:**
- Produces: a systemd unit running gunicorn on 127.0.0.1:8099; an nginx server block for `android.danwand.in` proxying to it; a cron line stopping the service at session end; a `deploy.sh` that uploads `server/`, installs deps in a venv, and enables the unit — all without touching the existing app.

- [ ] **Step 1: Write `deploy/mnit-vulnapp.service`**

```ini
[Unit]
Description=MNIT Vuln App (training target)
After=network.target

[Service]
Type=simple
WorkingDirectory=/opt/mnit-vulnapp/server
ExecStart=/opt/mnit-vulnapp/venv/bin/gunicorn -w 2 -b 127.0.0.1:8099 app:app
Restart=on-failure
User=root

[Install]
WantedBy=multi-user.target
```

- [ ] **Step 2: Write `deploy/nginx-mnit-vulnapp.conf`** (its own server block; port 8099)

```nginx
server {
    listen 80;
    server_name android.danwand.in;
    location / {
        proxy_pass http://127.0.0.1:8099;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

- [ ] **Step 3: Write `deploy/teardown.cron`** (placeholder time; `deploy.sh` rewrites it to server-local 23:00 on 2026-10-14)

```cron
# m h dom mon dow command  -- stop ONLY the vuln service at session end
0 23 14 10 * systemctl stop mnit-vulnapp.service && systemctl disable mnit-vulnapp.service
```

- [ ] **Step 4: Write `deploy/deploy.sh`**

```bash
#!/usr/bin/env bash
set -euo pipefail
HOST="${VPS_HOST:-31.42.125.200}"
SSH="ssh -o StrictHostKeyChecking=accept-new root@$HOST"
# password comes from env VPS_PASS via sshpass, or interactive ssh. Never hardcode.
run() { if [ -n "${VPS_PASS:-}" ]; then sshpass -e $SSH "$@"; else $SSH "$@"; fi; }

echo "[*] sanity: do not disturb existing services"
run "systemctl is-active nginx || true; ss -ltnp | grep -E ':(80|443|8099) ' || true"

echo "[*] upload server/"
if [ -n "${VPS_PASS:-}" ]; then
  sshpass -e rsync -az --delete ../server/ root@"$HOST":/opt/mnit-vulnapp/server/
else
  rsync -az --delete ../server/ root@"$HOST":/opt/mnit-vulnapp/server/
fi

echo "[*] venv + deps + unit + nginx"
run bash -s <<'REMOTE'
set -euo pipefail
apt-get update -qq && apt-get install -y -qq python3-venv nginx certbot python3-certbot-nginx rsync >/dev/null
python3 -m venv /opt/mnit-vulnapp/venv
/opt/mnit-vulnapp/venv/bin/pip install -q -r /opt/mnit-vulnapp/server/requirements.txt
cd /opt/mnit-vulnapp/server && /opt/mnit-vulnapp/venv/bin/python -c "import db; db.init_db('/opt/mnit-vulnapp/server/tea.db'); db.seed('/opt/mnit-vulnapp/server/tea.db')"
cp /opt/mnit-vulnapp/server/../deploy/mnit-vulnapp.service /etc/systemd/system/ 2>/dev/null || true
REMOTE
echo "[*] see deploy/README.md for the remaining unit/nginx/cert/cron commands (kept explicit, run with eyes open)"
```

Note: `deploy.sh` intentionally stops before enabling services; Task 15 runs the remaining steps interactively so a human watches the box. Rationale: the box has a live app we must not disturb.

- [ ] **Step 5: Write `deploy/README.md`** with the exact remaining commands (copy the service file, `systemctl daemon-reload && systemctl enable --now mnit-vulnapp`, place nginx conf in `/etc/nginx/sites-available` + symlink, `nginx -t && systemctl reload nginx`, `certbot --nginx -d android.danwand.in`, install the teardown cron adjusted to server TZ).

- [ ] **Step 6: Commit**

```bash
git add deploy/ && git commit -m "feat(deploy): isolated systemd+nginx artifacts, teardown cron, deploy script"
```

### Task 15: Live deploy + verification (human-in-the-loop)

**Files:** none new. This task runs against the live VPS; confirm each step's output before the next.

- [ ] **Step 1: Pre-flight — inventory the box WITHOUT changing it**

SSH in; record: `systemctl list-units --type=service --state=running`, `ss -ltnp`, `ls /etc/nginx/sites-enabled`, `timedatectl` (for TZ), and whether port 8099 is free. Confirm the existing app and its port. If 8099 is taken, pick another and update the unit + nginx conf.

- [ ] **Step 2: Run `deploy.sh`** to upload code + build the venv (does not enable services).

Run: `cd deploy && VPS_HOST=31.42.125.200 ./deploy.sh` (supply the password via a secure prompt / `VPS_PASS` env you set in the shell, not in any file).

- [ ] **Step 3: Enable the service**

On the box: `cp /opt/mnit-vulnapp/deploy/mnit-vulnapp.service /etc/systemd/system/ && systemctl daemon-reload && systemctl enable --now mnit-vulnapp && systemctl status mnit-vulnapp --no-pager`
Expected: active (running); `curl -s localhost:8099/health` → `{"status":"ok"}`.

- [ ] **Step 4: Add the nginx server block (new file only — do not edit existing ones)**

`cp deploy/nginx-mnit-vulnapp.conf /etc/nginx/sites-available/mnit-vulnapp && ln -sf /etc/nginx/sites-available/mnit-vulnapp /etc/nginx/sites-enabled/ && nginx -t && systemctl reload nginx`
Expected: `nginx -t` OK; existing sites still listed in `sites-enabled`.

- [ ] **Step 5: TLS**

`certbot --nginx -d android.danwand.in --non-interactive --agree-tos -m danwand47@gmail.com --redirect`
Expected: cert issued; nginx reloaded. Confirm other vhosts untouched (`certbot certificates`, `ls /etc/nginx/sites-enabled`).

- [ ] **Step 6: Install the teardown cron at server-local 23:00 on 2026-10-14**

Compute the cron minute/hour from `timedatectl` output so it fires at 23:00 local. Append (do not overwrite root's crontab):
`( crontab -l 2>/dev/null; echo '0 23 14 10 * systemctl stop mnit-vulnapp.service && systemctl disable mnit-vulnapp.service' ) | crontab -`
Verify with `crontab -l`.

- [ ] **Step 7: End-to-end exploit verification against the live domain**

Run from the dev box:
```bash
TOKEN=mnit_app_2026_bearer_9f3c1a
curl -s https://android.danwand.in/api/users -H "Authorization: Bearer $TOKEN" | head
curl -s https://android.danwand.in/api/tea -H "Authorization: Bearer $TOKEN" -H "X-User-Id: 1"
# expect the flag in the response
curl -s -o /dev/null -w "%{http_code}\n" https://android.danwand.in/api/users   # expect 401 (no token)
```
Expected: users list; admin tea containing the flag; `401` without the token.

- [ ] **Step 8: Confirm the existing app still works** (hit its URL/port as recorded in Step 1). Report final status: live URL, teardown time, existing app healthy.

---

## Self-Review

**Spec coverage:** Static vulns → Tasks 8–10, 13. Hardcoded token gate → Tasks 3, 9, 10. X-User-Id IDOR → Task 6. Tea model + admin flag → Tasks 2, 6. Four screens + heading → Tasks 9, 11. Web landing + heading → Task 7. Neo-brutalist palette → Tasks 7, 9. Deploy isolation → Tasks 14, 15. Teardown cron → Tasks 14, 15. DNS verify-not-change → Task 15 Step 7. Existing app untouched → Tasks 14–15 (pre-flight + new-file-only). MobSF verification → Task 13. Secrets never in repo → `.gitignore`, deploy via env/prompt. All covered.

**Placeholders:** none — every code step carries real content; deploy README commands are enumerated in Task 15 steps.

**Type consistency:** `caller_id()`, `require_token`, `Store`/`Api` method names, header names (`X-User-Id`), token value, and port `8099` are consistent across tasks and match the Global Constraints.

**Review Focus:** the five listed failure modes each have a test — 401 no-token (Task 3), 400 bad `X-User-Id` (Task 6), 409 dup register (Task 4), empty tea 200 (Task 6), 400 bad recipient (Task 6).
