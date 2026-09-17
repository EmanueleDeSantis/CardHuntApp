import db
import json
from push_service import send_push_to_user


def _counts(conn, user_id):
    total = db.query(conn, "SELECT COUNT(*) c FROM user_cards WHERE user_id=%s",
                     (user_id,), one=True)["c"]
    photo = db.query(conn,
        "SELECT COUNT(*) c FROM user_cards WHERE user_id=%s AND acquisition='PHOTO'",
        (user_id,), one=True)["c"]
    shared = db.query(conn,
        "SELECT COUNT(*) c FROM user_cards WHERE user_id=%s AND acquisition='SHARE'",
        (user_id,), one=True)["c"]
    friends = db.query(conn,
        """SELECT COUNT(*) c FROM friendships
           WHERE status='ACCEPTED' AND (requester_id=%s OR addressee_id=%s)""",
        (user_id, user_id), one=True)["c"]
    return {
        "FIRST_CARD": total,
        "PHOTOGRAPHER": photo,
        "COLLECTOR_1": total,
        "COLLECTOR_5": total,
        "SOCIAL_CARDS": shared,
        "SOCIAL_FRIENDS": friends,
    }


def check(conn, user_id):
    """Inserts any newly-reached achievements; returns list of {code,name}."""
    counts = _counts(conn, user_id)
    newly = []
    for ach in db.query(conn, "SELECT * FROM achievements"):
        if counts.get(ach["code"], 0) >= ach["threshold"]:
            already = db.query(conn,
                "SELECT 1 FROM user_achievements WHERE user_id=%s AND achievement_id=%s",
                (user_id, ach["id"]), one=True)
            if not already:
                db.execute(conn,
                    "INSERT INTO user_achievements (user_id, achievement_id) VALUES (%s,%s)",
                    (user_id, ach["id"]))
                newly.append({"code": ach["code"], "name": ach["name"]})

    # Send push notification AND insert in-app notification for newly unlocked achievements
    if newly:
        names = ", ".join(a["name"] for a in newly)
        message = f"You unlocked: {names}"
        
        # Insert into notifications table for in-app inbox
        db.execute(conn, """
            INSERT INTO notifications (user_id, type, message, payload)
            VALUES (%s, 'ACHIEVEMENT', %s, %s)
        """, (user_id, message, json.dumps({
            "achievement_codes": [a["code"] for a in newly]
        })))
        
        # Send Firebase push notification
        send_push_to_user(conn, user_id, "Achievement Unlocked!",
                          message,
                          {"type": "ACHIEVEMENT"})

    return newly