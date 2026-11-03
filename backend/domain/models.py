from __future__ import annotations

import re
from dataclasses import dataclass
from datetime import datetime
from enum import Enum

from .errors import InvalidSecurityId, InvalidTimeframe


class TimeFrame(str, Enum):
    FIVE_MINUTES = "5m"
    THIRTY_MINUTES = "30m"
    ONE_HOUR = "1h"
    ONE_DAY = "1d"

    @property
    def label(self) -> str:
        return {
            TimeFrame.FIVE_MINUTES: "5 分钟",
            TimeFrame.THIRTY_MINUTES: "30 分钟",
            TimeFrame.ONE_HOUR: "1 小时",
            TimeFrame.ONE_DAY: "日线",
        }[self]

    @classmethod
    def parse(cls, value: str) -> TimeFrame:
        try:
            return cls(value)
        except ValueError as error:
            raise InvalidTimeframe("不支持的周期，请选择 5m、30m、1h 或 1d") from error


def validate_security_id(value: str) -> str:
    secid = value.strip()
    if not re.fullmatch(r"\d{1,3}\.[A-Za-z0-9.-]{1,24}", secid):
        raise InvalidSecurityId("证券标识格式不正确")
    return secid


@dataclass(frozen=True)
class Stock:
    secid: str
    symbol: str
    name: str
    market: str


@dataclass(frozen=True)
class Instrument:
    secid: str
    symbol: str
    name: str
    decimal: int
    data_source: str
    currency: str | None = None
    exchange: str | None = None


@dataclass(frozen=True)
class Candle:
    time: datetime
    open: float
    close: float
    high: float
    low: float
    volume: int


@dataclass(frozen=True)
class Fractal:
    type: str
    merged_index: int
    candle_index: int
    time: datetime
    price: float


@dataclass(frozen=True)
class Pen:
    id: int
    direction: str
    start_index: int
    end_index: int
    start_time: datetime
    end_time: datetime
    start_price: float
    end_price: float
    high: float
    low: float
    confirmed: bool


@dataclass(frozen=True)
class Segment:
    id: int
    direction: str
    start_pen_index: int
    end_pen_index: int
    start_index: int
    end_index: int
    start_time: datetime
    end_time: datetime
    start_price: float
    end_price: float
    high: float
    low: float
    confirmed: bool


@dataclass(frozen=True)
class Zhongshu:
    id: int
    start_index: int
    end_index: int
    start_time: datetime
    end_time: datetime
    low: float
    high: float
    gg: float
    dd: float
    confirmed: bool


@dataclass(frozen=True)
class ChanAnalysis:
    fractals: tuple[Fractal, ...]
    pens: tuple[Pen, ...]
    segments: tuple[Segment, ...]
    zhongshu: tuple[Zhongshu, ...]
    merged_count: int
    trend: str


@dataclass(frozen=True)
class MarketData:
    instrument: Instrument
    candles: tuple[Candle, ...]


@dataclass(frozen=True)
class MarketSummary:
    latest: float
    change: float
    change_percent: float
    high: float
    low: float
    volume: int


@dataclass(frozen=True)
class QuotePoint:
    time: datetime
    price: float


@dataclass(frozen=True)
class QuoteSeries:
    secid: str
    points: tuple[QuotePoint, ...]


@dataclass(frozen=True)
class RealtimeQuote:
    secid: str
    symbol: str
    name: str
    time: datetime
    open: float
    high: float
    low: float
    previous_close: float
    latest: float
    change: float
    change_percent: float
    volume: int
    amount: float


@dataclass(frozen=True)
class MarketGuidance:
    bias: str
    reasons: tuple[str, ...]
    invalidation_price: float | None
    strength: int


@dataclass(frozen=True)
class MacdPoint:
    dif: float
    dea: float
    histogram: float


@dataclass(frozen=True)
class Divergence:
    kind: str
    direction: str
    zhongshu_id: int
    entering_pen_id: int
    leaving_pen_id: int
    entering_area: float
    leaving_area: float
    candle_index: int
    time: datetime
    price: float
    confirmed: bool


@dataclass(frozen=True)
class BuySellPoint:
    kind: int
    side: str
    pen_id: int
    candle_index: int
    time: datetime
    price: float
    confirmed: bool
    reason: str


@dataclass(frozen=True)
class ChanSignals:
    trend_type: str
    divergences: tuple[Divergence, ...]
    points: tuple[BuySellPoint, ...]


@dataclass(frozen=True)
class LevelSignal:
    timeframe: TimeFrame
    timeframe_label: str
    trend_type: str
    latest_point: BuySellPoint | None
    lower_confirmation: BuySellPoint | None


@dataclass(frozen=True)
class SignalConfluence:
    secid: str
    levels: tuple[LevelSignal, ...]
    conclusion: str


@dataclass(frozen=True)
class MarketSnapshot:
    instrument: Instrument
    timeframe: TimeFrame
    timeframe_label: str
    candles: tuple[Candle, ...]
    analysis: ChanAnalysis
    summary: MarketSummary
    guidance: MarketGuidance
    macd: tuple[MacdPoint, ...]
    signals: ChanSignals


@dataclass(frozen=True)
class RankingEntry:
    rank: int
    stock: Stock
    bias: str
    strength: int
    latest: float
    change_percent: float
    invalidation_price: float | None
    latest_point: BuySellPoint | None = None


@dataclass(frozen=True)
class RankingBoard:
    generated_at: datetime
    timeframe: TimeFrame
    bullish: tuple[RankingEntry, ...]
    bearish: tuple[RankingEntry, ...]


@dataclass(frozen=True)
class UserIdentity:
    user_id: int
    nickname: str
    avatar_url: str | None = None
