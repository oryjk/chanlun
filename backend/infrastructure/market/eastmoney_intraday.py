from datetime import datetime
from zoneinfo import ZoneInfo

from ...domain.errors import MarketDataNotFound
from ...domain.models import QuotePoint, QuoteSeries
from ...domain.security import SecurityId
from ..http.json_client import JsonHttpClient
from .eastmoney_codes import to_eastmoney

SHANGHAI_TZ = ZoneInfo("Asia/Shanghai")


class EastmoneyIntradayReader:
    URL = "http://push2his.eastmoney.com/api/qt/stock/trends2/get"
    FIELDS1 = "f1,f2,f3,f4,f5,f6"
    FIELDS2 = "f51,f52"

    def __init__(self, http_client: JsonHttpClient):
        self._http = http_client

    def read_intraday(self, secid: str) -> QuoteSeries:
        canonical_secid = str(SecurityId.parse(secid))
        payload = self._http.get(
            self.URL,
            {
                "secid": to_eastmoney(canonical_secid),
                "ndays": 1,
                "iscr": 0,
                "fields1": self.FIELDS1,
                "fields2": self.FIELDS2,
            },
        )
        data = payload.get("data")
        if not data:
            raise MarketDataNotFound("未找到该证券的当日分时数据")
        points = []
        for trend in data.get("trends") or []:
            try:
                time_value, price_value = trend.split(",")[:2]
                points.append(
                    QuotePoint(
                        time=datetime.strptime(time_value, "%Y-%m-%d %H:%M").replace(tzinfo=SHANGHAI_TZ),
                        price=float(price_value),
                    )
                )
            except (AttributeError, TypeError, ValueError):
                continue
        if not points:
            raise MarketDataNotFound("该证券的当日分时数据不足")
        return QuoteSeries(secid=canonical_secid, points=tuple(points))
