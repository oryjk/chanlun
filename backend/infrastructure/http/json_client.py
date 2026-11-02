import json
import socket
from http.client import RemoteDisconnected
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

from ...domain.errors import MarketDataNotFound, MarketDataUnavailable


class JsonHttpClient:
    def get(self, url: str, params: dict[str, Any]) -> dict:
        try:
            return json.loads(self.get_text(url, params))
        except json.JSONDecodeError as error:
            raise MarketDataUnavailable("行情服务暂时不可用，请稍后重试") from error

    def get_text(self, url: str, params: dict[str, Any]) -> str:
        request = Request(
            f"{url}?{urlencode(params)}",
            headers={
                "Accept": "application/json",
                "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/131.0.0.0 Safari/537.36",
            },
        )
        try:
            with urlopen(request, timeout=15) as response:
                encoding = response.headers.get_content_charset() or "utf-8"
                return response.read().decode(encoding)
        except HTTPError as error:
            if error.code == 404:
                raise MarketDataNotFound("未找到该证券的行情数据") from error
            raise MarketDataUnavailable("行情服务暂时不可用，请稍后重试") from error
        except (URLError, RemoteDisconnected, ConnectionError, socket.timeout, TimeoutError) as error:
            raise MarketDataUnavailable("行情服务暂时不可用，请稍后重试") from error
