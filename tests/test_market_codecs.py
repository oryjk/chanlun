from datetime import date, datetime, timezone
from types import SimpleNamespace
import unittest

from backend.domain.models import Candle, Instrument, MarketData
from backend.infrastructure.market.closed_trading_day import IndexClosedTradingDayReader
from backend.infrastructure.market.eastmoney_codes import from_eastmoney, to_eastmoney
from backend.infrastructure.market.eastmoney_stock_search import EastmoneyStockSearchReader
from backend.infrastructure.market.sina_market_data import SinaMarketDataReader


class StubHttpClient:
    def __init__(self, payload):
        self.payload = payload

    def get(self, url, params):
        return self.payload


def market_data_with_day() -> MarketData:
    return MarketData(
        instrument=Instrument(
            secid="000001.SH",
            symbol="000001",
            name="上证指数",
            decimal=2,
            data_source="test",
        ),
        candles=(
            Candle(
                time=datetime(2026, 11, 10, tzinfo=timezone.utc),
                open=3000.0,
                close=3010.0,
                high=3020.0,
                low=2990.0,
                volume=100,
            ),
        ),
    )


class MarketCodecTests(unittest.TestCase):
    def test_eastmoney_round_trip(self):
        for canonical, source in (
            ("600519.SH", "1.600519"),
            ("000001.SZ", "0.000001"),
            ("920118.BJ", "0.920118"),
        ):
            self.assertEqual(to_eastmoney(canonical), source)
            self.assertEqual(from_eastmoney(source), canonical)

    def test_eastmoney_unsupported_markets_are_skipped(self):
        for source in ("116.00700", "105.AAPL"):
            self.assertIsNone(from_eastmoney(source))

    def test_sina_symbol_from_canonical_id(self):
        reader = SinaMarketDataReader(StubHttpClient({}))
        self.assertEqual(reader._sina_symbol("600519.SH"), "sh600519")
        self.assertEqual(reader._sina_symbol("920118.BJ"), "bj920118")

    def test_search_returns_canonical_ids_and_drops_hk_and_us(self):
        stub = StubHttpClient(
            {
                "QuotationCodeTable": {
                    "Data": [
                        {
                            "QuoteID": "1.600519",
                            "Code": "600519",
                            "Name": "贵州茅台",
                            "Classify": "AStock",
                            "SecurityTypeName": "沪A",
                        },
                        {
                            "QuoteID": "116.00700",
                            "Code": "00700",
                            "Name": "腾讯控股",
                            "Classify": "HK",
                            "SecurityTypeName": "港股",
                        },
                        {
                            "QuoteID": "105.AAPL",
                            "Code": "AAPL",
                            "Name": "苹果",
                            "Classify": "UsStock",
                            "SecurityTypeName": "美股",
                        },
                    ]
                }
            }
        )
        results = EastmoneyStockSearchReader(stub).search("a")
        self.assertEqual([s.secid for s in results], ["600519.SH"])

    def test_trading_calendar_reads_shanghai_index(self):
        seen = []
        reader = SimpleNamespace(read=lambda secid, tf, **k: seen.append(secid) or market_data_with_day())
        IndexClosedTradingDayReader(reader).latest_on_or_before(date(2026, 11, 10))
        self.assertEqual(seen, ["000001.SH"])


if __name__ == "__main__":
    unittest.main()
