import unittest

from backend.domain.errors import MarketDataUnavailable
from backend.domain.models import Stock, TimeFrame
from backend.infrastructure.market.fallback_market_data import FallbackMarketDataReader
from backend.infrastructure.market.sina_market_data import SinaMarketDataReader


class StubTextClient:
    def get_text(self, url, params):
        return 'var _data=([{"day":"2026-11-02","open":"33.64","high":"34.00","low":"33.00","close":"33.90","volume":"1000"},{"day":"2026-11-03","open":"33.90","high":"34.20","low":"33.70","close":"34.10","volume":"1200"}]);'


class BrokenTextClient:
    def __init__(self, text):
        self.text = text

    def get_text(self, url, params):
        return self.text


class StubSearch:
    def search(self, query):
        return [Stock("1.510300", "510300", "沪深300ETF", "ETF"), Stock("0.510300", "510300", "其他", "深A")]


class FailingSearch:
    def search(self, query):
        raise MarketDataUnavailable("unavailable")


class StubReader:
    def read(self, secid, timeframe, force_refresh=False):
        return "fallback"


class FailingReader:
    def read(self, secid, timeframe, force_refresh=False):
        raise MarketDataUnavailable("unavailable")


class MarketDataFallbackTests(unittest.TestCase):
    def test_sina_jsonp_is_converted_to_candles(self):
        data = SinaMarketDataReader(StubTextClient()).read("0.301162", TimeFrame.ONE_DAY)

        self.assertEqual(len(data.candles), 2)
        self.assertEqual(data.candles[-1].close, 34.10)
        self.assertEqual(data.instrument.data_source, "新浪财经 · 不复权（备用）")
        self.assertEqual(data.instrument.name, "301162")

    def test_malformed_sina_payload_is_unavailable(self):
        for text in ('var _data=([{"day":"2026-11-02"}]);', 'var _data=([oops]);', 'var _data=([1, 2]);'):
            with self.assertRaises(MarketDataUnavailable):
                SinaMarketDataReader(BrokenTextClient(text)).read("0.301162", TimeFrame.ONE_DAY)

    def test_sina_name_and_etf_precision_come_from_search(self):
        data = SinaMarketDataReader(StubTextClient(), StubSearch()).read("1.510300", TimeFrame.ONE_DAY)

        self.assertEqual(data.instrument.name, "沪深300ETF")
        self.assertEqual(data.instrument.decimal, 3)

    def test_search_failure_falls_back_to_code(self):
        data = SinaMarketDataReader(StubTextClient(), FailingSearch()).read("1.600519", TimeFrame.ONE_DAY)

        self.assertEqual((data.instrument.name, data.instrument.decimal), ("600519", 2))

    def test_unavailable_primary_uses_fallback(self):
        reader = FallbackMarketDataReader(FailingReader(), StubReader())

        result = reader.read("0.301162", TimeFrame.ONE_DAY)

        self.assertEqual(result, "fallback")

    def test_repeated_primary_failures_skip_primary_during_cooldown(self):
        class CountingFailing:
            calls = 0

            def read(self, secid, timeframe, force_refresh=False):
                CountingFailing.calls += 1
                raise MarketDataUnavailable("down")

        now = [0.0]
        primary = CountingFailing()
        reader = FallbackMarketDataReader(primary, StubReader(), failure_threshold=3, cooldown_seconds=300,
                                          clock=lambda: now[0])
        for _ in range(10):
            self.assertEqual(reader.read("0.301162", TimeFrame.ONE_DAY), "fallback")
        self.assertEqual(primary.calls, 3)
        now[0] = 301
        reader.read("0.301162", TimeFrame.ONE_DAY)
        reader.read("0.301162", TimeFrame.ONE_DAY)
        self.assertEqual(primary.calls, 4)

    def test_primary_success_resets_failure_count(self):
        class Flaky:
            def __init__(self):
                self.results = iter(["fail", "fail", "ok", "fail", "fail", "ok"])

            def read(self, secid, timeframe, force_refresh=False):
                if next(self.results) == "fail":
                    raise MarketDataUnavailable("down")
                return "primary"

        reader = FallbackMarketDataReader(Flaky(), StubReader(), failure_threshold=3, clock=lambda: 0.0)
        results = [reader.read("0.301162", TimeFrame.ONE_DAY) for _ in range(6)]
        self.assertEqual(results, ["fallback", "fallback", "primary", "fallback", "fallback", "primary"])


if __name__ == "__main__":
    unittest.main()
