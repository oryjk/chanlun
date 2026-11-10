from dataclasses import dataclass, replace
from datetime import date, datetime

from .models import QuotePoint, RankingBoard, RankingEntry
from .analysis_version import ANALYSIS_VERSION


@dataclass(frozen=True)
class RankingProgress:
    state: str = "pending"
    trade_date: date | None = None
    total_count: int = 0
    processed_count: int = 0
    analyzed_count: int = 0
    failed_count: int = 0
    updated_at: datetime | None = None
    error: str | None = None
    analysis_version: int = ANALYSIS_VERSION


@dataclass(frozen=True)
class RankingView:
    board: RankingBoard | None
    progress: RankingProgress


@dataclass(frozen=True)
class RankingItem:
    secid: str
    entry: RankingEntry | None = None
    error: str | None = None


def build_ranking_entry(stock, snapshot) -> RankingEntry | None:
    guidance = snapshot.guidance
    if guidance.bias == "neutral":
        return None
    points = getattr(snapshot.signals, "points", ()) or ()
    latest_point = points[-1] if points else None
    buy_point = next((point for point in reversed(points) if getattr(point, "side", None) == "buy"), None)
    sell_point = next((point for point in reversed(points) if getattr(point, "side", None) == "sell"), None)
    candles = getattr(snapshot, "candles", ()) or ()
    trend = _extract_trend(candles, buy_point, sell_point)
    return RankingEntry(
        0,
        stock,
        guidance.bias,
        guidance.strength,
        snapshot.summary.latest,
        snapshot.summary.change_percent,
        guidance.invalidation_price,
        latest_point,
        buy_point,
        sell_point,
        trend,
        signal_quality=getattr(snapshot, "signal_quality", None),
    )


def _extract_trend(candles, buy_point, sell_point, max_points: int = 48) -> tuple[QuotePoint, ...]:
    if not candles:
        return ()
    indices = [max(0, len(candles) - 45)]
    point_prices = {}
    must_keep = {len(candles) - 1}
    for point in (buy_point, sell_point):
        index = getattr(point, "candle_index", None)
        price = getattr(point, "price", None)
        if isinstance(index, int) and 0 <= index < len(candles) and isinstance(price, (int, float)):
            indices.append(max(0, index - 2))
            point_prices[index] = float(price)
            must_keep.add(index)
    start = min(indices)
    must_keep.add(start)
    all_indices = list(range(start, len(candles)))
    if len(all_indices) > max_points:
        step = (len(all_indices) - 1) / (max_points - 1)
        sampled = {all_indices[round(i * step)] for i in range(max_points)} | must_keep
        all_indices = sorted(sampled)
    return tuple(
        QuotePoint(candles[index].time, point_prices.get(index, float(candles[index].close)))
        for index in all_indices
    )


def _entry_sort_key(entry: RankingEntry, bias: str) -> tuple:
    side = "buy" if bias == "bullish" else "sell"
    sign = -1 if bias == "bullish" else 1
    point = entry.latest_point
    aligned = point is not None and getattr(point, "side", None) == side
    price = getattr(point, "price", None)
    point_time = getattr(point, "time", None)
    invalidation = entry.signal_quality.invalidation_price if entry.signal_quality else None
    if aligned and isinstance(price, (int, float)) and price > 0 and isinstance(invalidation, (int, float)) and entry.latest > 0:
        direction = 1 if side == "buy" else -1
        move_percent = (entry.latest - price) / price * 100 * direction
        stop_percent = abs(entry.latest - invalidation) / entry.latest * 100
        if -2.0 <= move_percent <= 12.0 and stop_percent <= 10.0:
            tier = 0
            ordinal = point_time.date().toordinal() if hasattr(point_time, "date") else 0
        elif -2.0 <= move_percent <= 12.0:
            tier = 1
            ordinal = point_time.date().toordinal() if hasattr(point_time, "date") else 0
        elif move_percent < -2.0:
            tier = 2
            ordinal = 0
        else:
            tier = 3
            ordinal = 0
        return (tier, -ordinal, -entry.strength, sign * entry.change_percent, entry.stock.symbol)

    if aligned:
        subtier = 0
    elif point is None or (
        isinstance(price, (int, float))
        and ((side == "buy" and entry.latest > price) or (side == "sell" and entry.latest < price))
    ):
        subtier = 1
    else:
        subtier = 2
    return (4, subtier, -entry.strength, sign * entry.change_percent, entry.stock.symbol)


def rank_entries(entries, bias: str, limit: int | None = None) -> tuple[RankingEntry, ...]:
    def classify(entry):
        point, quality = entry.latest_point, entry.signal_quality
        side = "buy" if bias == "bullish" else "sell"
        reference = quality.invalidation_price if quality else None
        unbroken = reference is not None and (
            entry.latest >= reference if side == "buy" else entry.latest <= reference)
        confirmed = (point is not None and getattr(point, "confirmed", False)
                     and point.side == side and quality is not None
                     and quality.state == "confirmed" and quality.history_status == "checked_from_pivot"
                     and quality.first_reference_crossed_at is None and unbroken)
        return replace(entry, signal_group="confirmed" if confirmed else "observation")

    selected = sorted(
        (classify(entry) for entry in entries if entry.bias == bias),
        key=lambda entry: (entry.signal_group != "confirmed", *_entry_sort_key(entry, bias)),
    )
    return tuple(replace(entry, rank=index) for index, entry in enumerate(selected[:limit], 1))
