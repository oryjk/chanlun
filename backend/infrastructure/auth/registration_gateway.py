import json
import socket
from collections.abc import Callable
from http.client import RemoteDisconnected
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

from ...domain.errors import AuthenticationFailed, IdentityServiceUnavailable
from ...domain.models import UserIdentity

# (method, url, json_body, headers) -> (status, parsed_json)
Transport = Callable[[str, str, dict | None, dict], tuple[int, dict | None]]


def urllib_transport(method: str, url: str, body: dict | None, headers: dict) -> tuple[int, dict | None]:
    data = json.dumps(body).encode("utf-8") if body is not None else None
    request = Request(url, data=data, method=method, headers={"Accept": "application/json", **headers})
    if data is not None:
        request.add_header("Content-Type", "application/json")
    try:
        with urlopen(request, timeout=10) as response:
            return response.status, json.loads(response.read() or b"null")
    except HTTPError as error:
        return error.code, None
    except (URLError, RemoteDisconnected, ConnectionError, socket.timeout, TimeoutError, json.JSONDecodeError) as error:
        raise IdentityServiceUnavailable("报名系统暂时不可用，请稍后重试") from error


class RegistrationSystemGateway:
    """报名系统 App API（/api/v1/app）适配器：web-view code 换身份、生成小程序外链。

    报名系统的 JWT 只在服务端短暂使用，用来读取用户资料，不会下发给缠观前端。
    """

    def __init__(self, base_url: str, transport: Transport = urllib_transport):
        self._base_url = base_url.rstrip("/")
        self._transport = transport

    def exchange_webview_code(self, code: str) -> UserIdentity:
        status, payload = self._transport("POST", f"{self._base_url}/auth/webview-codes/exchange", {"code": code}, {})
        if status == 401:
            raise AuthenticationFailed("登录凭证无效或已过期")
        token = self._data(status, payload).get("token")
        if not token:
            raise IdentityServiceUnavailable("报名系统返回格式异常")

        status, payload = self._transport("GET", f"{self._base_url}/users/me", None, {"Authorization": f"Bearer {token}"})
        if status == 401:
            raise AuthenticationFailed("登录凭证无效或已过期")
        profile = self._data(status, payload)
        try:
            return UserIdentity(
                user_id=int(profile["id"]),
                nickname=str(profile.get("nickname") or "微信用户"),
                avatar_url=profile.get("avatar_url"),
            )
        except (KeyError, TypeError, ValueError) as error:
            raise IdentityServiceUnavailable("报名系统返回格式异常") from error

    def chanlun_link(self, return_query: str) -> str:
        status, payload = self._transport(
            "POST", f"{self._base_url}/mini-links/chanlun", {"return_query": return_query}, {}
        )
        try:
            link = self._data(status, payload).get("url")
        except IdentityServiceUnavailable as error:
            raise IdentityServiceUnavailable("暂时无法生成小程序链接，请在微信中搜索「报名系统」小程序进入") from error
        if not link:
            raise IdentityServiceUnavailable("暂时无法生成小程序链接，请在微信中搜索「报名系统」小程序进入")
        return link

    def _data(self, status: int, payload: dict | None) -> dict:
        if status != 200 or not isinstance(payload, dict) or payload.get("code") != 0:
            raise IdentityServiceUnavailable("报名系统暂时不可用，请稍后重试")
        data = payload.get("data")
        if not isinstance(data, dict):
            raise IdentityServiceUnavailable("报名系统返回格式异常")
        return data
