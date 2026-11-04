import json
import sqlite3
from contextlib import contextmanager
from dataclasses import asdict
from datetime import date, datetime
from pathlib import Path

from ...domain.models import BuySellPoint, QuotePoint, RankingBoard, RankingEntry, Stock, TimeFrame
from ...domain.rankings import RankingItem, RankingProgress, RankingView


def _json(value):
    return json.dumps(asdict(value), ensure_ascii=False, default=lambda item: item.isoformat())


def _point(value):
    if not value:
        return None
    point = dict(value)
    point["time"] = datetime.fromisoformat(point["time"])
    return BuySellPoint(**point)


def _entry(data):
    if data is None:
        return None
    data = dict(data)
    data["stock"] = Stock(**data["stock"])
    for key in ("latest_point", "buy_point", "sell_point"):
        data[key] = _point(data.get(key))
    data["trend"] = tuple(
        QuotePoint(datetime.fromisoformat(item["time"]), float(item["price"]))
        for item in (data.get("trend") or ())
    )
    return RankingEntry(**data)


def _board(payload):
    if not payload:
        return None
    data = json.loads(payload)
    data["generated_at"] = datetime.fromisoformat(data["generated_at"]) if data["generated_at"] else None
    data["trade_date"] = date.fromisoformat(data["trade_date"]) if data["trade_date"] else None
    data["timeframe"] = TimeFrame(data["timeframe"])
    for key in ("bullish", "bearish"):
        data[key] = tuple(_entry(entry) for entry in data[key])
    return RankingBoard(**data)


class SqliteRankingStore:
    """Read-only connections serve queries; commands own all writes and leases."""

    def __init__(self, path):
        self.path = Path(path).resolve()
        self.path.parent.mkdir(parents=True, exist_ok=True)
        with self._connection() as connection:
            connection.execute("PRAGMA journal_mode=WAL")
            connection.executescript("""
                CREATE TABLE IF NOT EXISTS ranking_snapshot (id INTEGER PRIMARY KEY CHECK(id=1), payload TEXT NOT NULL);
                CREATE TABLE IF NOT EXISTS ranking_state (id INTEGER PRIMARY KEY CHECK(id=1), payload TEXT NOT NULL, owner TEXT, lease_until REAL NOT NULL DEFAULT 0);
                CREATE TABLE IF NOT EXISTS ranking_items (trade_date TEXT NOT NULL, secid TEXT NOT NULL, payload TEXT NOT NULL, PRIMARY KEY(trade_date,secid));
                CREATE TABLE IF NOT EXISTS ranking_days (trade_date TEXT PRIMARY KEY);
            """)
            connection.execute("INSERT OR IGNORE INTO ranking_state(id,payload) VALUES(1,?)", (_json(RankingProgress()),))

    @contextmanager
    def _connection(self, readonly=False):
        connection = sqlite3.connect(self.path.as_uri() + "?mode=ro" if readonly else str(self.path), uri=readonly, timeout=10)
        try:
            if not readonly:
                connection.execute("PRAGMA synchronous=NORMAL")
            with connection:
                yield connection
        finally:
            connection.close()

    def read(self):
        with self._connection(True) as connection:
            connection.execute("BEGIN")
            result = connection.execute("SELECT payload FROM ranking_snapshot WHERE id=1").fetchone()
            data = json.loads(connection.execute("SELECT payload FROM ranking_state WHERE id=1").fetchone()[0])
        if data["trade_date"]:
            data["trade_date"] = date.fromisoformat(data["trade_date"])
        if data["updated_at"]:
            data["updated_at"] = datetime.fromisoformat(data["updated_at"])
        return RankingView(_board(result[0]) if result else None, RankingProgress(**data))

    def read_items(self, trade_date):
        with self._connection(True) as connection:
            rows = connection.execute("SELECT payload FROM ranking_items WHERE trade_date=?", (trade_date.isoformat(),)).fetchall()
        return tuple(RankingItem(data["secid"], _entry(data["entry"]), data["error"]) for data in (json.loads(row[0]) for row in rows))

    def claim(self, trade_date, owner, now, lease_seconds):
        with self._connection() as connection:
            connection.execute("BEGIN IMMEDIATE")
            latest = connection.execute("SELECT payload FROM ranking_snapshot WHERE id=1").fetchone()
            if latest:
                latest_date = json.loads(latest[0]).get("trade_date")
                if latest_date and latest_date >= trade_date.isoformat():
                    return False
            if connection.execute("SELECT 1 FROM ranking_days WHERE trade_date=?", (trade_date.isoformat(),)).fetchone():
                return False
            current = connection.execute("SELECT owner,lease_until FROM ranking_state WHERE id=1").fetchone()
            if current[0] and current[1] > now.timestamp():
                return False
            progress = RankingProgress("preparing", trade_date, updated_at=now)
            connection.execute("UPDATE ranking_state SET payload=?,owner=?,lease_until=? WHERE id=1", (_json(progress), owner, now.timestamp() + lease_seconds))
        return True

    def heartbeat(self, owner, now, lease_seconds):
        with self._connection() as connection:
            return connection.execute("UPDATE ranking_state SET lease_until=? WHERE id=1 AND owner=?", (now.timestamp() + lease_seconds, owner)).rowcount == 1

    @staticmethod
    def _owned(connection, owner):
        if not connection.execute("SELECT 1 FROM ranking_state WHERE id=1 AND owner=?", (owner,)).fetchone():
            raise RuntimeError("榜单更新任务已被其他进程接管")

    def set_progress(self, progress, owner):
        with self._connection() as connection:
            connection.execute("BEGIN IMMEDIATE")
            self._owned(connection, owner)
            connection.execute("UPDATE ranking_state SET payload=? WHERE id=1", (_json(progress),))

    def record(self, trade_date, item, progress, owner, now, lease_seconds):
        with self._connection() as connection:
            connection.execute("BEGIN IMMEDIATE")
            self._owned(connection, owner)
            connection.execute("INSERT OR REPLACE INTO ranking_items VALUES(?,?,?)", (trade_date.isoformat(), item.secid, _json(item)))
            connection.execute("UPDATE ranking_state SET payload=?,lease_until=? WHERE id=1", (_json(progress), now.timestamp() + lease_seconds))

    def publish(self, board, owner, now):
        if board.generated_at is None or board.trade_date is None or board.analyzed_count == 0:
            raise ValueError("不能发布未完成的收盘榜单")
        with self._connection() as connection:
            connection.execute("BEGIN IMMEDIATE")
            self._owned(connection, owner)
            connection.execute("INSERT OR REPLACE INTO ranking_snapshot VALUES(1,?)", (_json(board),))
            connection.execute("INSERT OR IGNORE INTO ranking_days VALUES(?)", (board.trade_date.isoformat(),))
            progress = RankingProgress(board.state, board.trade_date, board.total_count, board.total_count, board.analyzed_count, board.failed_count, now)
            connection.execute("UPDATE ranking_state SET payload=?,owner=NULL,lease_until=0 WHERE id=1", (_json(progress),))
            connection.execute("DELETE FROM ranking_items WHERE trade_date < ?", (board.trade_date.isoformat(),))

    def fail(self, owner, message, now):
        with self._connection() as connection:
            connection.execute("BEGIN IMMEDIATE")
            row = connection.execute("SELECT payload FROM ranking_state WHERE id=1 AND owner=?", (owner,)).fetchone()
            if not row:
                return
            data = json.loads(row[0])
            data.update(state="error", updated_at=now.isoformat(), error=message)
            connection.execute("UPDATE ranking_state SET payload=?,owner=NULL,lease_until=0 WHERE id=1", (json.dumps(data, ensure_ascii=False),))

    def release(self, owner, now):
        with self._connection() as connection:
            connection.execute("BEGIN IMMEDIATE")
            row = connection.execute("SELECT payload FROM ranking_state WHERE id=1 AND owner=?", (owner,)).fetchone()
            if not row:
                return
            data = json.loads(row[0])
            data.update(state="preparing", updated_at=now.isoformat(), error=None)
            connection.execute("UPDATE ranking_state SET payload=?,owner=NULL,lease_until=0 WHERE id=1", (json.dumps(data, ensure_ascii=False),))

    def note_error(self, message, now):
        with self._connection() as connection:
            connection.execute("BEGIN IMMEDIATE")
            row = connection.execute("SELECT payload,owner,lease_until FROM ranking_state WHERE id=1").fetchone()
            if row[1] and row[2] > now.timestamp():
                return
            data = json.loads(row[0])
            data.update(state="error", updated_at=now.isoformat(), error=message)
            connection.execute("UPDATE ranking_state SET payload=?,owner=NULL,lease_until=0 WHERE id=1", (json.dumps(data, ensure_ascii=False),))
