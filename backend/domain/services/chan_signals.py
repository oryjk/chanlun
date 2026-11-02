from ..models import BuySellPoint, ChanAnalysis, ChanSignals, Divergence, MacdPoint, Pen, Zhongshu

SIDE_LABELS = {"buy": "买", "sell": "卖"}


class ChanSignalAnalyzer:
    """基于笔中枢识别走势类型、MACD 背驰和三类买卖点。

    约定：中枢覆盖 pens[start..end]，其中末笔是最后一笔与核心区间重叠的笔，
    pens[end + 1] 是第一笔完全不回到核心区间的笔。
    """

    def analyze(self, analysis: ChanAnalysis, macd: tuple[MacdPoint, ...]) -> ChanSignals:
        pens = list(analysis.pens)
        zones = list(analysis.zhongshu)
        ranges = [self._pen_range(pens, zone) for zone in zones]

        divergences: list[Divergence] = []
        for position, zone in enumerate(zones):
            next_start = ranges[position + 1][0] if position + 1 < len(zones) else len(pens) - 1
            divergence = self._find_divergence(
                pens, zone, ranges[position], next_start, macd, self._relation(zones, position)
            )
            if divergence is not None and divergence not in divergences:
                divergences.append(divergence)

        points: dict[tuple[int, str, int], BuySellPoint] = {}
        pen_positions = {pen.id: index for index, pen in enumerate(pens)}
        for divergence in divergences:
            if divergence.kind != "trend":
                continue
            first = self._first_point(divergence, pens[pen_positions[divergence.leaving_pen_id]])
            points.setdefault((first.kind, first.side, first.pen_id), first)
            second = self._second_point(pens, pen_positions[first.pen_id], first)
            if second is not None:
                points.setdefault((second.kind, second.side, second.pen_id), second)

        for zone, (_, end) in zip(zones, ranges):
            third = self._third_point(pens, zone, end)
            if third is not None:
                points.setdefault((third.kind, third.side, third.pen_id), third)

        return ChanSignals(
            trend_type=self._trend_type(zones),
            divergences=tuple(divergences),
            points=tuple(sorted(points.values(), key=lambda point: (point.candle_index, point.kind))),
        )

    def _pen_range(self, pens: list[Pen], zone: Zhongshu) -> tuple[int, int]:
        start = next(index for index, pen in enumerate(pens) if pen.start_index == zone.start_index)
        end = next(index for index in range(start, len(pens)) if pens[index].end_index == zone.end_index)
        return start, end

    def _relation(self, zones: list[Zhongshu], position: int) -> str:
        if position == 0:
            return "consolidation"
        previous, current = zones[position - 1], zones[position]
        if current.high < previous.low:
            return "down_trend"
        if current.low > previous.high:
            return "up_trend"
        return "consolidation"

    def _trend_type(self, zones: list[Zhongshu]) -> str:
        if not zones:
            return "none"
        return self._relation(zones, len(zones) - 1)

    def _find_divergence(
        self,
        pens: list[Pen],
        zone: Zhongshu,
        pen_range: tuple[int, int],
        next_start: int,
        macd: tuple[MacdPoint, ...],
        relation: str,
    ) -> Divergence | None:
        start, end = pen_range
        if start == 0:
            return None

        entering = pens[start - 1]
        down = entering.direction == "down"
        leaving = [
            pen
            for pen in pens[end : next_start + 1]
            if pen.direction == entering.direction and (pen.low < zone.low if down else pen.high > zone.high)
        ]
        if not leaving:
            return None

        extreme = min(leaving, key=lambda pen: pen.low) if down else max(leaving, key=lambda pen: pen.high)
        new_extreme = extreme.low < entering.low if down else extreme.high > entering.high
        if not new_extreme:
            return None

        entering_area = self._area(macd, entering.start_index, entering.end_index, down)
        leaving_area = self._area(macd, leaving[0].start_index, extreme.end_index, down)
        if entering_area <= 0 or leaving_area >= entering_area:
            return None

        trend = relation == ("down_trend" if down else "up_trend")
        return Divergence(
            kind="trend" if trend else "consolidation",
            direction="bottom" if down else "top",
            zhongshu_id=zone.id,
            entering_pen_id=entering.id,
            leaving_pen_id=extreme.id,
            entering_area=round(entering_area, 6),
            leaving_area=round(leaving_area, 6),
            candle_index=extreme.end_index,
            time=extreme.end_time,
            price=extreme.end_price,
            confirmed=extreme.confirmed,
        )

    def _area(self, macd: tuple[MacdPoint, ...], start: int, end: int, negative: bool) -> float:
        bars = (point.histogram for point in macd[start : end + 1])
        return sum(-value for value in bars if value < 0) if negative else sum(value for value in bars if value > 0)

    def _first_point(self, divergence: Divergence, pen: Pen) -> BuySellPoint:
        side = "buy" if divergence.direction == "bottom" else "sell"
        trend_label = "下跌" if side == "buy" else "上涨"
        return BuySellPoint(
            kind=1,
            side=side,
            pen_id=pen.id,
            candle_index=pen.end_index,
            time=pen.end_time,
            price=pen.end_price,
            confirmed=pen.confirmed,
            reason=(
                f"{trend_label}趋势中离开中枢 {divergence.zhongshu_id} 的一段创新"
                f"{'低' if side == 'buy' else '高'}，MACD 面积 {divergence.leaving_area:.4g} "
                f"小于进入段 {divergence.entering_area:.4g}，趋势背驰"
            ),
        )

    def _second_point(self, pens: list[Pen], position: int, first: BuySellPoint) -> BuySellPoint | None:
        if position + 2 >= len(pens):
            return None
        pullback = pens[position + 2]
        holds = pullback.low > first.price if first.side == "buy" else pullback.high < first.price
        if not holds:
            return None
        return BuySellPoint(
            kind=2,
            side=first.side,
            pen_id=pullback.id,
            candle_index=pullback.end_index,
            time=pullback.end_time,
            price=pullback.end_price,
            confirmed=pullback.confirmed,
            reason=(
                f"一类{SIDE_LABELS[first.side]}点后首次"
                f"{'回调未破前低' if first.side == 'buy' else '反弹未过前高'} {first.price:g}"
            ),
        )

    def _third_point(self, pens: list[Pen], zone: Zhongshu, end: int) -> BuySellPoint | None:
        if end + 1 >= len(pens):
            return None
        pullback = pens[end + 1]
        if pullback.direction == "down" and pullback.low >= zone.high:
            side, detail = "buy", f"向上离开中枢后回踩 {pullback.low:g} 未回到核心上沿 {zone.high:g}"
        elif pullback.direction == "up" and pullback.high <= zone.low:
            side, detail = "sell", f"向下离开中枢后反抽 {pullback.high:g} 未回到核心下沿 {zone.low:g}"
        else:
            return None
        return BuySellPoint(
            kind=3,
            side=side,
            pen_id=pullback.id,
            candle_index=pullback.end_index,
            time=pullback.end_time,
            price=pullback.end_price,
            confirmed=pullback.confirmed,
            reason=detail,
        )
