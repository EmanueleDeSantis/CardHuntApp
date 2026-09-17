import sqlite3
from config import DB_PATH


def get_conn():
    conn = sqlite3.connect(DB_PATH, timeout=10)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys = ON") # enforce ON DELETE CASCADE
    conn.execute("PRAGMA journal_mode = WAL")  # safer concurrency for a web server
    conn.execute("PRAGMA busy_timeout = 5000")
    return conn


def _adapt(sql):
    # Endpoints were written with MySQL-style %s placeholders; sqlite3 uses ?.
    return sql.replace("%s", "?")


def query(conn, sql, params=(), one=False):
    cur = conn.execute(_adapt(sql), params)
    rows = [dict(r) for r in cur.fetchall()]
    return (rows[0] if rows else None) if one else rows


def execute(conn, sql, params=()):
    """Execute WITHOUT committing; returns lastrowid. Caller commits."""
    cur = conn.execute(_adapt(sql), params)
    return cur.lastrowid


def iso(dt):
    """SQLite returns timestamps as strings; handles both engines."""
    if dt is None:
        return None
    return dt.isoformat() if hasattr(dt, "isoformat") else str(dt)