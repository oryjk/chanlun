from fastapi import APIRouter, HTTPException, Request

from .app_tracking_routes import (
    build_start_tracking_payload,
    build_tracking_overview_response,
    collect_cancel_secids,
    require_tracking_services,
)
from .auth_routes import SESSION_COOKIE
from .schemas import CancelTrackingRequest, StartTrackingRequest, TrackingOverviewResponse

router = APIRouter(prefix="/api/tracking")


def _require_h5_user_key(request: Request) -> str:
    queries = getattr(request.app.state, "queries", None)
    session_query = queries.get_current_user if queries is not None else None
    if session_query is None:
        raise HTTPException(503, "账号会话服务尚未配置")
    identity = session_query.execute(request.cookies.get(SESSION_COOKIE))
    if identity is None:
        raise HTTPException(401, "请先登录")
    return identity.tracking_key


@router.get("", response_model=TrackingOverviewResponse)
def list_h5_trackings(request: Request) -> TrackingOverviewResponse:
    user_key = _require_h5_user_key(request)
    overview = require_tracking_services(request).query.execute(user_key)
    return build_tracking_overview_response(overview)


@router.post("/start")
def start_h5_tracking(request: Request, body: StartTrackingRequest) -> dict:
    user_key = _require_h5_user_key(request)
    record = require_tracking_services(request).commands.start(user_key, body.secid)
    return build_start_tracking_payload(record)


@router.post("/cancel")
def cancel_h5_tracking(request: Request, body: CancelTrackingRequest) -> dict:
    user_key = _require_h5_user_key(request)
    removed = require_tracking_services(request).commands.cancel(user_key, collect_cancel_secids(body))
    return {"ok": True, "removed": removed}
