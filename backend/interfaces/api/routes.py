from fastapi import APIRouter, Query, Request

from ...application.commands.services import CommandServices
from ...application.queries.services import QueryServices
from ...domain.models import TimeFrame
from .schemas import (
    MarketSnapshotResponse,
    QuoteSampleRequest,
    QuoteSeriesResponse,
    RankingBoardResponse,
    RealtimeQuoteResponse,
    SignalConfluenceResponse,
    StockResponse,
    StockSearchResponse,
)

router = APIRouter()


def queries(request: Request) -> QueryServices:
    return request.app.state.queries


def commands(request: Request) -> CommandServices:
    return request.app.state.commands


@router.get("/api/health")
def health() -> dict:
    return {"status": "ok"}


@router.get("/api/rankings", response_model=RankingBoardResponse)
def get_rankings(request: Request, limit: int = Query(default=500, ge=1, le=5000)) -> RankingBoardResponse:
    board = queries(request).get_rankings.execute(limit)
    return RankingBoardResponse.model_validate(board)


@router.get("/api/timeframes")
def timeframes() -> dict:
    return {"items": [{"value": period.value, "label": period.label} for period in TimeFrame]}


@router.get("/api/stocks/search", response_model=StockSearchResponse)
def search_stocks(request: Request, query: str = Query(alias="q", min_length=1, max_length=40)) -> StockSearchResponse:
    items = queries(request).search_stocks.execute(query)
    return StockSearchResponse(items=[StockResponse.model_validate(item) for item in items])


@router.get("/api/quote", response_model=RealtimeQuoteResponse)
def get_realtime_quote(request: Request, secid: str) -> RealtimeQuoteResponse:
    quote = queries(request).get_quote.execute(secid)
    return RealtimeQuoteResponse.model_validate(quote)


@router.get("/api/quote/series", response_model=QuoteSeriesResponse)
def get_quote_series(request: Request, secid: str) -> QuoteSeriesResponse:
    series = queries(request).get_quote_series.execute(secid)
    return QuoteSeriesResponse.model_validate(series)


@router.post("/api/quote/samples", response_model=QuoteSeriesResponse)
def record_quote_sample(request: Request, sample: QuoteSampleRequest) -> QuoteSeriesResponse:
    series = commands(request).record_quote_sample.execute(sample.secid, sample.time, sample.price)
    return QuoteSeriesResponse.model_validate(series)


@router.get("/api/chan", response_model=MarketSnapshotResponse)
def get_chan_snapshot(
    request: Request,
    secid: str = "1.600519",
    timeframe: str = "30m",
    refresh: bool = False,
) -> MarketSnapshotResponse:
    snapshot = queries(request).get_market_snapshot.execute(secid, timeframe, refresh)
    return MarketSnapshotResponse.model_validate(snapshot)


@router.get("/api/chan/confluence", response_model=SignalConfluenceResponse)
def get_signal_confluence(request: Request, secid: str) -> SignalConfluenceResponse:
    confluence = queries(request).get_signal_confluence.execute(secid)
    return SignalConfluenceResponse.model_validate(confluence)
