from ..models import ChanAnalysis, MarketGuidance


class StructureGuidanceEvaluator:
    def evaluate(self, analysis: ChanAnalysis, latest_price: float) -> MarketGuidance:
        confirmed_pens = [pen for pen in analysis.pens if pen.confirmed]
        if not confirmed_pens or not analysis.zhongshu:
            return MarketGuidance(
                bias="neutral",
                reasons=("已确认笔或笔中枢不足，暂不形成方向判断",),
                invalidation_price=None,
            )

        zone = analysis.zhongshu[-1]
        latest_pen = confirmed_pens[-1]
        confirmed_segments = [segment for segment in analysis.segments if segment.confirmed]
        latest_segment = confirmed_segments[-1] if confirmed_segments else None
        reasons = []
        score = 0

        if latest_price > zone.high:
            score += 2
            reasons.append("最新价位于笔中枢核心上沿之上")
        elif latest_price < zone.low:
            score -= 2
            reasons.append("最新价位于笔中枢核心下沿之下")
        else:
            reasons.append("最新价仍处在笔中枢核心区间内")

        pen_score = 1 if latest_pen.direction == "up" else -1
        score += pen_score
        reasons.append(f"最近已确认笔向{'上' if latest_pen.direction == 'up' else '下'}")

        if latest_segment is not None:
            segment_score = 1 if latest_segment.direction == "up" else -1
            score += segment_score
            reasons.append(f"最近已确认线段向{'上' if latest_segment.direction == 'up' else '下'}")

        price_inside_zone = zone.low <= latest_price <= zone.high
        directions_conflict = latest_segment is not None and latest_segment.direction != latest_pen.direction
        if not price_inside_zone and score >= 2 and not directions_conflict:
            bias = "bullish"
            invalidation_price = zone.high
        elif not price_inside_zone and score <= -2 and not directions_conflict:
            bias = "bearish"
            invalidation_price = zone.low
        else:
            bias = "neutral"
            invalidation_price = None
            if directions_conflict:
                reasons.append("已确认笔与线段方向不一致，等待进一步确认")
            elif price_inside_zone:
                reasons.append("价格尚未离开中枢核心，保持观察")
            else:
                reasons.append("价格位置与确认结构尚未形成充分同向证据")

        if analysis.pens and not analysis.pens[-1].confirmed:
            reasons.append("末笔仍为候选，端点可能修正")
        if not zone.confirmed:
            reasons.append("当前笔中枢仍可延伸")

        return MarketGuidance(
            bias=bias,
            reasons=tuple(reasons),
            invalidation_price=invalidation_price,
        )
