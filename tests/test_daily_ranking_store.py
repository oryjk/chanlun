import tempfile
import unittest
from datetime import date, datetime, timedelta, timezone
from pathlib import Path

from backend.domain.models import RankingBoard, TimeFrame
from backend.domain.rankings import RankingProgress
from backend.infrastructure.persistence.sqlite_ranking_store import SqliteRankingStore
from backend.domain.analysis_version import ANALYSIS_VERSION, LEGACY_ANALYSIS_VERSION


NOW = datetime(2026, 11, 3, 8, tzinfo=timezone.utc)
DAY = date(2026, 11, 3)


class RankingStoreTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.path = Path(self.directory.name) / "rankings.sqlite3"
        self.store = SqliteRankingStore(self.path)

    def tearDown(self):
        self.directory.cleanup()

    def test_empty_reads_do_not_write_or_initialize_results(self):
        before = self.path.stat().st_mtime_ns
        view = self.store.read()
        self.assertIsNone(view.board)
        self.assertEqual(view.progress.state, "pending")
        self.assertEqual(self.path.stat().st_mtime_ns, before)

    def test_lease_excludes_other_owner_and_can_be_taken_over_after_expiry(self):
        self.assertTrue(self.store.claim(DAY, "one", NOW, 60, analysis_version=ANALYSIS_VERSION))
        self.assertFalse(self.store.claim(DAY, "two", NOW, 60, analysis_version=ANALYSIS_VERSION))
        later = NOW + timedelta(seconds=61)
        self.assertTrue(self.store.claim(DAY, "two", later, 60, analysis_version=ANALYSIS_VERSION))
        self.assertFalse(self.store.heartbeat("one", later, 60))
        self.assertTrue(self.store.heartbeat("two", later, 60))

    def test_published_results_survive_restart_and_failed_next_scan(self):
        self.assertTrue(self.store.claim(DAY, "one", NOW, 60, analysis_version=ANALYSIS_VERSION))
        board = RankingBoard(NOW, TimeFrame.ONE_DAY, (), (), trade_date=DAY, total_count=2, analyzed_count=2)
        self.store.publish(board, "one", NOW)
        recovered = SqliteRankingStore(self.path)
        self.assertEqual(recovered.read().board, board)
        self.assertFalse(recovered.claim(DAY, "again", NOW, 60, analysis_version=ANALYSIS_VERSION))
        self.assertFalse(recovered.claim(DAY - timedelta(days=1), "older", NOW, 60, analysis_version=ANALYSIS_VERSION))
        next_day = DAY + timedelta(days=1)
        self.assertTrue(recovered.claim(next_day, "next", NOW, 60, analysis_version=ANALYSIS_VERSION))
        recovered.fail("next", "暂时无法更新收盘榜单，请稍后重试", NOW)
        self.assertEqual(recovered.read().board, board)
        self.assertEqual(recovered.read().progress.state, "error")

    def test_release_frees_lease_without_entering_error_backoff(self):
        self.assertTrue(self.store.claim(DAY, "one", NOW, 60, analysis_version=ANALYSIS_VERSION))
        self.store.release("one", NOW)
        self.assertEqual(self.store.read().progress.state, "preparing")
        self.assertIsNone(self.store.read().progress.error)
        self.assertTrue(self.store.claim(DAY, "two", NOW, 60, analysis_version=ANALYSIS_VERSION))

    def test_same_day_new_analysis_version_rebuilds_once_and_preserves_old_snapshot(self):
        from dataclasses import replace
        self.assertTrue(self.store.claim(DAY, 'old', NOW, 60, analysis_version=LEGACY_ANALYSIS_VERSION))
        old = RankingBoard(NOW, TimeFrame.ONE_DAY, (), (), trade_date=DAY,
                           total_count=2, analyzed_count=2, analysis_version=LEGACY_ANALYSIS_VERSION)
        self.store.publish(old, 'old', NOW)
        self.assertTrue(self.store.claim(DAY, 'new', NOW, 60, analysis_version=ANALYSIS_VERSION))
        self.assertEqual(self.store.read().board, old)
        self.assertFalse(self.store.claim(DAY, 'competitor', NOW, 60, analysis_version=ANALYSIS_VERSION))
        self.store.publish(replace(old, analysis_version=ANALYSIS_VERSION), 'new', NOW)
        self.assertFalse(self.store.claim(DAY, 'again', NOW, 60, analysis_version=ANALYSIS_VERSION))
        self.assertFalse(self.store.claim(DAY, 'downgrade', NOW, 60, analysis_version=LEGACY_ANALYSIS_VERSION))

    def test_checkpoint_quality_roundtrip_and_version_isolation(self):
        from backend.domain.models import BuySellPoint, RankingEntry, SignalQuality, Stock
        from backend.domain.rankings import RankingItem, RankingProgress
        self.assertTrue(self.store.claim(DAY, 'new', NOW, 60, analysis_version=ANALYSIS_VERSION))
        point = BuySellPoint(3, 'buy', 2, 30, NOW, 10, True, 'test', invalidation_price=9)
        entry = RankingEntry(0, Stock('1.600001', '600001', 'test', '沪A'), 'bullish', 3,
                             10, 1, 9, latest_point=point,
                             signal_quality=SignalQuality('invalidated', 9, history_status='invalidated',
                                first_reference_crossed_at=NOW, first_reference_crossed_close=8.8,
                                history_checked_through=NOW, reasons=('test',)))
        item = RankingItem('1.600001', entry)
        progress = RankingProgress('running', DAY, 1, 1, 1, 0, NOW, analysis_version=ANALYSIS_VERSION)
        self.store.record(DAY, item, progress, 'new', NOW, 60)
        self.assertEqual(self.store.read_items(DAY, analysis_version=1), ())
        self.assertEqual(self.store.read_items(DAY, analysis_version=ANALYSIS_VERSION), (item,))


if __name__ == "__main__":
    unittest.main()
