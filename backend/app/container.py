from ..application.queries.get_market_snapshot import GetMarketSnapshotQuery
from ..application.queries.search_stocks import SearchStocksQuery
from ..application.queries.services import QueryServices
from ..domain.services.chan_analyzer import ChanAnalyzer
from ..infrastructure.cache.market_data_cache import CachedMarketDataReader
from ..infrastructure.http.json_client import JsonHttpClient
from ..infrastructure.market.eastmoney_stock_search import EastmoneyStockSearchReader
from ..infrastructure.market.yahoo_market_data import YahooMarketDataReader


def build_queries() -> QueryServices:
    http_client = JsonHttpClient()
    stock_search = EastmoneyStockSearchReader(http_client)
    market_data = CachedMarketDataReader(YahooMarketDataReader(http_client))
    return QueryServices(
        search_stocks=SearchStocksQuery(stock_search),
        get_market_snapshot=GetMarketSnapshotQuery(market_data, ChanAnalyzer()),
    )
