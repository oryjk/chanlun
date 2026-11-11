from datetime import date, datetime, timedelta, timezone
from types import SimpleNamespace
import unittest
from zoneinfo import ZoneInfo

from backend.application.queries.get_market_snapshot import GetMarketSnapshotQuery
from backend.domain.errors import MarketDataNotFound
from backend.domain.models import Candle, Instrument, MarketData
from backend.domain.services.chan_analyzer import ChanAnalyzer
from backend.domain.services.chan_signals import ChanSignalAnalyzer
from backend.domain.services.market_analysis import MarketAnalysisPipeline, close_window
from backend.domain.services.signal_quality import SignalQualityEvaluator
from backend.domain.services.structure_guidance import StructureGuidanceEvaluator
from backend.infrastructure.clock import SystemClock


SHANGHAI = ZoneInfo("Asia/Shanghai")
INSTRUMENT = Instrument("1.600000", "600000", "浦发银行", 2, "沪A")


class FixedClock:
    def __init__(self, value: datetime):
        self.value = value

    def now(self) -> datetime:
        return self.value


def pipeline() -> MarketAnalysisPipeline:
    return MarketAnalysisPipeline(
        ChanAnalyzer(),
        StructureGuidanceEvaluator(),
        ChanSignalAnalyzer(),
        SignalQualityEvaluator(),
    )


def market_data(*, days: list[str]) -> MarketData:
    candles = tuple(
        Candle(
            time=datetime.fromisoformat(f"{day}T00:00:00").replace(tzinfo=SHANGHAI),
            open=10.0 + index,
            close=10.0 + index,
            high=11.0 + index,
            low=9.0 + index,
            volume=100,
        )
        for index, day in enumerate(days)
    )
    return MarketData(instrument=INSTRUMENT, candles=candles)


def _build_data_with_points() -> MarketData:
    anchors = [90, 80, 100, 90, 96, 92, 110, 104, 108, 105, 115]
    prices: list[float] = [anchors[0]]
    for start, end in zip(anchors, anchors[1:]):
        for step in range(1, 5):
            prices.append(start + (end - start) * step / 4)
    while len(prices) < 120:
        prices.insert(0, 88 + (len(prices) % 2))
    end_day = datetime(2026, 11, 10, tzinfo=SHANGHAI)
    start_day = end_day - timedelta(days=len(prices) - 1)
    candles = tuple(
        Candle(
            time=start_day + timedelta(days=index),
            open=price,
            close=price,
            high=price + 1,
            low=price - 1,
            volume=100,
        )
        for index, price in enumerate(prices)
    )
    return MarketData(instrument=INSTRUMENT, candles=candles)


data_with_points = _build_data_with_points()


class MarketAnalysisTests(unittest.TestCase):
    def test_close_window_drops_later_bars_and_keeps_trade_date(self):
        data = market_data(days=["2026-11-06", "2026-11-09", "2026-11-10"])
        window = close_window(data, date(2026, 11, 9))
        self.assertEqual(window.candles[-1].time.date(), date(2026, 11, 9))
        self.assertEqual(len(window.candles), len(data.candles) - 1)

    def test_close_window_requires_a_bar_on_the_trade_date(self):
        data = market_data(days=["2026-11-06", "2026-11-10"])
        with self.assertRaises(MarketDataNotFound):
            close_window(data, date(2026, 11, 9))

    def test_snapshot_completion_follows_the_injected_clock(self):
        reader = SimpleNamespace(read=lambda *a, **k: data_with_points)
        intraday = datetime(2026, 11, 10, 2, 0, tzinfo=timezone.utc)
        after_close = datetime(2026, 11, 10, 7, 30, tzinfo=timezone.utc)
        q1 = GetMarketSnapshotQuery(reader, pipeline(), FixedClock(intraday)).execute("1.600000", "1d")
        q2 = GetMarketSnapshotQuery(reader, pipeline(), FixedClock(after_close)).execute("1.600000", "1d")
        self.assertEqual(q1.signal_quality.state, "insufficient")
        self.assertNotEqual(q2.signal_quality.reasons[0], "当前 K 线尚未完成，等待收盘后评价")

    def test_system_clock_returns_aware_utc(self):
        now = SystemClock().now()
        self.assertIsNotNone(now.tzinfo)
        self.assertEqual(now.utcoffset(), timedelta(0))


if __name__ == "__main__":
    unittest.main()
