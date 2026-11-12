import json
import tempfile
import unittest
from datetime import date, datetime, timezone
from pathlib import Path

from backend.application.ranking_jobs import RankingItem
from backend.domain.analysis_version import ANALYSIS_VERSION
from backend.domain.models import RankingBoard, RankingEntry, Stock, TimeFrame
from backend.infrastructure.persistence.json_quote_store import JsonQuoteStore
from backend.infrastructure.persistence.sqlite_ranking_store import SqliteRankingStore, _json


DAY = date(2026, 11, 10)
NOW = datetime(2026, 11, 10, 8, tzinfo=timezone.utc)


def _make_entry(secid: str, symbol: str = "600519", name: str = "贵州茅台", market: str = "沪A") -> RankingEntry:
    return RankingEntry(
        rank=1,
        stock=Stock(secid=secid, symbol=symbol, name=name, market=market),
        bias="bullish",
        strength=3,
        latest=1500.0,
        change_percent=1.2,
        invalidation_price=1450.0,
    )


class SecurityIdPersistenceTests(unittest.TestCase):
    def test_legacy_snapshot_entries_decode_to_canonical_ids(self):
        with tempfile.TemporaryDirectory() as tmp:
            store = SqliteRankingStore(Path(tmp) / "rankings.db")
            board = RankingBoard(
                generated_at=NOW,
                timeframe=TimeFrame.ONE_DAY,
                bullish=(
                    _make_entry("1.600519", "600519", "贵州茅台", "沪A"),
                    _make_entry("0.000001", "000001", "平安银行", "深A"),
                ),
                bearish=(),
                trade_date=DAY,
                total_count=2,
                analyzed_count=2,
            )
            with store._connection() as connection:
                connection.execute("INSERT INTO ranking_snapshot VALUES(1,?)", (_json(board),))

            recovered = store.read().board
            self.assertEqual([e.stock.secid for e in recovered.bullish], ["600519.SH", "000001.SZ"])

    def test_undecodable_legacy_entries_are_skipped(self):
        with tempfile.TemporaryDirectory() as tmp:
            store = SqliteRankingStore(Path(tmp) / "rankings.db")
            board = RankingBoard(
                generated_at=NOW,
                timeframe=TimeFrame.ONE_DAY,
                bullish=(
                    _make_entry("105.AAPL", "AAPL", "Apple", "美股"),
                    _make_entry("116.00700", "00700", "腾讯控股", "港股"),
                    _make_entry("1.600519", "600519", "贵州茅台", "沪A"),
                ),
                bearish=(),
                trade_date=DAY,
                total_count=3,
                analyzed_count=3,
            )
            with store._connection() as connection:
                connection.execute("INSERT INTO ranking_snapshot VALUES(1,?)", (_json(board),))

            recovered = store.read().board
            self.assertEqual([e.stock.secid for e in recovered.bullish], ["600519.SH"])

    def test_legacy_checkpoint_items_resume_under_canonical_ids(self):
        with tempfile.TemporaryDirectory() as tmp:
            store = SqliteRankingStore(Path(tmp) / "rankings.db")
            valid_item = RankingItem("1.600519", _make_entry("1.600519", "600519", "贵州茅台", "沪A"))
            hk_item = RankingItem("116.00700", _make_entry("116.00700", "00700", "腾讯控股", "港股"))
            with store._connection() as connection:
                for item in (valid_item, hk_item):
                    connection.execute(
                        "INSERT INTO ranking_items(trade_date,secid,payload,analysis_version) VALUES(?,?,?,?)",
                        (DAY.isoformat(), item.secid, _json(item), ANALYSIS_VERSION),
                    )

            items = store.read_items(DAY, analysis_version=ANALYSIS_VERSION)
            self.assertEqual([item.secid for item in items], ["600519.SH"])
            self.assertEqual(items[0].entry.stock.secid, "600519.SH")

    def test_same_day_legacy_quote_file_is_read_and_merged(self):
        with tempfile.TemporaryDirectory() as tmp:
            today = datetime.now().astimezone().date()
            legacy_file = Path(tmp) / f"1.600519-{today:%Y%m%d}.json"
            canonical_file = Path(tmp) / f"600519.SH-{today:%Y%m%d}.json"
            legacy_file.write_text(
                json.dumps(
                    {
                        "secid": "1.600519",
                        "date": f"{today:%Y-%m-%d}",
                        "points": [{"time": "2026-11-10T01:30:00Z", "price": 1500.0}],
                    }
                ),
                encoding="utf-8",
            )

            store = JsonQuoteStore(tmp)
            initial = store.read_series("600519.SH")
            self.assertEqual(initial.secid, "600519.SH")
            self.assertEqual(len(initial.points), 1)
            self.assertEqual(initial.points[0].price, 1500.0)

            updated = store.append(
                "600519.SH",
                datetime(2026, 11, 10, 1, 31, tzinfo=timezone.utc),
                1501.0,
            )
            self.assertEqual(updated.secid, "600519.SH")
            self.assertEqual([p.price for p in updated.points], [1500.0, 1501.0])
            self.assertEqual(len(store.read_series("600519.SH").points), 2)
            self.assertFalse(legacy_file.exists())
            self.assertTrue(canonical_file.exists())
            saved_payload = json.loads(canonical_file.read_text(encoding="utf-8"))
            self.assertEqual(saved_payload["secid"], "600519.SH")
            self.assertEqual(len(saved_payload["points"]), 2)


if __name__ == "__main__":
    unittest.main()
