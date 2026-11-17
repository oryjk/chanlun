from __future__ import annotations

from collections.abc import Iterable
from contextlib import contextmanager
import json
from pathlib import Path
import sqlite3
from threading import Lock
import time

from ...domain.models import Stock
from ...domain.tracking import (
    AssetCategory,
    AssetSortField,
    MarketAssetItem,
    MarketAssetList,
)


def _sort_value(item: MarketAssetItem, sort_field: AssetSortField) -> float:
    if sort_field is AssetSortField.CHANGE_PERCENT:
        return item.change_percent
    if sort_field is AssetSortField.LATEST:
        return item.latest
    return item.amount


class SqliteAssetCatalogCache:
    """持久化最近活跃交易日的行情资产快照，用于盘前 09:00–09:25 清零期与主源异常回退。"""

    def __init__(self, db_path: str | Path):
        self.path = Path(db_path).resolve()
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self._lock = Lock()
        with self._connect() as conn:
            conn.execute("PRAGMA journal_mode=WAL")
            conn.execute(
                """
                CREATE TABLE IF NOT EXISTS asset_catalog_entries (
                    category TEXT NOT NULL,
                    secid TEXT NOT NULL,
                    symbol TEXT NOT NULL,
                    name TEXT NOT NULL,
                    market TEXT NOT NULL,
                    as_of_date TEXT NOT NULL,
                    latest REAL NOT NULL,
                    change_val REAL NOT NULL,
                    change_percent REAL NOT NULL,
                    open_price REAL NOT NULL,
                    high_price REAL NOT NULL,
                    low_price REAL NOT NULL,
                    previous_close REAL NOT NULL,
                    volume INTEGER NOT NULL,
                    amount REAL NOT NULL,
                    sparkline_json TEXT NOT NULL DEFAULT '[]',
                    updated_at REAL NOT NULL,
                    PRIMARY KEY (category, secid)
                )
                """
            )
            conn.execute(
                "CREATE INDEX IF NOT EXISTS idx_asset_catalog_category_date "
                "ON asset_catalog_entries (category, as_of_date)"
            )

    @contextmanager
    def _connect(self, *, readonly: bool = False):
        conn = sqlite3.connect(
            self.path.as_uri() + "?mode=ro" if readonly else str(self.path),
            uri=readonly,
            timeout=5.0,
        )
        try:
            if not readonly:
                conn.execute("PRAGMA synchronous=NORMAL")
            yield conn
            if not readonly:
                conn.commit()
        finally:
            conn.close()

    def save_items(
        self,
        category: AssetCategory,
        as_of_date: str,
        items: Iterable[MarketAssetItem],
    ) -> None:
        normalized_date = (as_of_date or "").strip()
        if not normalized_date:
            return
        valid = [item for item in items if item.amount > 0 and item.latest > 0]
        if not valid:
            return
        now = time.time()
        rows = [
            (
                category.value,
                item.stock.secid,
                item.stock.symbol,
                item.stock.name,
                item.stock.market,
                normalized_date,
                float(item.latest),
                float(item.change),
                float(item.change_percent),
                float(item.open),
                float(item.high),
                float(item.low),
                float(item.previous_close),
                int(item.volume),
                float(item.amount),
                json.dumps(list(item.sparkline)),
                now,
            )
            for item in valid
        ]
        with self._lock, self._connect() as conn:
            existing_max = conn.execute(
                "SELECT MAX(as_of_date) FROM asset_catalog_entries WHERE category = ?",
                (category.value,),
            ).fetchone()[0]
            if existing_max is not None and normalized_date < str(existing_max):
                return
            if existing_max is not None and str(existing_max) < normalized_date:
                conn.execute(
                    "DELETE FROM asset_catalog_entries WHERE category = ? AND as_of_date < ?",
                    (category.value, normalized_date),
                )
            conn.executemany(
                """
                INSERT INTO asset_catalog_entries (
                    category, secid, symbol, name, market, as_of_date,
                    latest, change_val, change_percent,
                    open_price, high_price, low_price, previous_close,
                    volume, amount, sparkline_json, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(category, secid) DO UPDATE SET
                    symbol = excluded.symbol,
                    name = excluded.name,
                    market = excluded.market,
                    as_of_date = excluded.as_of_date,
                    latest = excluded.latest,
                    change_val = excluded.change_val,
                    change_percent = excluded.change_percent,
                    open_price = excluded.open_price,
                    high_price = excluded.high_price,
                    low_price = excluded.low_price,
                    previous_close = excluded.previous_close,
                    volume = excluded.volume,
                    amount = excluded.amount,
                    sparkline_json = CASE
                        WHEN excluded.sparkline_json != '[]' THEN excluded.sparkline_json
                        ELSE asset_catalog_entries.sparkline_json
                    END,
                    updated_at = excluded.updated_at
                """,
                rows,
            )

    def count_items(self, category: AssetCategory) -> int:
        with self._lock, self._connect(readonly=True) as conn:
            max_date = conn.execute(
                "SELECT MAX(as_of_date) FROM asset_catalog_entries WHERE category = ? AND amount > 0",
                (category.value,),
            ).fetchone()[0]
            if not max_date:
                return 0
            count = conn.execute(
                "SELECT COUNT(*) FROM asset_catalog_entries "
                "WHERE category = ? AND as_of_date = ? AND amount > 0",
                (category.value, max_date),
            ).fetchone()[0]
            return int(count or 0)

    def read_items(
        self,
        category: AssetCategory,
        *,
        sort_field: AssetSortField,
        descending: bool,
        limit: int,
    ) -> MarketAssetList | None:
        with self._lock, self._connect(readonly=True) as conn:
            max_date = conn.execute(
                "SELECT MAX(as_of_date) FROM asset_catalog_entries WHERE category = ? AND amount > 0",
                (category.value,),
            ).fetchone()[0]
            if not max_date:
                return None
            raw_rows = conn.execute(
                """
                SELECT secid, symbol, name, market, latest, change_val, change_percent,
                       open_price, high_price, low_price, previous_close,
                       volume, amount, sparkline_json
                FROM asset_catalog_entries
                WHERE category = ? AND as_of_date = ? AND amount > 0
                """,
                (category.value, max_date),
            ).fetchall()

        if not raw_rows:
            return None

        items: list[MarketAssetItem] = []
        for (
            secid,
            symbol,
            name,
            market,
            latest,
            change_val,
            change_percent,
            open_price,
            high_price,
            low_price,
            previous_close,
            volume,
            amount,
            sparkline_json,
        ) in raw_rows:
            try:
                parsed_sparkline = tuple(float(point) for point in json.loads(sparkline_json or "[]"))
            except (TypeError, ValueError, json.JSONDecodeError):
                parsed_sparkline = ()
            items.append(
                MarketAssetItem(
                    stock=Stock(secid=secid, symbol=symbol, name=name, market=market),
                    latest=float(latest),
                    change=float(change_val),
                    change_percent=float(change_percent),
                    open=float(open_price),
                    high=float(high_price),
                    low=float(low_price),
                    previous_close=float(previous_close),
                    volume=int(volume),
                    amount=float(amount),
                    sparkline=parsed_sparkline,
                )
            )

        items.sort(key=lambda entry: _sort_value(entry, sort_field), reverse=descending)
        return MarketAssetList(
            category=category,
            as_of_date=str(max_date),
            items=tuple(items[:limit]),
        )
