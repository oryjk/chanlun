import unittest
from datetime import datetime, timedelta, timezone

from backend.domain.models import Candle, ChanAnalysis, MacdPoint, Pen
from backend.domain.services.chan_analyzer import ChanAnalyzer
from backend.domain.services.chan_signals import ChanSignalAnalyzer
from backend.domain.services.macd import compute_macd


START = datetime(2024, 1, 1, tzinfo=timezone.utc)

# 两个不重叠的下降笔中枢，最后一笔创新低：
# 中枢一 pens 0..4 核心 [104, 110]，中枢二 pens 5..8 核心 [92, 95]
DOWN_TREND = [120, 100, 110, 104, 108, 90, 96, 92, 95, 85]


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
            confirmed=last_confirmed or index < len(prices) - 2,
        )
        for index in range(len(prices) - 1)
    ]


def make_analysis(prices, last_confirmed=True):
    pens = make_pens(prices, last_confirmed)
    return ChanAnalysis(
        fractals=(),
        pens=tuple(pens),
        segments=(),
        zhongshu=tuple(ChanAnalyzer()._build_zhongshu(pens)),
        merged_count=len(prices),
        trend=pens[-1].direction,
    )


def make_macd(histograms):
    return tuple(MacdPoint(dif=0.0, dea=0.0, histogram=value) for value in histograms)


def mirror(prices):
    return [200 - price for price in prices]


def summarize(points):
    return [(point.kind, point.side, point.price) for point in points]


class MacdTests(unittest.TestCase):
    def test_constant_prices_have_zero_macd(self):
        candles = tuple(
            Candle(START + timedelta(hours=index), 10, 10, 10, 10, 100) for index in range(40)
        )
        self.assertTrue(all(point.histogram == 0 and point.dif == 0 for point in compute_macd(candles)))

    def test_rising_prices_have_positive_dif_and_doubled_histogram(self):
        candles = tuple(
            Candle(START + timedelta(hours=index), 10 + index, 10 + index, 11 + index, 9 + index, 100)
            for index in range(40)
        )
        macd = compute_macd(candles)
        self.assertEqual(len(macd), 40)
        self.assertGreater(macd[-1].dif, 0)
        self.assertAlmostEqual(macd[-1].histogram, 2 * (macd[-1].dif - macd[-1].dea), places=4)


class ChanSignalTests(unittest.TestCase):
    def setUp(self):
        self.analyzer = ChanSignalAnalyzer()

    def test_down_trend_with_weaker_new_low_gives_first_buy(self):
        histograms = [0, 0, 0, 0, -2, -3, 0, 0, -1, -1]
        signals = self.analyzer.analyze(make_analysis(DOWN_TREND), make_macd(histograms))

        self.assertEqual(signals.trend_type, "down_trend")
        self.assertEqual(len(signals.divergences), 1)
        divergence = signals.divergences[0]
        self.assertEqual((divergence.kind, divergence.direction), ("trend", "bottom"))
        self.assertEqual((divergence.entering_area, divergence.leaving_area), (5, 2))
        self.assertIn((1, "buy", 85), summarize(signals.points))
        first = next(point for point in signals.points if point.kind == 1)
        self.assertEqual(first.invalidation_price, 85)

    def test_stronger_new_low_is_not_divergence(self):
        histograms = [0, 0, 0, 0, -2, -3, 0, 0, -4, -4]
        signals = self.analyzer.analyze(make_analysis(DOWN_TREND), make_macd(histograms))

        self.assertEqual(signals.divergences, ())
        self.assertNotIn(1, [point.kind for point in signals.points])

    def test_higher_pullback_after_first_buy_gives_second_buy(self):
        prices = DOWN_TREND + [91, 87]
        histograms = [0, 0, 0, 0, -2, -3, 0, 0, -1, -1, 0, 0]
        signals = self.analyzer.analyze(make_analysis(prices), make_macd(histograms))

        points = summarize(signals.points)
        self.assertIn((1, "buy", 85), points)
        self.assertIn((2, "buy", 87), points)
        second = next(point for point in signals.points if point.kind == 2)
        self.assertEqual(second.invalidation_price, 85)

    def test_lower_pullback_after_first_buy_gives_no_second_buy(self):
        prices = DOWN_TREND + [91, 84]
        histograms = [0, 0, 0, 0, -2, -3, 0, 0, -1, -1, 0, 0]
        signals = self.analyzer.analyze(make_analysis(prices), make_macd(histograms))

        self.assertNotIn(2, [point.kind for point in signals.points])

    def test_rebound_that_stays_below_zone_gives_third_sell(self):
        signals = self.analyzer.analyze(make_analysis(DOWN_TREND), make_macd([0] * 10))

        # 中枢一之后反弹至 96，未回到核心下沿 104
        self.assertIn((3, "sell", 96), summarize(signals.points))
        third = next(point for point in signals.points if point.kind == 3)
        self.assertEqual(third.invalidation_price, 104)
        self.assertEqual(third.source_zone_id, 1)

    def test_mirrored_up_trend_gives_first_sell_and_third_buy(self):
        histograms = [0, 0, 0, 0, 2, 3, 0, 0, 1, 1]
        signals = self.analyzer.analyze(make_analysis(mirror(DOWN_TREND)), make_macd(histograms))

        self.assertEqual(signals.trend_type, "up_trend")
        points = summarize(signals.points)
        self.assertIn((1, "sell", 115), points)
        self.assertIn((3, "buy", 104), points)

    def test_point_on_candidate_pen_is_unconfirmed(self):
        histograms = [0, 0, 0, 0, -2, -3, 0, 0, -1, -1]
        signals = self.analyzer.analyze(make_analysis(DOWN_TREND, last_confirmed=False), make_macd(histograms))

        first_buy = next(point for point in signals.points if point.kind == 1)
        self.assertFalse(first_buy.confirmed)

    def test_no_zone_means_no_signals(self):
        signals = self.analyzer.analyze(make_analysis([100, 110, 105]), make_macd([0, 0, 0]))

        self.assertEqual(signals.trend_type, "none")
        self.assertEqual(signals.points, ())


if __name__ == "__main__":
    unittest.main()
