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
