from typing import Protocol

from ...domain.models import MarketData, QuoteSeries, RealtimeQuote, Stock, TimeFrame


class StockSearchReader(Protocol):
    def search(self, query: str) -> list[Stock]: ...


class StockPoolReader(Protocol):
    def list_liquid(self, limit: int) -> list[Stock]: ...


class MarketDataReader(Protocol):
    def read(self, secid: str, timeframe: TimeFrame, force_refresh: bool = False) -> MarketData: ...


class QuoteReader(Protocol):
    def read_quote(self, secid: str) -> RealtimeQuote: ...


class QuoteSeriesReader(Protocol):
    def read_series(self, secid: str) -> QuoteSeries: ...


class IntradayQuoteReader(Protocol):
    def read_intraday(self, secid: str) -> QuoteSeries: ...
