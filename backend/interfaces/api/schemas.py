from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field


class ApiModel(BaseModel):
    model_config = ConfigDict(from_attributes=True)


class StockResponse(ApiModel):
    secid: str
    symbol: str
    name: str
    market: str


class StockSearchResponse(ApiModel):
    items: list[StockResponse]


class InstrumentResponse(ApiModel):
    secid: str
    symbol: str
    name: str
    decimal: int
    data_source: str
    currency: str | None
    exchange: str | None


class CandleResponse(ApiModel):
    time: datetime
    open: float
    close: float
    high: float
    low: float
    volume: int


class FractalResponse(ApiModel):
    type: str
    merged_index: int
    candle_index: int
    time: datetime
    price: float


class PenResponse(ApiModel):
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


class SegmentResponse(ApiModel):
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


class ZhongshuResponse(ApiModel):
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


class ChanAnalysisResponse(ApiModel):
    fractals: tuple[FractalResponse, ...]
    pens: tuple[PenResponse, ...]
    segments: tuple[SegmentResponse, ...]
    zhongshu: tuple[ZhongshuResponse, ...]
    merged_count: int
    trend: str


class MarketSummaryResponse(ApiModel):
    latest: float
    change: float
    change_percent: float
    high: float
    low: float
    volume: int


class RealtimeQuoteResponse(ApiModel):
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


class QuotePointResponse(ApiModel):
    time: datetime
    price: float


class QuoteSeriesResponse(ApiModel):
    secid: str
    points: tuple[QuotePointResponse, ...]


class QuoteSampleRequest(BaseModel):
    secid: str
    time: datetime
    price: float = Field(gt=0)


class MarketGuidanceResponse(ApiModel):
    bias: str
    reasons: tuple[str, ...]
    invalidation_price: float | None


class MarketSnapshotResponse(ApiModel):
    instrument: InstrumentResponse
    timeframe: str
    timeframe_label: str
    candles: tuple[CandleResponse, ...]
    analysis: ChanAnalysisResponse
    summary: MarketSummaryResponse
    guidance: MarketGuidanceResponse
