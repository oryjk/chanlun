import unittest
from datetime import datetime, timedelta, timezone

from backend.application.queries.get_market_snapshot import GetMarketSnapshotQuery
from backend.domain.models import Candle, Fractal, Instrument, MarketData, Pen
from backend.domain.services.chan_analyzer import ChanAnalyzer
from backend.domain.services.chan_signals import ChanSignalAnalyzer
from backend.domain.services.market_analysis import MarketAnalysisPipeline
from backend.domain.services.signal_quality import SignalQualityEvaluator
from backend.domain.services.structure_guidance import StructureGuidanceEvaluator


START = datetime(2024, 1, 1, tzinfo=timezone.utc)


def make_candles(centers):
    return tuple(
        Candle(
            time=START + timedelta(hours=index),
            open=price,
            close=price,
            high=price + 1,
            low=price - 1,
            volume=100,
        )
        for index, price in enumerate(centers)
    )


def make_pens(prices, last_confirmed=True):
    return [
        Pen(
            id=index + 1,
            direction="up" if prices[index + 1] > prices[index] else "down",
            start_index=index,
            end_index=index + 1,
            start_time=START + timedelta(hours=index),
            end_time=START + timedelta(hours=index + 1),
            start_price=prices[index],
            end_price=prices[index + 1],
            high=max(prices[index], prices[index + 1]),
            low=min(prices[index], prices[index + 1]),
            confirmed=last_confirmed or index < len(prices) - 3,
        )
        for index in range(len(prices) - 1)
    ]


def make_fractal(kind, merged_index, price):
    return Fractal(
        type=kind,
        merged_index=merged_index,
        candle_index=merged_index,
        time=START + timedelta(hours=merged_index),
        price=price,
    )


class PenTests(unittest.TestCase):
    def setUp(self):
        self.analyzer = ChanAnalyzer()

    def test_rejected_candidate_keeps_internal_extreme(self):
        result = self.analyzer.analyze(
            make_candles([101, 100, 105, 110, 115, 120, 90, 110, 115, 112, 105, 110])
        )

        self.assertEqual([(pen.start_price, pen.end_price) for pen in result.pens], [(99, 121), (121, 89)])
        self.assertEqual(result.pens[-1].low, 89)
        self.assertTrue(result.pens[0].confirmed)
        self.assertFalse(result.pens[-1].confirmed)

    def test_opposite_fractals_require_independent_candle(self):
        fractals = [make_fractal("top", 1, 120), make_fractal("bottom", 2, 90)]

        pens, selected = self.analyzer._build_pens(fractals)

        self.assertEqual(pens, [])
        self.assertEqual(len(selected), 1)

    def test_more_extreme_same_type_fractal_replaces_endpoint(self):
        fractals = [
            make_fractal("bottom", 0, 100),
            make_fractal("top", 4, 120),
            make_fractal("top", 6, 125),
            make_fractal("bottom", 10, 110),
        ]

        pens, selected = self.analyzer._build_pens(fractals)

        self.assertEqual([fractal.price for fractal in selected], [100, 125, 110])
        self.assertEqual(pens[0].end_price, 125)
        self.assertTrue(pens[0].confirmed)
        self.assertFalse(pens[-1].confirmed)


class ZhongshuTests(unittest.TestCase):
    def setUp(self):
        self.analyzer = ChanAnalyzer()

    def test_extension_does_not_shrink_core(self):
        zones = self.analyzer._build_zhongshu(make_pens([100, 120, 105, 115, 108]))

        self.assertEqual(len(zones), 1)
        self.assertEqual((zones[0].low, zones[0].high), (105, 115))
        self.assertEqual((zones[0].dd, zones[0].gg), (100, 120))
        self.assertEqual(zones[0].end_index, 4)
        self.assertFalse(zones[0].confirmed)

    def test_leave_and_new_zone_do_not_overlap_or_repeat(self):
        zones = self.analyzer._build_zhongshu(
            make_pens([100, 120, 105, 115, 108, 130, 120, 135, 125, 140])
        )

        self.assertEqual(len(zones), 2)
        self.assertEqual((zones[0].low, zones[0].high), (105, 115))
        self.assertEqual((zones[0].dd, zones[0].gg), (100, 130))
        self.assertTrue(zones[0].confirmed)
        self.assertEqual(zones[0].end_index, 5)
        self.assertEqual((zones[1].low, zones[1].high), (125, 130))
        self.assertEqual(zones[1].start_index, 5)
        self.assertFalse(zones[1].confirmed)

    def test_touching_core_boundary_without_overlap_ends_extension(self):
        zones = self.analyzer._build_zhongshu(make_pens([100, 120, 105, 115, 120]))

        self.assertEqual(len(zones), 1)
        self.assertEqual(zones[0].end_index, 3)
        self.assertTrue(zones[0].confirmed)


class SegmentTests(unittest.TestCase):
    def setUp(self):
        self.analyzer = ChanAnalyzer()

    def test_three_overlapping_pens_are_only_a_candidate(self):
        segments = self.analyzer._build_segments(make_pens([100, 120, 110, 130]))

        self.assertEqual(len(segments), 1)
        self.assertFalse(segments[0].confirmed)

    def test_three_pens_without_overlap_do_not_form_a_segment(self):
        segments = self.analyzer._build_segments(make_pens([100, 120, 110, 105]))

        self.assertEqual(segments, [])

    def test_feature_top_without_gap_confirms_up_segment(self):
        segments = self.analyzer._build_segments(
            make_pens([100, 120, 110, 125, 115, 118, 112])
        )

        self.assertEqual(segments[0].direction, "up")
        self.assertEqual((segments[0].start_price, segments[0].end_price), (100, 125))
        self.assertEqual((segments[0].start_pen_index, segments[0].end_pen_index), (0, 3))
        self.assertTrue(segments[0].confirmed)
        self.assertFalse(segments[-1].confirmed)

    def test_feature_bottom_without_gap_confirms_down_segment(self):
        segments = self.analyzer._build_segments(
            make_pens([100, 80, 90, 75, 85, 82, 88])
        )

        self.assertEqual(segments[0].direction, "down")
        self.assertEqual((segments[0].start_price, segments[0].end_price), (100, 75))
        self.assertTrue(segments[0].confirmed)

    def test_feature_inclusion_is_processed_before_confirmation(self):
        segments = self.analyzer._build_segments(
            make_pens([100, 125, 110, 120, 115, 130, 120, 126, 118])
        )

        self.assertEqual(segments[0].end_price, 130)
        self.assertEqual(segments[0].end_pen_index, 5)
        self.assertTrue(segments[0].confirmed)

    def test_gap_requires_opposite_feature_fractal(self):
        prices = [100, 120, 110, 135, 125, 130]
        unconfirmed = self.analyzer._build_segments(make_pens(prices))
        self.assertFalse(any(segment.confirmed for segment in unconfirmed))

        confirmed = self.analyzer._build_segments(
            make_pens(prices + [120, 122, 121, 123])
        )
        self.assertEqual(confirmed[0].end_price, 135)
        self.assertEqual(confirmed[0].end_pen_index, 3)
        self.assertTrue(confirmed[0].confirmed)

    def test_last_candidate_pen_cannot_confirm_a_segment(self):
        segments = self.analyzer._build_segments(
            make_pens([100, 120, 110, 125, 115, 118, 112], last_confirmed=False)
        )

        self.assertFalse(any(segment.confirmed for segment in segments))


class SnapshotSummaryTests(unittest.TestCase):
    def test_intraday_snapshot_uses_previous_trading_day_close_for_daily_change(self):
        candles = (
            Candle(datetime(2026, 11, 4, 14, 30, tzinfo=timezone.utc), 99.0, 99.5, 100.0, 98.5, 100),
            Candle(datetime(2026, 11, 4, 15, 0, tzinfo=timezone.utc), 99.5, 100.0, 100.5, 99.0, 100),
            Candle(datetime(2026, 11, 5, 10, 0, tzinfo=timezone.utc), 100.0, 102.0, 102.5, 99.8, 100),
            Candle(datetime(2026, 11, 5, 10, 30, tzinfo=timezone.utc), 102.0, 105.0, 105.5, 101.8, 100),
        )
        instrument = Instrument("1.600519", "600519", "贵州茅台", 2, "test")

        class StubMarketReader:
            def read(self, secid, timeframe, force_refresh=False):
                return MarketData(instrument=instrument, candles=candles)

        class FixedClock:
            def __init__(self, value):
                self.value = value

            def now(self):
                return self.value

        query = GetMarketSnapshotQuery(
            StubMarketReader(),
            MarketAnalysisPipeline(
                ChanAnalyzer(),
                StructureGuidanceEvaluator(),
                ChanSignalAnalyzer(),
                SignalQualityEvaluator(),
            ),
            FixedClock(START),
        )
        snapshot = query.execute("1.600519", "30m")

        self.assertEqual(snapshot.summary.latest, 105.0)
        self.assertEqual(snapshot.summary.change, 5.0)
        self.assertEqual(snapshot.summary.change_percent, 5.0)


if __name__ == "__main__":
    unittest.main()
