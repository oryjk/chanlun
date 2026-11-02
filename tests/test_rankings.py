import unittest
from types import SimpleNamespace

from backend.application.queries.get_rankings import GetRankingsQuery
from backend.domain.errors import MarketDataNotFound
from backend.domain.models import Stock
from backend.infrastructure.market.eastmoney_stock_pool import EastmoneyStockPoolReader


class StubPoolReader:
    def __init__(self, stocks):
        self.stocks = stocks

    def list_liquid(self, limit):
        return self.stocks


class StubSnapshotQuery:
    def __init__(self, results):
        self.results = results

    def execute(self, secid, timeframe):
        guidance, summary, *points = self.results[secid]
        return SimpleNamespace(guidance=guidance, summary=summary, signals=SimpleNamespace(points=tuple(points)))


class StubHttpClient:
    def get(self, url, params):
        return {
            "data": {
                "diff": [
                    {"f12": "601939", "f13": 1, "f14": "建设银行"},
                    {"f12": "510300", "f13": 1, "f14": "沪深300ETF"},
                ]
            }
        }


class NullDataClient:
    def get(self, url, params):
        return {"data": None}


class RankingTests(unittest.TestCase):
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
        board = GetRankingsQuery(StubPoolReader(stocks), StubSnapshotQuery(results)).execute(6)

        self.assertEqual([entry.stock.secid for entry in board.bullish], ["1.600001", "1.600000"])

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
        board = GetRankingsQuery(StubPoolReader(stocks), StubSnapshotQuery(results)).execute(6)

        self.assertEqual([entry.stock.secid for entry in board.bullish], ["1.600001", "1.600000"])
        self.assertEqual([entry.rank for entry in board.bullish], [1, 2])
        self.assertEqual([entry.stock.secid for entry in board.bearish], ["1.600002"])
        self.assertEqual(board.timeframe.value, "1d")


if __name__ == "__main__":
    unittest.main()
