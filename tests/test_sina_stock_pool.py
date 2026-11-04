import unittest

from backend.domain.errors import MarketDataUnavailable
from backend.domain.models import Stock
from backend.infrastructure.market.fallback_market_data import FallbackStockUniverseReader
from backend.infrastructure.market.sina_stock_pool import SinaStockPoolReader


class StubSinaClient:
    def __init__(self, nodes, counts=None):
        self.nodes = nodes
        self.counts = counts or {node: len(rows) for node, rows in nodes.items()}

    def get_text(self, url, params):
        return f'"{self.counts[params["node"]]}"'

    def get(self, url, params):
        rows = self.nodes[params["node"]]
        start = (params["page"] - 1) * params["num"]
        return rows[start:start + params["num"]]


def rows(*symbols):
    return [{"symbol": symbol, "name": name} for symbol, name in symbols]


class SinaStockPoolTests(unittest.TestCase):
    def nodes(self):
        return {
            "sh_a": rows(("sh600000", "浦发银行"), ("sh688981", "中芯国际")),
            "sz_a": rows(("sz000001", "平安银行"), ("sz300750", "宁德时代"), ("sz200002", "万科B")),
            "etf_hq_fund": rows(("sh510300", "沪深300ETF"), ("sz159915", "创业板ETF"), ("sz160105", "南方积配LOF")),
        }

    def test_lists_sh_sz_equities_and_etfs_with_eastmoney_identities(self):
        stocks = SinaStockPoolReader(StubSinaClient(self.nodes())).list_all()
        self.assertEqual([stock.secid for stock in stocks],
                         ["1.600000", "1.688981", "0.000001", "0.300750", "1.510300", "0.159915"])
        self.assertEqual({stock.secid: stock.market for stock in stocks}["1.510300"], "ETF")
        self.assertEqual({stock.secid: stock.market for stock in stocks}["0.000001"], "深A")

    def test_paginates_beyond_one_page(self):
        many = rows(*((f"sh60{index:04d}", f"股票{index}") for index in range(250)))
        stocks = SinaStockPoolReader(StubSinaClient({"sh_a": many, "sz_a": rows(("sz000001", "平安银行")),
                                                     "etf_hq_fund": rows(("sh510300", "沪深300ETF"))})).list_all()
        self.assertEqual(len(stocks), 252)

    def test_transient_empty_page_is_retried(self):
        client = StubSinaClient(self.nodes())
        original, failures, sleeps = client.get, [None, []], []

        def flaky(url, params):
            if params["node"] == "sz_a" and failures:
                return failures.pop(0)
            return original(url, params)

        client.get = flaky
        stocks = SinaStockPoolReader(client, sleep=sleeps.append).list_all()
        self.assertEqual(len(stocks), 6)
        self.assertEqual(sleeps, [1.0, 2.0])

    def test_persistent_empty_page_is_unavailable(self):
        client = StubSinaClient(self.nodes())
        client.get = lambda url, params: []
        with self.assertRaises(MarketDataUnavailable):
            SinaStockPoolReader(client, sleep=lambda seconds: None).list_all()

    def test_rejects_listing_shorter_than_reported_count(self):
        counts = {"sh_a": 3, "sz_a": 3, "etf_hq_fund": 3}
        with self.assertRaises(MarketDataUnavailable):
            SinaStockPoolReader(StubSinaClient(self.nodes(), counts)).list_all()


class FallbackUniverseTests(unittest.TestCase):
    def test_uses_fallback_only_when_primary_unavailable(self):
        stock = Stock("1.600000", "600000", "浦发银行", "沪A")

        class Down:
            def list_all(self, **kwargs):
                raise MarketDataUnavailable("down")

        class Up:
            def list_all(self, **kwargs):
                return (stock,)

        self.assertEqual(FallbackStockUniverseReader(Down(), Up()).list_all(), (stock,))
        self.assertEqual(FallbackStockUniverseReader(Up(), Down()).list_all(), (stock,))


if __name__ == "__main__":
    unittest.main()
