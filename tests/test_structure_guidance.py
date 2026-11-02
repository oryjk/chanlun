import unittest
from datetime import datetime, timedelta, timezone

from backend.domain.models import ChanAnalysis, Pen, Segment, Zhongshu
from backend.domain.services.structure_guidance import StructureGuidanceEvaluator


START = datetime(2024, 1, 1, tzinfo=timezone.utc)


def make_analysis(pen_direction="up", segment_direction="up", include_zone=True, candidate_pen=False):
    pens = [
        Pen(
            id=1,
            direction=pen_direction,
            start_index=0,
            end_index=1,
            start_time=START,
            end_time=START + timedelta(hours=1),
            start_price=100,
            end_price=110,
            high=110,
            low=100,
            confirmed=True,
        )
    ]
    if candidate_pen:
        pens.append(
            Pen(
                id=2,
                direction="down" if pen_direction == "up" else "up",
                start_index=1,
                end_index=2,
                start_time=START + timedelta(hours=1),
                end_time=START + timedelta(hours=2),
                start_price=110,
                end_price=105,
                high=110,
                low=105,
                confirmed=False,
            )
        )

    segments = []
    if segment_direction:
        segments.append(
            Segment(
                id=1,
                direction=segment_direction,
                start_pen_index=0,
                end_pen_index=0,
                start_index=0,
                end_index=1,
                start_time=START,
                end_time=START + timedelta(hours=1),
                start_price=100,
                end_price=110,
                high=110,
                low=100,
                confirmed=True,
            )
        )

    zones = []
    if include_zone:
        zones.append(
            Zhongshu(
                id=1,
                start_index=0,
                end_index=1,
                start_time=START,
                end_time=START + timedelta(hours=1),
                low=105,
                high=115,
                gg=120,
                dd=100,
                confirmed=False,
            )
        )

    return ChanAnalysis(
        fractals=(),
        pens=tuple(pens),
        segments=tuple(segments),
        zhongshu=tuple(zones),
        merged_count=2,
        trend=pens[-1].direction,
    )


class StructureGuidanceTests(unittest.TestCase):
    def setUp(self):
        self.evaluator = StructureGuidanceEvaluator()

    def test_aligned_structure_above_zone_is_bullish(self):
        guidance = self.evaluator.evaluate(make_analysis(), 120)

        self.assertEqual(guidance.bias, "bullish")
        self.assertEqual(guidance.invalidation_price, 115)
        self.assertIn("最新价位于笔中枢核心上沿之上", guidance.reasons)

    def test_aligned_structure_below_zone_is_bearish(self):
        guidance = self.evaluator.evaluate(
            make_analysis(pen_direction="down", segment_direction="down"),
            100,
        )

        self.assertEqual(guidance.bias, "bearish")
        self.assertEqual(guidance.invalidation_price, 105)

    def test_price_inside_zone_waits(self):
        guidance = self.evaluator.evaluate(make_analysis(), 110)

        self.assertEqual(guidance.bias, "neutral")
        self.assertIsNone(guidance.invalidation_price)

    def test_conflicting_pen_and_segment_waits(self):
        guidance = self.evaluator.evaluate(
            make_analysis(pen_direction="up", segment_direction="down"),
            120,
        )

        self.assertEqual(guidance.bias, "neutral")
        self.assertIn("已确认笔与线段方向不一致，等待进一步确认", guidance.reasons)

    def test_insufficient_structure_waits(self):
        guidance = self.evaluator.evaluate(make_analysis(include_zone=False), 120)

        self.assertEqual(guidance.bias, "neutral")
        self.assertIn("已确认笔或笔中枢不足，暂不形成方向判断", guidance.reasons)

    def test_candidate_structures_are_disclosed(self):
        guidance = self.evaluator.evaluate(make_analysis(candidate_pen=True), 120)

        self.assertIn("末笔仍为候选，端点可能修正", guidance.reasons)
        self.assertIn("当前笔中枢仍可延伸", guidance.reasons)


if __name__ == "__main__":
    unittest.main()
