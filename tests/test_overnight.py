from dataclasses import replace
from datetime import date, datetime, timedelta
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
from zoneinfo import ZoneInfo

from backend.application.commands.overnight import ScanOvernightCommand, SyncOvernightHistoryCommand
from backend.application.overnight import ExitObservation
from backend.application.queries.get_overnight import GetOvernightQuery
from backend.domain.overnight import (
    BoardQuote, DailySeal, HistoricalSeals, MinuteBar, MinuteSeries, ScreeningQuote,
    evaluate_minutes, exit_exception, screen_quotes,
)
from backend.infrastructure.persistence.sqlite_overnight import SqliteOvernightStore


TZ = ZoneInfo('Asia/Shanghai')
DAY = date(2026, 11, 19)
NEXT = date(2026, 11, 20)
NOW = datetime(2026, 11, 19, 14, 32, 10, tzinfo=TZ)
CODE = '600000.SH'
INDEX = '000300.SH'
QUOTE = ScreeningQuote(CODE, '600000', '浦发银行', 10.40, 10, 4, 1, 5, 50e8, 11, NOW)


def series(secid=CODE, day=DAY, end=NOW, *, price=10.40, average=10.38, low=10.39, volume=200):
    start = datetime.combine(day, datetime.min.time().replace(hour=9, minute=30), TZ)
    bars = []
    for offset in range(int((end - start).total_seconds() // 60) + 1):
        at = start + timedelta(minutes=offset)
        if (at.hour, at.minute) > (11, 30) and (at.hour, at.minute) < (13, 1):
            continue
        bars.append(MinuteBar(at, price, price, average, volume))
    bars[-1] = replace(bars[-1], low=low)
    return MinuteSeries(secid, 10, tuple(bars))


class OvernightRuleTests(unittest.TestCase):
    def test_batch_thresholds_use_total_cap_and_reject_missing_data(self):
        self.assertTrue(screen_quotes(QUOTE))
        for invalid in (
            replace(QUOTE, price=10.51), replace(QUOTE, volume_ratio=None),
            replace(QUOTE, turnover_rate=10.01), replace(QUOTE, total_market_cap=200e8 + 1),
        ):
            self.assertFalse(screen_quotes(invalid))

    def test_pullback_uses_latest_closed_minute_low_and_same_minute_index(self):
        stock = series(low=10.38)
        index = series(INDEX, price=10.20, average=10.19, low=10.19)
        self.assertEqual(evaluate_minutes(stock, index, NOW).status, 'pullback')
        pierced = replace(stock, bars=(*stock.bars[:-1], replace(stock.bars[-1], low=10.37)))
        self.assertEqual(evaluate_minutes(pierced, index, NOW).status, 'watching')
        self.assertEqual(evaluate_minutes(stock, replace(index, bars=index.bars[:-1]), NOW).status, 'unavailable')
        weak = series(INDEX, price=10.40, average=10.39, low=10.39)
        self.assertEqual(evaluate_minutes(stock, weak, NOW).status, 'filtered')

    def test_pullback_at_a_new_gain_outside_the_batch_band_is_not_a_signal(self):
        old = series(low=10.38)
        stock = replace(old, bars=(*old.bars[:-1], replace(old.bars[-1], close=10.80)))
        index = series(INDEX, price=10.20, average=10.19, low=10.19)
        detail = evaluate_minutes(stock, index, NOW)
        self.assertEqual(detail.status, 'filtered')
        self.assertAlmostEqual(detail.minute_change_percent, 8)

    def test_missing_morning_minute_or_an_earlier_drop_cannot_pass(self):
        stock = series(low=10.38)
        index = series(INDEX, price=10.2, average=10.2, low=10.2)
        self.assertEqual(evaluate_minutes(replace(stock, bars=stock.bars[1:]), index, NOW).status, 'unavailable')
        dropped = replace(stock.bars[8], close=10.37)
        stock = replace(stock, bars=(*stock.bars[:8], dropped, *stock.bars[9:]))
        self.assertEqual(evaluate_minutes(stock, index, NOW).status, 'filtered')

    def test_exit_exception_requires_one_price_limit_and_matching_smaller_volume(self):
        at = datetime(2026, 11, 20, 9, 35, 10, tzinfo=TZ)
        previous = series(end=NOW)
        today = series(day=NEXT, end=at, price=11, low=11, average=11, volume=100)
        self.assertTrue(exit_exception(today, previous, 11, at, session_low=11))
        self.assertFalse(exit_exception(today, previous, 11, at, session_low=10.99))
        self.assertFalse(exit_exception(today, series(volume=100), 11, at, session_low=11))
        self.assertIsNone(exit_exception(today, None, 11, at, session_low=11))
        self.assertIsNone(exit_exception(today, previous, 11, at, session_low=None))


class OvernightWorkflowTests(unittest.TestCase):
    def test_history_sync_reads_once_and_resumed_signal_reminds_only_next_session(self):
        with TemporaryDirectory() as directory:
            path = Path(directory) / 'strategy.sqlite3'
            store = SqliteOvernightStore(path)
            days = tuple(DAY - timedelta(days=n) for n in range(23, -1, -1))
            reads = []

            class Source:
                def read_seals(self, day):
                    reads.append(day)
                    return HistoricalSeals(day, (DailySeal(CODE, 11, 11),) if day == days[-2] else (), True)

                def read_universe(self):
                    return (BoardQuote(CODE, '600000', '浦发银行', 10.4, 10, 4, NOW),)

                def read_quotes(self, codes):
                    return {CODE: QUOTE} if CODE in codes else {}

                def read_minutes(self, code):
                    return series(low=10.38) if code == CODE else series(INDEX, price=10.2, low=10.2, average=10.2)

            source = Source()
            store.save_sessions((*days, NEXT))
            sync = SyncOvernightHistoryCommand(source, store, store)
            sync.execute(days[-21:-1])
            sync.execute(days[-21:-1])
            self.assertEqual(reads, list(days[-21:-1]))
            command = ScanOvernightCommand(source, source, source, store, store)
            command.execute(NOW)
            command.execute(NOW + timedelta(seconds=15))

            class Clock:
                now = lambda self: datetime(2026, 11, 20, 10, 0, tzinfo=TZ)

            class Afternoon:
                now = lambda self: datetime(2026, 11, 20, 14, 0, tzinfo=TZ)

            reopened = SqliteOvernightStore(path)
            board = GetOvernightQuery(reopened, reopened, Clock()).execute()
            self.assertEqual(board.phase, 'exit')
            self.assertEqual(board.reminders_until, datetime(2026, 11, 20, 11, 30, tzinfo=TZ))
            self.assertEqual([item.secid for item in board.reminders], [CODE])
            self.assertEqual(board.reminders[0].signal_at, NOW.replace(second=0))
            reopened.save_exit_observations(NEXT, (ExitObservation(CODE, Clock().now() - timedelta(minutes=3), True),), ())
            self.assertIsNone(GetOvernightQuery(reopened, reopened, Clock()).execute().reminders[0].exception_observed)
            reopened.save_exit_observations(NEXT, (ExitObservation(CODE, Clock().now() - timedelta(minutes=1), True),), ())
            latest = GetOvernightQuery(reopened, reopened, Clock()).execute().reminders[0]
            self.assertTrue(latest.exception_observed)
            self.assertEqual(latest.exception_valid_until, Clock().now() + timedelta(minutes=1))
            self.assertEqual(GetOvernightQuery(reopened, reopened, Afternoon()).execute().reminders, ())
            self.assertEqual(len(reopened.read_minutes(DAY, CODE).bars), 213)

    def test_incomplete_history_retry_does_not_claim_coverage(self):
        with TemporaryDirectory() as directory:
            store = SqliteOvernightStore(Path(directory) / 'strategy.sqlite3')

            class Source:
                complete = False

                def read_seals(self, day):
                    return HistoricalSeals(day, (DailySeal(CODE, 11, 11),), self.complete)

            source = Source()
            command = SyncOvernightHistoryCommand(source, store, store)
            command.execute((DAY,))
            self.assertEqual(store.read_seal_coverage((DAY,)), ())
            source.complete = True
            command.execute((DAY,))
            self.assertEqual(store.read_seal_coverage((DAY,)), (DAY,))
