import unittest
from datetime import date

from backend.domain.errors import MarketDataUnavailable
from backend.domain.models import TimeFrame
from backend.infrastructure.market.sina_adjustment import SinaForwardAdjustment
from backend.infrastructure.market.sina_market_data import SinaMarketDataReader

FACTORS = ('var sh600015qfq={"total":3,"data":[{"d":"2026-07-30", "f":"1.0000000000000000"},'
           '{"d":"2026-01-23", "f":"1.0500000000000000"},{"d":"1900-01-01", "f":"1.2000000000000000"}]}\n'
           '/* opaque trailer */')
KLINE = ('var _data=([{"day":"2026-01-22","open":"6.000","high":"6.000","low":"6.000","close":"6.000","volume":"100"},'
         '{"day":"2026-07-29","open":"7.000","high":"7.350","low":"6.930","close":"7.140","volume":"200"},'
         '{"day":"2026-07-30","open":"6.820","high":"7.040","low":"6.810","close":"7.030","volume":"300"}]);')


class StubClient:
    def __init__(self, factors=FACTORS):
        self.factors = factors
        self.factor_calls = 0

    def get_text(self, url, params):
        if url.endswith("qfq.js"):
            self.factor_calls += 1
            return self.factors
        return KLINE


class SinaForwardAdjustmentTests(unittest.TestCase):
    def test_factor_applies_from_its_date_until_next_factor(self):
        lookup = SinaForwardAdjustment(StubClient(), today=lambda: date(2026, 11, 4)).factor_lookup("sh600015")
        self.assertEqual(lookup(date(2026, 1, 22)), 1.2)
        self.assertEqual(lookup(date(2026, 1, 23)), 1.05)
        self.assertEqual(lookup(date(2026, 7, 29)), 1.05)
        self.assertEqual(lookup(date(2026, 7, 30)), 1.0)
        self.assertEqual(lookup(date(2026, 11, 3)), 1.0)

    def test_candles_are_forward_adjusted_and_latest_price_is_unchanged(self):
        reader = SinaMarketDataReader(StubClient(), adjustment=SinaForwardAdjustment(StubClient()))
        data = reader.read("600015.SH", TimeFrame.ONE_DAY)
        self.assertEqual([candle.close for candle in data.candles], [5.0, 6.8, 7.03])
        self.assertEqual(data.candles[1].high, 7.0)
        self.assertEqual(data.candles[1].volume, 200)
        self.assertIn("前复权", data.instrument.data_source)

    def test_factors_are_cached_per_day(self):
        client = StubClient()
        today = [date(2026, 11, 4)]
        adjustment = SinaForwardAdjustment(client, today=lambda: today[0])
        adjustment.factor_lookup("sh600015")
        adjustment.factor_lookup("sh600015")
        self.assertEqual(client.factor_calls, 1)
        today[0] = date(2026, 11, 5)
        adjustment.factor_lookup("sh600015")
        self.assertEqual(client.factor_calls, 2)

    def test_malformed_factors_are_unavailable_instead_of_silently_unadjusted(self):
        reader = SinaMarketDataReader(StubClient(), adjustment=SinaForwardAdjustment(StubClient("/* blocked */")))
        with self.assertRaises(MarketDataUnavailable):
            reader.read("600015.SH", TimeFrame.ONE_DAY)


if __name__ == "__main__":
    unittest.main()
