from dataclasses import asdict

from fastapi import APIRouter, HTTPException, Request
from pydantic import BaseModel, Field

from ...domain.app_auth import AuthRejected, AuthUnavailable

router = APIRouter(prefix='/api/app-auth')


class AppleLoginRequest(BaseModel):
    challenge_id: str = Field(min_length=1, max_length=256)
    identity_token: str = Field(min_length=1, max_length=16384)
    authorization_code: str = Field(min_length=1, max_length=4096)
    nickname: str | None = Field(default=None, max_length=60)


class AppleNotificationRequest(BaseModel):
    payload: str = Field(min_length=1, max_length=32768)


def services(request):
    value = request.app.state.native_auth
    if value.commands is None:
        raise HTTPException(503, 'Apple 登录尚未配置，行情仍可正常使用')
    return value


def bearer(request):
    value = request.headers.get('authorization', '')
    scheme, _, token = value.partition(' ')
    return token if scheme.lower() == 'bearer' and 0 < len(token) <= 256 else None


def invoke(action):
    try:
        return action()
    except AuthRejected as error:
        raise HTTPException(401, str(error)) from None
    except AuthUnavailable as error:
        raise HTTPException(502, str(error)) from None


@router.post('/challenge')
def challenge(request: Request):
    return asdict(services(request).commands.challenge())


@router.post('/apple')
def login(request: Request, body: AppleLoginRequest):
    return asdict(invoke(lambda: services(request).commands.login(
        body.challenge_id, body.identity_token, body.authorization_code, body.nickname)))


@router.get('/session')
def session(request: Request):
    value = request.app.state.native_auth
    user = value.session.execute(bearer(request)) if value.session else None
    return {'authenticated':user is not None, 'user':asdict(user) if user else None}


@router.post('/logout')
def logout(request: Request):
    services(request).commands.logout(bearer(request))
    return {'ok':True}


@router.post('/delete-account')
def delete_account(request: Request):
    token = bearer(request)
    if not token:
        raise HTTPException(401, '请先登录')
    invoke(lambda: services(request).commands.delete_account(token))
    return {'ok':True}


@router.post('/apple-notifications')
def notifications(request: Request, body: AppleNotificationRequest):
    value = services(request)
    event = invoke(lambda: value.apple.verify_notification(body.payload))
    value.commands.process_event(*event)
    return {'ok':True}
