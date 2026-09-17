import os

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DB_PATH = os.path.join(BASE_DIR, "cardhunt.db")

JWT_SECRET = "zQXuVStznjAHh0b81efJKFe4RVf+wUhBbyMTYCTjez4="
JWT_EXPIRES_HOURS = 24 * 7