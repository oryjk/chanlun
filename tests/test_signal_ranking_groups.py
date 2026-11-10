import unittest
from dataclasses import replace
from datetime import datetime, timezone

from backend.domain.models import BuySellPoint, RankingEntry, SignalQuality, Stock
from backend.domain.rankings import rank_entries


def entry(symbol, *, confirmed=True, quality='confirmed', side='buy', latest=10):
    point = BuySellPoint(3, side, 1, 30, datetime(2026, 11, 6, tzinfo=timezone.utc),
                         9.5, confirmed, 'test', invalidation_price=9)
    return RankingEntry(0, Stock('1.' + symbol, symbol, symbol, '沪A'), 'bullish',
                        3, latest, 1, 9, latest_point=point,
                        signal_quality=SignalQuality(quality, 9, history_status='checked_from_pivot') if quality else None)


class RankingGroupTests(unittest.TestCase):
    def test_unknown_or_violated_history_cannot_reenter_confirmed_group_after_price_recovers(self):
        for history_status in ('unknown', 'invalidated'):
            original = entry('600001')
            stale = replace(original, signal_quality=replace(original.signal_quality, history_status=history_status))
            self.assertEqual(rank_entries([stale], 'bullish')[0].signal_group, 'observation')
        original = entry('600001')
        contradictory = replace(original, signal_quality=replace(original.signal_quality,
            first_reference_crossed_at=datetime(2026, 11, 7, tzinfo=timezone.utc)))
        self.assertEqual(rank_entries([contradictory], 'bullish')[0].signal_group, 'observation')

    def test_group_order_uses_each_signals_own_reference_not_guidance_reference(self):
        high_risk = replace(entry('600001', latest=100), invalidation_price=99,
                            latest_point=replace(entry('600001').latest_point, price=100),
                            signal_quality=SignalQuality('confirmed', 70, history_status='checked_from_pivot'))
        low_risk = replace(entry('600002', latest=100), invalidation_price=70,
                           latest_point=replace(entry('600002').latest_point, price=100),
                           signal_quality=SignalQuality('confirmed', 98, history_status='checked_from_pivot'))
        self.assertEqual([e.stock.symbol for e in rank_entries([high_risk, low_risk], 'bullish')],
                         ['600002', '600001'])

    def test_missing_signal_reference_cannot_borrow_guidance_to_enter_a_better_sort_tier(self):
        first = replace(entry('600001', latest=100, quality=None), invalidation_price=99,
                        latest_point=replace(entry('600001').latest_point, price=100), strength=2)
        second = replace(entry('600002', latest=100, quality=None), invalidation_price=70,
                         latest_point=replace(entry('600002').latest_point, price=100), strength=4)
        self.assertEqual([e.stock.symbol for e in rank_entries([first, second], 'bullish')],
                         ['600002', '600001'])

    def test_confirmed_structures_precede_a_newer_more_attractive_candidate_before_limit(self):
        candidate = entry('600001', confirmed=False, quality='candidate')
        confirmed = entry('600002', latest=20)
        result = rank_entries([candidate, confirmed], 'bullish', 1)
        self.assertEqual(result[0].stock.symbol, '600002')
        self.assertEqual(result[0].signal_group, 'confirmed')

    def test_legacy_missing_invalidated_and_opposite_points_are_observations(self):
        cases = (entry('600001', quality=None), entry('600002', quality='invalidated'),
                 entry('600003', side='sell'), entry('600004', confirmed=False))
        self.assertTrue(all(e.signal_group == 'observation' for e in rank_entries(cases, 'bullish')))

    def test_actual_current_crossing_cannot_be_promoted_by_a_stale_quality_state(self):
        result = rank_entries([entry('600001', latest=8)], 'bullish')
        self.assertEqual(result[0].signal_group, 'observation')


if __name__ == '__main__':
    unittest.main()
