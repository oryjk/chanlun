from datetime import date, datetime

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


class MacdPointResponse(ApiModel):
    dif: float
    dea: float
    histogram: float


class DivergenceResponse(ApiModel):
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


class BuySellPointResponse(ApiModel):
    kind: int
    side: str
    pen_id: int
    candle_index: int
    time: datetime
    price: float
    confirmed: bool
    reason: str
    invalidation_price: float | None = None
    source_zone_id: int | None = None


class SignalQualityResponse(ApiModel):
    state: str
    invalidation_price: float | None
    momentum_status: str
    volume_status: str
    macd_histogram: float | None
    macd_area_ratio: float | None
    pullback_macd_ratio: float | None
    reference_touched: bool | None
    breakout_volume_ratio: float | None
    pullback_volume_ratio: float | None
    history_status: str
    first_detected_at: datetime | None
    confirmed_at: datetime | None
    reasons: tuple[str, ...]
    bars_since_pivot: int | None = None
    first_reference_crossed_at: datetime | None = None
    first_reference_crossed_close: float | None = None
    history_checked_through: datetime | None = None


class ChanSignalsResponse(ApiModel):
    trend_type: str
    divergences: tuple[DivergenceResponse, ...]
    points: tuple[BuySellPointResponse, ...]


class LevelSignalResponse(ApiModel):
    timeframe: str
    timeframe_label: str
    trend_type: str
    latest_point: BuySellPointResponse | None
    lower_confirmation: BuySellPointResponse | None


class SignalConfluenceResponse(ApiModel):
    secid: str
    levels: tuple[LevelSignalResponse, ...]
    conclusion: str


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
    strength: int


class MarketSnapshotResponse(ApiModel):
    instrument: InstrumentResponse
    timeframe: str
    timeframe_label: str
    candles: tuple[CandleResponse, ...]
    analysis: ChanAnalysisResponse
    summary: MarketSummaryResponse
    guidance: MarketGuidanceResponse
    macd: tuple[MacdPointResponse, ...]
    signals: ChanSignalsResponse
    signal_quality: SignalQualityResponse | None = None


class RankingEntryResponse(ApiModel):
    rank: int
    stock: StockResponse
    bias: str
    strength: int
    latest: float
    change_percent: float
    invalidation_price: float | None
    latest_point: BuySellPointResponse | None = None
    buy_point: BuySellPointResponse | None = None
    sell_point: BuySellPointResponse | None = None
    trend: tuple[QuotePointResponse, ...] = ()
    signal_quality: SignalQualityResponse | None = None
    signal_group: str = "observation"


class RankingBoardResponse(ApiModel):
    generated_at: datetime | None
    timeframe: str
    bullish: tuple[RankingEntryResponse, ...]
    bearish: tuple[RankingEntryResponse, ...]
    trade_date: date | None = None
    total_count: int = 0
    analyzed_count: int = 0
    failed_count: int = 0
    state: str = "pending"
    progress_total: int = 0
    progress_processed: int = 0
    update_error: str | None = None
    analysis_version: int


class UserIdentityResponse(ApiModel):
    user_id: int
    nickname: str
    avatar_url: str | None


class SessionResponse(ApiModel):
    authenticated: bool
    user: UserIdentityResponse | None


class WebviewLoginRequest(BaseModel):
    code: str = Field(min_length=1, max_length=256)


class MiniProgramLinkResponse(ApiModel):
    url: str
