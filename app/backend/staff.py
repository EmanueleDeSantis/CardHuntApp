from flask import Blueprint, request, jsonify, g
import db, json
from auth import admin_required, staff_required

staff_bp = Blueprint("staff", __name__, url_prefix="/api/staff")


@staff_bp.get("/users")
@staff_required
def list_users():
    conn = db.get_conn()
    try:
        if g.role == "MODERATOR":
            # Moderators cannot see admins
            sql = """
                SELECT u.id, u.username, u.email, u.role, u.status, u.xp,
                       (SELECT COUNT(*) FROM user_cards uc WHERE uc.user_id = u.id) AS cards_count
                FROM users u WHERE u.role <> 'ADMIN' ORDER BY u.id
            """
        else:
            sql = """
                SELECT u.id, u.username, u.email, u.role, u.status, u.xp,
                       (SELECT COUNT(*) FROM user_cards uc WHERE uc.user_id = u.id) AS cards_count
                FROM users u ORDER BY u.id
            """
        rows = db.query(conn, sql)
        return jsonify([dict(r) for r in rows])
    finally:
        conn.close()


@staff_bp.patch("/users/<int:user_id>/status")
@staff_required
def update_status(user_id):
    d = request.get_json(force=True, silent=True) or {}
    status = d.get("status")
    if status not in ("ACTIVE", "SUSPENDED", "BANNED"):
        return jsonify(error="Invalid status"), 400
    conn = db.get_conn()
    try:
        target = db.query(conn, "SELECT role FROM users WHERE id=?", (user_id,), one=True)
        if not target:
            return jsonify(error="User not found"), 404
        if user_id == g.user_id:
            return jsonify(error="You cannot change your own status"), 400
        if target["role"] in ("ADMIN", "MODERATOR") and g.role != "ADMIN":
            return jsonify(error="Only admins can moderate staff members"), 403
        db.execute(conn, "UPDATE users SET status=? WHERE id=?", (status, user_id))
        conn.commit()
        return jsonify(ok=True)
    finally:
        conn.close()


@staff_bp.patch("/users/<int:user_id>/role")
@admin_required
def update_role(user_id):
    d = request.get_json(force=True, silent=True) or {}
    role = d.get("role")
    if role not in ("MODERATOR", "USER"):  # ADMIN removed - no new admins
        return jsonify(error="Invalid role"), 400
    if user_id == g.user_id:
        return jsonify(error="You cannot change your own role"), 400
    conn = db.get_conn()
    try:
        db.execute(conn, "UPDATE users SET role=? WHERE id=?", (role, user_id))
        conn.commit()
        return jsonify(ok=True)
    finally:
        conn.close()


@staff_bp.get("/cards")
@staff_required
def list_all_cards():
    conn = db.get_conn()
    try:
        rows = db.query(conn, """
            SELECT c.*,
                   (SELECT COUNT(*) FROM user_cards uc
                     WHERE uc.card_id = c.id) AS collections,
                   (SELECT COUNT(*) FROM user_cards uc
                     WHERE uc.card_id = c.id AND uc.acquisition = 'PHOTO') AS photo_collections
            FROM cards c ORDER BY c.created_at DESC
        """)
        return jsonify([dict(r) for r in rows])
    finally:
        conn.close()


@staff_bp.patch("/cards/<int:card_id>")
@staff_required
def update_card(card_id):
    d = request.get_json(force=True, silent=True) or {}
    conn = db.get_conn()
    try:
        db.execute(conn, """
            UPDATE cards SET name=?, description=?, latitude=?, longitude=?,
            radius_meters=?, rarity=?, points=?, updated_at=CURRENT_TIMESTAMP WHERE id=?""",
            (d["name"], d.get("description"), d["latitude"], d["longitude"],
             d["radius_meters"], d["rarity"], d["points"], card_id))
        conn.commit()
        return jsonify(ok=True)
    finally:
        conn.close()


@staff_bp.delete("/cards/<int:card_id>")
@staff_required
def delete_card(card_id):
    conn = db.get_conn()
    try:
        card = db.query(conn, "SELECT * FROM cards WHERE id=%s", (card_id,), one=True)
        if not card:
            return jsonify(error="Card not found"), 404

        # Find all users who collected this card
        collectors = db.query(conn,
            "SELECT user_id FROM user_cards WHERE card_id = %s", (card_id,))

        # Revoke XP and notify each collector
        for c in collectors:
            db.execute(conn,
                "UPDATE users SET xp = MAX(xp - %s, 0) WHERE id = %s",
                (card["points"], c["user_id"]))
            db.execute(conn, """
                INSERT INTO notifications (user_id, type, message, payload)
                VALUES (%s, 'CARD_REMOVED', %s, %s)
            """, (c["user_id"],
                  f"The card \"{card['name']}\" was removed by staff. {card['points']} XP has been deducted.",
                  json.dumps({"card_id": card_id, "card_name": card["name"],
                              "xp_removed": card["points"]})))

        # Delete collection records and the card itself
        db.execute(conn, "DELETE FROM user_cards WHERE card_id = %s", (card_id,))
        db.execute(conn, "DELETE FROM cards WHERE id = %s", (card_id,))
        conn.commit()
        return jsonify(ok=True)
    finally:
        conn.close()