from datetime import datetime, timedelta, timezone
from functools import wraps

import jwt
from flask import Blueprint, request, jsonify, g
from werkzeug.security import generate_password_hash, check_password_hash

import db
import config

auth_bp = Blueprint("auth", __name__, url_prefix="/api/auth")


def create_token(user_id, role):
    payload = {
        "user_id": user_id,
        "role": role,
        "exp": datetime.now(timezone.utc) + timedelta(hours=config.JWT_EXPIRES_HOURS),
    }
    return jwt.encode(payload, config.JWT_SECRET, algorithm="HS256")


def jwt_required(fn):
    @wraps(fn)
    def wrapper(*args, **kwargs):
        header = request.headers.get("Authorization", "")
        if not header.startswith("Bearer "):
            return jsonify(error="Missing token"), 401
        try:
            payload = jwt.decode(header[7:], config.JWT_SECRET, algorithms=["HS256"])
        except jwt.PyJWTError:
            return jsonify(error="Invalid or expired token"), 401
        g.user_id = payload["user_id"]
        g.role = payload["role"]
        return fn(*args, **kwargs)
    return wrapper


def admin_required(fn):
    @wraps(fn)
    def wrapper(*args, **kwargs):
        if g.role != "ADMIN":
            return jsonify(error="Admins only"), 403
        return fn(*args, **kwargs)
    return jwt_required(wrapper)


def staff_required(fn):
    @wraps(fn)
    def wrapper(*args, **kwargs):
        if g.role not in ("ADMIN", "MODERATOR"):
            return jsonify(error="Staff access required"), 403
        return fn(*args, **kwargs)
    return jwt_required(wrapper)


@auth_bp.post("/register")
def register():
    data = request.get_json(force=True, silent=True) or {}
    username = (data.get("username") or "").strip()
    email = (data.get("email") or "").strip().lower()
    password = data.get("password") or ""

    if len(username) < 3:
        return jsonify(error="Username must be at least 3 characters"), 400
    if "@" not in email or "." not in email:
        return jsonify(error="Invalid email address"), 400
    if len(password) < 6:
        return jsonify(error="Password must be at least 6 characters"), 400

    conn = db.get_conn()
    try:
        if db.query(conn, "SELECT 1 FROM users WHERE username=%s OR email=%s",
                    (username, email), one=True):
            return jsonify(error="Username or email already taken"), 409
        user_id = db.execute(conn,
            "INSERT INTO users (username, email, password_hash) VALUES (%s,%s,%s)",
            (username, email, generate_password_hash(password)))
        conn.commit()
        return jsonify(token=create_token(user_id, "USER"),
                       user={"id": user_id, "username": username, "role": "USER"}), 201
    finally:
        conn.close()


@auth_bp.post("/login")
def login():
    data = request.get_json(force=True, silent=True) or {}
    identifier = (data.get("username") or "").strip()
    password = data.get("password") or ""

    conn = db.get_conn()
    try:
        user = db.query(conn,
            "SELECT * FROM users WHERE username=%s OR email=%s",
            (identifier, identifier.lower()), one=True)
        if not user or not check_password_hash(user["password_hash"], password):
            return jsonify(error="Wrong credentials"), 401
        if user.get("status", "ACTIVE") != "ACTIVE":
            return jsonify(error=f"Your account is {user['status'].lower()}"), 403
        return jsonify(token=create_token(user["id"], user["role"]),
                       user={"id": user["id"], "username": user["username"],
                             "role": user["role"],
                             "status": user.get("status", "ACTIVE")})
    finally:
        conn.close()