from datetime import datetime, timezone

from ...domain.errors import MarketDataNotFound
from ...domain.models import RealtimeQuote
from ...domain.security import SecurityId
from ..http.json_client import JsonHttpClient
from .eastmoney_codes import to_eastmoney


class EastmoneyQuoteReader:
    URL = "http://push2.eastmoney.com/api/qt/stock/get"
    FIELDS = "f43,f44,f45,f46,f47,f48,f57,f58,f59,f60,f86,f169,f170"

    def __init__(self, http_client: JsonHttpClient):
        self._http = http_client

    def read_quote(self, secid: str) -> RealtimeQuote:
        payload = self._http.get(self.URL, {"secid": to_eastmoney(secid), "fields": self.FIELDS})
        data = payload.get("data")
        if not data:
            raise MarketDataNotFound("未找到该证券的实时行情")
        return self._to_quote(secid, data)

    def _to_quote(self, secid: str, data: dict) -> RealtimeQuote:
        sid = SecurityId.parse(secid)
        canonical_secid = str(sid)
        decimal = int(data.get("f59") or 2)
        scale = 10**decimal

        def price(field: str) -> float:
            return round(float(data[field]) / scale, decimal)

        return RealtimeQuote(
            secid=canonical_secid,
            symbol=str(data.get("f57") or sid.code),
            name=data.get("f58") or canonical_secid,
            time=datetime.fromtimestamp(int(data["f86"]), timezone.utc),
            open=price("f46"),
            high=price("f44"),
            low=price("f45"),
            previous_close=price("f60"),
            latest=price("f43"),
            change=price("f169"),
            change_percent=round(float(data["f170"]) / 100, 2),
            volume=round(float(data["f47"]) * 100),
            amount=round(float(data["f48"]), 2),
        )
