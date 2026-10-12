from flask import Flask, jsonify, render_template
import config


def create_app(db_path=None):
    app = Flask(__name__)
    app.config["DB_PATH"] = db_path or config.DEFAULT_DB_PATH

    @app.get("/")
    def index():
        return render_template("index.html", heading=config.HEADING)

    @app.get("/health")
    def health():
        return jsonify(status="ok")

    from auth import require_token
    import sqlite3
    from flask import request
    import db

    @app.get("/api/users")
    @require_token
    def api_users():
        rows = db.list_users(app.config["DB_PATH"])
        return jsonify(users=[{"id": r["id"], "username": r["username"]} for r in rows])

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

    return app


app = create_app()

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=8099)
