import hmac

from fastapi import APIRouter, HTTPException, Request, Response
from pydantic import BaseModel, Field

from .auth_routes import SESSION_COOKIE

router = APIRouter(prefix="/api/auth/sumly")


class IssueTicketRequest(BaseModel):
    user_id: int = Field(gt=0, le=2**63 - 1, strict=True)


class ExchangeTicketRequest(BaseModel):
    code: str = Field(pattern=r"^[A-Za-z0-9_-]{43}$")


def _bridge(request: Request):
    bridge = getattr(request.app.state, "sumly_bridge", None)
    if bridge is None:
        raise HTTPException(404)
    return bridge


@router.post("/tickets", status_code=201)
def issue_ticket(request: Request, response: Response, body: IssueTicketRequest) -> dict[str, str]:
    bridge = _bridge(request)
    if bridge.issuer_ip is not None and (
        request.client is None
        or request.client.host != bridge.issuer_ip
        or "x-forwarded-for" in request.headers
        or "forwarded" in request.headers
    ):
        raise HTTPException(403, "请求来源无权签发票据")
    expected = f"Bearer {bridge.secret}".encode()
    if not hmac.compare_digest(request.headers.get("authorization", "").encode(), expected):
        raise HTTPException(401, "未授权")
    response.headers["Cache-Control"] = "no-store"
    return {"code": bridge.login.issue(body.user_id)}


@router.post("/session", status_code=204)
def exchange_ticket(request: Request, body: ExchangeTicketRequest) -> Response:
    bridge = _bridge(request)
    token = bridge.login.exchange(body.code)
    if token is None:
        raise HTTPException(401, "登录凭证无效或已过期")
    response = Response(status_code=204, headers={"Cache-Control": "no-store"})
    response.set_cookie(
        SESSION_COOKIE,
        token,
        max_age=bridge.login.sessions.max_age_seconds,
        httponly=True,
        secure=request.app.state.settings.session_cookie_secure,
        samesite="lax",
        path="/",
    )
    return response
