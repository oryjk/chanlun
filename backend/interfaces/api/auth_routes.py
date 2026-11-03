from fastapi import APIRouter, Query, Request, Response

from ...application.commands.services import CommandServices
from ...application.queries.services import QueryServices
from ...domain.models import UserIdentity
from .schemas import MiniProgramLinkResponse, SessionResponse, UserIdentityResponse, WebviewLoginRequest

SESSION_COOKIE = "chanlun_session"

router = APIRouter(prefix="/api/auth")


def queries(request: Request) -> QueryServices:
    return request.app.state.queries


def commands(request: Request) -> CommandServices:
    return request.app.state.commands


def session_response(identity: UserIdentity | None) -> SessionResponse:
    return SessionResponse(
        authenticated=identity is not None,
        user=UserIdentityResponse.model_validate(identity) if identity else None,
    )


@router.post("/webview-login", response_model=SessionResponse)
def webview_login(request: Request, response: Response, body: WebviewLoginRequest) -> SessionResponse:
    result = commands(request).login_with_webview_code.execute(body.code)
    response.set_cookie(
        SESSION_COOKIE,
        result.session_token,
        max_age=result.max_age_seconds,
        httponly=True,
        samesite="lax",
        secure=request.app.state.settings.session_cookie_secure,
        path="/",
    )
    return session_response(result.identity)


@router.get("/session", response_model=SessionResponse)
def get_session(request: Request) -> SessionResponse:
    return session_response(queries(request).get_current_user.execute(request.cookies.get(SESSION_COOKIE)))


@router.get("/mini-program-link", response_model=MiniProgramLinkResponse)
def get_mini_program_link(
    request: Request,
    return_query: str = Query(default="", max_length=1024),
) -> MiniProgramLinkResponse:
    return MiniProgramLinkResponse(url=queries(request).get_mini_program_link.execute(return_query))
