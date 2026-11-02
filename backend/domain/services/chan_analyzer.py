from __future__ import annotations

from dataclasses import dataclass, replace
from datetime import datetime

from ..models import Candle, ChanAnalysis, Fractal, Pen, Segment, Zhongshu


@dataclass
class _MergedCandle:
    high: float
    low: float
    start_index: int
    end_index: int
    time: datetime


@dataclass
class _Feature:
    high: float
    low: float
    extreme_pen_index: int


class ChanAnalyzer:
    def analyze(self, candles: tuple[Candle, ...]) -> ChanAnalysis:
        merged = self._merge_inclusions(candles)
        fractals = self._find_fractals(merged)
        pens, selected = self._build_pens(fractals)
        zones = self._build_zhongshu(pens)
        segments = self._build_segments(pens)
        return ChanAnalysis(
            fractals=tuple(selected),
            pens=tuple(pens),
            segments=tuple(segments),
            zhongshu=tuple(zones),
            merged_count=len(merged),
            trend=pens[-1].direction if pens else "neutral",
        )

    def _merge_inclusions(self, candles: tuple[Candle, ...]) -> list[_MergedCandle]:
        merged: list[_MergedCandle] = []
        for index, candle in enumerate(candles):
            current = _MergedCandle(candle.high, candle.low, index, index, candle.time)
            if not merged:
                merged.append(current)
                continue

            previous = merged[-1]
            included = (
                current.high <= previous.high and current.low >= previous.low
            ) or (
                current.high >= previous.high and current.low <= previous.low
            )
            if not included:
                merged.append(current)
                continue

            if len(merged) >= 2:
                direction = "up" if previous.high >= merged[-2].high else "down"
            else:
                direction = "up" if candle.close >= candle.open else "down"

            if direction == "up":
                previous.high = max(previous.high, current.high)
                previous.low = max(previous.low, current.low)
            else:
                previous.high = min(previous.high, current.high)
                previous.low = min(previous.low, current.low)
            previous.end_index = index
            previous.time = candle.time
        return merged

    def _find_fractals(self, merged: list[_MergedCandle]) -> list[Fractal]:
        fractals: list[Fractal] = []
        for index in range(1, len(merged) - 1):
            previous, current, following = merged[index - 1 : index + 2]
            is_top = (
                current.high > previous.high
                and current.high > following.high
                and current.low > previous.low
                and current.low > following.low
            )
            is_bottom = (
                current.low < previous.low
                and current.low < following.low
                and current.high < previous.high
                and current.high < following.high
            )
            if not is_top and not is_bottom:
                continue
            fractals.append(
                Fractal(
                    type="top" if is_top else "bottom",
                    merged_index=index,
                    candle_index=current.end_index,
                    time=current.time,
                    price=current.high if is_top else current.low,
                )
            )
        return fractals

    def _build_pens(self, fractals: list[Fractal]) -> tuple[list[Pen], list[Fractal]]:
        selected: list[Fractal] = []
        pending: list[Fractal] = []
        for fractal in fractals:
            if not selected:
                selected.append(fractal)
                continue

            previous = selected[-1]
            if fractal.type == previous.type:
                more_extreme = (
                    fractal.price >= previous.price
                    if fractal.type == "top"
                    else fractal.price <= previous.price
                )
                if more_extreme:
                    selected[-1] = fractal
                    pending.clear()
                continue

            if not self._valid_opposite_price(previous, fractal):
                continue

            pending.append(fractal)
            if fractal.merged_index - previous.merged_index < 4:
                continue

            endpoint = pending[0]
            for candidate in pending[1:]:
                if (
                    candidate.price > endpoint.price
                    if endpoint.type == "top"
                    else candidate.price < endpoint.price
                ):
                    endpoint = candidate
            selected.append(endpoint)
            pending.clear()

        pens = []
        for index, (start, end) in enumerate(zip(selected, selected[1:])):
            pens.append(
                Pen(
                    id=index + 1,
                    direction="up" if start.type == "bottom" else "down",
                    start_index=start.candle_index,
                    end_index=end.candle_index,
                    start_time=start.time,
                    end_time=end.time,
                    start_price=start.price,
                    end_price=end.price,
                    high=max(start.price, end.price),
                    low=min(start.price, end.price),
                    confirmed=index < len(selected) - 2,
                )
            )
        return pens, selected

    def _valid_opposite_price(self, previous: Fractal, fractal: Fractal) -> bool:
        return (
            fractal.price < previous.price
            if previous.type == "top"
            else fractal.price > previous.price
        )

    def _build_zhongshu(self, pens: list[Pen]) -> list[Zhongshu]:
        zones: list[Zhongshu] = []
        index = 0
        while index + 2 < len(pens):
            initial = pens[index : index + 3]
            low = max(pen.low for pen in initial)
            high = min(pen.high for pen in initial)
            if low >= high:
                index += 1
                continue

            zone = Zhongshu(
                id=len(zones) + 1,
                start_index=initial[0].start_index,
                end_index=initial[-1].end_index,
                start_time=initial[0].start_time,
                end_time=initial[-1].end_time,
                low=low,
                high=high,
                gg=max(pen.high for pen in initial),
                dd=min(pen.low for pen in initial),
                confirmed=False,
            )
            following = index + 3
            while following < len(pens):
                pen = pens[following]
                if pen.low >= zone.high or pen.high <= zone.low:
                    break
                zone = replace(
                    zone,
                    end_index=pen.end_index,
                    end_time=pen.end_time,
                    gg=max(zone.gg, pen.high),
                    dd=min(zone.dd, pen.low),
                )
                following += 1

            if following < len(pens):
                zone = replace(zone, confirmed=pens[following].confirmed)
            zones.append(zone)
            if following == len(pens):
                break
            index = following
        return zones

    def _build_segments(self, pens: list[Pen]) -> list[Segment]:
        if len(pens) < 3:
            return []

        confirmed_pens = pens if pens[-1].confirmed else pens[:-1]
        segments: list[Segment] = []
        start = 0
        while start + 2 < len(confirmed_pens):
            if not self._has_initial_overlap(confirmed_pens, start):
                start += 1
                continue
            end = self._find_segment_end(confirmed_pens, start)
            if end is None:
                break
            segments.append(self._make_segment(pens, start, end, len(segments) + 1, True))
            start = end

        if start + 2 < len(pens) and self._has_initial_overlap(pens, start):
            segments.append(self._make_segment(pens, start, len(pens) - 1, len(segments) + 1, False))
        return segments

    def _has_initial_overlap(self, pens: list[Pen], start: int) -> bool:
        initial = pens[start : start + 3]
        return max(pen.low for pen in initial) < min(pen.high for pen in initial)

    def _find_segment_end(self, pens: list[Pen], start: int) -> int | None:
        direction = pens[start].direction
        features = self._standard_features(pens, start)
        target = "top" if direction == "up" else "bottom"
        for index in range(1, len(features) - 1):
            if not self._is_feature_fractal(features, index, target):
                continue
            end = features[index].extreme_pen_index
            if not self._has_gap(features[index - 1], features[index]):
                return end
            if self._has_opposite_feature_fractal(pens, end):
                return end
        return None

    def _standard_features(self, pens: list[Pen], start: int) -> list[_Feature]:
        direction = pens[start].direction
        features: list[_Feature] = []
        for pen_index in range(start + 1, len(pens), 2):
            pen = pens[pen_index]
            current = _Feature(pen.high, pen.low, pen_index)
            if not features:
                features.append(current)
                continue

            previous = features[-1]
            included = (
                current.high <= previous.high and current.low >= previous.low
            ) or (
                current.high >= previous.high and current.low <= previous.low
            )
            if not included:
                features.append(current)
                continue

            if len(features) >= 2:
                merge_direction = "up" if previous.high >= features[-2].high else "down"
            else:
                merge_direction = direction
            if merge_direction == "up":
                if current.high >= previous.high:
                    previous.extreme_pen_index = current.extreme_pen_index
                previous.high = max(previous.high, current.high)
                previous.low = max(previous.low, current.low)
            else:
                if current.low <= previous.low:
                    previous.extreme_pen_index = current.extreme_pen_index
                previous.high = min(previous.high, current.high)
                previous.low = min(previous.low, current.low)
        return features

    def _is_feature_fractal(self, features: list[_Feature], index: int, target: str) -> bool:
        previous, current, following = features[index - 1 : index + 2]
        if target == "top":
            return (
                current.high > previous.high
                and current.high > following.high
                and current.low > previous.low
                and current.low > following.low
            )
        return (
            current.low < previous.low
            and current.low < following.low
            and current.high < previous.high
            and current.high < following.high
        )

    def _has_gap(self, first: _Feature, second: _Feature) -> bool:
        return first.high < second.low or second.high < first.low

    def _has_opposite_feature_fractal(self, pens: list[Pen], start: int) -> bool:
        target = "bottom" if pens[start].direction == "down" else "top"
        features = self._standard_features(pens, start)
        return any(
            self._is_feature_fractal(features, index, target)
            for index in range(1, len(features) - 1)
        )

    def _make_segment(
        self,
        pens: list[Pen],
        start: int,
        end: int,
        segment_id: int,
        confirmed: bool,
    ) -> Segment:
        start_pen = pens[start]
        end_pen = pens[end]
        ranged_pens = pens[start:end] if confirmed else pens[start : end + 1]
        return Segment(
            id=segment_id,
            direction=start_pen.direction,
            start_pen_index=start,
            end_pen_index=end,
            start_index=start_pen.start_index,
            end_index=end_pen.start_index if confirmed else end_pen.end_index,
            start_time=start_pen.start_time,
            end_time=end_pen.start_time if confirmed else end_pen.end_time,
            start_price=start_pen.start_price,
            end_price=end_pen.start_price if confirmed else end_pen.end_price,
            high=max(pen.high for pen in ranged_pens),
            low=min(pen.low for pen in ranged_pens),
            confirmed=confirmed,
        )
