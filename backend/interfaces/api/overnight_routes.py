from datetime import date, datetime
from typing import Literal

from fastapi import APIRouter, HTTPException, Request, Response
from pydantic import BaseModel, ConfigDict

from ...application.queries.services import QueryServices


router = APIRouter()


class OvernightCandidateResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    secid: str
    symbol: str
    name: str
    price: float
    change_percent: float
    volume_ratio: float
    turnover_rate: float
    total_market_cap: float
    limit_up_date: date
    as_of: datetime
    average_price: float | None
    benchmark_change_percent: float | None
    pullback_at: datetime | None
    minute_at: datetime | None
    minute_change_percent: float | None
    status: Literal['watching', 'pullback', 'filtered', 'unavailable']
    reason: str | None


class OvernightReminderResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    secid: str
    symbol: str
    name: str
    signal_date: date
    signal_at: datetime
    exception_observed: bool | None
    exception_as_of: datetime | None
    exception_valid_until: datetime | None


class OvernightResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    trade_date: date | None
    phase: Literal['waiting', 'preparing', 'scanning', 'closed', 'exit']
    observed_at: datetime | None
    history_sessions: int
    universe_count: int
    fresh_count: int
    matched_count: int
    minute_checked_count: int
    elapsed_ms: int | None
    max_quote_age_seconds: float | None
    status: Literal['pending', 'ready', 'partial', 'unavailable']
    candidates: tuple[OvernightCandidateResponse, ...]
    reminders: tuple[OvernightReminderResponse, ...]
    reminders_until: datetime | None


@router.get('/api/methods/overnight', response_model=OvernightResponse)
def get_overnight(request: Request, response: Response) -> OvernightResponse:
    queries: QueryServices = request.app.state.queries
    if queries.get_overnight is None:
        raise HTTPException(503, '隔夜套利数据暂不可用')
    response.headers['Cache-Control'] = 'no-store'
    return OvernightResponse.model_validate(queries.get_overnight.execute())
