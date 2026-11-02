from ..application.commands.record_quote_sample import RecordQuoteSampleCommand
from ..application.commands.services import CommandServices
from ..application.queries.get_market_snapshot import GetMarketSnapshotQuery
from ..application.queries.get_quote import GetQuoteQuery
from ..application.queries.get_quote_series import GetQuoteSeriesQuery
from ..application.queries.get_rankings import GetRankingsQuery
from ..application.queries.get_signal_confluence import GetSignalConfluenceQuery
from ..application.queries.search_stocks import SearchStocksQuery
from ..application.queries.services import QueryServices
from ..domain.services.chan_analyzer import ChanAnalyzer
from ..domain.services.chan_signals import ChanSignalAnalyzer
from ..domain.services.structure_guidance import StructureGuidanceEvaluator
from ..infrastructure.cache.market_data_cache import CachedMarketDataReader
from ..infrastructure.http.json_client import JsonHttpClient
from ..infrastructure.market.eastmoney_intraday import EastmoneyIntradayReader
from ..infrastructure.market.eastmoney_market_data import EastmoneyMarketDataReader
from ..infrastructure.market.eastmoney_quote import EastmoneyQuoteReader
from ..infrastructure.market.eastmoney_stock_pool import EastmoneyStockPoolReader
from ..infrastructure.market.eastmoney_stock_search import EastmoneyStockSearchReader
from ..infrastructure.market.fallback_market_data import FallbackMarketDataReader
from ..infrastructure.market.sina_market_data import SinaMarketDataReader
from ..infrastructure.persistence.json_quote_store import JsonQuoteStore


def build_services() -> tuple[QueryServices, CommandServices]:
    http_client = JsonHttpClient()
    stock_search = EastmoneyStockSearchReader(http_client)
    market_data = CachedMarketDataReader(
        FallbackMarketDataReader(
            EastmoneyMarketDataReader(http_client),
            SinaMarketDataReader(http_client, stock_search),
        )
    )
    quote_store = JsonQuoteStore("var/quotes")
    snapshot_query = GetMarketSnapshotQuery(
        market_data,
        ChanAnalyzer(),
        StructureGuidanceEvaluator(),
        ChanSignalAnalyzer(),
    )
    queries = QueryServices(
        search_stocks=SearchStocksQuery(stock_search),
        get_market_snapshot=snapshot_query,
        get_quote=GetQuoteQuery(EastmoneyQuoteReader(http_client)),
        get_quote_series=GetQuoteSeriesQuery(quote_store, EastmoneyIntradayReader(http_client)),
        get_rankings=GetRankingsQuery(EastmoneyStockPoolReader(http_client), snapshot_query),
        get_signal_confluence=GetSignalConfluenceQuery(snapshot_query),
    )
    commands = CommandServices(record_quote_sample=RecordQuoteSampleCommand(quote_store))
    return queries, commands
