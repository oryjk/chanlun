from ..application.commands.overnight import (
    ArchiveOvernightMinutesCommand, ObserveOvernightExitCommand, ScanOvernightCommand, SyncOvernightHistoryCommand,
)
from ..application.ports.clock import Clock
from ..application.queries.get_overnight import GetOvernightQuery
from ..infrastructure.http.json_client import JsonHttpClient
from ..infrastructure.market.overnight import (
    EastmoneyOvernightMinuteReader, OvernightSessionReader, SinaOvernightUniverseReader,
    TencentOvernightQuoteReader, TonghuashunOvernightSealReader,
)
from ..infrastructure.market.sina_market_data import SinaMarketDataReader
from ..infrastructure.persistence.sqlite_overnight import SqliteOvernightStore
from .overnight_worker import OvernightWorker
from .settings import Settings


def build_overnight(settings: Settings, http: JsonHttpClient,
                    clock: Clock) -> tuple[GetOvernightQuery, OvernightWorker | None]:
    store = SqliteOvernightStore(settings.overnight_store_path)
    query = GetOvernightQuery(store, store, clock)
    if not settings.overnight_enabled:
        return query, None
    quotes = TencentOvernightQuoteReader(http)
    minutes = EastmoneyOvernightMinuteReader(http)
    calendar = OvernightSessionReader(SinaMarketDataReader(http))
    worker = OvernightWorker(
        clock, calendar, store, store, store.worker_lock,
        SyncOvernightHistoryCommand(TonghuashunOvernightSealReader(http), store, store),
        ScanOvernightCommand(SinaOvernightUniverseReader(http), quotes, minutes, store, store),
        ObserveOvernightExitCommand(quotes, minutes, store, store, store),
        ArchiveOvernightMinutesCommand(minutes, store, store),
    )
    return query, worker
