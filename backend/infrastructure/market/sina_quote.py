from __future__ import annotations

from datetime import datetime
from zoneinfo import ZoneInfo

from ...domain.errors import MarketDataNotFound, MarketDataUnavailable
from ...domain.models import RealtimeQuote
from ...domain.security import SecurityId
from ..http.json_client import JsonHttpClient

_SHANGHAI_TZ = ZoneInfo("Asia/Shanghai")


def _sina_symbol(secid: str) -> str:
    sid = SecurityId.parse(secid)
    return f"{sid.exchange.value.lower()}{sid.code}"


def _parse_float(raw: str, default: float = 0.0) -> float:
    try:
        return float(raw)
    except (TypeError, ValueError):
        return default


class SinaQuoteReader:
    """新浪财经实时报价适配器，支持单只与批量查询。"""

    URL = "https://hq.sinajs.cn/list="
    HEADERS = {"Accept": "*/*", "Referer": "https://finance.sina.com.cn"}
    BATCH_SIZE = 50

    def __init__(self, http_client: JsonHttpClient):
        self._http = http_client

    def read_quote(self, secid: str) -> RealtimeQuote:
        sid = SecurityId.parse(secid)
        canonical_secid = str(sid)
        quotes = self.read_quotes((canonical_secid,))
        quote = quotes.get(canonical_secid)
        if quote is None:
            raise MarketDataNotFound("未找到该证券的实时行情")
        return quote

    def read_quotes(self, secids: tuple[str, ...]) -> dict[str, RealtimeQuote]:
        if not secids:
            return {}
        canonical_by_sina: dict[str, tuple[str, SecurityId]] = {}
        ordered_symbols: list[str] = []
        for raw_secid in secids:
            sid = SecurityId.parse(raw_secid)
            canonical = str(sid)
            sina_key = f"{sid.exchange.value.lower()}{sid.code}"
            if sina_key not in canonical_by_sina:
                canonical_by_sina[sina_key] = (canonical, sid)
                ordered_symbols.append(sina_key)

        results: dict[str, RealtimeQuote] = {}
        for index in range(0, len(ordered_symbols), self.BATCH_SIZE):
            batch = ordered_symbols[index : index + self.BATCH_SIZE]
            text = self._http.get_text(
                f"{self.URL}{','.join(batch)}",
                None,
                headers=self.HEADERS,
                encoding="gbk",
            )
            for line in text.splitlines():
                quote = self._parse_line(line, canonical_by_sina)
                if quote is not None:
                    results[quote.secid] = quote
        return results

    @staticmethod
    def _parse_line(
        line: str,
        canonical_by_sina: dict[str, tuple[str, SecurityId]],
    ) -> RealtimeQuote | None:
        prefix = "var hq_str_"
        if not line.startswith(prefix) or "=" not in line:
            return None
        key_part, _, rest = line[len(prefix) :].partition("=")
        sina_key = key_part.strip()
        target = canonical_by_sina.get(sina_key)
        if target is None:
            return None
        canonical_secid, sid = target
        start_quote = rest.find('"')
        end_quote = rest.rfind('"')
        if start_quote < 0 or end_quote <= start_quote:
            return None
        payload = rest[start_quote + 1 : end_quote]
        if not payload:
            return None
        fields = payload.split(",")
        if len(fields) < 32:
            raise MarketDataUnavailable("新浪实时报价返回格式异常")

        name = fields[0].strip() or canonical_secid
        prev_close = _parse_float(fields[2])
        latest = _parse_float(fields[3])
        if latest <= 0 and prev_close > 0:
            latest = prev_close
        if latest <= 0:
            return None
        open_price = _parse_float(fields[1]) or prev_close or latest
        high_price = _parse_float(fields[4]) or latest
        low_price = _parse_float(fields[5]) or latest
        volume = round(_parse_float(fields[8]))
        amount = round(_parse_float(fields[9]), 2)

        is_etf = sid.code.startswith(("5", "15", "16"))
        decimal = 3 if is_etf else 2
        change = round(latest - prev_close, decimal) if prev_close > 0 else 0.0
        change_percent = round((change / prev_close) * 100.0, 2) if prev_close > 0 else 0.0

        date_part = fields[30].strip()
        time_part = fields[31].strip()
        try:
            dt = datetime.strptime(f"{date_part} {time_part}", "%Y-%m-%d %H:%M:%S").replace(
                tzinfo=_SHANGHAI_TZ
            )
        except ValueError as error:
            raise MarketDataUnavailable("新浪实时报价时间格式异常") from error

        return RealtimeQuote(
            secid=canonical_secid,
            symbol=sid.code,
            name=name,
            time=dt,
            open=round(open_price, decimal),
            high=round(high_price, decimal),
            low=round(low_price, decimal),
            previous_close=round(prev_close, decimal),
            latest=round(latest, decimal),
            change=change,
            change_percent=change_percent,
            volume=volume,
            amount=amount,
        )
