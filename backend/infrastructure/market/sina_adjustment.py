import json
from bisect import bisect_right
from datetime import date, datetime
from threading import Lock
from zoneinfo import ZoneInfo

from ...domain.errors import MarketDataUnavailable
from ..http.json_client import JsonHttpClient

SHANGHAI = ZoneInfo("Asia/Shanghai")


class SinaForwardAdjustment:
    """新浪前复权因子：每个因子自其日期起生效至下一个因子日期，前复权价 = 不复权价 / 因子。

    因子只在除权除息日变化，按北京时间自然日缓存，避免全市场榜单每只证券重复请求。
    """

    URL = "https://finance.sina.com.cn/realstock/company/{symbol}/qfq.js"

    def __init__(self, http_client: JsonHttpClient, today=lambda: datetime.now(SHANGHAI).date()):
        self._http = http_client
        self._today = today
        self._cache: dict[str, tuple[date, tuple[tuple[date, ...], tuple[float, ...]]]] = {}
        self._lock = Lock()

    def factor_lookup(self, symbol: str):
        today = self._today()
        with self._lock:
            cached = self._cache.get(symbol)
        if cached is None or cached[0] != today:
            cached = (today, self._load(symbol))
            with self._lock:
                self._cache[symbol] = cached
        starts, factors = cached[1]

        def factor(day: date) -> float:
            index = bisect_right(starts, day) - 1
            return factors[index] if index >= 0 else factors[0]

        return factor

    def _load(self, symbol: str):
        text = self._http.get_text(self.URL.format(symbol=symbol), {})
        marker = text.find("=")
        if marker < 0:
            raise MarketDataUnavailable("复权数据暂时不可用，请稍后重试")
        try:
            payload, _ = json.JSONDecoder().raw_decode(text[marker + 1:].lstrip())
            pairs = sorted((date.fromisoformat(item["d"]), float(item["f"])) for item in payload["data"])
        except (json.JSONDecodeError, KeyError, TypeError, ValueError) as error:
            raise MarketDataUnavailable("复权数据暂时不可用，请稍后重试") from error
        if not pairs or any(factor <= 0 for _, factor in pairs):
            raise MarketDataUnavailable("复权数据暂时不可用，请稍后重试")
        return tuple(day for day, _ in pairs), tuple(factor for _, factor in pairs)
