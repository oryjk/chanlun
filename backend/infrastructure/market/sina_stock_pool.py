import time

from ...domain.errors import MarketDataNotFound, MarketDataUnavailable
from ...domain.models import Stock
from ..http.json_client import JsonHttpClient


class SinaStockPoolReader:
    """新浪行情中心全市场清单：沪 A、深 A 与 ETF 三个节点分页枚举并逐节点核对数量。"""

    BASE = "https://vip.stock.finance.sina.com.cn/quotes_service/api/json_v2.php/Market_Center"
    NODES = ("sh_a", "sz_a", "etf_hq_fund")
    PAGE_SIZE = 100
    PAGE_ATTEMPTS = 3
    RETRY_DELAY_SECONDS = 1.0

    def __init__(self, http_client: JsonHttpClient, sleep=time.sleep):
        self._http = http_client
        self._sleep = sleep

    def list_all(self, *, cancelled=lambda: False) -> tuple[Stock, ...]:
        stocks: dict[str, Stock] = {}
        for node in self.NODES:
            for row in self._node_rows(node, cancelled):
                stock = self._to_stock(row)
                if stock is not None:
                    stocks.setdefault(stock.secid, stock)
        if not stocks:
            raise MarketDataNotFound("暂未获取到沪深股票和 ETF 清单")
        return tuple(stocks.values())

    def _node_rows(self, node, cancelled):
        expected = self._count(node)
        rows = []
        pages = (expected + self.PAGE_SIZE - 1) // self.PAGE_SIZE
        for page in range(1, pages + 1):
            if cancelled():
                raise InterruptedError
            rows.extend(self._page(node, page))
        symbols = {str(row.get("symbol") or "") for row in rows if isinstance(row, dict)}
        if len(rows) != expected or len(symbols) != expected or "" in symbols:
            raise MarketDataUnavailable("证券清单暂不完整，请稍后重试")
        if self._count(node) != expected:
            raise MarketDataUnavailable("证券清单发生变化，请稍后重试")
        return rows

    def _page(self, node, page):
        # 新浪分页偶发返回空或连接中断，单页有限重试，避免整轮清单失败后进入长时间退避。
        for attempt in range(self.PAGE_ATTEMPTS):
            try:
                items = self._http.get(f"{self.BASE}.getHQNodeData", {
                    "page": page, "num": self.PAGE_SIZE, "sort": "symbol", "asc": 1, "node": node,
                })
                if isinstance(items, list) and items:
                    return items
            except MarketDataUnavailable:
                pass
            if attempt + 1 < self.PAGE_ATTEMPTS:
                self._sleep(self.RETRY_DELAY_SECONDS * (attempt + 1))
        raise MarketDataUnavailable("证券清单暂不完整，请稍后重试")

    def _count(self, node) -> int:
        text = self._http.get_text(f"{self.BASE}.getHQNodeStockCount", {"node": node}).strip().strip('"')
        if not text.isdigit() or not 0 < int(text) <= 50000:
            raise MarketDataUnavailable("证券清单暂不完整，请稍后重试")
        return int(text)

    @staticmethod
    def _to_stock(row) -> Stock | None:
        symbol = str(row.get("symbol") or "")
        exchange, code = symbol[:2], symbol[2:]
        if exchange not in {"sh", "sz"}:
            return None
        if len(code) != 6 or not code.isdigit():
            raise MarketDataUnavailable("证券清单包含无效代码，请稍后重试")
        market = "1" if exchange == "sh" else "0"
        name = str(row.get("name") or code)
        # 与东方财富清单保持同一范围：沪市 6 开头、深市 0/3 开头的 A 股，沪市 5 开头、深市 15 开头的 ETF。
        equity = (market == "0" and code.startswith(("0", "3"))) or (market == "1" and code.startswith("6"))
        etf = ((market == "0" and code.startswith("15")) or (market == "1" and code.startswith("5"))) \
            and not any(term in name.upper() for term in ("LOF", "REIT"))
        if not (equity or etf):
            return None
        return Stock(f"{market}.{code}", code, name, "ETF" if etf else "沪A" if market == "1" else "深A")
