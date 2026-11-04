import unittest
import tempfile
from datetime import date
from pathlib import Path
from threading import Lock
from types import SimpleNamespace

from backend.application.commands.build_daily_rankings import BuildDailyRankingsCommand
from backend.domain.rankings import rank_entries
from backend.domain.errors import MarketDataNotFound
from backend.domain.models import RankingEntry, Stock
from backend.infrastructure.market.eastmoney_stock_pool import EastmoneyStockPoolReader
from backend.infrastructure.persistence.sqlite_ranking_store import SqliteRankingStore


class StubPoolReader:
    def __init__(self, stocks):
        self.stocks = stocks

    def list_liquid(self, limit):
        return self.stocks


class StubSnapshotQuery:
    def __init__(self, results):
        self.results = results
        self.calls = []
        self.lock = Lock()

    def execute(self, secid, timeframe, **kwargs):
        with self.lock:
            self.calls.append((secid, timeframe))
        guidance, summary, *points = self.results[secid]
        return SimpleNamespace(guidance=guidance, summary=summary, signals=SimpleNamespace(points=tuple(points)))


class StubHttpClient:
    def __init__(self, items=None):
        self.items = items if items is not None else [
            {"f12": "601939", "f13": 1, "f14": "建设银行"},
            {"f12": "510300", "f13": 1, "f14": "沪深300ETF"},
        ]

    def get(self, url, params):
        return {"data": {"diff": self.items, "total": len(self.items)}}


class NullDataClient:
    def get(self, url, params):
        return {"data": None}


class RankingTests(unittest.TestCase):
    def test_stock_pool_preserves_numeric_zero_market_for_shenzhen_stock_and_etf(self):
        items = [
            {"f12": "000001", "f13": 0, "f14": "平安银行"},
            {"f12": "159915", "f13": 0, "f14": "创业板ETF"},
            {"f12": "600519", "f13": 1, "f14": "贵州茅台"},
            {"f12": "510300", "f13": 1, "f14": "沪深300ETF"},
        ]

        stocks = EastmoneyStockPoolReader(StubHttpClient(items)).list_liquid(40)

        self.assertEqual(stocks, [
            Stock("0.000001", "000001", "平安银行", "深A"),
            Stock("0.159915", "159915", "创业板ETF", "ETF"),
            Stock("1.600519", "600519", "贵州茅台", "沪A"),
            Stock("1.510300", "510300", "沪深300ETF", "ETF"),
        ])

    def test_stock_pool_rejects_missing_and_unsupported_markets(self):
        for market in (None, "", 2, 116):
            with self.subTest(market=market):
                items = [{"f12": "000001", "f13": market, "f14": "证券"}]
                with self.assertRaises(MarketDataNotFound):
                    EastmoneyStockPoolReader(StubHttpClient(items)).list_liquid(40)

    def test_all_40_candidates_are_analyzed_even_when_output_limit_is_six(self):
        items = [
            {"f12": f"{prefix}{index:03d}", "f13": market, "f14": f"候选{prefix}{index}"}
            for index in range(1, 11)
            for prefix, market in (("600", 1), ("000", 0), ("510", 1), ("159", 0))
        ]
        guidance = SimpleNamespace(bias="bullish", strength=3, invalidation_price=9)
        results = {
            f"{item['f13']}.{item['f12']}": (guidance, SimpleNamespace(latest=10, change_percent=1))
            for item in items
        }
        snapshots = StubSnapshotQuery(results)

        with tempfile.TemporaryDirectory() as directory:
            store = SqliteRankingStore(Path(directory) / "rankings.db")
            BuildDailyRankingsCommand(EastmoneyStockPoolReader(StubHttpClient(items)), snapshots, store, store).execute(date(2026, 11, 3))
            board = store.read().board

        self.assertEqual(len(snapshots.calls), 40)
        self.assertCountEqual(snapshots.calls, [(secid, "1d") for secid in results])
        self.assertEqual(len(board.bullish), 40)

    def test_stock_pool_null_data_is_not_found(self):
        with self.assertRaises(MarketDataNotFound):
            EastmoneyStockPoolReader(NullDataClient()).list_liquid(40)

    def test_aligned_point_breaks_strength_tie(self):
        stocks = [Stock(f"1.60000{index}", f"60000{index}", f"股票{index}", "沪A") for index in range(2)]
        guidance = SimpleNamespace(bias="bullish", strength=3, invalidation_price=9)
        results = {
            "1.600000": (guidance, SimpleNamespace(latest=10, change_percent=5.0), SimpleNamespace(side="sell")),
            "1.600001": (guidance, SimpleNamespace(latest=10, change_percent=-1.0), SimpleNamespace(side="buy")),
        }
        entries = [RankingEntry(0, stock, "bullish", 3, 10, results[stock.secid][1].change_percent, 9, results[stock.secid][2]) for stock in stocks]
        self.assertEqual([entry.stock.secid for entry in rank_entries(entries, "bullish", 6)], ["1.600001", "1.600000"])

    def test_stock_pool_is_converted_to_domain_stocks(self):
        stocks = EastmoneyStockPoolReader(StubHttpClient()).list_liquid(2)

        self.assertEqual(stocks[0].secid, "1.601939")
        self.assertEqual(stocks[0].market, "沪A")
        self.assertEqual(stocks[1].secid, "1.510300")
        self.assertEqual(stocks[1].market, "ETF")

    def test_rankings_sort_by_strength_and_filter_neutral(self):
        stocks = [Stock(f"1.60000{index}", f"60000{index}", f"股票{index}", "沪A") for index in range(4)]
        results = {
            "1.600000": (SimpleNamespace(bias="bullish", strength=2, invalidation_price=9), SimpleNamespace(latest=10, change_percent=1.0)),
            "1.600001": (SimpleNamespace(bias="bullish", strength=4, invalidation_price=19), SimpleNamespace(latest=20, change_percent=-0.5)),
            "1.600002": (SimpleNamespace(bias="bearish", strength=3, invalidation_price=29), SimpleNamespace(latest=30, change_percent=-2.0)),
            "1.600003": (SimpleNamespace(bias="neutral", strength=0, invalidation_price=None), SimpleNamespace(latest=40, change_percent=0)),
        }
        entries = [RankingEntry(0, stock, results[stock.secid][0].bias, results[stock.secid][0].strength, results[stock.secid][1].latest, results[stock.secid][1].change_percent, results[stock.secid][0].invalidation_price) for stock in stocks]
        bullish = rank_entries(entries, "bullish", 6)
        self.assertEqual([entry.stock.secid for entry in bullish], ["1.600001", "1.600000"])
        self.assertEqual([entry.rank for entry in bullish], [1, 2])
        self.assertEqual([entry.stock.secid for entry in rank_entries(entries, "bearish", 6)], ["1.600002"])

    def test_actionable_recent_point_ranks_ahead_of_overextended_old_point(self):
        from datetime import datetime, timezone
        from backend.domain.models import BuySellPoint, Candle
        from backend.domain.rankings import build_ranking_entry

        stocks = [
            Stock("1.688220", "688220", "已追高", "沪A"),
            Stock("1.688410", "688410", "刚买点", "沪A"),
        ]
        chased_point = BuySellPoint(1, "buy", 1, 0, datetime(2026, 9, 10, tzinfo=timezone.utc), 80.0, True, "一买")
        fresh_point = BuySellPoint(3, "buy", 2, 1, datetime(2026, 11, 3, tzinfo=timezone.utc), 10.0, False, "三买")
        prior_sell = BuySellPoint(3, "sell", 1, 0, datetime(2026, 9, 23, tzinfo=timezone.utc), 9.9, True, "三卖")
        candles = (
            Candle(datetime(2026, 9, 23, tzinfo=timezone.utc), 9.8, 9.7, 9.9, 9.6, 100),
            Candle(datetime(2026, 11, 3, tzinfo=timezone.utc), 10.1, 10.2, 10.3, 10.0, 100),
            Candle(datetime(2026, 11, 4, tzinfo=timezone.utc), 10.2, 10.36, 10.4, 10.1, 100),
        )
        chased = RankingEntry(0, stocks[0], "bullish", 4, 109.75, 16.93, 92.8, chased_point, buy_point=chased_point)
        fresh = build_ranking_entry(
            stocks[1],
            SimpleNamespace(
                guidance=SimpleNamespace(bias="bullish", strength=4, invalidation_price=9.93),
                summary=SimpleNamespace(latest=10.36, change_percent=2.07),
                signals=SimpleNamespace(points=(prior_sell, fresh_point)),
                candles=candles,
            ),
        )
        ranked = rank_entries([chased, fresh], "bullish", 6)
        self.assertEqual([entry.stock.secid for entry in ranked], ["1.688410", "1.688220"])
        self.assertEqual(fresh.buy_point, fresh_point)
        self.assertEqual(fresh.sell_point, prior_sell)
        self.assertEqual([point.price for point in fresh.trend], [9.9, 10.0, 10.36])

        strength3_actionable = RankingEntry(
            0,
            Stock("1.560570", "560570", "A500红利ETF国联安", "ETF"),
            "bullish",
            3,
            1.048,
            -1.0,
            1.010,
            BuySellPoint(2, "buy", 1, 0, datetime(2026, 10, 26, tzinfo=timezone.utc), 1.015, True, "二买"),
        )
        strength4_unaligned = RankingEntry(
            0,
            Stock("1.512290", "512290", "生物医药ETF国泰", "ETF"),
            "bullish",
            4,
            1.015,
            3.0,
            1.006,
            BuySellPoint(3, "sell", 1, 0, datetime(2026, 4, 2, tzinfo=timezone.utc), 1.006, True, "三卖"),
        )
        all_ranked = rank_entries([strength4_unaligned, chased, strength3_actionable, fresh], "bullish", 6)
        self.assertEqual(
            [entry.stock.secid for entry in all_ranked],
            ["1.688410", "1.560570", "1.688220", "1.512290"],
        )


if __name__ == "__main__":
    unittest.main()
