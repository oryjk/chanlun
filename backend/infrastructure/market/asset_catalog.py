from __future__ import annotations

from concurrent.futures import ThreadPoolExecutor
from dataclasses import replace
from datetime import datetime
import json
import logging
import time
from threading import Lock
from zoneinfo import ZoneInfo

from ...domain.errors import MarketDataNotFound, MarketDataUnavailable
from ...domain.models import Stock
from ...domain.security import SecurityId
from ...domain.tracking import (
    AssetCategory,
    AssetSortField,
    MarketAssetItem,
    MarketAssetList,
)
from ..cache.asset_catalog_cache import SqliteAssetCatalogCache
from ..http.json_client import JsonHttpClient
from .eastmoney_codes import from_eastmoney, to_eastmoney
from .fallback_market_data import _PrimaryCircuitBreaker

_SHANGHAI_TZ = ZoneInfo("Asia/Shanghai")


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


def _merge_sparklines(
    all_items: list[MarketAssetItem],
    enriched_slice: list[MarketAssetItem],
) -> list[MarketAssetItem]:
    by_secid = {item.stock.secid: item for item in enriched_slice}
    return [by_secid.get(item.stock.secid, item) for item in all_items]


def _to_sina_symbol(secid: str) -> str:
    sid = SecurityId.parse(secid)
    return f"{sid.exchange.value.lower()}{sid.code}"


class MarketAssetCatalogAdapter:
    """沪深股票与 ETF 资产清单适配器：东方财富主源 + 新浪分市备用 + 盘前历史收盘兜底 + 20 日走势小线补齐。"""

    EM_URL = "http://push2.eastmoney.com/api/qt/clist/get"
    EM_KLINE_URL = "https://push2his.eastmoney.com/api/qt/stock/kline/get"
    EM_KLINE_UT = "fa5fd1943c7b386f172d6893dbfba10b"
    TENCENT_KLINE_URL = "https://web.ifzq.gtimg.cn/appstock/app/fqkline/get"
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
    SINA_KLINE_URL = "https://quotes.sina.cn/cn/api/jsonp_v2.php/var%20_data=/CN_MarketDataService.getKLineData"
    SINA_QUOTE_URL = "https://hq.sinajs.cn/list=sh000001,sz399001"
    SINA_HEADERS = {"Accept": "*/*", "Referer": "https://finance.sina.com.cn/"}
    SINA_NODES = {
        AssetCategory.STOCK: ("sh_a", "sz_a"),
        AssetCategory.ETF: ("etf_hq_fund",),
    }
    SINA_SORT = {
        AssetSortField.AMOUNT: "amount",
        AssetSortField.CHANGE_PERCENT: "changepercent",
        AssetSortField.LATEST: "trade",
    }
    MAX_SINA_PAGES = 6

    def __init__(
        self,
        http_client: JsonHttpClient,
        *,
        cache_store: SqliteAssetCatalogCache | None = None,
        ttl_seconds: float = 20.0,
        clock=time.monotonic,
    ):
        self._http = http_client
        self._cache_store = cache_store
        self._ttl = ttl_seconds
        self._clock = clock
        self._breaker = _PrimaryCircuitBreaker(clock=clock, label="主资产清单")
        self._cache: dict[tuple[AssetCategory, AssetSortField, bool, int], tuple[float, MarketAssetList]] = {}
        self._status_cache: tuple[float, tuple[str | None, bool]] | None = None
        self._sparkline_cache: dict[tuple[str, str], tuple[float, ...]] = {}
        self._lock = Lock()

    def list_assets(
        self,
        category: AssetCategory,
        *,
        sort_field: AssetSortField,
        descending: bool,
        limit: int,
    ) -> MarketAssetList:
        key = (category, sort_field, descending, limit)
        now = self._clock()
        with self._lock:
            cached = self._cache.get(key)
            if cached is not None and now - cached[0] < self._ttl:
                return cached[1]

        result = self._fetch(category, sort_field=sort_field, descending=descending, limit=limit)
        with self._lock:
            self._cache[key] = (self._clock(), result)
        return result

    def _fetch(
        self,
        category: AssetCategory,
        *,
        sort_field: AssetSortField,
        descending: bool,
        limit: int,
    ) -> MarketAssetList:
        quote_date, market_has_turnover = self._probe_sina_market_status()
        active_items: list[MarketAssetItem] = []
        live_error: MarketDataUnavailable | MarketDataNotFound | None = None

        if market_has_turnover:
            if not self._breaker.suspended():
                try:
                    em_items = self._fetch_eastmoney(
                        category,
                        sort_field=sort_field,
                        descending=descending,
                        limit=limit,
                    )
                    if em_items:
                        self._breaker.record_success()
                        active_items = em_items
                except MarketDataUnavailable as error:
                    self._breaker.record_failure()
                    live_error = error
                    logging.getLogger(__name__).warning("东方财富资产清单不可用，切换至新浪行情中心")
                except MarketDataNotFound as error:
                    live_error = error

            if not active_items:
                try:
                    active_items = self._fetch_sina(
                        category,
                        sort_field=sort_field,
                        descending=descending,
                        limit=limit,
                    )
                    if active_items:
                        live_error = None
                except (MarketDataUnavailable, MarketDataNotFound) as error:
                    live_error = error

        if active_items:
            cache_date = quote_date or datetime.now(_SHANGHAI_TZ).date().isoformat()
            top_items = self._enrich_sparklines(active_items[:limit], cache_date)
            if self._cache_store is not None:
                merged = _merge_sparklines(active_items, top_items)
                self._cache_store.save_items(category, cache_date, merged)
            return MarketAssetList(
                category=category,
                as_of_date=quote_date,
                items=tuple(top_items),
            )

        # 盘前 09:00–09:25 清零窗口或主备源暂缺有效成交时，优先使用最近活跃交易日 SQLite 快照
        if self._cache_store is not None:
            min_cached = min(limit, 30) if not market_has_turnover else 1
            cached_count = self._cache_store.count_items(category)
            if cached_count >= min_cached or (live_error is not None and cached_count > 0):
                cached_list = self._cache_store.read_items(
                    category,
                    sort_field=sort_field,
                    descending=descending,
                    limit=limit,
                )
                if cached_list is not None and cached_list.items:
                    spark_date = cached_list.as_of_date or quote_date or ""
                    enriched = self._enrich_sparklines(list(cached_list.items), spark_date)
                    enriched_tuple = tuple(enriched)
                    if spark_date and enriched_tuple != cached_list.items:
                        self._cache_store.save_items(category, spark_date, enriched_tuple)
                    return replace(cached_list, items=enriched_tuple)

        # 冷启动且正好处于盘前清零期：通过 K 线补齐上一完整交易日的真实收盘与成交额
        try:
            kline_list = self._fetch_previous_session_from_klines(
                category,
                sort_field=sort_field,
                descending=descending,
                limit=limit,
            )
            if kline_list is not None and kline_list.items:
                return kline_list
        except (MarketDataUnavailable, MarketDataNotFound) as error:
            if live_error is None:
                live_error = error

        if live_error is not None:
            raise live_error
        raise MarketDataNotFound("暂未获取到有效资产行情清单")

    def _probe_sina_market_status(self) -> tuple[str | None, bool]:
        now = self._clock()
        with self._lock:
            if self._status_cache is not None and now - self._status_cache[0] < 15.0:
                return self._status_cache[1]

        quote_date: str | None = None
        has_turnover = True
        try:
            text = self._http.get_text(
                self.SINA_QUOTE_URL,
                None,
                headers=self.SINA_HEADERS,
                encoding="gbk",
            )
            dates: list[str] = []
            turnovers: list[float] = []
            for line in text.splitlines():
                start = line.find('"')
                end = line.rfind('"')
                if start < 0 or end <= start:
                    continue
                payload = line[start + 1 : end]
                if not payload:
                    continue
                fields = payload.split(",")
                if len(fields) < 31:
                    continue
                raw_date = fields[30].strip()
                if len(raw_date) == 10 and raw_date[4] == "-" and raw_date[7] == "-":
                    dates.append(raw_date)
                turnovers.append(max(_num(fields[8]), _num(fields[9])))
            if dates:
                quote_date = max(dates)
            if turnovers:
                has_turnover = any(value > 0 for value in turnovers)
        except (MarketDataUnavailable, MarketDataNotFound, ValueError):
            return (None, True)

        status = (quote_date, has_turnover)
        with self._lock:
            self._status_cache = (self._clock(), status)
        return status

    def _fetch_eastmoney(
        self,
        category: AssetCategory,
        *,
        sort_field: AssetSortField,
        descending: bool,
        limit: int,
    ) -> list[MarketAssetItem]:
        fetch_size = min(200, max(limit + 30, 100))
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
            if item is not None and item.amount > 0 and item.volume > 0:
                items.append(item)
        if not items:
            raise MarketDataNotFound("暂未获取到有效资产行情清单")
        items.sort(key=lambda entry: _sort_value(entry, sort_field), reverse=descending)
        return items

    def _fetch_sina(
        self,
        category: AssetCategory,
        *,
        sort_field: AssetSortField,
        descending: bool,
        limit: int,
    ) -> list[MarketAssetItem]:
        per_page = 100
        combined_by_secid: dict[str, MarketAssetItem] = {}
        nodes = self.SINA_NODES[category]

        for node in nodes:
            node_active_count = 0
            for page in range(1, self.MAX_SINA_PAGES + 1):
                raw_text = self._http.get_text(
                    self.SINA_URL,
                    {
                        "page": page,
                        "num": per_page,
                        "sort": self.SINA_SORT[sort_field],
                        "asc": 0 if descending else 1,
                        "node": node,
                    },
                    headers=self.SINA_HEADERS,
                )
                try:
                    rows = json.loads(raw_text)
                except json.JSONDecodeError as error:
                    raise MarketDataUnavailable("资产清单返回格式异常") from error
                if not isinstance(rows, list):
                    raise MarketDataUnavailable("资产清单返回格式异常")
                if not rows:
                    break

                page_has_active = False
                for row in rows:
                    item = self._parse_sina_row(row, category)
                    if item is None:
                        continue
                    if item.amount <= 0 or item.volume <= 0:
                        continue
                    page_has_active = True
                    if item.stock.secid not in combined_by_secid:
                        combined_by_secid[item.stock.secid] = item
                        node_active_count += 1

                if node_active_count >= limit or len(rows) < per_page:
                    break
                # 降序按成交额第一页都没有有效成交说明全市场尚无成交，不必继续翻页
                if page == 1 and not page_has_active and descending and sort_field is AssetSortField.AMOUNT:
                    break

        items = list(combined_by_secid.values())
        if not items:
            raise MarketDataNotFound("暂未获取到有效资产行情清单")
        items.sort(key=lambda entry: _sort_value(entry, sort_field), reverse=descending)
        return items

    def _fetch_previous_session_from_klines(
        self,
        category: AssetCategory,
        *,
        sort_field: AssetSortField,
        descending: bool,
        limit: int,
    ) -> MarketAssetList | None:
        candidate_stocks: dict[str, Stock] = {}
        nodes = self.SINA_NODES[category]
        node_quota = max(35, min(60, (limit + len(nodes) - 1) // len(nodes) + 15))
        orders = (0, 1) if not descending else (0,)

        for asc_flag in orders:
            for node in nodes:
                try:
                    raw_text = self._http.get_text(
                        self.SINA_URL,
                        {
                            "page": 1,
                            "num": node_quota,
                            "sort": "mktcap",
                            "asc": asc_flag,
                            "node": node,
                        },
                        headers=self.SINA_HEADERS,
                    )
                    rows = json.loads(raw_text)
                except (MarketDataUnavailable, MarketDataNotFound, json.JSONDecodeError):
                    continue
                if not isinstance(rows, list):
                    continue
                for row in rows:
                    stock = self._parse_sina_stock_only(row, category)
                    if stock is not None:
                        candidate_stocks.setdefault(stock.secid, stock)

        stocks = list(candidate_stocks.values())[:100]
        if not stocks:
            return None

        hydrated: list[MarketAssetItem] = []
        latest_dates: list[str] = []
        with ThreadPoolExecutor(max_workers=4) as executor:
            for result in executor.map(lambda s: self._fetch_kline_summary(s, category), stocks):
                if result is None:
                    continue
                bar_date, item = result
                hydrated.append(item)
                latest_dates.append(bar_date)

        if not hydrated or not latest_dates:
            return None

        as_of_date = max(latest_dates)
        if self._cache_store is not None:
            self._cache_store.save_items(category, as_of_date, hydrated)

        hydrated.sort(key=lambda entry: _sort_value(entry, sort_field), reverse=descending)
        return MarketAssetList(
            category=category,
            as_of_date=as_of_date,
            items=tuple(hydrated[:limit]),
        )

    def _fetch_sina_daily_bars(
        self,
        sina_symbol: str,
        count: int = 20,
    ) -> list[tuple[str, float, float, float, float, int]]:
        try:
            text = self._http.get_text(
                self.SINA_KLINE_URL,
                {
                    "symbol": sina_symbol,
                    "scale": 240,
                    "ma": "no",
                    "datalen": count,
                },
                headers=self.SINA_HEADERS,
            )
        except (MarketDataUnavailable, MarketDataNotFound):
            return []
        start = text.find("([")
        end = text.rfind(")")
        if start < 0 or end <= start:
            return []
        try:
            rows = json.loads(text[start + 1 : end])
        except json.JSONDecodeError:
            return []
        if not isinstance(rows, list):
            return []
        bars: list[tuple[str, float, float, float, float, int]] = []
        for row in rows:
            if not isinstance(row, dict):
                continue
            bar_date = str(row.get("day") or "").strip()[:10]
            open_p = _num(row.get("open"))
            close_p = _num(row.get("close"))
            high_p = _num(row.get("high"))
            low_p = _num(row.get("low"))
            volume = round(_num(row.get("volume")))
            if len(bar_date) == 10 and close_p > 0:
                bars.append((bar_date, open_p, close_p, high_p, low_p, volume))
        return bars

    def _fetch_kline_summary(
        self,
        stock: Stock,
        category: AssetCategory,
    ) -> tuple[str, MarketAssetItem] | None:
        decimals = 3 if category is AssetCategory.ETF else 2
        klines: list = []
        try:
            payload = self._http.get(
                self.EM_KLINE_URL,
                {
                    "secid": to_eastmoney(stock.secid),
                    "ut": self.EM_KLINE_UT,
                    "fields1": "f1,f2,f3",
                    "fields2": "f51,f52,f53,f54,f55,f56,f57,f58,f59,f60",
                    "klt": 101,
                    "fqt": 1,
                    "end": "20500101",
                    "lmt": 20,
                },
            )
            raw_klines = (payload.get("data") or {}).get("klines") or []
            if isinstance(raw_klines, list):
                klines = raw_klines
        except (MarketDataUnavailable, MarketDataNotFound):
            klines = []

        if klines:
            parsed_bars: list[tuple[str, float, float, float, float, int, float, float, float]] = []
            closes: list[float] = []
            for raw_bar in klines:
                if not isinstance(raw_bar, str):
                    continue
                parts = raw_bar.split(",")
                if len(parts) < 10:
                    continue
                bar_date = parts[0].strip()
                open_p = _num(parts[1])
                close_p = _num(parts[2])
                high_p = _num(parts[3])
                low_p = _num(parts[4])
                vol_lots = _num(parts[5])
                amount = _num(parts[6])
                pct = _num(parts[8])
                chg = _num(parts[9])
                if close_p > 0:
                    closes.append(round(close_p, 4))
                    parsed_bars.append((bar_date, open_p, close_p, high_p, low_p, round(vol_lots * 100), amount, pct, chg))

            active_bars = [bar for bar in parsed_bars if bar[6] > 0 and bar[5] > 0]
            if active_bars:
                last_date, open_p, close_p, high_p, low_p, volume, amount, pct, chg = active_bars[-1]
                prev_close = round(close_p - chg, decimals) if close_p > chg else round(close_p, decimals)
                sparkline = tuple(closes[-20:])
                if last_date and len(sparkline) >= 2:
                    with self._lock:
                        self._sparkline_cache[(stock.secid, last_date)] = sparkline
                return (
                    last_date,
                    MarketAssetItem(
                        stock=stock,
                        latest=round(close_p, decimals),
                        change=round(chg, decimals),
                        change_percent=round(pct, 2),
                        open=round(open_p or close_p, decimals),
                        high=round(high_p or close_p, decimals),
                        low=round(low_p or close_p, decimals),
                        previous_close=prev_close,
                        volume=volume,
                        amount=round(amount, 2),
                        sparkline=sparkline,
                    ),
                )

        # 备用 1：东方财富 K 线被限流/断连时使用新浪 JSONP 日 K 线兜底
        sina_symbol = _to_sina_symbol(stock.secid)
        sina_bars = self._fetch_sina_daily_bars(sina_symbol, count=20)
        sina_active = [bar for bar in sina_bars if bar[5] > 0 and bar[2] > 0]
        if sina_active:
            last_date, open_p, close_p, high_p, low_p, volume = sina_active[-1]
            prev_close = sina_active[-2][2] if len(sina_active) >= 2 else (open_p or close_p)
            chg = round(close_p - prev_close, decimals)
            pct = round((chg / prev_close) * 100.0, 2) if prev_close > 0 else 0.0
            sparkline = tuple(round(bar[2], 4) for bar in sina_bars[-20:])
            if last_date and len(sparkline) >= 2:
                with self._lock:
                    self._sparkline_cache[(stock.secid, last_date)] = sparkline
            return (
                last_date,
                MarketAssetItem(
                    stock=stock,
                    latest=round(close_p, decimals),
                    change=chg,
                    change_percent=pct,
                    open=round(open_p or close_p, decimals),
                    high=round(high_p or close_p, decimals),
                    low=round(low_p or close_p, decimals),
                    previous_close=round(prev_close, decimals),
                    volume=volume,
                    amount=0.0,
                    sparkline=sparkline,
                ),
            )

        # 备用 2：使用腾讯日 K 线兜底收盘与涨跌幅
        try:
            gt_payload = self._http.get(
                self.TENCENT_KLINE_URL,
                {"param": f"{sina_symbol},day,,,20,qfq"},
            )
        except (MarketDataUnavailable, MarketDataNotFound):
            return None
        node = (gt_payload.get("data") or {}).get(sina_symbol)
        if not isinstance(node, dict):
            return None
        bars = node.get("qfqday") or node.get("day") or []
        if not isinstance(bars, list):
            return None

        gt_bars: list[tuple[str, float, float, float, float, int]] = []
        gt_closes: list[float] = []
        for bar in bars:
            if not isinstance(bar, list) or len(bar) < 6:
                continue
            bar_date = str(bar[0]).strip()
            open_p = _num(bar[1])
            close_p = _num(bar[2])
            high_p = _num(bar[3])
            low_p = _num(bar[4])
            vol_lots = _num(bar[5])
            if close_p > 0:
                gt_closes.append(round(close_p, 4))
                if vol_lots > 0:
                    gt_bars.append((bar_date, open_p, close_p, high_p, low_p, round(vol_lots * 100)))

        if not gt_bars:
            return None
        last_date, open_p, close_p, high_p, low_p, volume = gt_bars[-1]
        prev_close = gt_bars[-2][2] if len(gt_bars) >= 2 else (open_p or close_p)
        chg = round(close_p - prev_close, decimals)
        pct = round((chg / prev_close) * 100.0, 2) if prev_close > 0 else 0.0
        sparkline = tuple(gt_closes[-20:])
        if last_date and len(sparkline) >= 2:
            with self._lock:
                self._sparkline_cache[(stock.secid, last_date)] = sparkline
        return (
            last_date,
            MarketAssetItem(
                stock=stock,
                latest=round(close_p, decimals),
                change=chg,
                change_percent=pct,
                open=round(open_p or close_p, decimals),
                high=round(high_p or close_p, decimals),
                low=round(low_p or close_p, decimals),
                previous_close=round(prev_close, decimals),
                volume=volume,
                amount=0.0,
                sparkline=sparkline,
            ),
        )

    def _enrich_sparklines(
        self,
        items: list[MarketAssetItem],
        as_of_date: str,
    ) -> list[MarketAssetItem]:
        if not items or not as_of_date:
            return items

        missing_indices: list[int] = []
        resolved: list[MarketAssetItem] = list(items)
        with self._lock:
            for idx, item in enumerate(resolved):
                if len(item.sparkline) >= 2:
                    continue
                cached = self._sparkline_cache.get((item.stock.secid, as_of_date))
                if cached and len(cached) >= 2:
                    resolved[idx] = replace(item, sparkline=cached)
                else:
                    missing_indices.append(idx)

        if not missing_indices:
            return resolved

        target_items = [resolved[idx] for idx in missing_indices]

        def _fetch_closes(item: MarketAssetItem) -> tuple[str, tuple[float, ...]]:
            secid = item.stock.secid
            sina_symbol = _to_sina_symbol(secid)

            sina_bars = self._fetch_sina_daily_bars(sina_symbol, count=20)
            if sina_bars:
                points = [(bar[0], round(bar[2], 4)) for bar in sina_bars]
                if points[-1][0] < as_of_date and item.previous_close > 0 and points[-1][1] > 0:
                    ratio = item.previous_close / points[-1][1]
                    if abs(ratio - 1.0) > 0.02:
                        points = [(d, round(p * ratio, 4)) for d, p in points]
                if item.latest > 0:
                    if points[-1][0] < as_of_date:
                        points.append((as_of_date, round(item.latest, 4)))
                    elif points[-1][0] == as_of_date:
                        points[-1] = (as_of_date, round(item.latest, 4))
                if len(points) >= 2:
                    return secid, tuple(price for _, price in points[-20:])

            try:
                payload = self._http.get(
                    self.TENCENT_KLINE_URL,
                    {"param": f"{sina_symbol},day,,,20,qfq"},
                )
                node = (payload.get("data") or {}).get(sina_symbol)
                if isinstance(node, dict):
                    bars = node.get("qfqday") or node.get("day") or []
                    points: list[tuple[str, float]] = []
                    for bar in bars:
                        if not isinstance(bar, list) or len(bar) < 3:
                            continue
                        bar_date = str(bar[0]).strip()
                        close_p = _num(bar[2])
                        if close_p > 0:
                            points.append((bar_date, round(close_p, 4)))
                    if len(points) >= 2:
                        if points[-1][0] == as_of_date and item.latest > 0:
                            points[-1] = (points[-1][0], round(item.latest, 4))
                        return secid, tuple(price for _, price in points[-20:])
            except (MarketDataUnavailable, MarketDataNotFound):
                pass

            try:
                payload = self._http.get(
                    self.EM_KLINE_URL,
                    {
                        "secid": to_eastmoney(secid),
                        "ut": self.EM_KLINE_UT,
                        "fields1": "f1,f2,f3",
                        "fields2": "f51,f53",
                        "klt": 101,
                        "fqt": 1,
                        "end": "20500101",
                        "lmt": 20,
                    },
                )
            except (MarketDataUnavailable, MarketDataNotFound):
                return secid, ()
            klines = (payload.get("data") or {}).get("klines") or []
            closes: list[float] = []
            for raw_bar in klines:
                if not isinstance(raw_bar, str):
                    continue
                parts = raw_bar.split(",")
                if len(parts) < 2:
                    continue
                close_p = _num(parts[1])
                if close_p > 0:
                    closes.append(round(close_p, 4))
            return secid, tuple(closes[-20:])

        with ThreadPoolExecutor(max_workers=12) as executor:
            fetched = dict(executor.map(_fetch_closes, target_items))

        with self._lock:
            if len(self._sparkline_cache) > 2000:
                self._sparkline_cache.clear()
            for idx in missing_indices:
                secid = resolved[idx].stock.secid
                points = fetched.get(secid, ())
                if len(points) >= 2:
                    self._sparkline_cache[(secid, as_of_date)] = points
                    resolved[idx] = replace(resolved[idx], sparkline=points)

        return resolved

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
    def _parse_sina_stock_only(row: dict, category: AssetCategory) -> Stock | None:
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
        market_label = "ETF" if category is AssetCategory.ETF else ("沪A" if exchange == "sh" else "深A")
        return Stock(secid=f"{code}.{exchange.upper()}", symbol=code, name=name, market=market_label)

    @classmethod
    def _parse_sina_row(cls, row: dict, category: AssetCategory) -> MarketAssetItem | None:
        stock = cls._parse_sina_stock_only(row, category)
        if stock is None:
            return None
        latest = _num(row.get("trade"))
        prev_close = _num(row.get("settlement"))
        if latest <= 0:
            latest = prev_close
        if latest <= 0:
            return None
        decimals = 3 if category is AssetCategory.ETF else 2
        return MarketAssetItem(
            stock=stock,
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
