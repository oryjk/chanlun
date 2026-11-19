from concurrent.futures import ThreadPoolExecutor
from datetime import date, datetime, time
import json
from math import isfinite
from typing import Any, Callable

from ...application.ports.readers import MarketDataReader
from ...domain.errors import ChanlunError, MarketDataNotFound, MarketDataUnavailable
from ...domain.models import TimeFrame
from ...domain.overnight import (
    MARKET_TZ, BoardQuote, DailySeal, HistoricalSeals, MinuteBar, MinuteSeries,
    ScreeningQuote, is_mainboard,
)
from ...domain.security import SecurityId
from ..http.json_client import JsonHttpClient
from .eastmoney_codes import to_eastmoney
from .eastmoney_intraday import EastmoneyIntradayReader
from .sina_quote import SinaQuoteReader
from .sina_stock_pool import SinaStockPoolReader


def _number(value: Any) -> float | None:
    try:
        number = float(value)
    except (TypeError, ValueError):
        return None
    return number if isfinite(number) else None


def _required(value: Any, *, minimum: float | None = None) -> float:
    number = _number(value)
    if number is None or (minimum is not None and number < minimum):
        raise ValueError("无效行情数字")
    return number


def _get_json(http, url, params, headers):
    try:
        payload = json.loads(http.get_text(url, params, headers=headers))
    except json.JSONDecodeError as error:
        raise MarketDataUnavailable("行情服务返回格式异常") from error
    if not isinstance(payload, dict):
        raise MarketDataUnavailable("行情服务返回格式异常")
    return payload


class SinaOvernightUniverseReader(SinaStockPoolReader):
    NODES = ("sh_a", "sz_a")
    WORKERS = 6

    def read_universe(self) -> tuple[BoardQuote, ...]:
        # 行情中心只给 ticktime，用同源指数的交易日补足日期，保留各标的源时间。
        with ThreadPoolExecutor(max_workers=self.WORKERS) as executor:
            index_quote = executor.submit(SinaQuoteReader(self._http).read_quote, "000001.SH")
            rows = self._read_universe_pages(executor)
            trade_date = index_quote.result().time.date()

        quotes = []
        try:
            for row in rows:
                symbol = row["symbol"]
                exchange, code = symbol[:2], symbol[2:]
                secid = f"{code}.{exchange.upper()}"
                if not is_mainboard(secid):
                    continue
                previous_close = _required(row["settlement"], minimum=0.01)
                price = _required(row["trade"], minimum=0)
                change = _required(row["changepercent"])
                if price == 0 and _number(row.get("volume")) == 0:
                    price, change = previous_close, 0.0
                if price <= 0:
                    raise ValueError("无效股票价格")
                name = row["name"].strip()
                if not name:
                    raise ValueError("缺少股票简称")
                tick_time = time.fromisoformat(row["ticktime"])
                quotes.append(BoardQuote(
                    secid, code, name, price, previous_close,
                    change, datetime.combine(trade_date, tick_time, tzinfo=MARKET_TZ),
                ))
        except (AttributeError, KeyError, TypeError, ValueError) as error:
            raise MarketDataUnavailable("新浪沪深主板行情字段异常") from error

        if not quotes or len({quote.secid for quote in quotes}) != len(quotes):
            raise MarketDataUnavailable("新浪沪深主板行情暂不完整")
        return tuple(quotes)

    def _read_universe_pages(self, executor: ThreadPoolExecutor) -> list[dict]:
        counts = dict(zip(self.NODES, executor.map(self._count, self.NODES), strict=True))
        pages = {
            node: [
                executor.submit(self._page, node, page)
                for page in range(1, (count + self.PAGE_SIZE - 1) // self.PAGE_SIZE + 1)
            ]
            for node, count in counts.items()
        }
        rows = []
        for node, count in counts.items():
            node_rows = [row for page in pages[node] for row in page.result()]
            symbols = {
                row["symbol"] for row in node_rows
                if isinstance(row, dict) and isinstance(row.get("symbol"), str)
                and row["symbol"].startswith(node[:2])
            }
            if len(node_rows) != count or len(symbols) != count:
                raise MarketDataUnavailable("新浪沪深主板行情暂不完整")
            rows.extend(node_rows)
        if tuple(executor.map(self._count, self.NODES)) != tuple(counts.values()):
            raise MarketDataUnavailable("新浪沪深主板证券清单发生变化，请稍后重试")
        return rows

    def _page(self, node, page):
        for attempt in range(self.PAGE_ATTEMPTS):
            try:
                text = self._http.get_text(
                    f"{self.BASE}.getHQNodeData",
                    {"page": page, "num": self.PAGE_SIZE, "sort": "symbol", "asc": 1, "node": node},
                    headers=SinaQuoteReader.HEADERS,
                )
                rows = json.loads(text)
                if isinstance(rows, list) and rows:
                    return rows
            except (ChanlunError, json.JSONDecodeError):
                pass
            if attempt + 1 < self.PAGE_ATTEMPTS:
                self._sleep(self.RETRY_DELAY_SECONDS * (attempt + 1))
        raise MarketDataUnavailable("新浪沪深主板行情暂不完整")


class TencentOvernightQuoteReader:
    URL = "https://qt.gtimg.cn/q="
    HEADERS = {"Accept": "*/*", "Referer": "https://gu.qq.com/"}
    BATCH_SIZE = 50
    WORKERS = 4

    def __init__(self, http_client: JsonHttpClient):
        self._http = http_client

    def read_quotes(self, secids: tuple[str, ...]) -> dict[str, ScreeningQuote]:
        symbols = {}
        for secid in secids:
            sid = SecurityId.parse(secid)
            symbols[f"{sid.exchange.value.lower()}{sid.code}"] = sid
        if not symbols:
            return {}
        keys = tuple(symbols)
        batches = [
            {key: symbols[key] for key in keys[start:start + self.BATCH_SIZE]}
            for start in range(0, len(keys), self.BATCH_SIZE)
        ]
        results = {}
        with ThreadPoolExecutor(max_workers=min(self.WORKERS, len(batches))) as executor:
            requests = [executor.submit(self._read_batch, batch) for batch in batches]
            for request in requests:
                try:
                    results.update(request.result())
                except (MarketDataNotFound, MarketDataUnavailable):
                    # 调用方根据缺失证券判定覆盖不完整。
                    continue
        return results

    def _read_batch(self, symbols: dict[str, SecurityId]) -> dict[str, ScreeningQuote]:
        text = self._http.get_text(
            f"{self.URL}{','.join(symbols)}", None, headers=self.HEADERS, encoding="gbk",
        )
        quotes = {}
        for line in text.splitlines():
            key, separator, payload = line.strip().partition("=")
            sid = symbols.get(key.removeprefix("v_"))
            if not separator or sid is None or not payload.startswith('"'):
                continue
            fields = payload.rstrip('";').removeprefix('"').split("~")
            if len(fields) <= 32 or fields[2] != sid.code or not fields[1].strip():
                continue
            try:
                as_of = datetime.strptime(fields[30], "%Y%m%d%H%M%S").replace(tzinfo=MARKET_TZ)
                price = _required(fields[3], minimum=0.01)
                previous_close = _required(fields[4], minimum=0.01)
                change = _required(fields[32])
            except ValueError:
                continue
            turnover = _number(fields[38]) if len(fields) > 38 else None
            market_cap = _number(fields[45]) if len(fields) > 45 else None
            limit_up = (_number(fields[47]) or None) if len(fields) > 47 else None
            volume_ratio = _number(fields[49]) if len(fields) > 49 else None
            session_low = (_number(fields[34]) or None) if len(fields) > 34 else None
            canonical = str(sid)
            quotes[canonical] = ScreeningQuote(
                canonical, sid.code, fields[1].strip(), price, previous_close, change,
                volume_ratio, turnover, round(market_cap * 100_000_000) if market_cap is not None else None,
                limit_up, as_of, session_low,
            )
        return quotes


class EastmoneyOvernightMinuteReader:
    HEADERS = {"Referer": "https://quote.eastmoney.com/"}
    FIELDS1 = "f1,f2,f3,f4,f5,f6,f7,f8,f9,f10,f11,f12,f13"

    def __init__(self, http_client: JsonHttpClient):
        self._http = http_client

    def read_minutes(self, secid: str) -> MinuteSeries:
        sid = SecurityId.parse(secid)
        canonical = str(sid)
        payload = _get_json(self._http, EastmoneyIntradayReader.URL, {
            "secid": to_eastmoney(canonical),
            "ndays": 1,
            "iscr": 0,
            "fields1": self.FIELDS1,
            "fields2": "f51,f52,f53,f54,f55,f56,f57,f58",
        }, self.HEADERS)
        data = payload.get("data")
        if not isinstance(data, dict) or not data.get("trends"):
            raise MarketDataNotFound("未找到该证券的完整当日分时数据")
        if (
            payload.get("rc") != 0 or payload.get("full") != 1
            or data.get("code") != sid.code or data.get("market") != (1 if sid.exchange.value == "SH" else 0)
        ):
            raise MarketDataUnavailable("东方财富分时行情证券标识不一致")
        try:
            previous_close = _required(data["preClose"], minimum=0.01)
            bars = tuple(self._parse_bar(row) for row in data["trends"])
        except (AttributeError, KeyError, TypeError, ValueError) as error:
            raise MarketDataUnavailable("东方财富分时行情字段异常") from error
        return MinuteSeries(canonical, previous_close, bars)

    @staticmethod
    def _parse_bar(row: str) -> MinuteBar:
        stamp, _, close, _, low, volume, _, average = row.split(",")
        return MinuteBar(
            datetime.strptime(stamp, "%Y-%m-%d %H:%M").replace(tzinfo=MARKET_TZ),
            _required(close, minimum=0.01), _required(low, minimum=0.01),
            _required(average, minimum=0.001),
            round(_required(volume, minimum=0) * 100),
        )


class OvernightSessionReader:
    def __init__(self, market_data: MarketDataReader):
        self._market_data = market_data

    def read_sessions(self, through: date) -> tuple[date, ...]:
        index = self._market_data.read("000001.SH", TimeFrame.ONE_DAY, force_refresh=True)
        dates = {bar.time.astimezone(MARKET_TZ).date() for bar in index.candles}
        sessions = tuple(sorted(day for day in dates if day <= through))
        if not sessions:
            raise MarketDataUnavailable("暂时无法确认沪深交易日")
        return sessions


class TonghuashunOvernightSealReader:
    URL = "https://data.10jqka.com.cn/dataapi/limit_up/limit_up_pool"
    HEADERS = {"Referer": "https://data.10jqka.com.cn/"}
    PAGE_SIZE = 100
    # 主板常规 / 风险警示及退市整理市场标识。
    EXCHANGES = {17: "SH", 22: "SH", 33: "SZ", 37: "SZ"}

    def __init__(self, http_client: JsonHttpClient, *, today: Callable[[], date] | None = None):
        self._http = http_client
        self._today = today if today is not None else lambda: datetime.now(MARKET_TZ).date()

    def read_seals(self, trade_date: date) -> HistoricalSeals:
        today = self._today()
        if trade_date > today:
            return HistoricalSeals(trade_date, (), False)
        try:
            return self._read_seals(trade_date, require_closed=trade_date == today)
        except (ChanlunError, KeyError, TypeError, ValueError):
            return HistoricalSeals(trade_date, (), False)

    def _read_seals(self, trade_date: date, *, require_closed: bool) -> HistoricalSeals:
        data = self._read_page(trade_date, 1, require_closed=require_closed)
        counters = data["page"]
        total, pages, size = counters["total"], counters["count"], counters["limit"]
        if not 1 <= size <= self.PAGE_SIZE or not 0 <= total <= 10_000 or pages != (total + size - 1) // size:
            raise ValueError("无效涨停池分页")

        rows = list(data["info"])
        for page in range(2, pages + 1):
            data = self._read_page(trade_date, page, require_closed=require_closed)
            if any(data["page"][field] != counters[field] for field in ("total", "count", "limit")):
                raise ValueError("涨停池分页发生变化")
            rows.extend(data["info"])

        seals = []
        seen = set()
        for row in rows:
            exchange = self.EXCHANGES[row["market_id"]]
            code = row["code"]
            if not isinstance(code, str) or len(code) != 6 or not code.isascii() or not code.isdigit():
                raise ValueError("无效涨停证券代码")
            secid = f"{code}.{exchange}"
            if secid in seen:
                raise ValueError("涨停池分页包含重复股票")
            seen.add(secid)
            if is_mainboard(secid):
                # 封板事件的收盘成交价就是当日涨停价。盘中炸板记录由同花顺的另一数据集维护。
                close = _required(row["latest"], minimum=0.01)
                seals.append(DailySeal(secid, close, close))
        if len(seen) != total:
            raise ValueError("涨停池分页数量不符")
        return HistoricalSeals(trade_date, tuple(seals), True)

    def _read_page(self, trade_date: date, page: int, *, require_closed: bool) -> dict:
        key = trade_date.strftime("%Y%m%d")
        payload = _get_json(self._http, self.URL, {
            "page": page,
            "limit": self.PAGE_SIZE,
            "field": "10,market_id,code,name,latest,330324",
            # 同花顺的 HS 单独查询会遗漏主板风险警示股票。
            "filter": "HS,ST",
            "order_field": "330324",
            "order_type": 0,
            "date": key,
        }, self.HEADERS)
        data = payload.get("data")
        if payload.get("status_code") != 0 or not isinstance(data, dict) or data.get("date") != key:
            raise MarketDataUnavailable("同花顺未返回指定交易日的收盘封板")
        if require_closed and data["trade_status"]["id"] != "closed":
            raise MarketDataUnavailable("同花顺尚未更新当日收盘封板")
        if not isinstance(data.get("info"), list):
            raise ValueError("无效涨停池数据")
        counters = data["page"]
        current = counters["page"]
        if current != page and not (page == 1 and current == 0 and counters["total"] == 0):
            raise ValueError("涨停池页码错误")
        return data
