from dataclasses import dataclass

from .get_current_user import GetCurrentUserQuery
from .get_market_snapshot import GetMarketSnapshotQuery
from .get_mini_program_link import GetMiniProgramLinkQuery
from .get_quote import GetQuoteQuery
from .get_quote_series import GetQuoteSeriesQuery
from .get_rankings import GetRankingsQuery
from .get_signal_confluence import GetSignalConfluenceQuery
from .search_stocks import SearchStocksQuery


@dataclass(frozen=True)
class QueryServices:
    search_stocks: SearchStocksQuery
    get_market_snapshot: GetMarketSnapshotQuery
    get_quote: GetQuoteQuery
    get_quote_series: GetQuoteSeriesQuery
    get_rankings: GetRankingsQuery
    get_signal_confluence: GetSignalConfluenceQuery
    get_current_user: GetCurrentUserQuery
    get_mini_program_link: GetMiniProgramLinkQuery
