from __future__ import annotations

import logging
import time
from threading import Lock

from ...domain.errors import MarketDataNotFound, MarketDataUnavailable
from ...domain.models import Stock
from ...domain.tracking import AssetCategory, AssetSortField, MarketAssetItem
from ..http.json_client import JsonHttpClient
from .eastmoney_codes import from_eastmoney
from .fallback_market_data import _PrimaryCircuitBreaker


def _num(value, default: float = 0.0) -> float:
    try:
        parsed = float(value)
    except (TypeError, ValueError):
        return default
    return parsed if parsed == parsed else default


def _sort_value(item: MarketAssetItem, sort_field: AssetSortField) -> float:
    if sort_field is AssetSortField.CHANGE_PERCENT:
        return item.change_percent
    if sort_field is AssetSortField.LATEST:
        return item.latest
    return item.amount


class MarketAssetCatalogAdapter:
    """沪深股票与 ETF 资产清单适配器：东方财富主源 + 新浪行情中心备用 + 20 秒短期缓存。"""

    EM_URL = "http://push2.eastmoney.com/api/qt/clist/get"
    EM_FS = {
        AssetCategory.STOCK: "m:0+t:6,m:0+t:80,m:1+t:2,m:1+t:23",
        AssetCategory.ETF: "b:MK0021,b:MK0022,b:MK0023,b:MK0024",
    }
    EM_FID = {
        AssetSortField.AMOUNT: "f6",
        AssetSortField.CHANGE_PERCENT: "f3",
        AssetSortField.LATEST: "f2",
    }

    SINA_URL = "https://vip.stock.finance.sina.com.cn/quotes_service/api/json_v2.php/Market_Center.getHQNodeData"
    SINA_NODE = {
        AssetCategory.STOCK: "hs_a",
        AssetCategory.ETF: "etf_hq_fund",
    }
    SINA_SORT = {
        AssetSortField.AMOUNT: "amount",
        AssetSortField.CHANGE_PERCENT: "changepercent",
        AssetSortField.LATEST: "trade",
    }

    def __init__(
        self,
        http_client: JsonHttpClient,
        *,
        ttl_seconds: float = 20.0,
        clock=time.monotonic,
    ):
        self._http = http_client
        self._ttl = ttl_seconds
        self._clock = clock
        self._breaker = _PrimaryCircuitBreaker(clock=clock, label="主资产清单")
        self._cache: dict[tuple[AssetCategory, AssetSortField, bool, int], tuple[float, tuple[MarketAssetItem, ...]]] = {}
        self._lock = Lock()

    def list_assets(
        self,
        category: AssetCategory,
        *,
        sort_field: AssetSortField,
        descending: bool,
        limit: int,
    ) -> tuple[MarketAssetItem, ...]:
        key = (category, sort_field, descending, limit)
        now = self._clock()
        with self._lock:
            cached = self._cache.get(key)
            if cached is not None and now - cached[0] < self._ttl:
                return cached[1]

        items = self._fetch(category, sort_field=sort_field, descending=descending, limit=limit)
        with self._lock:
            self._cache[key] = (self._clock(), items)
        return items

    def _fetch(
        self,
        category: AssetCategory,
        *,
        sort_field: AssetSortField,
        descending: bool,
        limit: int,
    ) -> tuple[MarketAssetItem, ...]:
        if not self._breaker.suspended():
            try:
                items = self._fetch_eastmoney(category, sort_field=sort_field, descending=descending, limit=limit)
                if items:
                    self._breaker.record_success()
                    return items
            except MarketDataUnavailable:
                self._breaker.record_failure()
                logging.getLogger(__name__).warning("东方财富资产清单不可用，切换至新浪行情中心")
        return self._fetch_sina(category, sort_field=sort_field, descending=descending, limit=limit)

    def _fetch_eastmoney(
        self,
        category: AssetCategory,
        *,
        sort_field: AssetSortField,
        descending: bool,
        limit: int,
    ) -> tuple[MarketAssetItem, ...]:
        fetch_size = min(200, max(limit + 20, 80))
        payload = self._http.get(
            self.EM_URL,
            {
                "pn": 1,
                "pz": fetch_size,
                "po": 1 if descending else 0,
                "np": 1,
                "fltt": 2,
                "invt": 2,
                "fid": self.EM_FID[sort_field],
                "fs": self.EM_FS[category],
                "fields": "f2,f3,f4,f5,f6,f12,f13,f14,f15,f16,f17,f18",
            },
        )
        rows = (payload.get("data") or {}).get("diff") or []
        items: list[MarketAssetItem] = []
        for row in rows:
            item = self._parse_eastmoney_row(row, category)
            if item is not None:
                items.append(item)
        if not items:
            raise MarketDataNotFound("暂未获取到资产行情清单")
        items.sort(key=lambda entry: _sort_value(entry, sort_field), reverse=descending)
        return tuple(items[:limit])

    def _fetch_sina(
        self,
        category: AssetCategory,
        *,
        sort_field: AssetSortField,
        descending: bool,
        limit: int,
    ) -> tuple[MarketAssetItem, ...]:
        fetch_size = min(100, max(limit + 20, 80))
        rows = self._http.get(
            self.SINA_URL,
            {
                "page": 1,
                "num": fetch_size,
                "sort": self.SINA_SORT[sort_field],
                "asc": 0 if descending else 1,
                "node": self.SINA_NODE[category],
            },
        )
        if not isinstance(rows, list):
            raise MarketDataUnavailable("资产清单返回格式异常")
        items: list[MarketAssetItem] = []
        for row in rows:
            item = self._parse_sina_row(row, category)
            if item is not None:
                items.append(item)
        if not items:
            raise MarketDataNotFound("暂未获取到资产行情清单")
        items.sort(key=lambda entry: _sort_value(entry, sort_field), reverse=descending)
        return tuple(items[:limit])

    @staticmethod
    def _parse_eastmoney_row(row: dict, category: AssetCategory) -> MarketAssetItem | None:
        code = str(row.get("f12") or "").strip()
        market = str(row.get("f13", "")).strip()
        name = str(row.get("f14") or code).strip()
        secid = from_eastmoney(f"{market}.{code}")
        if secid is None or len(code) != 6 or not code.isdigit():
            return None
        if category is AssetCategory.ETF:
            if any(term in name.upper() for term in ("LOF", "REIT")):
                return None
            if not ((market == "0" and code.startswith("15")) or (market == "1" and code.startswith("5"))):
                return None
        else:
            if not ((market == "0" and code.startswith(("0", "3"))) or (market == "1" and code.startswith("6"))):
                return None
        latest = _num(row.get("f2"))
        if latest <= 0:
            return None
        decimals = 3 if category is AssetCategory.ETF else 2
        market_label = "ETF" if category is AssetCategory.ETF else ("沪A" if market == "1" else "深A")
        return MarketAssetItem(
            stock=Stock(secid=secid, symbol=code, name=name, market=market_label),
            latest=round(latest, decimals),
            change=round(_num(row.get("f4")), decimals),
            change_percent=round(_num(row.get("f3")), 2),
            open=round(_num(row.get("f17"), latest), decimals),
            high=round(_num(row.get("f15"), latest), decimals),
            low=round(_num(row.get("f16"), latest), decimals),
            previous_close=round(_num(row.get("f18"), latest), decimals),
            volume=round(_num(row.get("f5")) * 100),
            amount=round(_num(row.get("f6")), 2),
        )

    @staticmethod
    def _parse_sina_row(row: dict, category: AssetCategory) -> MarketAssetItem | None:
        if not isinstance(row, dict):
            return None
        symbol = str(row.get("symbol") or "").strip().lower()
        if len(symbol) < 8:
            return None
        exchange, code = symbol[:2], symbol[2:]
        if exchange not in {"sh", "sz"} or len(code) != 6 or not code.isdigit():
            return None
        name = str(row.get("name") or code).strip()
        if category is AssetCategory.ETF:
            if any(term in name.upper() for term in ("LOF", "REIT")):
                return None
            if not ((exchange == "sz" and code.startswith("15")) or (exchange == "sh" and code.startswith("5"))):
                return None
        else:
            if not ((exchange == "sz" and code.startswith(("0", "3"))) or (exchange == "sh" and code.startswith("6"))):
                return None
        latest = _num(row.get("trade"))
        prev_close = _num(row.get("settlement"))
        if latest <= 0:
            latest = prev_close
        if latest <= 0:
            return None
        decimals = 3 if category is AssetCategory.ETF else 2
        market_label = "ETF" if category is AssetCategory.ETF else ("沪A" if exchange == "sh" else "深A")
        return MarketAssetItem(
            stock=Stock(secid=f"{code}.{exchange.upper()}", symbol=code, name=name, market=market_label),
            latest=round(latest, decimals),
            change=round(_num(row.get("pricechange"), latest - prev_close if prev_close > 0 else 0.0), decimals),
            change_percent=round(_num(row.get("changepercent")), 2),
            open=round(_num(row.get("open"), latest), decimals),
            high=round(_num(row.get("high"), latest), decimals),
            low=round(_num(row.get("low"), latest), decimals),
            previous_close=round(prev_close or latest, decimals),
            volume=round(_num(row.get("volume"))),
            amount=round(_num(row.get("amount")), 2),
        )
