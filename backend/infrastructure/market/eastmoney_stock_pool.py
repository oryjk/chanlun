from ...domain.errors import MarketDataNotFound
from ...domain.models import Stock
from ..http.json_client import JsonHttpClient


class EastmoneyStockPoolReader:
    URL = "http://push2.eastmoney.com/api/qt/clist/get"
    MARKETS = "m:0+t:6,m:0+t:80,m:1+t:2,m:1+t:23,b:MK0021,b:MK0022,b:MK0023,b:MK0024"

    def __init__(self, http_client: JsonHttpClient):
        self._http = http_client

    def list_liquid(self, limit: int) -> list[Stock]:
        payload = self._http.get(
            self.URL,
            {
                "pn": 1,
                "pz": limit,
                "po": 1,
                "np": 1,
                "fltt": 2,
                "invt": 2,
                "fid": "f6",
                "fs": self.MARKETS,
                "fields": "f12,f13,f14,f6",
            },
        )
        items = (payload.get("data") or {}).get("diff") or []
        stocks = []
        for item in items:
            code = str(item.get("f12") or "")
            market = str(item.get("f13") or "")
            if not code or market not in {"0", "1"}:
                continue
            is_etf = code.startswith(("5", "15"))
            stocks.append(
                Stock(
                    secid=f"{market}.{code}",
                    symbol=code,
                    name=item.get("f14") or code,
                    market="ETF" if is_etf else "沪A" if market == "1" else "深A",
                )
            )
        if not stocks:
            raise MarketDataNotFound("暂未找到榜单候选证券")
        return stocks[:limit]
