import unittest
from datetime import datetime, timedelta, timezone

from backend.domain.models import QuotePoint, QuoteSeries
from backend.domain.services.quote_series import merge_quote_series
from backend.infrastructure.market.eastmoney_intraday import EastmoneyIntradayReader


class StubHttpClient:
    def get(self, url, params):
        return {
            "data": {
                "market": 0,
                "trends": [
                    "2026-11-02 09:30,33.64",
                    "2026-11-02 09:31,33.92",
                ],
            }
        }


class QuoteSeriesTests(unittest.TestCase):
    def test_intraday_response_is_converted(self):
        series = EastmoneyIntradayReader(StubHttpClient()).read_intraday("0.301162")

        self.assertEqual(len(series.points), 2)
        self.assertEqual(series.points[0].price, 33.64)
        self.assertEqual(series.points[0].time, datetime(2026, 11, 2, 1, 30, tzinfo=timezone.utc))

    def test_merge_sorts_and_uses_latest_sample_for_duplicate_time(self):
        timestamp = datetime(2026, 11, 2, 1, 30, tzinfo=timezone.utc)
        history = QuoteSeries(
            secid="0.301162",
            points=(QuotePoint(timestamp, 33.64),),
        )
        local = QuoteSeries(
            secid="0.301162",
            points=(
                QuotePoint(timestamp + timedelta(seconds=3), 33.80),
                QuotePoint(timestamp, 33.70),
            ),
        )

        merged = merge_quote_series(history, local)

        self.assertEqual(len(merged.points), 2)
        self.assertEqual([point.price for point in merged.points], [33.70, 33.80])


if __name__ == "__main__":
    unittest.main()
