import firebase_admin
from firebase_admin import credentials, messaging
import os

# Initialize Firebase Admin SDK
cred = credentials.Certificate(os.path.join(os.path.dirname(__file__), "firebase-service-account.json"))
firebase_admin.initialize_app(cred)


def send_push(device_token, title, body, data=None):
    """Send a push notification to a specific device."""
    if not device_token:
        return False
    try:
        message = messaging.Message(
            notification=messaging.Notification(title=title, body=body),
            data=data or {},
            token=device_token
        )
        messaging.send(message)
        return True
    except Exception as e:
        print(f"FCM error: {e}")
        return False


def send_push_to_user(conn, user_id, title, body, data=None):
    """Look up user's device token and send push."""
    row = conn.execute(
        "SELECT fcm_token FROM users WHERE id = ?", (user_id,)
    ).fetchone()
    if row and row[0]:
        return send_push(row[0], title, body, data)
    return False