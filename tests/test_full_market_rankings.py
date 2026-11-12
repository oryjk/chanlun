import unittest
from datetime import date, datetime
from types import SimpleNamespace
from zoneinfo import ZoneInfo

from backend.domain.errors import MarketDataNotFound, MarketDataUnavailable
from backend.domain.models import Candle, Instrument, MarketData, TimeFrame
from backend.infrastructure.market.eastmoney_stock_pool import EastmoneyStockPoolReader
from backend.domain.services.chan_analyzer import ChanAnalyzer
from backend.domain.services.chan_signals import ChanSignalAnalyzer
from backend.domain.services.market_analysis import MarketAnalysisPipeline, close_window
from backend.domain.services.signal_quality import SignalQualityEvaluator
from backend.domain.services.structure_guidance import StructureGuidanceEvaluator


class PageClient:
    def __init__(self, pages, total):
        self.pages, self.total, self.calls = pages, total, []

    def get(self, url, params):
        self.calls.append(params)
        return {"data": {"total": self.total, "diff": self.pages.get(params["pn"], [])}}


class FullMarketTests(unittest.TestCase):
    def test_full_universe_is_paginated_and_preserves_shenzhen(self):
        rows = [{"f12": f"000{i:03d}", "f13": 0, "f14": f"股票{i}"} for i in range(1, 102)]
        client = PageClient({1: rows[:100], 2: rows[100:]}, 101)
        stocks = EastmoneyStockPoolReader(client).list_all()
        self.assertEqual(len(stocks), 101)
        self.assertEqual(stocks[-1].secid, "000101.SZ")
        self.assertEqual([call["pn"] for call in client.calls], [1, 2])

    def test_duplicate_or_missing_page_cannot_be_published_as_complete(self):
        row = {"f12": "600519", "f13": 1, "f14": "贵州茅台"}
        for pages in ({1: [row], 2: []}, {1: [row, row]}):
            with self.subTest(pages=pages), self.assertRaises(MarketDataUnavailable):
                EastmoneyStockPoolReader(PageClient(pages, 2)).list_all()

    def test_non_target_products_are_excluded_from_complete_catalog(self):
        rows = [
            {"f12": "600519", "f13": 1, "f14": "贵州茅台"},
            {"f12": "159915", "f13": 0, "f14": "创业板ETF"},
            {"f12": "513100", "f13": 1, "f14": "纳指ETF"},
            {"f12": "501018", "f13": 1, "f14": "南方原油LOF"},
            {"f12": "920001", "f13": 0, "f14": "北交股票"},
            {"f12": "00700", "f13": 116, "f14": "腾讯控股"},
        ]
        stocks = EastmoneyStockPoolReader(PageClient({1: rows}, 6)).list_all()
        self.assertEqual([stock.secid for stock in stocks], ["600519.SH", "159915.SZ", "513100.SH"])
        self.assertEqual(stocks[-1].market, "ETF")

    def test_malformed_market_or_mainland_code_is_not_silently_excluded(self):
        good = {"f12": "600519", "f13": 1, "f14": "贵州茅台"}
        for bad in ({"f12": "000001"}, {"f12": "000001", "f13": None},
                    {"f12": "000001", "f13": "?"}, {"f12": "00001", "f13": 0},
                    {"f12": "60051X", "f13": 1}):
            with self.subTest(bad=bad), self.assertRaises(MarketDataUnavailable):
                EastmoneyStockPoolReader(PageClient({1: [good, bad]}, 2)).list_all()

    def test_close_snapshot_excludes_unfinished_future_candle(self):
        candles = tuple(Candle(datetime(2026, 11, day, tzinfo=ZoneInfo("Asia/Shanghai")), 10, close, close + 1, 9, 100)
                        for day, close in ((2, 10), (3, 11), (4, 20)))
        data = MarketData(Instrument("1.600519", "600519", "贵州茅台", 2, "test"), candles)
        pipeline = MarketAnalysisPipeline(
            ChanAnalyzer(),
            StructureGuidanceEvaluator(),
            ChanSignalAnalyzer(),
            SignalQualityEvaluator(),
        )
        snapshot = pipeline.analyze(close_window(data, date(2026, 11, 3)), TimeFrame.ONE_DAY, as_of=candles[-1].time)
        self.assertEqual(len(snapshot.candles), 2)
        self.assertEqual(snapshot.summary.latest, 11)
        with self.assertRaises(MarketDataNotFound):
            close_window(data, date(2026, 11, 5))


if __name__ == "__main__":
    unittest.main()
