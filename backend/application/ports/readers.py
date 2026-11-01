from typing import Protocol

from ...domain.models import MarketData, Stock, TimeFrame


class StockSearchReader(Protocol):
    def search(self, query: str) -> list[Stock]: ...


class MarketDataReader(Protocol):
    def read(self, secid: str, timeframe: TimeFrame, force_refresh: bool = False) -> MarketData: ...
