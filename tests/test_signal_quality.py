import unittest
from dataclasses import replace
from datetime import datetime, timedelta, timezone

from backend.domain.models import (
    BuySellPoint, Candle, ChanAnalysis, ChanSignals, MacdPoint, Pen, TimeFrame,
)
from backend.domain.services.signal_quality import SignalQualityEvaluator


START = datetime(2026, 9, 1, tzinfo=timezone.utc)
AS_OF = START + timedelta(days=40)


def fixture(*, confirmed=True, close=10.5, boundary=10.0):
    candles = tuple(
        Candle(START + timedelta(days=i), 10.5, close if i == 24 else 10.5,
               11, 10, 200 if i in (21, 22) else 80 if i in (23, 24) else 100)
        for i in range(25)
    )
    pens = (
        Pen(1, 'up', 20, 22, candles[20].time, candles[22].time, 10, 11, 11, 10, True),
        Pen(2, 'down', 22, 24, candles[22].time, candles[24].time, 11, 10.4, 11, 10.4, confirmed),
    )
    point = BuySellPoint(3, 'buy', 2, 24, candles[24].time, 10.4, confirmed, 'test',
                         invalidation_price=boundary, source_zone_id=1)
    analysis = ChanAnalysis((), pens, (), (), 25, 'down')
    macd = tuple(MacdPoint(.3, .1, .4) for _ in candles)
    return candles, analysis, macd, ChanSignals('up_trend', (), (point,))


class SignalQualityTests(unittest.TestCase):
    def evaluate(self, data, **kwargs):
        return SignalQualityEvaluator().evaluate(*data, timeframe=TimeFrame.ONE_DAY, as_of=AS_OF, **kwargs)

    def recovered(self, *, side='buy', crossed_close=9.8):
        data = fixture(boundary=10 if side == 'buy' else 11)
        point = replace(data[-1].points[0], side=side)
        crossed = replace(data[0][-1], time=START + timedelta(days=25), close=crossed_close,
                          low=min(9.7, crossed_close), high=max(11.5, crossed_close))
        recovered = replace(data[0][-1], time=START + timedelta(days=26), close=10.5)
        return ((*data[0], crossed, recovered), data[1], (*data[2], data[2][-1], data[2][-1]),
                replace(data[-1], points=(point,)))

    def test_buy_reference_crossed_midway_stays_invalidated_after_price_recovers(self):
        quality = self.evaluate(self.recovered())
        self.assertEqual(quality.state, 'invalidated')
        self.assertEqual(quality.history_status, 'invalidated')
        self.assertEqual(quality.first_reference_crossed_at, START + timedelta(days=25))
        self.assertEqual(quality.first_reference_crossed_close, 9.8)

    def test_sell_reference_crossed_midway_stays_invalidated_after_price_recovers(self):
        quality = self.evaluate(self.recovered(side='sell', crossed_close=11.3))
        self.assertEqual(quality.state, 'invalidated')
        self.assertEqual(quality.first_reference_crossed_at, START + timedelta(days=25))
        self.assertEqual(quality.first_reference_crossed_close, 11.3)

    def test_historical_equality_and_intrabar_cross_do_not_count_as_close_invalidation(self):
        quality = self.evaluate(self.recovered(crossed_close=10))
        self.assertEqual(quality.state, 'confirmed')
        self.assertEqual(quality.history_status, 'checked_from_pivot')
        self.assertIsNone(quality.first_reference_crossed_at)

    def test_unfinished_cross_is_excluded_but_a_known_earlier_cross_is_not_forgotten(self):
        data = self.recovered()
        quality = SignalQualityEvaluator().evaluate(*data, timeframe=TimeFrame.ONE_DAY,
                    as_of=data[0][-1].time + timedelta(hours=3))
        self.assertEqual(quality.state, 'invalidated')
        self.assertEqual(quality.first_reference_crossed_at, START + timedelta(days=25))
        prefix = (data[0][:-1], data[1], data[2][:-1], data[3])
        pending = SignalQualityEvaluator().evaluate(*prefix, timeframe=TimeFrame.ONE_DAY,
                    as_of=prefix[0][-1].time + timedelta(hours=3))
        self.assertEqual(pending.state, 'insufficient')
        self.assertIsNone(pending.first_reference_crossed_at)

    def test_unknown_pivot_interval_never_claims_history_checked_or_confirmed_eligibility(self):
        data = fixture()
        unknown = replace(data[-1], points=(replace(data[-1].points[0], candle_index=999),))
        quality = self.evaluate((*data[:-1], unknown))
        self.assertEqual(quality.state, 'insufficient')
        self.assertEqual(quality.history_status, 'unknown')

    def test_a_close_before_the_pivot_does_not_invalidate_the_later_signal(self):
        data = fixture()
        candles = tuple(replace(c, close=9.8) if i == 10 else c for i, c in enumerate(data[0]))
        quality = self.evaluate((candles, *data[1:]))
        self.assertEqual(quality.state, 'confirmed')
        self.assertEqual(quality.history_status, 'checked_from_pivot')
        self.assertIsNone(quality.first_reference_crossed_at)

    def test_pivot_age_counts_full_completed_candles_not_calendar_days_or_trend_samples(self):
        data = fixture()
        candles = (*data[0][:-1], replace(data[0][-1], time=data[0][-1].time + timedelta(days=14)))
        point = replace(data[-1].points[0], candle_index=20, time=data[0][20].time)
        result = self.evaluate((candles, *data[1:-1], replace(data[-1], points=(point,))))
        self.assertEqual(result.bars_since_pivot, 4)

    def test_pivot_age_excludes_the_unfinished_latest_bar_and_does_not_invent_unknown_indices(self):
        data = fixture()
        point = replace(data[-1].points[0], candle_index=20, time=data[0][20].time)
        signals = replace(data[-1], points=(point,))
        result = SignalQualityEvaluator().evaluate(*data[:-1], signals, timeframe=TimeFrame.ONE_DAY,
                    as_of=data[0][-1].time + timedelta(hours=3))
        self.assertEqual(result.bars_since_pivot, 3)
        unknown = replace(signals, points=(replace(point, candle_index=999),))
        self.assertIsNone(self.evaluate((*data[:-1], unknown)).bars_since_pivot)
        mismatched = replace(signals, points=(replace(point, time=point.time + timedelta(days=1)),))
        self.assertIsNone(self.evaluate((*data[:-1], mismatched)).bars_since_pivot)


    def test_candidate_stays_observation_even_with_positive_momentum_and_volume(self):
        quality = self.evaluate(fixture(confirmed=False))
        self.assertEqual(quality.state, 'candidate')
        self.assertEqual(quality.momentum_status, 'unvalidated')
        self.assertEqual(quality.volume_status, 'unvalidated')

    def test_confirmed_point_reports_hand_checked_volume_ratios_without_claiming_success(self):
        quality = self.evaluate(fixture())
        self.assertEqual(quality.state, 'confirmed')
        self.assertEqual(quality.invalidation_price, 10)
        self.assertEqual(quality.breakout_volume_ratio, 2)
        self.assertEqual(quality.pullback_volume_ratio, .4)
        self.assertEqual(quality.macd_histogram, .4)
        self.assertEqual(quality.history_status, 'checked_from_pivot')
        self.assertIsNone(quality.first_detected_at)
        self.assertIsNone(quality.confirmed_at)

    def test_close_strictly_beyond_signal_reference_invalidates_but_equality_does_not(self):
        self.assertEqual(self.evaluate(fixture(close=9.99)).state, 'invalidated')
        self.assertEqual(self.evaluate(fixture(close=10)).state, 'confirmed')
        data = fixture(close=10.01)
        point = replace(data[-1].points[0], side='sell')
        self.assertEqual(self.evaluate((*data[:-1], replace(data[-1], points=(point,)))).state, 'invalidated')

    def test_missing_reference_never_falls_back_to_a_different_current_zone(self):
        quality = self.evaluate(fixture(boundary=None))
        self.assertEqual(quality.state, 'insufficient')
        self.assertIsNone(quality.invalidation_price)

    def test_zero_or_short_volume_baseline_is_unknown_not_support(self):
        data = fixture()
        zero = tuple(replace(c, volume=0) if i <= 20 else c for i, c in enumerate(data[0]))
        quality = self.evaluate((zero, *data[1:]))
        self.assertEqual(quality.volume_status, 'insufficient')
        self.assertIsNone(quality.breakout_volume_ratio)
        short_pens = tuple(replace(p, start_index=p.start_index - 10, end_index=p.end_index - 10) for p in data[1].pens)
        short = (data[0][10:], replace(data[1], pens=short_pens), data[2][10:], data[3])
        self.assertEqual(self.evaluate(short).volume_status, 'insufficient')

    def test_only_latest_point_is_assessed_without_reusing_an_older_confirmed_point(self):
        data = fixture(confirmed=False)
        old = replace(data[-1].points[0], confirmed=True, candle_index=20)
        signals = replace(data[-1], points=(old, data[-1].points[0]))
        self.assertEqual(self.evaluate((*data[:-1], signals)).state, 'candidate')
        self.assertIsNone(self.evaluate((*data[:-1], replace(signals, points=()))))

    def test_unfinished_daily_candle_cannot_produce_a_confirmed_opportunity(self):
        data = fixture()
        quality = SignalQualityEvaluator().evaluate(*data, timeframe=TimeFrame.ONE_DAY,
                    as_of=data[0][-1].time + timedelta(hours=3))
        self.assertEqual(quality.state, 'insufficient')

    def test_intraday_volume_is_not_compared_against_unmatched_time_slots(self):
        quality = SignalQualityEvaluator().evaluate(*fixture(), timeframe=TimeFrame.FIVE_MINUTES, as_of=AS_OF)
        self.assertEqual(quality.volume_status, 'insufficient')
        self.assertIsNone(quality.breakout_volume_ratio)

    def test_minute_bars_use_source_close_timestamp_not_an_extra_period_delay(self):
        data = fixture()
        close_time = datetime.fromisoformat('2026-11-09T15:00:00+08:00')
        candles = (*data[0][:-1], replace(data[0][-1], time=close_time))
        signals = replace(data[-1], points=(replace(data[-1].points[0], time=close_time),))
        for timeframe in (TimeFrame.FIVE_MINUTES, TimeFrame.THIRTY_MINUTES, TimeFrame.ONE_HOUR):
            closed = SignalQualityEvaluator().evaluate(candles, data[1], data[2], signals, timeframe=timeframe,
                                                       as_of=close_time + timedelta(minutes=1))
            self.assertEqual(closed.state, 'confirmed')
            unfinished = SignalQualityEvaluator().evaluate(candles, data[1], data[2], signals, timeframe=timeframe,
                                                           as_of=close_time - timedelta(minutes=1))
            self.assertEqual(unfinished.state, 'insufficient')

    def test_intrabar_touch_is_a_fact_and_does_not_invalidate_an_unbroken_close(self):
        data = fixture()
        touched = (*data[0][:-1], replace(data[0][-1], low=9.8))
        quality = self.evaluate((touched, *data[1:]))
        self.assertEqual(quality.state, 'confirmed')
        self.assertTrue(quality.reference_touched)
        untouched = (*data[0][:-1], replace(data[0][-1], low=10.1))
        self.assertFalse(self.evaluate((untouched, *data[1:])).reference_touched)
        sell = replace(data[-1].points[0], side='sell', invalidation_price=11)
        candles = (*data[0][:-1], replace(data[0][-1], high=11.5))
        result = self.evaluate((candles, data[1], data[2], replace(data[-1], points=(sell,))))
        self.assertEqual(result.state, 'confirmed')
        self.assertTrue(result.reference_touched)


if __name__ == '__main__':
    unittest.main()
