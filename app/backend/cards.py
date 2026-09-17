from math import radians, sin, cos, asin, sqrt
import io
import requests
from PIL import Image, ImageStat
from flask import Blueprint, request, jsonify, g

import db
from db import iso
from auth import jwt_required, staff_required
import achievements_service

cards_bp = Blueprint("cards", __name__, url_prefix="/api")

RARITIES = ("COMMON", "RARE", "EPIC", "LEGENDARY")


def haversine_m(lat1, lng1, lat2, lng2):
    lat1, lng1, lat2, lng2 = map(radians, [lat1, lng1, lat2, lng2])
    h = sin((lat2 - lat1) / 2) ** 2 + cos(lat1) * cos(lat2) * sin((lng2 - lng1) / 2) ** 2
    return 2 * 6371000 * asin(sqrt(h))


def looks_like_real_photo(url):
    """
    Lightweight anti-cheat: reject obviously fake or useless photos.
    Returns False for:
      - images smaller than 200x200 (likely screenshots/icons)
      - images that are too dark (black photos, lens cap on)
      - images that are too bright (all white, overexposed)
      - solid-color images (near-zero variance)
    Returns True on any error (fail-open: don't block legitimate users).
    """
    try:
        resp = requests.get(url, timeout=10)
        if resp.status_code != 200:
            return False
        img = Image.open(io.BytesIO(resp.content))
        img.load()

        if img.width < 200 or img.height < 200:
            return False

        # Convert to grayscale and compute statistics
        gray = img.convert("L")
        stat = ImageStat.Stat(gray)

        avg_brightness = stat.mean[0]  # 0 (black) to 255 (white)
        variance = stat.stddev[0]      # how much the pixels vary

        # Reject if too dark (black photo, lens cap on, pitch darkness)
        if avg_brightness < 20:
            return False

        # Reject if too bright (all white, severe overexposure)
        if avg_brightness > 235:
            return False

        # Reject if solid color (near-zero variance)
        if variance < 2:
            return False

        return True
    except Exception as e:
        print(f"Photo validation failed-open: {e}")
        return True


@cards_bp.get("/cards")
@jwt_required
def list_cards():
    try:
        min_lat = float(request.args["min_lat"]); max_lat = float(request.args["max_lat"])
        min_lng = float(request.args["min_lng"]); max_lng = float(request.args["max_lng"])
    except (KeyError, ValueError):
        return jsonify(error="min_lat, max_lat, min_lng, max_lng are required"), 400

    conn = db.get_conn()
    try:
        rows = db.query(conn, """
            SELECT c.*, uc.acquisition, uc.photo_url
            FROM cards c
            LEFT JOIN user_cards uc ON uc.card_id = c.id AND uc.user_id = %s
            WHERE c.is_active = 1
              AND c.latitude  BETWEEN %s AND %s
              AND c.longitude BETWEEN %s AND %s
        """, (g.user_id, min_lat, max_lat, min_lng, max_lng))
        return jsonify([{
            "id": r["id"], "name": r["name"], "description": r["description"],
            "latitude": float(r["latitude"]), "longitude": float(r["longitude"]),
            "radius_meters": r["radius_meters"], "rarity": r["rarity"],
            "points": r["points"], "reference_image_url": r["reference_image_url"],
            "collected": r["acquisition"] is not None,
            "collected_by_photo": r["acquisition"] == "PHOTO",
            "photo_url": r["photo_url"],
        } for r in rows])
    finally:
        conn.close()


@cards_bp.get("/cards/<int:card_id>")
@jwt_required
def card_detail(card_id):
    conn = db.get_conn()
    try:
        r = db.query(conn, """
            SELECT c.*, uc.acquisition, uc.photo_url
            FROM cards c
            LEFT JOIN user_cards uc ON uc.card_id = c.id AND uc.user_id = %s
            WHERE c.id = %s AND c.is_active = 1
        """, (g.user_id, card_id), one=True)
        if not r:
            return jsonify(error="Card not found"), 404
        return jsonify({
            "id": r["id"], "name": r["name"], "description": r["description"],
            "latitude": float(r["latitude"]), "longitude": float(r["longitude"]),
            "radius_meters": r["radius_meters"], "rarity": r["rarity"],
            "points": r["points"], "reference_image_url": r["reference_image_url"],
            "collected": r["acquisition"] is not None,
            "collected_by_photo": r["acquisition"] == "PHOTO",
            "photo_url": r["photo_url"],
        })
    finally:
        conn.close()


@cards_bp.post("/cards")
@staff_required
def create_card():
    d = request.get_json(force=True, silent=True) or {}
    if not d.get("name") or "latitude" not in d or "longitude" not in d:
        return jsonify(error="name, latitude, longitude are required"), 400
    rarity = d.get("rarity", "COMMON")
    if rarity not in RARITIES:
        return jsonify(error="Invalid rarity"), 400

    conn = db.get_conn()
    try:
        card_id = db.execute(conn, """
            INSERT INTO cards (name, description, latitude, longitude,
                               radius_meters, rarity, points, created_by)
            VALUES (%s,%s,%s,%s,%s,%s,%s,%s)""",
            (d["name"], d.get("description"), d["latitude"], d["longitude"],
             int(d.get("radius_meters", 50)), rarity, int(d.get("points", 10)),
             g.user_id))
        conn.commit()
        return jsonify(id=card_id), 201
    finally:
        conn.close()


@cards_bp.post("/collect")
@jwt_required
def collect():
    d = request.get_json(force=True, silent=True) or {}
    try:
        card_id = int(d["card_id"])
        lat, lng = float(d["lat"]), float(d["lng"])
        photo_url, public_id = d["photo_url"], d["public_id"]
    except (KeyError, TypeError, ValueError):
        return jsonify(error="card_id, photo_url, public_id, lat, lng are required"), 400

    conn = db.get_conn()
    try:
        card = db.query(conn, "SELECT * FROM cards WHERE id=%s AND is_active=1",
                        (card_id,), one=True)
        if not card:
            return jsonify(error="Card not found"), 404

        # Check if already collected
        existing = db.query(conn, "SELECT * FROM user_cards WHERE user_id=%s AND card_id=%s",
                            (g.user_id, card_id), one=True)

        if existing:
            acquisition = existing["acquisition"]

            # Allow upgrading from SHARE to PHOTO (original shoot)
            if acquisition == "SHARE":
                # Verify distance
                if haversine_m(lat, lng, float(card["latitude"]),
                               float(card["longitude"])) > card["radius_meters"]:
                    return jsonify(error="You are too far from this card"), 403

                # Anti-cheat: validate the uploaded photo
                if not looks_like_real_photo(photo_url):
                    return jsonify(
                        error="Photo rejected: please take a real photo"), 400

                # Upgrade to original shoot
                db.execute(conn, """
                    UPDATE user_cards
                    SET acquisition='PHOTO', has_original_shoot=1,
                        photo_url=%s, cloudinary_public_id=%s, collected_at=CURRENT_TIMESTAMP
                    WHERE user_id=%s AND card_id=%s
                """, (photo_url, public_id, g.user_id, card_id))

                # Award XP for the original shoot
                db.execute(conn, "UPDATE users SET xp = xp + %s WHERE id=%s",
                           (card["points"], g.user_id))

                # Delete any CARD_NEARBY notifications for this card
                db.execute(conn, """
                    DELETE FROM notifications
                    WHERE user_id = %s AND type = 'CARD_NEARBY'
                      AND payload LIKE %s
                """, (g.user_id, f'%"card_id": {card_id}%'))

                new_achievements = achievements_service.check(conn, g.user_id)
                conn.commit()
                me = db.query(conn, "SELECT xp FROM users WHERE id=%s", (g.user_id,), one=True)
                return jsonify(card_id=card_id, xp_earned=card["points"],
                               total_xp=me["xp"], new_achievements=new_achievements)
            else:
                return jsonify(error=f"Already collected (acquisition={acquisition})"), 409

        # Verify distance for new collection
        if haversine_m(lat, lng, float(card["latitude"]),
                       float(card["longitude"])) > card["radius_meters"]:
            return jsonify(error="You are too far from this card"), 403

        # Anti-cheat: validate the uploaded photo
        if not looks_like_real_photo(photo_url):
            return jsonify(
                error="Photo rejected: please take a real photo"), 400

        # New collection
        db.execute(conn, """
            INSERT INTO user_cards
                (user_id, card_id, acquisition, has_original_shoot, photo_url, cloudinary_public_id)
            VALUES (%s,%s,'PHOTO',1,%s,%s)""",
            (g.user_id, card_id, photo_url, public_id))
        db.execute(conn, "UPDATE users SET xp = xp + %s WHERE id=%s",
                   (card["points"], g.user_id))

        # Delete any CARD_NEARBY notifications for this card
        db.execute(conn, """
            DELETE FROM notifications
            WHERE user_id = %s AND type = 'CARD_NEARBY'
              AND payload LIKE %s
        """, (g.user_id, f'%"card_id": {card_id}%'))

        new_achievements = achievements_service.check(conn, g.user_id)
        conn.commit()
        me = db.query(conn, "SELECT xp FROM users WHERE id=%s", (g.user_id,), one=True)
        return jsonify(card_id=card_id, xp_earned=card["points"],
                       total_xp=me["xp"], new_achievements=new_achievements)
    finally:
        conn.close()


@cards_bp.get("/me/cards")
@jwt_required
def my_cards():
    conn = db.get_conn()
    try:
        rows = db.query(conn, """
            SELECT c.id, c.name, c.rarity, c.points,
                   uc.acquisition, uc.has_original_shoot, uc.photo_url, uc.collected_at
            FROM user_cards uc
            JOIN cards c ON c.id = uc.card_id
            WHERE uc.user_id = %s
            ORDER BY uc.collected_at DESC
        """, (g.user_id,))
        return jsonify([{
            "id": r["id"], "name": r["name"], "rarity": r["rarity"],
            "points": r["points"], "acquisition": r["acquisition"],
            "has_original_shoot": bool(r["has_original_shoot"]),
            "photo_url": r["photo_url"], "collected_at": iso(r["collected_at"]),
        } for r in rows])
    finally:
        conn.close()
        
        
@cards_bp.delete("/me/cards/<int:card_id>")
@jwt_required
def delete_my_card(card_id):
    conn = db.get_conn()
    try:
        # Check if user owns this card
        user_card = db.query(conn, """
            SELECT uc.*, c.points, c.name 
            FROM user_cards uc 
            JOIN cards c ON c.id = uc.card_id 
            WHERE uc.user_id = %s AND uc.card_id = %s
        """, (g.user_id, card_id), one=True)
        
        if not user_card:
            return jsonify(error="Card not found in your collection"), 404
        
        # Deduct XP (only if collected by photo, shared cards don't give XP)
        if user_card["acquisition"] == "PHOTO":
            db.execute(conn, "UPDATE users SET xp = MAX(xp - %s, 0) WHERE id = %s",
                      (user_card["points"], g.user_id))
        
        # Delete the card from collection
        db.execute(conn, "DELETE FROM user_cards WHERE user_id = %s AND card_id = %s",
                  (g.user_id, card_id))
        
        conn.commit()
        return jsonify(ok=True)
    finally:
        conn.close()