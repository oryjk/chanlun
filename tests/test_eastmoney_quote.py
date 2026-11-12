import unittest
from datetime import datetime, timezone

from backend.domain.errors import MarketDataNotFound
from backend.infrastructure.market.eastmoney_quote import EastmoneyQuoteReader


class StubHttpClient:
    def __init__(self, payload):
        self.payload = payload

    def get(self, url, params):
        return self.payload


class EastmoneyQuoteTests(unittest.TestCase):
    def test_quote_is_scaled_and_converted(self):
        reader = EastmoneyQuoteReader(
            StubHttpClient(
                {
                    "data": {
                        "f43": 3377,
                        "f44": 3400,
                        "f45": 3276,
                        "f46": 3364,
                        "f47": 6778,
                        "f48": 22531663.8,
                        "f57": "301162",
                        "f58": "国能日新",
                        "f59": 2,
                        "f60": 3329,
                        "f86": 1793586339,
                        "f169": 48,
                        "f170": 144,
                    }
                }
            )
        )

        quote = reader.read_quote("301162.SZ")

        self.assertEqual(quote.latest, 33.77)
        self.assertEqual(quote.open, 33.64)
        self.assertEqual(quote.high, 34.00)
        self.assertEqual(quote.low, 32.76)
        self.assertEqual(quote.previous_close, 33.29)
        self.assertEqual(quote.change, 0.48)
        self.assertEqual(quote.change_percent, 1.44)
        self.assertEqual(quote.volume, 677800)
        self.assertEqual(quote.amount, 22531663.8)
        self.assertEqual(quote.time, datetime.fromtimestamp(1793586339, timezone.utc))

    def test_missing_quote_raises_not_found(self):
        reader = EastmoneyQuoteReader(StubHttpClient({"data": None}))

        with self.assertRaises(MarketDataNotFound):
            reader.read_quote("301162.SZ")


if __name__ == "__main__":
    unittest.main()
