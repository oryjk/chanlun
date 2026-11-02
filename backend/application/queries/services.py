from dataclasses import dataclass

from .get_market_snapshot import GetMarketSnapshotQuery
from .get_quote import GetQuoteQuery
from .get_quote_series import GetQuoteSeriesQuery
from .search_stocks import SearchStocksQuery


@dataclass(frozen=True)
class QueryServices:
    search_stocks: SearchStocksQuery
    get_market_snapshot: GetMarketSnapshotQuery
    get_quote: GetQuoteQuery
    get_quote_series: GetQuoteSeriesQuery
