from datetime import date
from typing import Callable, Protocol

from ...domain.models import MarketData, QuoteSeries, RealtimeQuote, Stock, TimeFrame
from ...domain.tracking import AssetCategory, AssetSortField, MarketAssetList
from ..ranking_jobs import RankingItem, RankingView


class StockSearchReader(Protocol):
    def search(self, query: str) -> list[Stock]: ...


class StockUniverseReader(Protocol):
    def list_all(self, *, cancelled: Callable[[], bool] = lambda: False) -> tuple[Stock, ...]: ...


class RankingReader(Protocol):
    def read(self) -> RankingView: ...
    def read_items(self, trade_date: date, *, analysis_version: int) -> tuple[RankingItem, ...]: ...


class ClosedTradingDayReader(Protocol):
    def latest_on_or_before(self, cutoff: date) -> date: ...


class MarketDataReader(Protocol):
    def read(self, secid: str, timeframe: TimeFrame, force_refresh: bool = False) -> MarketData: ...


class QuoteReader(Protocol):
    def read_quote(self, secid: str) -> RealtimeQuote: ...


class BatchQuoteReader(Protocol):
    def read_quotes(self, secids: tuple[str, ...]) -> dict[str, RealtimeQuote]: ...


class MarketAssetCatalogReader(Protocol):
    def list_assets(
        self,
        category: AssetCategory,
        *,
        sort_field: AssetSortField,
        descending: bool,
        limit: int,
    ) -> MarketAssetList: ...


class QuoteSeriesReader(Protocol):
    def read_series(self, secid: str) -> QuoteSeries: ...


class IntradayQuoteReader(Protocol):
    def read_intraday(self, secid: str) -> QuoteSeries: ...


class MiniProgramLinkReader(Protocol):
    def chanlun_link(self, return_query: str) -> str: ...
