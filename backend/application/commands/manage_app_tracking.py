from zoneinfo import ZoneInfo

from ...domain.errors import ChanlunError, MarketDataNotFound
from ...domain.models import TimeFrame
from ...domain.security import Exchange, SecurityId
from ...domain.tracking import TrackedAssetRecord
from ..ports.clock import Clock
from ..ports.readers import MarketDataReader, QuoteReader, StockSearchReader
from ..ports.tracking import AppTrackingReader, AppTrackingWriter
from ..security_ids import normalize_security_id

_SHANGHAI_TZ = ZoneInfo("Asia/Shanghai")


def _infer_market(secid: str, matched_market: str | None = None) -> str:
    if matched_market in {"沪A", "深A", "北A", "ETF"}:
        return matched_market
    sid = SecurityId.parse(secid)
    if (sid.exchange is Exchange.SH and sid.code.startswith("5")) or (
        sid.exchange is Exchange.SZ and sid.code.startswith("15")
    ):
        return "ETF"
    if sid.exchange is Exchange.SH:
        return "沪A"
    if sid.exchange is Exchange.BJ:
        return "北A"
    return "深A"


class AppTrackingCommands:
    def __init__(
        self,
        tracking_reader: AppTrackingReader,
        tracking_writer: AppTrackingWriter,
        quote_reader: QuoteReader,
        market_data: MarketDataReader,
        stock_search: StockSearchReader,
        clock: Clock,
    ):
        self._reader = tracking_reader
        self._writer = tracking_writer
        self._quote_reader = quote_reader
        self._market_data = market_data
        self._stock_search = stock_search
        self._clock = clock

    def start(self, user_id: str, raw_secid: str) -> TrackedAssetRecord:
        secid = normalize_security_id(raw_secid)
        for existing in self._reader.list_by_user(user_id):
            if existing.secid == secid:
                return existing

        sid = SecurityId.parse(secid)
        now = self._clock.now()
        start_date = now.astimezone(_SHANGHAI_TZ).date()
        symbol = sid.code
        name = secid
        start_price = 0.0
        matched_market: str | None = None

        try:
            quote = self._quote_reader.read_quote(secid)
            symbol = quote.symbol or symbol
            name = quote.name or name
            if quote.latest > 0:
                start_price = quote.latest
        except ChanlunError:
            pass

        if start_price <= 0 or name == secid:
            try:
                data = self._market_data.read(secid, TimeFrame.ONE_DAY, force_refresh=False)
                if data.instrument.name:
                    name = data.instrument.name
                if data.instrument.symbol:
                    symbol = data.instrument.symbol
                if start_price <= 0 and data.candles:
                    start_price = data.candles[-1].close
            except ChanlunError:
                pass

        if name == secid or matched_market is None:
            try:
                match = next((item for item in self._stock_search.search(sid.code) if item.secid == secid), None)
                if match is not None:
                    if name == secid and match.name:
                        name = match.name
                    matched_market = match.market
            except ChanlunError:
                pass

        if start_price <= 0:
            raise MarketDataNotFound("暂无法获取该标的当前价格，请稍后重试")

        record = TrackedAssetRecord(
            user_id=user_id,
            secid=secid,
            symbol=symbol,
            name=name,
            market=_infer_market(secid, matched_market),
            start_date=start_date,
            start_price=round(start_price, 4),
            created_at=now,
        )
        return self._writer.upsert_tracking(record)

    def cancel(self, user_id: str, raw_secids: list[str] | tuple[str, ...]) -> int:
        normalized = tuple(dict.fromkeys(normalize_security_id(secid) for secid in raw_secids))
        if not normalized:
            return 0
        return self._writer.remove_trackings(user_id, normalized)

    def clear_user(self, user_id: str) -> None:
        self._writer.clear_user_trackings(user_id)
