"""Composition root helper for native app asset tracking."""

from dataclasses import dataclass
import os

from ..application.commands.manage_app_tracking import AppTrackingCommands
from ..application.ports.clock import Clock
from ..application.ports.readers import (
    BatchQuoteReader,
    MarketDataReader,
    QuoteReader,
    RankingReader,
    StockSearchReader,
)
from ..application.queries.get_app_trackings import GetAppTrackingsQuery
from ..infrastructure.persistence.sqlite_app_tracking import SqliteAppTrackingStore


@dataclass(frozen=True)
class NativeTrackingServices:
    query: GetAppTrackingsQuery | None = None
    commands: AppTrackingCommands | None = None


def build_native_tracking(
    *,
    quote_reader: QuoteReader,
    batch_quotes: BatchQuoteReader,
    ranking_reader: RankingReader,
    market_data: MarketDataReader,
    stock_search: StockSearchReader,
    clock: Clock,
    store_path: str | None = None,
) -> NativeTrackingServices:
    resolved_path = (
        store_path
        or os.getenv("CHANLUN_TRACKING_STORE_PATH", "var/trackings/trackings.sqlite3").strip()
        or "var/trackings/trackings.sqlite3"
    )
    store = SqliteAppTrackingStore(resolved_path)
    query = GetAppTrackingsQuery(
        tracking_reader=store,
        batch_quotes=batch_quotes,
        ranking_reader=ranking_reader,
        market_data=market_data,
        clock=clock,
    )
    commands = AppTrackingCommands(
        tracking_reader=store,
        tracking_writer=store,
        quote_reader=quote_reader,
        market_data=market_data,
        stock_search=stock_search,
        clock=clock,
    )
    return NativeTrackingServices(query=query, commands=commands)
