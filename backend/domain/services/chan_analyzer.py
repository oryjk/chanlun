from __future__ import annotations

from dataclasses import dataclass, replace
from datetime import datetime

from ..models import Candle, ChanAnalysis, Fractal, Pen, Zhongshu


@dataclass
class _MergedCandle:
    high: float
    low: float
    start_index: int
    end_index: int
    time: datetime


class ChanAnalyzer:
    def analyze(self, candles: tuple[Candle, ...]) -> ChanAnalysis:
        merged = self._merge_inclusions(candles)
        fractals = self._find_fractals(merged)
        pens, selected = self._build_pens(fractals)
        zones = self._build_zhongshu(pens)
        return ChanAnalysis(
            fractals=tuple(selected),
            pens=tuple(pens),
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
                continue

            far_enough = fractal.merged_index - previous.merged_index >= 4
            valid_price = (
                fractal.price < previous.price
                if previous.type == "top"
                else fractal.price > previous.price
            )
            if far_enough and valid_price:
                selected.append(fractal)

        pens = [
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
            )
            for index, (start, end) in enumerate(zip(selected, selected[1:]))
        ]
        return pens, selected

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
            )
            following = index + 3
            while following < len(pens):
                pen = pens[following]
                next_low = max(zone.low, pen.low)
                next_high = min(zone.high, pen.high)
                if next_low >= next_high:
                    break
                zone = replace(
                    zone,
                    low=next_low,
                    high=next_high,
                    end_index=pen.end_index,
                    end_time=pen.end_time,
                )
                following += 1
            zones.append(zone)
            index = max(index + 1, following - 2)
        return zones
