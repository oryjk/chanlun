from ...domain.errors import MarketDataNotFound, MarketDataUnavailable
from ...domain.models import Stock
from ..http.json_client import JsonHttpClient


class EastmoneyStockPoolReader:
    URL = "http://push2.eastmoney.com/api/qt/clist/get"
    MARKETS = "m:0+t:6,m:0+t:80,m:1+t:2,m:1+t:23,b:MK0021,b:MK0022,b:MK0023,b:MK0024"

    def __init__(self, http_client: JsonHttpClient):
        self._http = http_client

    def list_all(self, *, cancelled=lambda: False) -> tuple[Stock, ...]:
        page_size = 100
        rows = []
        expected = None
        page = 1
        while expected is None or len(rows) < expected:
            if cancelled():
                raise InterruptedError
            payload = self._http.get(self.URL, {
                "pn": page, "pz": page_size, "po": 1, "np": 1, "fltt": 2,
                "invt": 2, "fid": "f6", "fs": self.MARKETS, "fields": "f12,f13,f14,f6",
            })
            data = payload.get("data") or {}
            total = data.get("total")
            items = data.get("diff")
            if not isinstance(total, int) or isinstance(total, bool) or not 0 < total <= 50000 or not isinstance(items, list) or not items:
                raise MarketDataUnavailable("证券清单暂不完整，请稍后重试")
            if expected is not None and total != expected:
                raise MarketDataUnavailable("证券清单发生变化，请稍后重试")
            expected = total
            rows.extend(items)
            page += 1
            if page > (expected + page_size - 1) // page_size + 1:
                raise MarketDataUnavailable("证券清单暂不完整，请稍后重试")
        identities = {(str(row.get("f13", "")), str(row.get("f12") or "")) for row in rows}
        if len(rows) != expected or len(identities) != expected or any(not code for _, code in identities):
            raise MarketDataUnavailable("证券清单暂不完整，请稍后重试")
        stocks = []
        for row in rows:
            market, code = str(row.get("f13", "")), str(row["f12"])
            name = str(row.get("f14") or code)
            if not market.isdigit() or isinstance(row.get("f13"), bool):
                raise MarketDataUnavailable("证券清单包含无效标识，请稍后重试")
            if market not in {"0", "1"}:
                continue
            if len(code) != 6 or not code.isdigit():
                raise MarketDataUnavailable("证券清单包含无效代码，请稍后重试")
            # MARKETS explicitly selects the four A-share boards and four ETF product groups.
            # Code validation excludes securities outside those exchange board conventions.
            equity = (market == "0" and code.startswith(("0", "3"))) or (market == "1" and code.startswith("6"))
            etf = ((market == "0" and code.startswith("15")) or (market == "1" and code.startswith("5"))) and not any(term in name.upper() for term in ("LOF", "REIT"))
            if equity or etf:
                stocks.append(Stock(f"{market}.{code}", code, name, "ETF" if etf else "沪A" if market == "1" else "深A"))
        if not stocks:
            raise MarketDataNotFound("暂未获取到沪深股票和 ETF 清单")
        return tuple(stocks)

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
            market = str(item.get("f13", ""))
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
