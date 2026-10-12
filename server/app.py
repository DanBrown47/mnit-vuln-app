from flask import Flask, jsonify
import config


def create_app(db_path=None):
    app = Flask(__name__)
    app.config["DB_PATH"] = db_path or config.DEFAULT_DB_PATH

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
        return jsonify(users=[])

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

    return app


app = create_app()

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=8099)
