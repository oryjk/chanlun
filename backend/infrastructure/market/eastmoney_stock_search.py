from ...domain.models import Stock
from ..http.json_client import JsonHttpClient


class EastmoneyStockSearchReader:
    URL = "https://searchapi.eastmoney.com/api/suggest/get"
    TOKEN = "D43BF722C8E33BDC906FB84D85E326E8"
    SUPPORTED_CLASSES = {"AStock", "HK", "UsStock", "Fund"}

    def __init__(self, http_client: JsonHttpClient):
        self._http = http_client

    def search(self, query: str) -> list[Stock]:
        payload = self._http.get(
            self.URL,
            {"input": query, "type": 14, "token": self.TOKEN, "count": 12},
        )
        items = (payload.get("QuotationCodeTable") or {}).get("Data") or []
        results = []
        for item in items:
            if not self._is_supported(item) or not item.get("QuoteID"):
                continue
            is_etf = item.get("Classify") == "Fund"
            results.append(
                Stock(
                    secid=item["QuoteID"],
                    symbol=item.get("Code") or item.get("UnifiedCode"),
                    name=item.get("Name") or item.get("Code"),
                    market="ETF" if is_etf else item.get("SecurityTypeName") or item.get("Classify"),
                )
            )
        return results[:8]

    def _is_supported(self, item: dict) -> bool:
        classify = item.get("Classify")
        if classify not in self.SUPPORTED_CLASSES:
            return False
        return classify != "Fund" or "ETF" in (item.get("Name") or "").upper()
