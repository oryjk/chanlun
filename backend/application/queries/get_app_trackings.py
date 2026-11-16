from zoneinfo import ZoneInfo

from ...domain.errors import ChanlunError
from ...domain.models import TimeFrame
from ...domain.tracking import (
    TrackedAssetSummary,
    TrackingOverview,
    build_tracked_asset_summary,
)
from ..ports.clock import Clock
from ..ports.readers import BatchQuoteReader, MarketDataReader, RankingReader
from ..ports.tracking import AppTrackingReader

_SHANGHAI_TZ = ZoneInfo("Asia/Shanghai")
_MAX_CANDLE_SPARKLINE_FALLBACKS = 12


class GetAppTrackingsQuery:
    def __init__(
        self,
        tracking_reader: AppTrackingReader,
        batch_quotes: BatchQuoteReader,
        ranking_reader: RankingReader,
        market_data: MarketDataReader,
        clock: Clock,
    ):
        self._tracking_reader = tracking_reader
        self._batch_quotes = batch_quotes
        self._ranking_reader = ranking_reader
        self._market_data = market_data
        self._clock = clock

    def execute(self, user_id: str) -> TrackingOverview:
        records = self._tracking_reader.list_by_user(user_id)
        today = self._clock.now().astimezone(_SHANGHAI_TZ).date()
        if not records:
            return TrackingOverview(as_of_date=today, items=())

        secids = tuple(record.secid for record in records)
        try:
            quotes = self._batch_quotes.read_quotes(secids)
        except ChanlunError:
            quotes = {}

        ranking_view = self._ranking_reader.read()
        sparkline_map: dict[str, tuple[float, ...]] = {}
        if ranking_view.board is not None:
            for entry in (*ranking_view.board.bullish, *ranking_view.board.bearish):
                if entry.trend:
                    sparkline_map.setdefault(
                        entry.stock.secid,
                        tuple(round(point.price, 4) for point in entry.trend),
                    )
        as_of_date = today
        if quotes:
            quote_dates = [
                quote.time.astimezone(_SHANGHAI_TZ).date()
                for quote in quotes.values()
                if quote.time is not None
            ]
            if quote_dates:
                as_of_date = max(quote_dates)
        elif ranking_view.board is not None and ranking_view.board.trade_date is not None:
            as_of_date = ranking_view.board.trade_date

        summaries: list[TrackedAssetSummary] = []
        candle_fallbacks = 0
        for record in records:
            quote = quotes.get(record.secid)
            sparkline = sparkline_map.get(record.secid, ())
            latest_price = quote.latest if quote is not None and quote.latest > 0 else 0.0
            prev_close = quote.previous_close if quote is not None and quote.previous_close > 0 else None
            day_pct = quote.change_percent if quote is not None else None

            if (not sparkline or latest_price <= 0) and candle_fallbacks < _MAX_CANDLE_SPARKLINE_FALLBACKS:
                candle_fallbacks += 1
                try:
                    data = self._market_data.read(record.secid, TimeFrame.ONE_DAY, force_refresh=False)
                    if data.candles:
                        if not sparkline:
                            sparkline = tuple(round(c.close, 4) for c in data.candles[-20:])
                        if latest_price <= 0:
                            latest_price = data.candles[-1].close
                        if prev_close is None and len(data.candles) >= 2:
                            prev_close = data.candles[-2].close
                except ChanlunError:
                    pass

            if latest_price <= 0:
                latest_price = record.start_price

            summaries.append(
                build_tracked_asset_summary(
                    record,
                    latest_price=latest_price,
                    previous_close=prev_close,
                    day_return_pct=day_pct,
                    sparkline=sparkline,
                )
            )
        return TrackingOverview(as_of_date=as_of_date, items=tuple(summaries))
