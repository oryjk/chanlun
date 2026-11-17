from fastapi import APIRouter, HTTPException, Request

from ...domain.app_auth import AppIdentity
from .app_auth_routes import bearer
from .schemas import (
    CancelTrackingRequest,
    StartTrackingRequest,
    StockResponse,
    TrackedAssetRecordResponse,
    TrackedAssetSummaryResponse,
    TrackingOverviewResponse,
)

router = APIRouter(prefix="/api/app-tracking")


def _require_user(request: Request) -> AppIdentity:
    auth = getattr(request.app.state, "native_auth", None)
    session_query = auth.session if auth is not None else None
    if session_query is None:
        raise HTTPException(503, "原生账号服务尚未配置")
    token = bearer(request)
    user = session_query.execute(token)
    if user is None:
        raise HTTPException(401, "请先登录")
    return user


def require_tracking_services(request: Request):
    services = getattr(request.app.state, "native_tracking", None)
    if services is None or services.query is None or services.commands is None:
        raise HTTPException(503, "跟踪服务尚未配置")
    return services


def build_tracking_overview_response(overview) -> TrackingOverviewResponse:
    """Serialize domain TrackingOverview into HTTP response DTO."""
    return TrackingOverviewResponse(
        as_of_date=overview.as_of_date.isoformat(),
        items=tuple(
            TrackedAssetSummaryResponse(
                stock=StockResponse.model_validate(item.stock),
                start_date=item.start_date.isoformat(),
                start_price=item.start_price,
                latest_price=item.latest_price,
                day_return_pct=item.day_return_pct,
                day_profit=item.day_profit,
                cumulative_return_pct=item.cumulative_return_pct,
                cumulative_profit=item.cumulative_profit,
                current_value=item.current_value,
                sparkline=item.sparkline,
            )
            for item in overview.items
        ),
    )


def build_start_tracking_payload(record) -> dict:
    """Serialize created/existing TrackedAssetRecord response payload."""
    return {
        "ok": True,
        "item": TrackedAssetRecordResponse(
            secid=record.secid,
            symbol=record.symbol,
            name=record.name,
            market=record.market,
            start_date=record.start_date.isoformat(),
            start_price=record.start_price,
        ).model_dump(),
    }


def collect_cancel_secids(body: CancelTrackingRequest) -> list[str]:
    targets = list(body.secids)
    if body.secid:
        targets.append(body.secid)
    return targets


@router.get("", response_model=TrackingOverviewResponse)
def list_trackings(request: Request) -> TrackingOverviewResponse:
    user = _require_user(request)
    overview = require_tracking_services(request).query.execute(user.user_id)
    return build_tracking_overview_response(overview)


@router.post("/start")
def start_tracking(request: Request, body: StartTrackingRequest) -> dict:
    user = _require_user(request)
    record = require_tracking_services(request).commands.start(user.user_id, body.secid)
    return build_start_tracking_payload(record)


@router.post("/cancel")
def cancel_tracking(request: Request, body: CancelTrackingRequest) -> dict:
    user = _require_user(request)
    removed = require_tracking_services(request).commands.cancel(user.user_id, collect_cancel_secids(body))
    return {"ok": True, "removed": removed}
