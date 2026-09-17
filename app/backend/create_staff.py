import db
from werkzeug.security import generate_password_hash

conn = db.get_conn()
db.execute(conn,
    "INSERT OR IGNORE INTO users (username, email, password_hash, role) VALUES (%s,%s,%s,'ADMIN')",
    ("admin", "admin@cardhunt.app", generate_password_hash("Admin123!")))
db.execute(conn,
    "INSERT OR IGNORE INTO users (username, email, password_hash, role) VALUES (%s,%s,%s,'MODERATOR')",
    ("mod", "mod@cardhunt.app", generate_password_hash("Mod123!")))
conn.commit()
conn.close()
print("Seeded: admin / Admin123!  and  mod / Mod123!")