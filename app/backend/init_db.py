import sqlite3
from pathlib import Path
from config import DB_PATH

schema = Path(__file__).with_name("schema.sql").read_text()
conn = sqlite3.connect(DB_PATH)
conn.executescript(schema)
conn.commit()
conn.close()
print(f"SQLite database initialized at {DB_PATH}")