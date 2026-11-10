from datetime import datetime, time
from math import isfinite
from statistics import mean

from ..models import Candle, ChanAnalysis, ChanSignals, MacdPoint, SignalQuality, TimeFrame


class SignalQualityEvaluator:
    """Describe the latest raw point; new momentum/volume rules are unvalidated."""

    def evaluate(self, candles: tuple[Candle, ...], analysis: ChanAnalysis,
                 macd: tuple[MacdPoint, ...], signals: ChanSignals, *,
                 timeframe: TimeFrame, as_of: datetime) -> SignalQuality | None:
        if not signals.points:
            return None
        point = signals.points[-1]
        reference = point.invalidation_price
        reference_known = reference is not None and isfinite(reference) and reference > 0
        completed = bool(candles) and self._completed(candles[-1], timeframe, as_of)
        touched = (candles[-1].low <= reference if point.side == "buy" else candles[-1].high >= reference
                   ) if reference_known and candles else None
        history_status, first_crossed, checked_through = self._history(
            candles, point, timeframe, as_of, reference_known)
        reasons = []
        if first_crossed is not None:
            state = "invalidated"
            reasons.append("现有行情中，自结构端点起已出现收盘严格越界，价格恢复不重新启用该信号")
        elif not completed:
            state = "insufficient"
            reasons.append("当前 K 线尚未完成，等待收盘后评价")
        elif reference_known and (
            candles[-1].close < reference if point.side == "buy" else candles[-1].close > reference
        ):
            state = "invalidated"
            reasons.append("收盘价已严格穿越该信号的失效参考")
        elif not point.confirmed:
            state = "candidate"
            reasons.append("末端结构仍待确认，端点可能修正")
        elif not reference_known:
            state = "insufficient"
            reasons.append("缺少该信号对应的失效参考，不能用最新中枢替代")
        elif history_status != "checked_from_pivot":
            state = "insufficient"
            reasons.append("端点对应的行情区间无法核对，暂列观察")
        else:
            state = "confirmed"
            reasons.append("结构已确认，现有端点起已收盘 K 线未见严格越界")
        if touched and completed and state != "invalidated":
            reasons.append("本根 K 线盘中已触及或越过参考，收盘未严格越界")

        histogram = macd[-1].histogram if completed and len(macd) == len(candles) and macd else None
        if histogram is not None and not isfinite(histogram):
            histogram = None
        divergence = next((item for item in reversed(signals.divergences)
                           if item.leaving_pen_id == point.pen_id), None)
        area_ratio = (divergence.leaving_area / divergence.entering_area
                      if divergence and divergence.entering_area > 0 else None)
        pullback_macd_ratio = self._pullback_momentum(analysis, macd, point.pen_id)
        breakout, pullback = self._volume(candles, analysis, point.pen_id, point.kind, timeframe)
        volume_known = completed and breakout is not None and pullback is not None
        pivot_index = point.candle_index
        bars_since_pivot = None
        if (isinstance(pivot_index, int) and 0 <= pivot_index < len(candles)
                and candles[pivot_index].time == point.time):
            bars_since_pivot = sum(self._completed(candle, timeframe, as_of)
                                   for candle in candles[pivot_index + 1:])
        reasons.append("新增动能与量能规则尚未回测验证，不作为买卖指令")
        reasons.append("首次识别和确认时间未追溯；区间价格检查不代表信号当时已被识别")
        return SignalQuality(
            state=state, invalidation_price=reference if reference_known else None,
            momentum_status="unvalidated" if histogram is not None else "insufficient",
            volume_status="unvalidated" if volume_known else "insufficient",
            macd_histogram=histogram, macd_area_ratio=area_ratio,
            pullback_macd_ratio=pullback_macd_ratio,
            reference_touched=touched,
            breakout_volume_ratio=breakout if volume_known else None,
            pullback_volume_ratio=pullback if volume_known else None,
            reasons=tuple(reasons),
            bars_since_pivot=bars_since_pivot,
            history_status=history_status,
            first_reference_crossed_at=first_crossed.time if first_crossed else None,
            first_reference_crossed_close=first_crossed.close if first_crossed else None,
            history_checked_through=checked_through,
        )

    @classmethod
    def _history(cls, candles, point, timeframe, as_of, reference_known):
        index = point.candle_index
        if (not reference_known or not isinstance(index, int) or not 0 <= index < len(candles)
                or candles[index].time != point.time or not cls._completed(candles[index], timeframe, as_of)):
            return "unknown", None, None
        # Check available closed bars from the pivot, never a sampled trend or future bars.
        closed = [candle for candle in candles[index:] if cls._completed(candle, timeframe, as_of)]
        crossed = next((candle for candle in closed if (
            candle.close < point.invalidation_price if point.side == "buy"
            else candle.close > point.invalidation_price)), None)
        return ("invalidated" if crossed else "checked_from_pivot"), crossed, closed[-1].time

    @staticmethod
    def _pullback_momentum(analysis, macd, pen_id):
        position = next((i for i, pen in enumerate(analysis.pens) if pen.id == pen_id), None)
        if position is None or position < 2:
            return None
        current, previous = analysis.pens[position], analysis.pens[position - 2]
        if current.direction != previous.direction or current.end_index >= len(macd):
            return None
        sign = -1 if current.direction == "down" else 1
        def area(pen):
            return sum(max(0, item.histogram * sign) for item in macd[pen.start_index + 1:pen.end_index + 1])
        baseline = area(previous)
        return round(area(current) / baseline, 4) if baseline > 0 else None

    @staticmethod
    def _completed(candle, timeframe, as_of):
        local = as_of.astimezone(candle.time.tzinfo)
        if timeframe == TimeFrame.ONE_DAY:
            return candle.time.date() < local.date() or (
                candle.time.date() == local.date() and local.time() >= time(15, 0))
        # Both mainland adapters preserve the source's bar-close timestamp.
        return candle.time <= as_of

    @staticmethod
    def _volume(candles, analysis, pen_id, kind, timeframe):
        if kind not in (2, 3) or timeframe != TimeFrame.ONE_DAY:
            return None, None
        position = next((i for i, pen in enumerate(analysis.pens) if pen.id == pen_id), None)
        if position is None or position == 0:
            return None, None
        breakout, pullback = analysis.pens[position - 1:position + 1]
        # Shared pivot candles belong to the preceding leg, never both legs.
        start = breakout.start_index + 1
        if start < 20 or pullback.end_index >= len(candles):
            return None, None
        baseline = candles[start - 20:start]
        advancing = candles[start:breakout.end_index + 1]
        returning = candles[pullback.start_index + 1:pullback.end_index + 1]
        if not advancing or not returning or any(
            not isfinite(c.volume) or c.volume <= 0 for c in (*baseline, *advancing, *returning)
        ):
            return None, None
        base, advance, retrace = (mean(c.volume for c in section) for section in (baseline, advancing, returning))
        return round(advance / base, 4), round(retrace / advance, 4)
