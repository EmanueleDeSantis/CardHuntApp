import json
from datetime import datetime, timezone
from uuid import uuid4
from push_service import send_push_to_user
from flask import Blueprint, request, jsonify, g

import db
from db import iso
from auth import jwt_required
import achievements_service

social_bp = Blueprint("social", __name__, url_prefix="/api")


def _notify(conn, user_id, ntype, message, payload=None):
    db.execute(conn,
        "INSERT INTO notifications (user_id, type, message, payload) VALUES (%s,%s,%s,%s)",
        (user_id, ntype, message, json.dumps(payload) if payload else None))


# ---------------------------------------------------------------- profile
@social_bp.get("/me")
@jwt_required
def me():
    conn = db.get_conn()
    try:
        u = db.query(conn,
            "SELECT id, username, email, role, xp, avatar_url FROM users WHERE id=%s",
            (g.user_id,), one=True)
        if not u:
            return jsonify(error="User not found"), 404
        stats = db.query(conn, """
            SELECT COUNT(*) total,
                   SUM(acquisition='PHOTO') photo,
                   SUM(acquisition='SHARE') shared
            FROM user_cards WHERE user_id=%s""", (g.user_id,), one=True)
        friends_count = db.query(conn, """
            SELECT COUNT(*) c FROM friendships
            WHERE status='ACCEPTED' AND (requester_id=%s OR addressee_id=%s)""",
            (g.user_id, g.user_id), one=True)["c"]
        achs = db.query(conn, """
            SELECT a.code, a.name, a.description, a.icon, ua.unlocked_at
            FROM achievements a
            LEFT JOIN user_achievements ua
              ON ua.achievement_id = a.id AND ua.user_id = %s
            ORDER BY a.id""", (g.user_id,))
        return jsonify({
            "id": u["id"], "username": u["username"], "email": u["email"],
            "role": u["role"], "xp": u["xp"], "avatar_url": u["avatar_url"],
            "stats": {
                "total_cards": stats["total"] or 0,
                "photo_cards": int(stats["photo"] or 0),
                "shared_cards": int(stats["shared"] or 0),
                "friends_count": friends_count,
            },
            "achievements": [{
                "code": a["code"], "name": a["name"], "description": a["description"],
                "icon": a["icon"], "unlocked": a["unlocked_at"] is not None,
            } for a in achs],
        })
    finally:
        conn.close()


@social_bp.patch("/me/avatar")
@jwt_required
def update_avatar():
    d = request.get_json(force=True, silent=True) or {}
    avatar_url = d.get("avatar_url") or None
    conn = db.get_conn()
    try:
        db.execute(conn, "UPDATE users SET avatar_url=%s WHERE id=%s", (avatar_url, g.user_id))
        conn.commit()
        return jsonify(ok=True)
    finally:
        conn.close()


# ---------------------------------------------------------------- public players list
@social_bp.get("/users")
@jwt_required
def players():
    conn = db.get_conn()
    try:
        me = db.query(conn, "SELECT role FROM users WHERE id=%s", (g.user_id,), one=True)
        my_role = me["role"]

        # Visibility: who appears in the list
        if my_role == "USER":
            visible_roles = ("USER", "MODERATOR")
        elif my_role == "MODERATOR":
            visible_roles = ("USER", "MODERATOR", "ADMIN")
        else:  # ADMIN
            visible_roles = ("USER", "MODERATOR")

        placeholders = ",".join("?" for _ in visible_roles)
        params = [g.user_id, g.user_id, g.user_id, g.user_id] + list(visible_roles)

        rows = db.query(conn, f"""
            SELECT u.id, u.username, u.role, u.xp, u.avatar_url,
                   (SELECT COUNT(*) FROM user_cards uc WHERE uc.user_id = u.id) AS cards_count,
                   f.id AS request_id,
                   CASE
                       WHEN f.status = 'ACCEPTED' THEN 'FRIENDS'
                       WHEN f.status = 'PENDING' AND f.requester_id = ? THEN 'PENDING_OUT'
                       WHEN f.status = 'PENDING' THEN 'PENDING_IN'
                       ELSE 'NONE'
                   END AS friend_state
            FROM users u
            LEFT JOIN friendships f
              ON ((f.requester_id = ? AND f.addressee_id = u.id)
               OR (f.requester_id = u.id AND f.addressee_id = ?))
              AND f.status IN ('ACCEPTED','PENDING')
            WHERE u.id <> ?
              AND u.status = 'ACTIVE'
              AND u.role IN ({placeholders})
            ORDER BY u.username
        """, tuple(params))
        return jsonify([{
            "id": r["id"], "username": r["username"], "role": r["role"],
            "xp": r["xp"], "avatar_url": r["avatar_url"], "cards_count": r["cards_count"],
            "request_id": r["request_id"], "friend_state": r["friend_state"],
        } for r in rows])
    finally:
        conn.close()


# ---------------------------------------------------------------- friends
@social_bp.get("/friends")
@jwt_required
def friends():
    conn = db.get_conn()
    try:
        rows = db.query(conn, """
            SELECT u.id, u.username, u.avatar_url, u.xp,
                   (SELECT COUNT(*) FROM user_cards uc WHERE uc.user_id = u.id) cards_count,
                   f.id AS request_id, f.status, f.requester_id
            FROM friendships f
            JOIN users u ON u.id = CASE WHEN f.requester_id=%s
                                        THEN f.addressee_id ELSE f.requester_id END
            WHERE (f.requester_id=%s OR f.addressee_id=%s)
              AND f.status IN ('PENDING','ACCEPTED')
        """, (g.user_id, g.user_id, g.user_id))
        out_friends, incoming, outgoing = [], [], []
        for r in rows:
            person = {"id": r["id"], "username": r["username"],
                      "avatar_url": r["avatar_url"], "xp": r["xp"],
                      "cards_count": r["cards_count"]}
            if r["status"] == "ACCEPTED":
                out_friends.append(person)
            elif r["requester_id"] == g.user_id:
                outgoing.append(r["id"])
            else:
                incoming.append({"request_id": r["request_id"], "from": person})
        return jsonify(friends=out_friends, incoming=incoming, outgoing=outgoing)
    finally:
        conn.close()


@social_bp.get("/users/search")
@jwt_required
def search_users():
    q = (request.args.get("q") or "").strip()
    if len(q) < 2:
        return jsonify([])
    conn = db.get_conn()
    try:
        rows = db.query(conn,
            "SELECT id, username, avatar_url FROM users WHERE username LIKE %s AND id <> %s LIMIT 10",
            (f"{q}%", g.user_id))
        return jsonify(rows)
    finally:
        conn.close()


@social_bp.delete("/friends/<int:user_id>")
@jwt_required
def remove_friend(user_id):
    conn = db.get_conn()
    try:
        db.execute(conn, """
            DELETE FROM friendships
            WHERE status = 'ACCEPTED'
              AND ((requester_id = %s AND addressee_id = %s)
                OR (requester_id = %s AND addressee_id = %s))
        """, (g.user_id, user_id, user_id, g.user_id))
        conn.commit()
        return jsonify(ok=True)
    finally:
        conn.close()


@social_bp.post("/friends/<int:user_id>/request")
@jwt_required
def send_friend_request(user_id):
    if user_id == g.user_id:
        return jsonify(error="You cannot add yourself"), 400
    conn = db.get_conn()
    try:
        target = db.query(conn, "SELECT username, role FROM users WHERE id=? AND status='ACTIVE'",
                          (user_id,), one=True)
        if not target:
            return jsonify(error="User not found"), 404

        me = db.query(conn, "SELECT username, role FROM users WHERE id=?", (g.user_id,), one=True)
        my_role = me["role"]
        target_role = target["role"]

        # Friendship rules: who can ADD whom
        # Users can only add Users
        if my_role == "USER" and target_role != "USER":
            return jsonify(error="You can only add other players as friends"), 403

        # Moderators can add Moderators and Admins
        if my_role == "MODERATOR" and target_role not in ("MODERATOR", "ADMIN"):
            return jsonify(error="You can only add staff members as friends"), 403

        # Admins can only add Moderators
        if my_role == "ADMIN" and target_role != "MODERATOR":
            return jsonify(error="You can only add moderators as friends"), 403

        existing = db.query(conn, """
            SELECT 1 FROM friendships
            WHERE ((requester_id=? AND addressee_id=?)
                OR (requester_id=? AND addressee_id=?))
              AND status IN ('ACCEPTED','PENDING')""",
            (g.user_id, user_id, user_id, g.user_id), one=True)
        if existing:
            return jsonify(error="Friendship already exists or is pending"), 409

        db.execute(conn, """
            DELETE FROM friendships
            WHERE ((requester_id=? AND addressee_id=?)
                OR (requester_id=? AND addressee_id=?))
              AND status='DECLINED'""",
            (g.user_id, user_id, user_id, g.user_id))

        rid = db.execute(conn,
            "INSERT INTO friendships (requester_id, addressee_id, status) VALUES (?,?, 'PENDING')",
            (g.user_id, user_id))
        _notify(conn, user_id, "FRIEND_REQUEST",
                f"{me['username']} sent you a friend request",
                {"request_id": rid})
        conn.commit()
        send_push_to_user(conn, user_id, "Friend Request",
                f"{me['username']} wants to be your friend!",
                {"type": "FRIEND_REQUEST", "request_id": str(rid)})
        return jsonify(request_id=rid), 201
    finally:
        conn.close()


@social_bp.patch("/friends/requests/<int:request_id>/accept")
@jwt_required
def accept_friend_request(request_id):
    conn = db.get_conn()
    try:
        row = db.query(conn, """
            SELECT * FROM friendships
            WHERE id=%s AND addressee_id=%s AND status='PENDING'""",
            (request_id, g.user_id), one=True)
        if not row:
            return jsonify(error="Request not found"), 404
        db.execute(conn, "UPDATE friendships SET status='ACCEPTED' WHERE id=%s", (request_id,))
        me = db.query(conn, "SELECT username FROM users WHERE id=%s", (g.user_id,), one=True)

        # Delete the original FRIEND_REQUEST notification
        db.execute(conn, """
            DELETE FROM notifications
            WHERE user_id = %s AND type = 'FRIEND_REQUEST'
              AND payload LIKE %s
        """, (g.user_id, f'%"request_id": {request_id}%'))

        _notify(conn, row["requester_id"], "FRIEND_ACCEPTED",
                f"{me['username']} accepted your friend request")
        new_achievements = achievements_service.check(conn, g.user_id)
        conn.commit()
        # Send push notification
        send_push_to_user(conn, row["requester_id"], "Friend Request Accepted",
                f"{me['username']} accepted your friend request!",
                {"type": "FRIEND_ACCEPTED"})
        return jsonify(new_achievements=new_achievements)
    finally:
        conn.close()


@social_bp.patch("/friends/requests/<int:request_id>/decline")
@jwt_required
def decline_friend_request(request_id):
    conn = db.get_conn()
    try:
        row = db.query(conn, """
            SELECT * FROM friendships
            WHERE id=%s AND addressee_id=%s AND status='PENDING'""",
            (request_id, g.user_id), one=True)
        if not row:
            return jsonify(error="Request not found"), 404
        db.execute(conn, "UPDATE friendships SET status='DECLINED' WHERE id=%s", (request_id,))
        me = db.query(conn, "SELECT username FROM users WHERE id=%s", (g.user_id,), one=True)

        # Delete the original FRIEND_REQUEST notification
        db.execute(conn, """
            DELETE FROM notifications
            WHERE user_id = %s AND type = 'FRIEND_REQUEST'
              AND payload LIKE %s
        """, (g.user_id, f'%"request_id": {request_id}%'))

        _notify(conn, row["requester_id"], "FRIEND_REQUEST",
                f"{me['username']} declined your friend request")
        conn.commit()
        return jsonify(ok=True)
    finally:
        conn.close()


@social_bp.delete("/friends/requests/<int:request_id>/cancel")
@jwt_required
def cancel_friend_request(request_id):
    conn = db.get_conn()
    try:
        row = db.query(conn, """
            SELECT * FROM friendships
            WHERE id=%s AND requester_id=%s AND status='PENDING'""",
            (request_id, g.user_id), one=True)
        if not row:
            return jsonify(error="Request not found or not yours"), 404

        # Delete the request
        db.execute(conn, "DELETE FROM friendships WHERE id=%s", (request_id,))

        # Delete the notification sent to the addressee
        db.execute(conn, """
            DELETE FROM notifications
            WHERE user_id = %s AND type = 'FRIEND_REQUEST'
              AND payload LIKE %s
        """, (row["addressee_id"], f'%"request_id": {request_id}%'))

        conn.commit()
        return jsonify(ok=True)
    finally:
        conn.close()


# ---------------------------------------------------------------- cards
@social_bp.get("/cards/<int:card_id>/available-friends")
@jwt_required
def available_friends_for_card(card_id):
    conn = db.get_conn()
    try:
        rows = db.query(conn, """
            SELECT u.id, u.username, u.avatar_url
            FROM friendships f
            JOIN users u ON u.id = CASE WHEN f.requester_id=%s
                                        THEN f.addressee_id ELSE f.requester_id END
            WHERE (f.requester_id=%s OR f.addressee_id=%s)
              AND f.status = 'ACCEPTED'
              AND NOT EXISTS (
                  SELECT 1 FROM user_cards uc
                  WHERE uc.user_id = u.id AND uc.card_id = %s
              )
              AND NOT EXISTS (
                  SELECT 1 FROM card_shares cs
                  WHERE cs.sender_id = %s AND cs.receiver_id = u.id
                    AND cs.card_id = %s AND cs.is_redeemed = 0
              )
            ORDER BY u.username
        """, (g.user_id, g.user_id, g.user_id, card_id, g.user_id, card_id))
        return jsonify([dict(r) for r in rows])
    finally:
        conn.close()


# ---------------------------------------------------------------- shares
@social_bp.post("/shares")
@jwt_required
def create_share():
    d = request.get_json(force=True, silent=True) or {}
    try:
        card_id, friend_id = int(d["card_id"]), int(d["friend_id"])
    except (KeyError, TypeError, ValueError):
        return jsonify(error="card_id and friend_id are required"), 400

    conn = db.get_conn()
    try:
        # Check if sender owns the card
        if not db.query(conn, "SELECT 1 FROM user_cards WHERE user_id=%s AND card_id=%s",
                        (g.user_id, card_id), one=True):
            return jsonify(error="You do not own this card"), 403

        # Check if they are friends
        is_friend = db.query(conn, """
            SELECT 1 FROM friendships WHERE status='ACCEPTED'
              AND ((requester_id=%s AND addressee_id=%s)
                OR (requester_id=%s AND addressee_id=%s))""",
            (g.user_id, friend_id, friend_id, g.user_id), one=True)
        if not is_friend:
            return jsonify(error="You can only share with friends"), 403

        # Check if friend already has this card
        if db.query(conn, "SELECT 1 FROM user_cards WHERE user_id=%s AND card_id=%s",
                    (friend_id, card_id), one=True):
            return jsonify(error="Your friend already has this card"), 409

        # Check if there's already an unredeemed share for this card to this friend
        existing_share = db.query(conn, """
            SELECT 1 FROM card_shares
            WHERE sender_id=%s AND receiver_id=%s AND card_id=%s AND is_redeemed=0
        """, (g.user_id, friend_id, card_id), one=True)
        if existing_share:
            return jsonify(error="You already shared this card with them"), 409

        # Create the share
        token = str(uuid4())
        db.execute(conn, """
            INSERT INTO card_shares (sender_id, receiver_id, card_id, share_token)
            VALUES (%s,%s,%s,%s)""", (g.user_id, friend_id, card_id, token))

        me = db.query(conn, "SELECT username FROM users WHERE id=%s", (g.user_id,), one=True)
        card = db.query(conn, "SELECT name FROM cards WHERE id=%s", (card_id,), one=True)
        _notify(conn, friend_id, "SHARE_RECEIVED",
                f"{me['username']} shared the card '{card['name']}' with you",
                {"share_token": token, "card_id": card_id})
        conn.commit()
        # Send push notification
        send_push_to_user(conn, friend_id, "Card Shared",
                f"{me['username']} shared '{card['name']}' with you!",
                {"type": "SHARE_RECEIVED", "share_token": token})
        return jsonify(share_token=token), 201
    finally:
        conn.close()


@social_bp.post("/shares/<token>/redeem")
@jwt_required
def redeem_share(token):
    conn = db.get_conn()
    try:
        share = db.query(conn, "SELECT * FROM card_shares WHERE share_token=%s",
                         (token,), one=True)
        if not share:
            return jsonify(error="Share not found"), 404
        if share["is_redeemed"]:
            return jsonify(error="This share was already redeemed"), 409
        now_utc = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S")
        if share["expires_at"] and share["expires_at"] < now_utc:
            return jsonify(error="This share has expired"), 410
        if share["sender_id"] == g.user_id:
            return jsonify(error="You cannot redeem your own share"), 400
        if db.query(conn, "SELECT 1 FROM user_cards WHERE user_id=%s AND card_id=%s",
                    (g.user_id, share["card_id"]), one=True):
            return jsonify(error="You already own this card"), 409

        # Copy the original photo from the sender's collection
        sender_card = db.query(conn, """
            SELECT photo_url, cloudinary_public_id
            FROM user_cards WHERE user_id=%s AND card_id=%s
        """, (share["sender_id"], share["card_id"]), one=True)
        photo_url = sender_card["photo_url"] if sender_card else None
        public_id = sender_card["cloudinary_public_id"] if sender_card else None

        db.execute(conn, """
            INSERT INTO user_cards
                (user_id, card_id, acquisition, has_original_shoot,
                 photo_url, cloudinary_public_id, shared_from_user_id)
            VALUES (%s,%s,'SHARE',0,%s,%s,%s)""",
            (g.user_id, share["card_id"], photo_url, public_id, share["sender_id"]))
        db.execute(conn,
            "UPDATE card_shares SET is_redeemed=1, redeemed_at=%s WHERE id=%s",
            (now_utc, share["id"]))

        card = db.query(conn, "SELECT name, points FROM cards WHERE id=%s",
                        (share["card_id"],), one=True)
        # Shared cards do NOT award XP

        # Delete the old SHARE_RECEIVED notification (replaced by a plain confirmation)
        db.execute(conn, """
            DELETE FROM notifications
            WHERE user_id = %s AND type = 'SHARE_RECEIVED'
              AND payload LIKE %s
        """, (g.user_id, f'%"share_token": "{token}"%'))

        # Notify the sender
        _notify(conn, share["sender_id"], "SHARE_RECEIVED",
                f"Your share of '{card['name']}' was redeemed")

        # Plain confirmation for the receiver (no action button)
        sender_name = db.query(conn, "SELECT username FROM users WHERE id=%s",
                               (share["sender_id"],), one=True)["username"]
        _notify(conn, g.user_id, "CARD_REDEEMED",
                f"You collected '{card['name']}' from {sender_name}'s share",
                {"card_id": share["card_id"]})

        new_achievements = achievements_service.check(conn, g.user_id)
        conn.commit()
        return jsonify(card_id=share["card_id"], new_achievements=new_achievements)
    finally:
        conn.close()


# ---------------------------------------------------------------- notifications
@social_bp.get("/notifications")
@jwt_required
def notifications():
    conn = db.get_conn()
    try:
        rows = db.query(conn,
            "SELECT * FROM notifications WHERE user_id=%s ORDER BY id DESC LIMIT 50",
            (g.user_id,))
        out = []
        for r in rows:
            try:
                payload = json.loads(r["payload"]) if r["payload"] else None
            except (TypeError, ValueError):
                payload = None
            out.append({"id": r["id"], "type": r["type"], "message": r["message"],
                        "payload": payload, "is_read": bool(r["is_read"]),
                        "created_at": iso(r["created_at"])})
        return jsonify(out)
    finally:
        conn.close()


@social_bp.patch("/notifications/read")
@jwt_required
def mark_notifications_read():
    conn = db.get_conn()
    try:
        db.execute(conn, "UPDATE notifications SET is_read=1 WHERE user_id=%s", (g.user_id,))
        conn.commit()
        return jsonify(ok=True)
    finally:
        conn.close()


@social_bp.post("/notifications/card-nearby")
@jwt_required
def card_nearby():
    d = request.get_json(force=True, silent=True) or {}
    try:
        card_id = int(d["card_id"])
    except (KeyError, TypeError, ValueError):
        return jsonify(error="card_id required"), 400
    conn = db.get_conn()
    try:
        existing_notifications = db.query(conn, """
            SELECT payload FROM notifications
            WHERE user_id = %s AND type = 'CARD_NEARBY'
              AND created_at >= datetime('now', '-1 hour')
        """, (g.user_id,))

        for notif in existing_notifications:
            try:
                payload = json.loads(notif["payload"]) if isinstance(notif["payload"], str) else notif["payload"]
                if payload and payload.get("card_id") == card_id:
                    return jsonify(ok=True, skipped=True)
            except (json.JSONDecodeError, AttributeError):
                continue

        card = db.query(conn, "SELECT name, latitude, longitude FROM cards WHERE id=%s", (card_id,), one=True)
        if card:
            _notify(conn, g.user_id, "CARD_NEARBY",
                    f"{card['name']} is nearby — go collect it!",
                    {"card_id": card_id, "lat": card["latitude"], "lng": card["longitude"]})
            conn.commit()
        return jsonify(ok=True)
    finally:
        conn.close()


@social_bp.delete("/notifications/<int:notification_id>")
@jwt_required
def delete_notification(notification_id):
    conn = db.get_conn()
    try:
        db.execute(conn,
            "DELETE FROM notifications WHERE id = %s AND user_id = %s",
            (notification_id, g.user_id))
        conn.commit()
        return jsonify(ok=True)
    finally:
        conn.close()


@social_bp.delete("/notifications")
@jwt_required
def clear_all_notifications():
    conn = db.get_conn()
    try:
        db.execute(conn,
            "DELETE FROM notifications WHERE user_id = %s",
            (g.user_id,))
        conn.commit()
        return jsonify(ok=True)
    finally:
        conn.close()


@social_bp.post("/fcm/register")
@jwt_required
def register_fcm_token():
    d = request.get_json(force=True, silent=True) or {}
    token = d.get("token")
    if not token:
        return jsonify(error="token required"), 400
    conn = db.get_conn()
    try:
        # Clear this token from all other users (prevents cross-user notifications on same device)
        db.execute(conn, "UPDATE users SET fcm_token = NULL WHERE fcm_token = %s AND id != %s",
                  (token, g.user_id))

        # Set token for current user
        db.execute(conn, "UPDATE users SET fcm_token = %s WHERE id = %s", (token, g.user_id))
        conn.commit()
        return jsonify(ok=True)
    finally:
        conn.close()


# ---------------------------------------------------------------- achievements
@social_bp.get("/achievements")
@jwt_required
def achievements():
    conn = db.get_conn()
    try:
        rows = db.query(conn, """
            SELECT a.code, a.name, a.description, a.icon, ua.unlocked_at
            FROM achievements a
            LEFT JOIN user_achievements ua
              ON ua.achievement_id = a.id AND ua.user_id = %s
            ORDER BY a.id""", (g.user_id,))
        return jsonify([{
            "code": a["code"], "name": a["name"], "description": a["description"],
            "icon": a["icon"], "unlocked": a["unlocked_at"] is not None,
        } for a in rows])
    finally:
        conn.close()