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
