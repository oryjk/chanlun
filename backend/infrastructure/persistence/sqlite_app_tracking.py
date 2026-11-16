from contextlib import contextmanager
from datetime import date, datetime
from pathlib import Path
import sqlite3

from ...domain.tracking import TrackedAssetRecord


class SqliteAppTrackingStore:
    """Native app tracking persistence: read-only connections for queries, IMMEDIATE tx for writes."""

    def __init__(self, path):
        self.path = Path(path).resolve()
        self.path.parent.mkdir(parents=True, exist_ok=True)
        with self._db() as db:
            db.executescript(
                """
                PRAGMA journal_mode=WAL;
                CREATE TABLE IF NOT EXISTS user_trackings (
                    user_id TEXT NOT NULL,
                    secid TEXT NOT NULL,
                    symbol TEXT NOT NULL,
                    name TEXT NOT NULL,
                    market TEXT NOT NULL,
                    start_date TEXT NOT NULL,
                    start_price REAL NOT NULL,
                    created_at TEXT NOT NULL,
                    PRIMARY KEY (user_id, secid)
                );
                CREATE INDEX IF NOT EXISTS idx_user_trackings_created
                    ON user_trackings (user_id, created_at DESC);
                """
            )

    @contextmanager
    def _db(self, readonly=False):
        db = sqlite3.connect(
            self.path.as_uri() + "?mode=ro" if readonly else str(self.path),
            uri=readonly,
            timeout=10,
        )
        try:
            with db:
                yield db
        finally:
            db.close()

    def list_by_user(self, user_id: str) -> tuple[TrackedAssetRecord, ...]:
        with self._db(readonly=True) as db:
            rows = db.execute(
                """
                SELECT user_id, secid, symbol, name, market, start_date, start_price, created_at
                FROM user_trackings
                WHERE user_id = ?
                ORDER BY created_at DESC
                """,
                (user_id,),
            ).fetchall()
        return tuple(self._to_record(row) for row in rows)

    def upsert_tracking(self, record: TrackedAssetRecord) -> TrackedAssetRecord:
        with self._db() as db:
            db.execute("BEGIN IMMEDIATE")
            db.execute(
                """
                INSERT OR IGNORE INTO user_trackings (
                    user_id, secid, symbol, name, market, start_date, start_price, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    record.user_id,
                    record.secid,
                    record.symbol,
                    record.name,
                    record.market,
                    record.start_date.isoformat(),
                    float(record.start_price),
                    record.created_at.isoformat(),
                ),
            )
            row = db.execute(
                """
                SELECT user_id, secid, symbol, name, market, start_date, start_price, created_at
                FROM user_trackings
                WHERE user_id = ? AND secid = ?
                """,
                (record.user_id, record.secid),
            ).fetchone()
        return self._to_record(row) if row else record

    def remove_trackings(self, user_id: str, secids: tuple[str, ...]) -> int:
        if not secids:
            return 0
        placeholders = ",".join("?" for _ in secids)
        with self._db() as db:
            db.execute("BEGIN IMMEDIATE")
            cursor = db.execute(
                f"DELETE FROM user_trackings WHERE user_id = ? AND secid IN ({placeholders})",
                (user_id, *secids),
            )
        return int(cursor.rowcount)

    def clear_user_trackings(self, user_id: str) -> None:
        with self._db() as db:
            db.execute("DELETE FROM user_trackings WHERE user_id = ?", (user_id,))

    @staticmethod
    def _to_record(row) -> TrackedAssetRecord:
        return TrackedAssetRecord(
            user_id=row[0],
            secid=row[1],
            symbol=row[2],
            name=row[3],
            market=row[4],
            start_date=date.fromisoformat(row[5]),
            start_price=float(row[6]),
            created_at=datetime.fromisoformat(row[7]),
        )
