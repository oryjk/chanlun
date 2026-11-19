import sqlite3
import time
from collections.abc import Callable
from contextlib import closing
from pathlib import Path


class SqliteSumlyTicketStore:
    def __init__(self, path: str, clock: Callable[[], float] = time.time):
        Path(path).parent.mkdir(parents=True, exist_ok=True)
        self._path = path
        self._clock = clock
        with closing(sqlite3.connect(path)) as connection, connection:
            connection.execute("""
                CREATE TABLE IF NOT EXISTS sumly_webview_tickets (
                    digest TEXT PRIMARY KEY,
                    user_id INTEGER NOT NULL,
                    expires_at REAL NOT NULL
                )
            """)

    def save(self, digest: str, user_id: int) -> None:
        now = self._clock()
        with closing(sqlite3.connect(self._path)) as connection, connection:
            connection.execute("DELETE FROM sumly_webview_tickets WHERE expires_at <= ?", (now,))
            connection.execute(
                "INSERT INTO sumly_webview_tickets VALUES (?, ?, ?)",
                (digest, user_id, now + 60),
            )

    def consume(self, digest: str) -> int | None:
        with closing(sqlite3.connect(self._path)) as connection, connection:
            row = connection.execute(
                "DELETE FROM sumly_webview_tickets WHERE digest = ? RETURNING user_id, expires_at",
                (digest,),
            ).fetchone()
            return row[0] if row and row[1] > self._clock() else None
