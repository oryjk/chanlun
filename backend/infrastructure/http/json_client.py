import json
import os
import socket
from http.client import RemoteDisconnected
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import ProxyHandler, Request, build_opener

from ...domain.errors import MarketDataNotFound, MarketDataUnavailable

_DEFAULT_USER_AGENT = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/131.0.0.0 Safari/537.36"
)


class JsonHttpClient:
    def __init__(self, *, use_env_proxy: bool | None = None):
        resolved_proxy = (
            os.getenv("CHANLUN_HTTP_USE_ENV_PROXY", "false").lower() == "true"
            if use_env_proxy is None
            else use_env_proxy
        )
        # 国内行情源默认直连，避免宿主环境变量里的代理干扰造成间歇性 502 / 断连
        self._opener = build_opener() if resolved_proxy else build_opener(ProxyHandler({}))

    def get(self, url: str, params: dict[str, Any]) -> dict:
        try:
            return json.loads(self.get_text(url, params))
        except json.JSONDecodeError as error:
            raise MarketDataUnavailable("行情服务暂时不可用，请稍后重试") from error

    def get_text(
        self,
        url: str,
        params: dict[str, Any] | None = None,
        *,
        headers: dict[str, str] | None = None,
        encoding: str | None = None,
    ) -> str:
        full_url = f"{url}?{urlencode(params)}" if params else url
        merged_headers = {
            "Accept": "application/json",
            "User-Agent": _DEFAULT_USER_AGENT,
        }
        if headers:
            merged_headers.update(headers)
        request = Request(full_url, headers=merged_headers)
        try:
            with self._opener.open(request, timeout=15) as response:
                charset = encoding or response.headers.get_content_charset() or "utf-8"
                return response.read().decode(charset, errors="replace")
        except HTTPError as error:
            if error.code == 404:
                raise MarketDataNotFound("未找到该证券的行情数据") from error
            raise MarketDataUnavailable("行情服务暂时不可用，请稍后重试") from error
        except (URLError, RemoteDisconnected, ConnectionError, socket.timeout, TimeoutError) as error:
            raise MarketDataUnavailable("行情服务暂时不可用，请稍后重试") from error
