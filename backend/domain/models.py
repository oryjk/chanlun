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


@dataclass(frozen=True)
class Zhongshu:
    id: int
    start_index: int
    end_index: int
    start_time: datetime
    end_time: datetime
    low: float
    high: float


@dataclass(frozen=True)
class ChanAnalysis:
    fractals: tuple[Fractal, ...]
    pens: tuple[Pen, ...]
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
class MarketSnapshot:
    instrument: Instrument
    timeframe: TimeFrame
    timeframe_label: str
    candles: tuple[Candle, ...]
    analysis: ChanAnalysis
    summary: MarketSummary
