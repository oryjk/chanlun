import unittest
from datetime import datetime, timedelta, timezone
from types import SimpleNamespace

from backend.application.queries.get_signal_confluence import GetSignalConfluenceQuery
from backend.domain.errors import MarketDataUnavailable
from backend.domain.models import BuySellPoint, ChanSignals, TimeFrame
from backend.domain.services.signal_confluence import evaluate_confluence


START = datetime(2024, 1, 1, tzinfo=timezone.utc)


def point(kind, side, hours):
    return BuySellPoint(
        kind=kind,
        side=side,
        pen_id=1,
        candle_index=0,
        time=START + timedelta(hours=hours),
        price=10,
        confirmed=True,
        reason="",
    )


def signals(*points):
    return ChanSignals(trend_type="consolidation", divergences=(), points=tuple(points))


class ConfluenceTests(unittest.TestCase):
    def test_same_side_lower_point_after_higher_point_confirms(self):
        result = evaluate_confluence(
            "1.600519",
            [
                (TimeFrame.ONE_DAY, signals(point(1, "buy", 0))),
                (TimeFrame.ONE_HOUR, signals(point(3, "sell", -5), point(2, "buy", 3))),
            ],
        )

        self.assertEqual(result.levels[0].lower_confirmation.kind, 2)
        self.assertEqual(result.conclusion, "日线一类买点得到1 小时二类买点印证")

    def test_opposite_or_earlier_lower_points_do_not_confirm(self):
        result = evaluate_confluence(
            "1.600519",
            [
                (TimeFrame.ONE_DAY, signals(point(1, "buy", 10))),
                (TimeFrame.ONE_HOUR, signals(point(2, "buy", 5), point(3, "sell", 12))),
            ],
        )

        self.assertIsNone(result.levels[0].lower_confirmation)
        self.assertIn("暂未形成同向印证", result.conclusion)

    def test_no_points_anywhere(self):
        result = evaluate_confluence("1.600519", [(TimeFrame.ONE_DAY, signals())])

        self.assertEqual(result.conclusion, "各级别均未出现买卖点")


class StubSnapshotQuery:
    def __init__(self, failing):
        self.failing = failing

    def execute(self, secid, timeframe):
        if timeframe in self.failing:
            raise MarketDataUnavailable("行情服务暂时不可用，请稍后重试")
        return SimpleNamespace(signals=signals())


class ConfluenceQueryTests(unittest.TestCase):
    def test_failed_level_is_skipped(self):
        result = GetSignalConfluenceQuery(StubSnapshotQuery({"5m"})).execute("1.600519")

        self.assertEqual([level.timeframe.value for level in result.levels], ["1d", "1h", "30m"])

    def test_all_levels_failing_raises(self):
        query = GetSignalConfluenceQuery(StubSnapshotQuery({"1d", "1h", "30m", "5m"}))

        with self.assertRaises(MarketDataUnavailable):
            query.execute("1.600519")


if __name__ == "__main__":
    unittest.main()
