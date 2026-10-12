from flask import Flask, jsonify
import config


def create_app(db_path=None):
    app = Flask(__name__)
    app.config["DB_PATH"] = db_path or config.DEFAULT_DB_PATH

    @app.get("/health")
    def health():
        return jsonify(status="ok")

    from auth import require_token

    @app.get("/api/users")
    @require_token
    def api_users():
        return jsonify(users=[])

    return app


app = create_app()

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=8099)
