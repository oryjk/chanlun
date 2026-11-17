from __future__ import annotations

from collections.abc import Iterable, Sequence
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
    sort_market_assets,
)


class SqliteAssetCatalogCache:
    """持久化最近活跃交易日的全市场行情快照与最近 25 个交易日收盘序列。"""

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
            conn.execute(
                """
                CREATE TABLE IF NOT EXISTS asset_daily_bars (
                    secid TEXT NOT NULL,
                    trade_date TEXT NOT NULL,
                    close REAL NOT NULL,
                    is_final INTEGER NOT NULL DEFAULT 1,
                    updated_at REAL NOT NULL,
                    PRIMARY KEY (secid, trade_date)
                )
                """
            )
            conn.execute(
                "CREATE INDEX IF NOT EXISTS idx_asset_daily_bars_date "
                "ON asset_daily_bars (trade_date)"
            )
            conn.execute(
                """
                CREATE TABLE IF NOT EXISTS asset_sparkline_sync (
                    secid TEXT PRIMARY KEY,
                    synced_through_date TEXT NOT NULL,
                    bar_count INTEGER NOT NULL,
                    updated_at REAL NOT NULL
                )
                """
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
        item_list = list(items)
        if not any(item.amount > 0 and item.latest > 0 for item in item_list):
            return
        valid = [item for item in item_list if item.latest > 0]
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

    def read_all_items(self, category: AssetCategory) -> MarketAssetList | None:
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
                WHERE category = ? AND as_of_date = ?
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
        return MarketAssetList(
            category=category,
            as_of_date=str(max_date),
            items=tuple(items),
            total=len(items),
            offset=0,
            has_more=False,
        )

    def read_items(
        self,
        category: AssetCategory,
        *,
        sort_field: AssetSortField,
        descending: bool,
        limit: int,
        offset: int = 0,
    ) -> MarketAssetList | None:
        all_list = self.read_all_items(category)
        if all_list is None or not all_list.items:
            return None
        ordered = sort_market_assets(all_list.items, sort_field, descending=descending)
        total = len(ordered)
        page = tuple(ordered[offset : offset + limit])
        return MarketAssetList(
            category=category,
            as_of_date=all_list.as_of_date,
            items=page,
            total=total,
            offset=offset,
            has_more=offset + len(page) < total,
        )

    def load_daily_bars_map(self) -> dict[str, list[tuple[str, float, bool]]]:
        with self._lock, self._connect(readonly=True) as conn:
            rows = conn.execute(
                "SELECT secid, trade_date, close, is_final FROM asset_daily_bars "
                "ORDER BY secid ASC, trade_date ASC"
            ).fetchall()
        grouped: dict[str, list[tuple[str, float, bool]]] = {}
        for secid, trade_date, close_price, is_final in rows:
            grouped.setdefault(secid, []).append((str(trade_date), float(close_price), bool(is_final)))
        return grouped

    def load_sync_dates(self) -> dict[str, str]:
        with self._lock, self._connect(readonly=True) as conn:
            rows = conn.execute(
                "SELECT secid, synced_through_date FROM asset_sparkline_sync"
            ).fetchall()
        return {str(secid): str(sync_date) for secid, sync_date in rows}

    def upsert_daily_bars_batch(
        self,
        batches: Iterable[tuple[str, Sequence[tuple[str, float, bool]], str | None]],
        *,
        replace_secids: set[str] | None = None,
        keep_per_secid: int = 25,
    ) -> None:
        batch_list = list(batches)
        if not batch_list and not replace_secids:
            return
        now = time.time()
        bar_rows: list[tuple[str, str, float, int, float]] = []
        sync_candidates: list[tuple[str, str]] = []
        touched_secids: list[str] = []
        seen_touched: set[str] = set()

        for secid, bars, synced_through in batch_list:
            if secid not in seen_touched:
                seen_touched.add(secid)
                touched_secids.append(secid)
            by_date: dict[str, tuple[float, bool]] = {}
            for trade_date, close_price, is_final in bars:
                if trade_date and close_price > 0:
                    by_date[trade_date] = (round(float(close_price), 4), bool(is_final))
            sorted_bars = sorted(by_date.items(), key=lambda entry: entry[0])[-keep_per_secid:]
            for trade_date, (close_price, is_final) in sorted_bars:
                bar_rows.append((secid, trade_date, close_price, 1 if is_final else 0, now))
            if synced_through:
                sync_candidates.append((secid, synced_through))

        with self._lock, self._connect() as conn:
            if replace_secids:
                replace_list = sorted(replace_secids)
                for idx in range(0, len(replace_list), 500):
                    chunk = replace_list[idx : idx + 500]
                    placeholders = ",".join("?" for _ in chunk)
                    conn.execute(
                        f"DELETE FROM asset_daily_bars WHERE secid IN ({placeholders})",
                        chunk,
                    )

            if bar_rows:
                conn.executemany(
                    """
                    INSERT INTO asset_daily_bars (secid, trade_date, close, is_final, updated_at)
                    VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT(secid, trade_date) DO UPDATE SET
                        close = excluded.close,
                        is_final = CASE
                            WHEN excluded.is_final = 1 THEN 1
                            ELSE asset_daily_bars.is_final
                        END,
                        updated_at = excluded.updated_at
                    """,
                    bar_rows,
                )

            for idx in range(0, len(touched_secids), 500):
                chunk = touched_secids[idx : idx + 500]
                placeholders = ",".join("?" for _ in chunk)
                overflow = conn.execute(
                    f"SELECT secid FROM asset_daily_bars WHERE secid IN ({placeholders}) "
                    "GROUP BY secid HAVING COUNT(*) > ?",
                    (*chunk, keep_per_secid),
                ).fetchall()
                for (over_secid,) in overflow:
                    conn.execute(
                        """
                        DELETE FROM asset_daily_bars
                        WHERE secid = ? AND trade_date NOT IN (
                            SELECT trade_date FROM asset_daily_bars
                            WHERE secid = ?
                            ORDER BY trade_date DESC
                            LIMIT ?
                        )
                        """,
                        (over_secid, over_secid, keep_per_secid),
                    )

            if sync_candidates:
                counts_by_secid: dict[str, int] = {}
                sync_secids = [secid for secid, _ in sync_candidates]
                for idx in range(0, len(sync_secids), 500):
                    chunk = sync_secids[idx : idx + 500]
                    placeholders = ",".join("?" for _ in chunk)
                    for secid, cnt in conn.execute(
                        f"SELECT secid, COUNT(*) FROM asset_daily_bars "
                        f"WHERE secid IN ({placeholders}) GROUP BY secid",
                        chunk,
                    ).fetchall():
                        counts_by_secid[str(secid)] = int(cnt)
                sync_rows = [
                    (secid, sync_date, counts_by_secid.get(secid, 0), now)
                    for secid, sync_date in sync_candidates
                ]
                conn.executemany(
                    """
                    INSERT INTO asset_sparkline_sync (secid, synced_through_date, bar_count, updated_at)
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT(secid) DO UPDATE SET
                        synced_through_date = CASE
                            WHEN excluded.synced_through_date > asset_sparkline_sync.synced_through_date
                            THEN excluded.synced_through_date
                            ELSE asset_sparkline_sync.synced_through_date
                        END,
                        bar_count = excluded.bar_count,
                        updated_at = excluded.updated_at
                    """,
                    sync_rows,
                )

    def prune_old_daily_bars(self, cutoff_trade_date: str) -> None:
        cutoff = (cutoff_trade_date or "").strip()
        if not cutoff:
            return
        with self._lock, self._connect() as conn:
            conn.execute("DELETE FROM asset_daily_bars WHERE trade_date < ?", (cutoff,))
