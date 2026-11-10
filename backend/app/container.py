from ..application.commands.login_with_webview_code import LoginWithWebviewCodeCommand
from ..application.commands.record_quote_sample import RecordQuoteSampleCommand
from ..application.commands.services import CommandServices
from ..application.commands.build_daily_rankings import BuildDailyRankingsCommand
from ..application.queries.get_current_user import GetCurrentUserQuery
from ..application.queries.get_market_snapshot import GetMarketSnapshotQuery
from ..application.queries.get_mini_program_link import GetMiniProgramLinkQuery
from ..application.queries.get_quote import GetQuoteQuery
from ..application.queries.get_quote_series import GetQuoteSeriesQuery
from ..application.queries.get_rankings import GetRankingsQuery
from ..application.queries.get_signal_confluence import GetSignalConfluenceQuery
from ..application.queries.search_stocks import SearchStocksQuery
from ..application.queries.services import QueryServices
from ..domain.services.chan_analyzer import ChanAnalyzer
from ..domain.services.chan_signals import ChanSignalAnalyzer
from ..domain.services.structure_guidance import StructureGuidanceEvaluator
from ..domain.services.signal_quality import SignalQualityEvaluator
from ..infrastructure.auth.registration_gateway import RegistrationSystemGateway
from ..infrastructure.auth.signed_session_codec import SignedSessionCodec
from ..infrastructure.cache.market_data_cache import CachedMarketDataReader
from ..infrastructure.http.json_client import JsonHttpClient
from ..infrastructure.market.eastmoney_intraday import EastmoneyIntradayReader
from ..infrastructure.market.eastmoney_market_data import EastmoneyMarketDataReader
from ..infrastructure.market.eastmoney_quote import EastmoneyQuoteReader
from ..infrastructure.market.eastmoney_stock_pool import EastmoneyStockPoolReader
from ..infrastructure.market.eastmoney_stock_search import EastmoneyStockSearchReader
from ..infrastructure.market.fallback_market_data import FallbackMarketDataReader, FallbackStockUniverseReader
from ..infrastructure.market.sina_adjustment import SinaForwardAdjustment
from ..infrastructure.market.sina_market_data import SinaMarketDataReader
from ..infrastructure.market.sina_stock_pool import SinaStockPoolReader
from ..infrastructure.persistence.json_quote_store import JsonQuoteStore
from ..infrastructure.persistence.sqlite_ranking_store import SqliteRankingStore
from ..infrastructure.market.closed_trading_day import IndexClosedTradingDayReader
from .ranking_worker import RankingWorker
from .settings import Settings


def build_services(settings: Settings) -> tuple[QueryServices, CommandServices]:
    http_client = JsonHttpClient()
    stock_search = EastmoneyStockSearchReader(http_client)
    market_data = CachedMarketDataReader(
        FallbackMarketDataReader(
            EastmoneyMarketDataReader(http_client),
            SinaMarketDataReader(http_client, stock_search, SinaForwardAdjustment(http_client)),
        )
    )
    quote_store = JsonQuoteStore("var/quotes")
    registration = RegistrationSystemGateway(settings.registration_api_base)
    session_codec = SignedSessionCodec(settings.session_secret, settings.session_max_age)
    snapshot_query = GetMarketSnapshotQuery(
        market_data,
        ChanAnalyzer(),
        StructureGuidanceEvaluator(),
        ChanSignalAnalyzer(),
        SignalQualityEvaluator(),
    )
    ranking_store = SqliteRankingStore(settings.ranking_store_path)
    stock_universe = FallbackStockUniverseReader(EastmoneyStockPoolReader(http_client), SinaStockPoolReader(http_client))
    queries = QueryServices(
        search_stocks=SearchStocksQuery(stock_search),
        get_market_snapshot=snapshot_query,
        get_quote=GetQuoteQuery(EastmoneyQuoteReader(http_client)),
        get_quote_series=GetQuoteSeriesQuery(quote_store, EastmoneyIntradayReader(http_client)),
        get_rankings=GetRankingsQuery(ranking_store),
        get_signal_confluence=GetSignalConfluenceQuery(snapshot_query),
        get_current_user=GetCurrentUserQuery(session_codec),
        get_mini_program_link=GetMiniProgramLinkQuery(registration),
    )
    commands = CommandServices(
        record_quote_sample=RecordQuoteSampleCommand(quote_store),
        login_with_webview_code=LoginWithWebviewCodeCommand(registration, session_codec),
        build_daily_rankings=BuildDailyRankingsCommand(stock_universe, snapshot_query,
                                                      ranking_store, ranking_store, workers=settings.ranking_workers),
    )
    return queries, commands


def build_ranking_worker(settings, commands):
    store = SqliteRankingStore(settings.ranking_store_path)
    data = CachedMarketDataReader(FallbackMarketDataReader(EastmoneyMarketDataReader(JsonHttpClient()),
                                                         SinaMarketDataReader(JsonHttpClient())))
    return RankingWorker(IndexClosedTradingDayReader(data), commands.build_daily_rankings, store, settings.ranking_run_at)
