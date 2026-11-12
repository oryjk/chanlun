import tempfile
import unittest
from backend.domain.analysis_version import ANALYSIS_VERSION
from datetime import date, datetime, timedelta, timezone
from pathlib import Path
from types import SimpleNamespace

from backend.application.commands.build_daily_rankings import BuildDailyRankingsCommand
from backend.application.queries.get_rankings import GetRankingsQuery
from backend.domain.errors import MarketDataUnavailable
from backend.domain.models import Candle, Instrument, MarketData, MarketGuidance, Stock
from backend.application.queries.get_market_snapshot import GetMarketSnapshotQuery
from backend.infrastructure.cache.market_data_cache import CachedMarketDataReader
from backend.domain.services.chan_analyzer import ChanAnalyzer
from backend.domain.services.chan_signals import ChanSignalAnalyzer
from backend.domain.services.market_analysis import MarketAnalysisPipeline
from backend.domain.services.signal_quality import SignalQualityEvaluator
from backend.domain.services.structure_guidance import StructureGuidanceEvaluator
from backend.infrastructure.persistence.sqlite_ranking_store import SqliteRankingStore


NOW = datetime(2026, 11, 3, 8, tzinfo=timezone.utc)
DAY = date(2026, 11, 3)
BULLISH_PRICES = (105, 100, 105, 110, 115, 120, 116, 112, 108, 105, 111, 117, 123, 130, 128, 126, 124, 122, 126)


class FixedClock:
    def __init__(self, value):
        self.value = value

    def now(self):
        return self.value


def bullish_candles():
    end = datetime(DAY.year, DAY.month, DAY.day, tzinfo=timezone.utc)
    start = end - timedelta(days=len(BULLISH_PRICES) - 1)
    candles = [
        Candle(start + timedelta(days=i), price, price, price + 1, price - 1, 100)
        for i, price in enumerate(BULLISH_PRICES)
    ]
    candles.append(Candle(end + timedelta(days=1), 999, 999, 1000, 998, 100))
    return tuple(candles)


class MarketDataStub:
    def __init__(self, fail=False):
        self.calls = []
        self.fail = fail

    def read(self, secid, timeframe, force_refresh=False):
        self.calls.append((secid, timeframe, force_refresh))
        if self.fail:
            raise MarketDataUnavailable("行情服务暂时不可用")
        return MarketData(Instrument(secid, secid.split(".")[-1], "股票", 2, "test"), bullish_candles())


class DailyCommandTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.store = SqliteRankingStore(Path(self.temp.name) / "rankings.db")
        self.stocks = tuple(Stock(f"000{i:03d}.SZ", f"000{i:03d}", f"股票{i}", "深A") for i in range(1, 42))
        self.pool = SimpleNamespace(list_all=lambda **kwargs: self.stocks)

    def tearDown(self):
        self.temp.cleanup()

    def command(self, market_data):
        pipeline = MarketAnalysisPipeline(
            ChanAnalyzer(),
            StructureGuidanceEvaluator(),
            ChanSignalAnalyzer(),
            SignalQualityEvaluator(),
        )
        return BuildDailyRankingsCommand(
            self.pool,
            market_data,
            pipeline,
            self.store,
            self.store,
            self.store,
            self.store,
            FixedClock(NOW),
        )

    def test_full_pool_is_processed_once_per_close_day_and_get_is_read_only(self):
        market_data = MarketDataStub()
        self.assertTrue(self.command(market_data).execute(DAY))
        self.assertEqual(len(market_data.calls), 41)
        self.assertFalse(self.command(market_data).execute(DAY))
        self.assertEqual(len(market_data.calls), 41)
        query = GetRankingsQuery(self.store)
        board = query.execute(6)
        self.assertEqual(len(board.bullish), 6)
        self.assertTrue(all(entry.latest == BULLISH_PRICES[-1] for entry in board.bullish))
        self.assertEqual(board.total_count, 41)
        self.assertEqual(board.analyzed_count, 41)
        self.assertEqual(board.trade_date, DAY)
        query.execute(10)
        self.assertEqual(len(market_data.calls), 41)

    def test_all_failures_keep_previous_board_and_record_error(self):
        self.command(MarketDataStub()).execute(DAY)
        previous = self.store.read().board
        with self.assertLogs("backend.application.commands.build_daily_rankings", level="ERROR"):
            self.command(MarketDataStub(True)).execute(date(2026, 11, 4))
        self.assertEqual(self.store.read().board, previous)
        self.assertEqual(self.store.read().progress.state, "error")

    def test_close_scan_bypasses_a_warm_intraday_price_cache(self):
        class Quotes:
            close = 10
            def read(inner, *args, **kwargs):
                candles = (Candle(datetime(2026, 11, 2, tzinfo=timezone.utc), 10, 10, 12, 9, 100),
                           Candle(datetime(2026, 11, 3, tzinfo=timezone.utc), 10, inner.close, 12, 9, 100))
                return MarketData(Instrument("0.000001", "000001", "平安银行", 2, "test"), candles)
        quotes = Quotes()
        reader = CachedMarketDataReader(quotes)
        pipeline = MarketAnalysisPipeline(
            ChanAnalyzer(),
            SimpleNamespace(evaluate=lambda *args: MarketGuidance("bullish", (), 9, 3)),
            ChanSignalAnalyzer(),
            SignalQualityEvaluator(),
        )
        query = GetMarketSnapshotQuery(reader, pipeline, FixedClock(NOW))
        self.assertEqual(query.execute("0.000001", "1d").summary.latest, 10)
        quotes.close = 11
        pool = SimpleNamespace(list_all=lambda **kwargs: (self.stocks[0],))
        command = BuildDailyRankingsCommand(
            pool,
            reader,
            pipeline,
            self.store,
            self.store,
            self.store,
            self.store,
            FixedClock(NOW),
        )
        self.assertTrue(command.execute(DAY))
        self.assertEqual(self.store.read().board.bullish[0].latest, 11)

    def test_cancellation_retains_checkpoint_without_publishing_empty_board(self):
        market_data = MarketDataStub()
        self.assertFalse(self.command(market_data).execute(DAY, cancelled=lambda: True))
        self.assertIsNone(self.store.read().board)
        self.assertEqual(market_data.calls, [])

    def test_partial_failure_is_counted_without_reusing_previous_prices(self):
        market_data = MarketDataStub()
        original = market_data.read
        failed_calls = []
        def read(secid, timeframe, force_refresh=False):
            if secid == self.stocks[0].secid:
                failed_calls.append(secid)
                raise MarketDataUnavailable("temporary")
            return original(secid, timeframe, force_refresh=force_refresh)
        market_data.read = read
        self.assertTrue(self.command(market_data).execute(DAY))
        board = self.store.read().board
        self.assertEqual((board.total_count, board.analyzed_count, board.failed_count), (41, 40, 1))
        self.assertEqual(board.state, "degraded")
        self.assertEqual(len(failed_calls), 3)
        self.assertNotIn(self.stocks[0].secid, [entry.stock.secid for entry in board.bullish])

    def test_interrupted_batch_resumes_saved_successful_items(self):
        market_data = MarketDataStub()
        self.assertFalse(self.command(market_data).execute(DAY, cancelled=lambda: len(self.store.read_items(DAY, analysis_version=ANALYSIS_VERSION)) >= 2))
        saved = {item.secid for item in self.store.read_items(DAY, analysis_version=ANALYSIS_VERSION) if item.error is None}
        self.assertGreaterEqual(len(saved), 2)
        self.assertEqual(self.store.read().progress.state, "preparing")
        self.assertIsNone(self.store.read().progress.error)
        resumed = MarketDataStub()
        self.assertTrue(self.command(resumed).execute(DAY))
        self.assertFalse(saved.intersection(call[0] for call in resumed.calls))
        self.assertEqual(self.store.read().board.analyzed_count, 41)


if __name__ == "__main__":
    unittest.main()
