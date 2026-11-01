from fastapi import APIRouter, Query, Request

from ...application.queries.services import QueryServices
from ...domain.models import TimeFrame
from .schemas import MarketSnapshotResponse, StockResponse, StockSearchResponse

router = APIRouter()


def queries(request: Request) -> QueryServices:
    return request.app.state.queries


@router.get("/api/health")
def health() -> dict:
    return {"status": "ok"}


@router.get("/api/timeframes")
def timeframes() -> dict:
    return {"items": [{"value": period.value, "label": period.label} for period in TimeFrame]}


@router.get("/api/stocks/search", response_model=StockSearchResponse)
def search_stocks(request: Request, query: str = Query(alias="q", min_length=1, max_length=40)) -> StockSearchResponse:
    items = queries(request).search_stocks.execute(query)
    return StockSearchResponse(items=[StockResponse.model_validate(item) for item in items])


@router.get("/api/chan", response_model=MarketSnapshotResponse)
def get_chan_snapshot(
    request: Request,
    secid: str = "1.600519",
    timeframe: str = "30m",
    refresh: bool = False,
) -> MarketSnapshotResponse:
    snapshot = queries(request).get_market_snapshot.execute(secid, timeframe, refresh)
    return MarketSnapshotResponse.model_validate(snapshot)
