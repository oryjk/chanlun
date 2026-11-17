from __future__ import annotations

from collections.abc import Callable, Sequence
from concurrent.futures import ThreadPoolExecutor
from dataclasses import replace
from datetime import datetime, time as dt_time
import json
import logging
import time
from threading import Event, Lock, Thread
from zoneinfo import ZoneInfo

from ...domain.errors import MarketDataNotFound, MarketDataUnavailable
from ...domain.models import Stock
from ...domain.security import SecurityId
from ...domain.tracking import (
    AssetCategory,
    AssetSortField,
    MarketAssetItem,
    MarketAssetList,
    is_active_market_asset,
    sort_market_assets,
)
from ..cache.asset_catalog_cache import SqliteAssetCatalogCache
from ..http.json_client import JsonHttpClient
from .eastmoney_codes import from_eastmoney, to_eastmoney
from .fallback_market_data import _PrimaryCircuitBreaker

_SHANGHAI_TZ = ZoneInfo("Asia/Shanghai")
_MARKET_CLOSE_TIME = dt_time(15, 5)
_STALE_MAX_AGE_SECONDS = 300.0
_SYNC_BATCH_SIZE = 100
_SYNC_INTERVAL_SECONDS = 60.0
_EX_DIVIDEND_RATIO_THRESHOLD = 0.02


def _num(value, default: float = 0.0) -> float:
    try:
        parsed = float(value)
    except (TypeError, ValueError):
        return default
    return parsed if parsed == parsed else default


def _to_sina_symbol(secid: str) -> str:
    sid = SecurityId.parse(secid)
    return f"{sid.exchange.value.lower()}{sid.code}"


class MarketAssetCatalogAdapter:
    """全市场沪深北 A 股与 ETF 资产清单适配器：东方财富主源 + 新浪分市全分页备用 + SQLite 20 日走势增量同步。"""

    EM_URL = "http://push2.eastmoney.com/api/qt/clist/get"
    EM_KLINE_URL = "https://push2his.eastmoney.com/api/qt/stock/kline/get"
    EM_KLINE_UT = "fa5fd1943c7b386f172d6893dbfba10b"
    TENCENT_KLINE_URL = "https://web.ifzq.gtimg.cn/appstock/app/fqkline/get"
    EM_FS = {
        AssetCategory.STOCK: "m:0+t:6,m:0+t:80,m:1+t:2,m:1+t:23,m:0+t:81+s:2048",
        AssetCategory.ETF: "b:MK0021,b:MK0022,b:MK0023,b:MK0024",
    }

    SINA_URL = "https://vip.stock.finance.sina.com.cn/quotes_service/api/json_v2.php/Market_Center.getHQNodeData"
    SINA_COUNT_URL = "https://vip.stock.finance.sina.com.cn/quotes_service/api/json_v2.php/Market_Center.getHQNodeStockCount"
    SINA_KLINE_URL = "https://quotes.sina.cn/cn/api/jsonp_v2.php/var%20_data=/CN_MarketDataService.getKLineData"
    SINA_QUOTE_URL = "https://hq.sinajs.cn/list=sh000001,sz399001"
    SINA_HEADERS = {"Accept": "*/*", "Referer": "https://finance.sina.com.cn/"}
    SINA_NODES = {
        AssetCategory.STOCK: ("sh_a", "sz_a", "hs_bjs"),
        AssetCategory.ETF: ("etf_hq_fund",),
    }
    SINA_NODE_EXCHANGE = {
        "sh_a": "sh",
        "sz_a": "sz",
        "hs_bjs": "bj",
        "etf_hq_fund": None,
    }
    MAX_SINA_PAGES = 60

    def __init__(
        self,
        http_client: JsonHttpClient,
        *,
        cache_store: SqliteAssetCatalogCache | None = None,
        ttl_seconds: float = 20.0,
        clock: Callable[[], float] = time.monotonic,
        now_fn: Callable[[], datetime] | None = None,
        sleep: Callable[[float], None] = time.sleep,
    ):
        self._http = http_client
        self._cache_store = cache_store
        self._ttl = ttl_seconds
        self._clock = clock
        self._now_fn = now_fn or (lambda: datetime.now(_SHANGHAI_TZ))
        self._sleep = sleep
        self._breaker = _PrimaryCircuitBreaker(clock=clock, label="主资产清单")
        self._status_cache: tuple[float, tuple[str | None, bool]] | None = None
        self._snapshot_cache: dict[AssetCategory, tuple[float, str | None, tuple[MarketAssetItem, ...]]] = {}
        self._refreshing: set[AssetCategory] = set()
        self._refresh_lock = Lock()
        self._lock = Lock()
        self._bars_cache: dict[str, list[tuple[str, float, bool]]] = {}
        self._sync_dates: dict[str, str] = {}
        self._resync_needed: set[str] = set()
        if self._cache_store is not None:
            self._bars_cache = self._cache_store.load_daily_bars_map()
            self._sync_dates = self._cache_store.load_sync_dates()
        self._sync_stop = Event()
        self._sync_thread: Thread | None = None

    def start_background_sync(self) -> None:
        if self._sync_thread is not None:
            return
        self._sync_stop.clear()
        self._sync_thread = Thread(target=self._sync_loop, name="asset-catalog-sync", daemon=True)
        self._sync_thread.start()

    def stop_background_sync(self) -> None:
        self._sync_stop.set()
        if self._sync_thread is not None:
            self._sync_thread.join(timeout=10)
            self._sync_thread = None

    def _sync_loop(self) -> None:
        while not self._sync_stop.is_set():
            try:
                self.sync_all_sparklines_once()
            except Exception:
                logging.getLogger(__name__).exception("全市场 20 日走势同步任务暂时失败")
            self._sync_stop.wait(_SYNC_INTERVAL_SECONDS)

    def list_assets(
        self,
        category: AssetCategory,
        *,
        sort_field: AssetSortField,
        descending: bool,
        limit: int,
        offset: int = 0,
    ) -> MarketAssetList:
        as_of_date, all_items = self._resolve_snapshot(category, min_cached_hint=min(limit, 30))
        ordered = sort_market_assets(all_items, sort_field, descending=descending)
        total = len(ordered)
        page = ordered[offset : offset + limit]
        has_more = offset + len(page) < total

        spark_date = as_of_date or self._now_fn().astimezone(_SHANGHAI_TZ).date().isoformat()
        composed = [self._apply_cached_sparkline(item, spark_date) for item in page]
        final_page = self._enrich_missing_page_sparklines(composed, spark_date)
        return MarketAssetList(
            category=category,
            as_of_date=as_of_date,
            items=tuple(final_page),
            total=total,
            offset=offset,
            has_more=has_more,
        )

    def _resolve_snapshot(
        self,
        category: AssetCategory,
        *,
        min_cached_hint: int,
    ) -> tuple[str | None, tuple[MarketAssetItem, ...]]:
        if self._ttl <= 0:
            return self._refresh_category(category, min_cached_hint=min_cached_hint)

        now = self._clock()
        with self._lock:
            cached = self._snapshot_cache.get(category)
        if cached is not None:
            fetched_at, as_of_date, items = cached
            age = now - fetched_at
            if age < self._ttl:
                return as_of_date, items
            if age < _STALE_MAX_AGE_SECONDS:
                self._trigger_async_refresh(category)
                return as_of_date, items

        if cached is None and self._cache_store is not None:
            min_full_count = 500 if category is AssetCategory.STOCK else 100
            if self._cache_store.count_items(category) >= min_full_count:
                stored = self._cache_store.read_all_items(category)
                if stored is not None and stored.items:
                    with self._lock:
                        self._snapshot_cache[category] = (
                            self._clock() - self._ttl,
                            stored.as_of_date,
                            stored.items,
                        )
                    self._trigger_async_refresh(category)
                    return stored.as_of_date, stored.items

        try:
            return self._refresh_category(category, min_cached_hint=min_cached_hint)
        except (MarketDataUnavailable, MarketDataNotFound):
            if cached is not None:
                return cached[1], cached[2]
            raise

    def _trigger_async_refresh(self, category: AssetCategory) -> None:
        with self._refresh_lock:
            if category in self._refreshing:
                return
            self._refreshing.add(category)

        def _worker() -> None:
            try:
                self._refresh_category(category)
            except Exception:
                logging.getLogger(__name__).debug("后台刷新 %s 资产清单失败", category.value, exc_info=True)
            finally:
                with self._refresh_lock:
                    self._refreshing.discard(category)

        Thread(target=_worker, name=f"refresh-{category.value}", daemon=True).start()

    def _refresh_category(
        self,
        category: AssetCategory,
        *,
        min_cached_hint: int = 30,
    ) -> tuple[str | None, tuple[MarketAssetItem, ...]]:
        quote_date, market_has_turnover = self._probe_sina_market_status()
        active_items: list[MarketAssetItem] = []
        live_error: MarketDataUnavailable | MarketDataNotFound | None = None

        if market_has_turnover:
            if not self._breaker.suspended():
                try:
                    em_items = self._fetch_eastmoney_all(category)
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
                    active_items = self._fetch_sina_all(category)
                    if active_items:
                        live_error = None
                except (MarketDataUnavailable, MarketDataNotFound) as error:
                    live_error = error

        if active_items:
            cache_date = quote_date or self._now_fn().astimezone(_SHANGHAI_TZ).date().isoformat()
            snapshot_tuple = tuple(active_items)
            with self._lock:
                self._snapshot_cache[category] = (self._clock(), cache_date, snapshot_tuple)
            if self._cache_store is not None:
                self._cache_store.save_items(category, cache_date, snapshot_tuple)
            return quote_date, snapshot_tuple

        # 盘前 09:00–09:25 清零窗口或主备源暂缺有效成交时，优先使用最近活跃交易日 SQLite 快照
        if self._cache_store is not None:
            min_cached = min(min_cached_hint, 30) if not market_has_turnover else 1
            cached_count = self._cache_store.count_items(category)
            if cached_count >= min_cached or (live_error is not None and cached_count > 0):
                cached_list = self._cache_store.read_all_items(category)
                if cached_list is not None and cached_list.items:
                    with self._lock:
                        self._snapshot_cache[category] = (
                            self._clock(),
                            cached_list.as_of_date,
                            cached_list.items,
                        )
                    return cached_list.as_of_date, cached_list.items

        # 冷启动且正好处于盘前清零期：通过 K 线补齐上一完整交易日的真实收盘与成交额
        try:
            kline_list = self._fetch_previous_session_from_klines(category)
            if kline_list is not None and kline_list.items:
                with self._lock:
                    self._snapshot_cache[category] = (
                        self._clock(),
                        kline_list.as_of_date,
                        kline_list.items,
                    )
                return kline_list.as_of_date, kline_list.items
        except (MarketDataUnavailable, MarketDataNotFound) as error:
            if live_error is None:
                live_error = error

        if live_error is not None:
            raise live_error
        raise MarketDataNotFound("暂未获取到有效资产行情清单")

    def _probe_sina_market_status(self) -> tuple[str | None, bool]:
        now = self._clock()
        with self._lock:
            if self._ttl > 0 and self._status_cache is not None and now - self._status_cache[0] < 15.0:
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

    def _fetch_eastmoney_all(self, category: AssetCategory) -> list[MarketAssetItem]:
        page_size = 100
        first_payload = self._http.get(
            self.EM_URL,
            {
                "pn": 1,
                "pz": page_size,
                "po": 0,
                "np": 1,
                "fltt": 2,
                "invt": 2,
                "fid": "f12",
                "fs": self.EM_FS[category],
                "fields": "f2,f3,f4,f5,f6,f12,f13,f14,f15,f16,f17,f18",
            },
        )
        data = first_payload.get("data") or {}
        first_rows = data.get("diff") or []
        if not isinstance(first_rows, list) or not first_rows:
            raise MarketDataNotFound("暂未获取到有效资产行情清单")

        by_secid: dict[str, MarketAssetItem] = {}
        for row in first_rows:
            item = self._parse_eastmoney_row(row, category)
            if item is not None:
                by_secid.setdefault(item.stock.secid, item)

        total = data.get("total")
        if isinstance(total, int) and not isinstance(total, bool) and total > page_size:
            total_pages = min(100, (total + page_size - 1) // page_size)

            def _fetch_page(page: int) -> list[dict]:
                payload = self._http.get(
                    self.EM_URL,
                    {
                        "pn": page,
                        "pz": page_size,
                        "po": 0,
                        "np": 1,
                        "fltt": 2,
                        "invt": 2,
                        "fid": "f12",
                        "fs": self.EM_FS[category],
                        "fields": "f2,f3,f4,f5,f6,f12,f13,f14,f15,f16,f17,f18",
                    },
                )
                rows = (payload.get("data") or {}).get("diff") or []
                return rows if isinstance(rows, list) else []

            with ThreadPoolExecutor(max_workers=6) as executor:
                for page_rows in executor.map(_fetch_page, range(2, total_pages + 1)):
                    for row in page_rows:
                        item = self._parse_eastmoney_row(row, category)
                        if item is not None:
                            by_secid.setdefault(item.stock.secid, item)

        items = list(by_secid.values())
        if not any(is_active_market_asset(item) for item in items):
            raise MarketDataNotFound("暂未获取到有效资产行情清单")
        return items

    def _fetch_sina_node_count(self, node: str) -> int | None:
        try:
            text = self._http.get_text(
                self.SINA_COUNT_URL,
                {"node": node},
                headers=self.SINA_HEADERS,
            ).strip().strip('"')
        except (MarketDataUnavailable, MarketDataNotFound):
            return None
        if text.isdigit() and 0 < int(text) <= 20000:
            return int(text)
        return None

    def _fetch_sina_page_rows(self, node: str, page: int, *, per_page: int = 100, allow_empty: bool = False) -> list[dict]:
        attempts = 3 if not allow_empty else 1
        last_error: MarketDataUnavailable | None = None
        for attempt in range(attempts):
            try:
                raw_text = self._http.get_text(
                    self.SINA_URL,
                    {
                        "page": page,
                        "num": per_page,
                        "sort": "symbol",
                        "asc": 1,
                        "node": node,
                    },
                    headers=self.SINA_HEADERS,
                )
                rows = json.loads(raw_text)
                if not isinstance(rows, list):
                    raise MarketDataUnavailable("资产清单返回格式异常")
                if rows or allow_empty:
                    return rows
            except json.JSONDecodeError as error:
                last_error = MarketDataUnavailable("资产清单返回格式异常")
                if attempt + 1 >= attempts:
                    raise last_error from error
            except MarketDataUnavailable as error:
                last_error = error
            if attempt + 1 < attempts:
                self._sleep(0.3 * (attempt + 1))
        if last_error is not None:
            raise last_error
        return []

    def _fetch_sina_all(self, category: AssetCategory) -> list[MarketAssetItem]:
        per_page = 100
        combined_by_secid: dict[str, MarketAssetItem] = {}

        for node in self.SINA_NODES[category]:
            expected_exchange = self.SINA_NODE_EXCHANGE.get(node)
            expected_count = self._fetch_sina_node_count(node)
            node_rows: list[dict] = []

            if expected_count is not None:
                pages = (expected_count + per_page - 1) // per_page
                with ThreadPoolExecutor(max_workers=6) as executor:
                    page_batches = list(
                        executor.map(
                            lambda page_idx: self._fetch_sina_page_rows(
                                node,
                                page_idx,
                                per_page=per_page,
                                allow_empty=(page_idx == pages),
                            ),
                            range(1, pages + 1),
                        )
                    )
                for batch in page_batches:
                    node_rows.extend(batch)
            else:
                for page in range(1, self.MAX_SINA_PAGES + 1):
                    rows = self._fetch_sina_page_rows(node, page, per_page=per_page, allow_empty=True)
                    if not rows:
                        break
                    node_rows.extend(rows)
                    if len(rows) < per_page:
                        break

            for row in node_rows:
                item = self._parse_sina_row(row, category, expected_exchange=expected_exchange)
                if item is not None:
                    combined_by_secid.setdefault(item.stock.secid, item)

        items = list(combined_by_secid.values())
        if not any(is_active_market_asset(item) for item in items):
            raise MarketDataNotFound("暂未获取到有效资产行情清单")
        return items

    def _fetch_previous_session_from_klines(self, category: AssetCategory) -> MarketAssetList | None:
        candidate_stocks: dict[str, Stock] = {}
        nodes = self.SINA_NODES[category]
        node_quota = 60

        for node in nodes:
            expected_exchange = self.SINA_NODE_EXCHANGE.get(node)
            try:
                raw_text = self._http.get_text(
                    self.SINA_URL,
                    {
                        "page": 1,
                        "num": node_quota,
                        "sort": "mktcap",
                        "asc": 0,
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
                stock = self._parse_sina_stock_only(row, category, expected_exchange=expected_exchange)
                if stock is not None:
                    candidate_stocks.setdefault(stock.secid, stock)

        stocks = list(candidate_stocks.values())[:100]
        if not stocks:
            return None

        hydrated: list[MarketAssetItem] = []
        latest_dates: list[str] = []
        bar_batches: list[tuple[str, list[tuple[str, float, bool]], str | None]] = []
        with ThreadPoolExecutor(max_workers=4) as executor:
            for result in executor.map(lambda s: self._fetch_kline_summary(s, category), stocks):
                if result is None:
                    continue
                bar_date, item, dated_closes = result
                hydrated.append(item)
                latest_dates.append(bar_date)
                if dated_closes:
                    final_bars = [(d, c, True) for d, c in dated_closes]
                    bar_batches.append((item.stock.secid, final_bars, bar_date))

        if not hydrated or not latest_dates:
            return None

        as_of_date = max(latest_dates)
        if bar_batches:
            self._commit_bar_batches(bar_batches)
        if self._cache_store is not None:
            self._cache_store.save_items(category, as_of_date, hydrated)

        return MarketAssetList(
            category=category,
            as_of_date=as_of_date,
            items=tuple(hydrated),
            total=len(hydrated),
            offset=0,
            has_more=False,
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

    def _fetch_tencent_daily_closes(
        self,
        sina_symbol: str,
        count: int = 20,
    ) -> tuple[list[tuple[str, float]], bool]:
        try:
            payload = self._http.get(
                self.TENCENT_KLINE_URL,
                {"param": f"{sina_symbol},day,,,{count},qfq"},
            )
        except (MarketDataUnavailable, MarketDataNotFound):
            return [], False
        node = (payload.get("data") or {}).get(sina_symbol)
        if not isinstance(node, dict):
            return [], False
        is_qfq = isinstance(node.get("qfqday"), list) and bool(node.get("qfqday"))
        bars = node.get("qfqday") or node.get("day") or []
        if not isinstance(bars, list):
            return [], False
        points: list[tuple[str, float]] = []
        for bar in bars:
            if not isinstance(bar, list) or len(bar) < 3:
                continue
            bar_date = str(bar[0]).strip()[:10]
            close_p = _num(bar[2])
            if len(bar_date) == 10 and close_p > 0:
                points.append((bar_date, round(close_p, 4)))
        return points[-count:], is_qfq

    def _fetch_eastmoney_daily_closes(
        self,
        secid: str,
        count: int = 20,
    ) -> list[tuple[str, float]]:
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
                    "lmt": count,
                },
            )
        except (MarketDataUnavailable, MarketDataNotFound):
            return []
        klines = (payload.get("data") or {}).get("klines") or []
        points: list[tuple[str, float]] = []
        for raw_bar in klines:
            if not isinstance(raw_bar, str):
                continue
            parts = raw_bar.split(",")
            if len(parts) < 2:
                continue
            bar_date = parts[0].strip()[:10]
            close_p = _num(parts[1])
            if len(bar_date) == 10 and close_p > 0:
                points.append((bar_date, round(close_p, 4)))
        return points[-count:]

    def _fetch_kline_summary(
        self,
        stock: Stock,
        category: AssetCategory,
    ) -> tuple[str, MarketAssetItem, list[tuple[str, float]]] | None:
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
            dated_closes: list[tuple[str, float]] = []
            for raw_bar in klines:
                if not isinstance(raw_bar, str):
                    continue
                parts = raw_bar.split(",")
                if len(parts) < 10:
                    continue
                bar_date = parts[0].strip()[:10]
                open_p = _num(parts[1])
                close_p = _num(parts[2])
                high_p = _num(parts[3])
                low_p = _num(parts[4])
                vol_lots = _num(parts[5])
                amount = _num(parts[6])
                pct = _num(parts[8])
                chg = _num(parts[9])
                if len(bar_date) == 10 and close_p > 0:
                    dated_closes.append((bar_date, round(close_p, 4)))
                    parsed_bars.append((bar_date, open_p, close_p, high_p, low_p, round(vol_lots * 100), amount, pct, chg))

            active_bars = [bar for bar in parsed_bars if bar[6] > 0 and bar[5] > 0]
            if active_bars:
                last_date, open_p, close_p, high_p, low_p, volume, amount, pct, chg = active_bars[-1]
                prev_close = round(close_p - chg, decimals) if close_p > chg else round(close_p, decimals)
                sparkline = tuple(c for _, c in dated_closes[-20:])
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
                    dated_closes[-20:],
                )

        # 备用 1：新浪 JSONP 日 K 线
        sina_symbol = _to_sina_symbol(stock.secid)
        sina_bars = self._fetch_sina_daily_bars(sina_symbol, count=20)
        sina_active = [bar for bar in sina_bars if bar[5] > 0 and bar[2] > 0]
        if sina_active:
            last_date, open_p, close_p, high_p, low_p, volume = sina_active[-1]
            prev_close = sina_active[-2][2] if len(sina_active) >= 2 else (open_p or close_p)
            chg = round(close_p - prev_close, decimals)
            pct = round((chg / prev_close) * 100.0, 2) if prev_close > 0 else 0.0
            dated_closes = [(bar[0], round(bar[2], 4)) for bar in sina_bars[-20:]]
            sparkline = tuple(c for _, c in dated_closes)
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
                dated_closes,
            )

        # 备用 2：腾讯日 K 线
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
        dated_closes = []
        for bar in bars:
            if not isinstance(bar, list) or len(bar) < 6:
                continue
            bar_date = str(bar[0]).strip()[:10]
            open_p = _num(bar[1])
            close_p = _num(bar[2])
            high_p = _num(bar[3])
            low_p = _num(bar[4])
            vol_lots = _num(bar[5])
            if len(bar_date) == 10 and close_p > 0:
                dated_closes.append((bar_date, round(close_p, 4)))
                if vol_lots > 0:
                    gt_bars.append((bar_date, open_p, close_p, high_p, low_p, round(vol_lots * 100)))

        if not gt_bars:
            return None
        last_date, open_p, close_p, high_p, low_p, volume = gt_bars[-1]
        prev_close = gt_bars[-2][2] if len(gt_bars) >= 2 else (open_p or close_p)
        chg = round(close_p - prev_close, decimals)
        pct = round((chg / prev_close) * 100.0, 2) if prev_close > 0 else 0.0
        sparkline = tuple(c for _, c in dated_closes[-20:])
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
            dated_closes[-20:],
        )

    def _apply_cached_sparkline(self, item: MarketAssetItem, as_of_date: str) -> MarketAssetItem:
        with self._lock:
            bars = self._bars_cache.get(item.stock.secid)
        if not bars:
            return item

        target_date = as_of_date.strip()
        if target_date:
            bars_before = [(d, c) for d, c, _ in bars if d < target_date]
        else:
            bars_before = [(d, c) for d, c, _ in bars]

        if not bars_before:
            all_points = [(d, c) for d, c, _ in bars]
            if len(all_points) >= 2:
                closes = [round(c, 4) for _, c in all_points[-20:]]
                if target_date and all_points[-1][0] == target_date and item.latest > 0 and is_active_market_asset(item):
                    closes[-1] = round(item.latest, 4)
                return replace(item, sparkline=tuple(closes))
            return item

        ratio = 1.0
        if item.previous_close > 0 and bars_before[-1][1] > 0:
            candidate = item.previous_close / bars_before[-1][1]
            if abs(candidate - 1.0) > _EX_DIVIDEND_RATIO_THRESHOLD:
                ratio = candidate
                with self._lock:
                    self._resync_needed.add(item.stock.secid)

        if is_active_market_asset(item) and item.latest > 0:
            closes = [round(c * ratio, 4) for _, c in bars_before[-19:]]
            closes.append(round(item.latest, 4))
        else:
            closes = [round(c * ratio, 4) for _, c in bars_before[-20:]]
        if len(closes) >= 2:
            return replace(item, sparkline=tuple(closes))
        return item

    def _enrich_missing_page_sparklines(
        self,
        items: list[MarketAssetItem],
        as_of_date: str,
    ) -> list[MarketAssetItem]:
        if not items or not as_of_date:
            return items

        missing_indices: list[int] = []
        with self._lock:
            for idx, item in enumerate(items):
                secid = item.stock.secid
                if secid in self._resync_needed:
                    missing_indices.append(idx)
                elif len(item.sparkline) < 2 and secid not in self._sync_dates:
                    missing_indices.append(idx)

        if not missing_indices:
            return items

        now_sh = self._now_fn().astimezone(_SHANGHAI_TZ)
        today_str = now_sh.date().isoformat()
        after_close = now_sh.time() >= _MARKET_CLOSE_TIME

        def _fetch_for_item(item: MarketAssetItem) -> tuple[str, list[tuple[str, float]] | None, bool]:
            secid = item.stock.secid
            with self._lock:
                prefer_qfq = secid in self._resync_needed
            points, replaced = self._fetch_daily_bar_points(
                item,
                as_of_date=as_of_date,
                datalen=22,
                prefer_qfq=prefer_qfq,
            )
            return secid, points, replaced

        targets = [items[idx] for idx in missing_indices]
        with ThreadPoolExecutor(max_workers=12) as executor:
            fetched = list(executor.map(_fetch_for_item, targets))

        bar_batches: list[tuple[str, list[tuple[str, float, bool]], str | None]] = []
        replace_secids: set[str] = set()
        resolved = list(items)
        fetched_by_secid: dict[str, list[tuple[str, float]] | None] = {}

        for secid, points, replaced in fetched:
            fetched_by_secid[secid] = points
            if points is None:
                continue
            typed_bars = [
                (d, round(c, 4), (d < today_str) or (d == today_str and after_close))
                for d, c in points
                if d <= today_str
            ]
            completed_dates = [d for d, _, is_final in typed_bars if is_final]
            sync_through = completed_dates[-1] if completed_dates else (typed_bars[-1][0] if typed_bars else as_of_date)
            bar_batches.append((secid, typed_bars, sync_through))
            if replaced:
                replace_secids.add(secid)

        if bar_batches:
            self._commit_bar_batches(bar_batches, replace_secids=replace_secids)

        for idx in missing_indices:
            secid = resolved[idx].stock.secid
            with self._lock:
                self._resync_needed.discard(secid)
            updated = self._apply_cached_sparkline(resolved[idx], as_of_date)
            if len(updated.sparkline) >= 2:
                resolved[idx] = updated
            else:
                points = fetched_by_secid.get(secid)
                if points:
                    pts = [(d, c) for d, c in points]
                    if resolved[idx].latest > 0 and is_active_market_asset(resolved[idx]):
                        if pts[-1][0] < as_of_date:
                            pts.append((as_of_date, round(resolved[idx].latest, 4)))
                        elif pts[-1][0] == as_of_date:
                            pts[-1] = (as_of_date, round(resolved[idx].latest, 4))
                    if len(pts) >= 2:
                        resolved[idx] = replace(resolved[idx], sparkline=tuple(c for _, c in pts[-20:]))

        return resolved

    def _fetch_daily_bar_points(
        self,
        item: MarketAssetItem,
        *,
        as_of_date: str,
        datalen: int = 22,
        prefer_qfq: bool = False,
    ) -> tuple[list[tuple[str, float]] | None, bool]:
        secid = item.stock.secid
        sina_symbol = _to_sina_symbol(secid)

        if prefer_qfq:
            qfq_points, is_qfq = self._fetch_tencent_daily_closes(sina_symbol, count=datalen)
            if is_qfq and self._matches_previous_close(qfq_points, item, as_of_date):
                return qfq_points, True

        sina_bars = self._fetch_sina_daily_bars(sina_symbol, count=datalen)
        if sina_bars:
            points = [(bar[0], round(bar[2], 4)) for bar in sina_bars]
            before = [p for p in points if p[0] < as_of_date] if as_of_date else points
            if before and item.previous_close > 0 and before[-1][1] > 0:
                ratio = item.previous_close / before[-1][1]
                if abs(ratio - 1.0) > _EX_DIVIDEND_RATIO_THRESHOLD:
                    qfq_points, is_qfq = self._fetch_tencent_daily_closes(sina_symbol, count=max(datalen, 22))
                    if is_qfq and self._matches_previous_close(qfq_points, item, as_of_date):
                        return qfq_points, True
                    em_points = self._fetch_eastmoney_daily_closes(secid, count=max(datalen, 22))
                    if self._matches_previous_close(em_points, item, as_of_date):
                        return em_points, True
                    points = [
                        (d, round(p * ratio, 4) if d < as_of_date else p)
                        for d, p in points
                    ]
                    return points, True
            return points, prefer_qfq

        gt_points, is_qfq = self._fetch_tencent_daily_closes(sina_symbol, count=datalen)
        if gt_points:
            return gt_points, prefer_qfq or is_qfq

        em_points = self._fetch_eastmoney_daily_closes(secid, count=datalen)
        if em_points:
            return em_points, True

        return None, False

    @staticmethod
    def _matches_previous_close(
        points: list[tuple[str, float]],
        item: MarketAssetItem,
        as_of_date: str,
    ) -> bool:
        before = [p for p in points if p[0] < as_of_date] if as_of_date else points
        if len(before) < 2:
            return False
        if item.previous_close <= 0 or before[-1][1] <= 0:
            return True
        return abs(item.previous_close / before[-1][1] - 1.0) <= _EX_DIVIDEND_RATIO_THRESHOLD

    def _commit_bar_batches(
        self,
        batches: Sequence[tuple[str, Sequence[tuple[str, float, bool]], str | None]],
        *,
        replace_secids: set[str] | None = None,
        keep_per_secid: int = 25,
    ) -> None:
        if not batches and not replace_secids:
            return
        with self._lock:
            if replace_secids:
                for secid in replace_secids:
                    self._bars_cache.pop(secid, None)
            for secid, bars, sync_through in batches:
                merged = {d: (c, f) for d, c, f in self._bars_cache.get(secid, ())}
                for d, c, f in bars:
                    if d and c > 0:
                        prev = merged.get(d)
                        final_flag = bool(f) or (prev[1] if prev is not None else False)
                        merged[d] = (round(float(c), 4), final_flag)
                self._bars_cache[secid] = [
                    (d, c, f)
                    for d, (c, f) in sorted(merged.items(), key=lambda entry: entry[0])[-keep_per_secid:]
                ]
                if sync_through:
                    prev_sync = self._sync_dates.get(secid, "")
                    if sync_through > prev_sync:
                        self._sync_dates[secid] = sync_through
                self._resync_needed.discard(secid)

        if self._cache_store is not None:
            self._cache_store.upsert_daily_bars_batch(
                batches,
                replace_secids=replace_secids,
                keep_per_secid=keep_per_secid,
            )

    def _fetch_closed_calendar(self) -> list[str]:
        now_sh = self._now_fn().astimezone(_SHANGHAI_TZ)
        today_str = now_sh.date().isoformat()
        after_close = now_sh.time() >= _MARKET_CLOSE_TIME

        dates: list[str] = []
        sina_bars = self._fetch_sina_daily_bars("sh000001", count=30)
        if sina_bars:
            dates = [bar[0] for bar in sina_bars]
        else:
            gt_points, _ = self._fetch_tencent_daily_closes("sh000001", count=30)
            if gt_points:
                dates = [d for d, _ in gt_points]
            else:
                em_points = self._fetch_eastmoney_daily_closes("000001.SH", count=30)
                dates = [d for d, _ in em_points]

        closed = sorted({d for d in dates if d < today_str or (d == today_str and after_close)})
        if after_close:
            quote_date, has_turnover = self._probe_sina_market_status()
            if quote_date == today_str and has_turnover and today_str not in closed:
                closed.append(today_str)
                closed.sort()
        return closed[-25:]

    def sync_all_sparklines_once(self) -> dict[str, int]:
        stats = {"synced": 0, "incremental": 0, "backfilled": 0, "failed": 0}
        if self._cache_store is None:
            return stats

        recent_closed = self._fetch_closed_calendar()
        if not recent_closed:
            return stats
        latest_closed = recent_closed[-1]
        prev_closed = recent_closed[-2] if len(recent_closed) >= 2 else None

        now_sh = self._now_fn().astimezone(_SHANGHAI_TZ)
        today_str = now_sh.date().isoformat()
        after_close = now_sh.time() >= _MARKET_CLOSE_TIME
        today_closed = (latest_closed == today_str and after_close)

        logger = logging.getLogger(__name__)
        all_items: dict[str, tuple[str | None, MarketAssetItem]] = {}
        category_snapshots: dict[AssetCategory, tuple[str | None, tuple[MarketAssetItem, ...]]] = {}
        for category in (AssetCategory.STOCK, AssetCategory.ETF):
            if self._sync_stop.is_set():
                return stats
            try:
                as_of_date, items = self._refresh_category(category)
                category_snapshots[category] = (as_of_date, items)
                for item in items:
                    all_items[item.stock.secid] = (as_of_date, item)
            except (MarketDataUnavailable, MarketDataNotFound):
                continue

        if not all_items:
            return stats

        incremental_batches: list[tuple[str, list[tuple[str, float, bool]], str | None]] = []
        fetch_queue: list[tuple[MarketAssetItem, str | None, int, bool]] = []

        with self._lock:
            resync_snapshot = set(self._resync_needed)
            for secid, (snap_date, item) in all_items.items():
                sync_date = self._sync_dates.get(secid, "")
                existing = self._bars_cache.get(secid, [])
                needs_resync = secid in resync_snapshot

                if sync_date >= latest_closed and not needs_resync:
                    stats["synced"] += 1
                    continue

                before_bars = [(d, c) for d, c, _ in existing if d < latest_closed]
                no_ex_div = (
                    item.previous_close <= 0
                    or not before_bars
                    or abs(item.previous_close / before_bars[-1][1] - 1.0) <= _EX_DIVIDEND_RATIO_THRESHOLD
                )
                if (
                    today_closed
                    and snap_date == today_str
                    and prev_closed is not None
                    and sync_date == prev_closed
                    and not needs_resync
                    and no_ex_div
                ):
                    bars_to_add = [(today_str, round(item.latest, 4), True)] if is_active_market_asset(item) and item.latest > 0 else []
                    incremental_batches.append((secid, bars_to_add, today_str))
                    continue

                if needs_resync or not sync_date or not existing:
                    datalen = 22
                    needs_replace = True
                else:
                    missing_days = sum(1 for d in recent_closed if d > sync_date)
                    if missing_days >= 18:
                        datalen = 22
                        needs_replace = True
                    else:
                        datalen = max(4, missing_days + 2)
                        needs_replace = False

                fetch_queue.append((item, snap_date, datalen, needs_replace))

        for start in range(0, len(incremental_batches), 500):
            if self._sync_stop.is_set():
                return stats
            chunk = incremental_batches[start : start + 500]
            self._commit_bar_batches(chunk)
            stats["incremental"] += len(chunk)
            stats["synced"] += len(chunk)

        def _sync_one(task: tuple[MarketAssetItem, str | None, int, bool]):
            item, snap_date, datalen, needs_replace = task
            secid = item.stock.secid
            target_ref_date = snap_date or latest_closed
            points, replaced = self._fetch_daily_bar_points(
                item,
                as_of_date=target_ref_date,
                datalen=datalen,
                prefer_qfq=needs_replace,
            )
            if points is None:
                return None

            if not needs_replace and not replaced:
                with self._lock:
                    existing_map = {d: c for d, c, _ in self._bars_cache.get(secid, ())}
                mismatch = any(
                    d in existing_map and existing_map[d] > 0 and abs(c / existing_map[d] - 1.0) > _EX_DIVIDEND_RATIO_THRESHOLD
                    for d, c in points
                )
                if mismatch:
                    full_points, _ = self._fetch_daily_bar_points(
                        item,
                        as_of_date=target_ref_date,
                        datalen=22,
                        prefer_qfq=True,
                    )
                    if full_points is not None:
                        points = full_points
                        replaced = True

            typed_bars = [
                (d, round(c, 4), (d < today_str) or (d == today_str and after_close))
                for d, c in points
                if d <= today_str
            ]
            return secid, typed_bars, (needs_replace or replaced)

        if fetch_queue:
            total_to_fetch = len(fetch_queue)
            logger.info("开始同步全市场 20 日走势: 待补齐=%d, 零请求增量=%d, 基准收盘日=%s", total_to_fetch, len(incremental_batches), latest_closed)
            next_log_at = 1000
            with ThreadPoolExecutor(max_workers=12) as executor:
                for idx in range(0, total_to_fetch, _SYNC_BATCH_SIZE):
                    if self._sync_stop.is_set():
                        return stats
                    chunk_tasks = fetch_queue[idx : idx + _SYNC_BATCH_SIZE]
                    results = list(executor.map(_sync_one, chunk_tasks))
                    commit_list: list[tuple[str, list[tuple[str, float, bool]], str | None]] = []
                    replace_set: set[str] = set()
                    for res in results:
                        if res is None:
                            stats["failed"] += 1
                            continue
                        secid, typed_bars, should_replace = res
                        commit_list.append((secid, typed_bars, latest_closed))
                        if should_replace:
                            replace_set.add(secid)
                        stats["backfilled"] += 1
                        stats["synced"] += 1
                    if commit_list:
                        self._commit_bar_batches(commit_list, replace_secids=replace_set)
                    processed = min(idx + len(chunk_tasks), total_to_fetch)
                    if processed >= next_log_at or processed == total_to_fetch:
                        logger.info("全市场 20 日走势同步进度: %d/%d", processed, total_to_fetch)
                        next_log_at += 1000

        if stats["incremental"] > 0 or stats["backfilled"] > 0:
            for category, (snap_date, snap_items) in category_snapshots.items():
                spark_date = snap_date or today_str
                updated_items = tuple(self._apply_cached_sparkline(item, spark_date) for item in snap_items)
                with self._lock:
                    existing_snap = self._snapshot_cache.get(category)
                    if existing_snap is not None and existing_snap[1] == snap_date:
                        self._snapshot_cache[category] = (existing_snap[0], snap_date, updated_items)
                self._cache_store.save_items(category, spark_date, updated_items)
            logger.info(
                "全市场 20 日走势同步完成: synced=%d incremental=%d backfilled=%d failed=%d",
                stats["synced"],
                stats["incremental"],
                stats["backfilled"],
                stats["failed"],
            )

        if len(recent_closed) >= 25:
            self._cache_store.prune_old_daily_bars(recent_closed[0])

        return stats

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
            market_label = "ETF"
        else:
            if market == "1" and code.startswith("6"):
                market_label = "沪A"
            elif market == "0" and code.startswith(("0", "3")):
                market_label = "深A"
            elif market == "0" and code.startswith(("4", "8", "9")):
                market_label = "京A"
            else:
                return None

        prev_close = _num(row.get("f18"))
        latest = _num(row.get("f2"))
        if latest <= 0:
            latest = prev_close
        if latest <= 0:
            return None
        decimals = 3 if category is AssetCategory.ETF else 2
        vol_lots = _num(row.get("f5"))
        amount = _num(row.get("f6"))
        return MarketAssetItem(
            stock=Stock(secid=secid, symbol=code, name=name, market=market_label),
            latest=round(latest, decimals),
            change=round(_num(row.get("f4")), decimals),
            change_percent=round(_num(row.get("f3")), 2),
            open=round(_num(row.get("f17"), latest), decimals),
            high=round(_num(row.get("f15"), latest), decimals),
            low=round(_num(row.get("f16"), latest), decimals),
            previous_close=round(prev_close or latest, decimals),
            volume=round(vol_lots * 100) if vol_lots > 0 else 0,
            amount=round(amount, 2) if amount > 0 else 0.0,
        )

    @staticmethod
    def _parse_sina_stock_only(
        row: dict,
        category: AssetCategory,
        *,
        expected_exchange: str | None = None,
    ) -> Stock | None:
        if not isinstance(row, dict):
            return None
        symbol = str(row.get("symbol") or "").strip().lower()
        if len(symbol) < 8:
            return None
        exchange, code = symbol[:2], symbol[2:]
        if exchange not in {"sh", "sz", "bj"} or len(code) != 6 or not code.isdigit():
            return None
        if expected_exchange is not None and exchange != expected_exchange:
            return None
        name = str(row.get("name") or code).strip()
        if category is AssetCategory.ETF:
            if any(term in name.upper() for term in ("LOF", "REIT")):
                return None
            if not ((exchange == "sz" and code.startswith("15")) or (exchange == "sh" and code.startswith("5"))):
                return None
            market_label = "ETF"
        else:
            if exchange == "sh" and code.startswith("6"):
                market_label = "沪A"
            elif exchange == "sz" and code.startswith(("0", "3")):
                market_label = "深A"
            elif exchange == "bj" and code.startswith(("4", "8", "9")):
                market_label = "京A"
            else:
                return None
        return Stock(secid=f"{code}.{exchange.upper()}", symbol=code, name=name, market=market_label)

    @classmethod
    def _parse_sina_row(
        cls,
        row: dict,
        category: AssetCategory,
        *,
        expected_exchange: str | None = None,
    ) -> MarketAssetItem | None:
        stock = cls._parse_sina_stock_only(row, category, expected_exchange=expected_exchange)
        if stock is None:
            return None
        latest = _num(row.get("trade"))
        prev_close = _num(row.get("settlement"))
        if latest <= 0:
            latest = prev_close
        if latest <= 0:
            return None
        decimals = 3 if category is AssetCategory.ETF else 2
        vol = _num(row.get("volume"))
        amt = _num(row.get("amount"))
        return MarketAssetItem(
            stock=stock,
            latest=round(latest, decimals),
            change=round(_num(row.get("pricechange"), latest - prev_close if prev_close > 0 else 0.0), decimals),
            change_percent=round(_num(row.get("changepercent")), 2),
            open=round(_num(row.get("open"), latest), decimals),
            high=round(_num(row.get("high"), latest), decimals),
            low=round(_num(row.get("low"), latest), decimals),
            previous_close=round(prev_close or latest, decimals),
            volume=round(vol) if vol > 0 else 0,
            amount=round(amt, 2) if amt > 0 else 0.0,
        )
