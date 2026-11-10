import json
import tempfile
import unittest
from datetime import date, datetime, timezone
from pathlib import Path

from backend.domain.models import RankingBoard, TimeFrame
from backend.domain.rankings import ANALYSIS_VERSION, RankingProgress
from backend.infrastructure.persistence.sqlite_ranking_store import SqliteRankingStore, _json


DAY = date(2026, 11, 9)
NOW = datetime(2026, 11, 10, tzinfo=timezone.utc)


class AnalysisVersionTests(unittest.TestCase):
    def test_omitting_runtime_version_is_rejected_for_both_read_and_claim(self):
        with tempfile.TemporaryDirectory() as directory:
            store = SqliteRankingStore(Path(directory) / 'rankings.db')
            with self.assertRaises(TypeError):
                store.claim(DAY, 'owner', NOW, 60)
            with self.assertRaises(TypeError):
                store.read_items(DAY)

    def test_legacy_missing_versions_remain_rebuildable_and_never_inherit_current_default(self):
        with tempfile.TemporaryDirectory() as directory:
            store = SqliteRankingStore(Path(directory) / 'rankings.db')
            board = RankingBoard(NOW, TimeFrame.ONE_DAY, (), (), trade_date=DAY,
                                 total_count=1, analyzed_count=1)
            payload = json.loads(_json(board))
            payload.pop('analysis_version')
            progress = json.loads(_json(RankingProgress('ready', DAY, 1, 1, 1, 0, NOW)))
            progress.pop('analysis_version')
            with store._connection() as connection:
                connection.execute('INSERT INTO ranking_snapshot VALUES(1,?)', (json.dumps(payload),))
                connection.execute('UPDATE ranking_state SET payload=? WHERE id=1', (json.dumps(progress),))
            view = store.read()
            self.assertEqual(view.board.analysis_version, 1)
            self.assertEqual(view.progress.analysis_version, 1)
            self.assertTrue(store.claim(DAY, 'upgrade', NOW, 60, analysis_version=ANALYSIS_VERSION))
            self.assertEqual(store.read().board.analysis_version, 1)

    def test_new_default_progress_and_board_publish_under_the_current_runtime_version(self):
        with tempfile.TemporaryDirectory() as directory:
            store = SqliteRankingStore(Path(directory) / 'rankings.db')
            self.assertEqual(store.read().progress.analysis_version, ANALYSIS_VERSION)
            self.assertTrue(store.claim(DAY, 'current', NOW, 60, analysis_version=ANALYSIS_VERSION))
            board = RankingBoard(NOW, TimeFrame.ONE_DAY, (), (), trade_date=DAY,
                                 total_count=1, analyzed_count=1)
            store.publish(board, 'current', NOW)
            self.assertFalse(store.claim(DAY, 'again', NOW, 60, analysis_version=ANALYSIS_VERSION))

    def test_version_two_quality_without_age_remains_unknown_and_can_upgrade(self):
        from backend.domain.models import RankingEntry, SignalQuality, Stock
        with tempfile.TemporaryDirectory() as directory:
            store = SqliteRankingStore(Path(directory) / 'rankings.db')
            entry = RankingEntry(1, Stock('1.600001', '600001', 'test', '沪A'),
                                 'bullish', 3, 10, 1, 9,
                                 signal_quality=SignalQuality('confirmed', 9, bars_since_pivot=7))
            board = RankingBoard(NOW, TimeFrame.ONE_DAY, (entry,), (), trade_date=DAY,
                                 total_count=1, analyzed_count=1, analysis_version=2)
            payload = json.loads(_json(board))
            payload['bullish'][0]['signal_quality'].pop('bars_since_pivot')
            with store._connection() as connection:
                connection.execute('INSERT INTO ranking_snapshot VALUES(1,?)', (json.dumps(payload),))
            recovered = store.read().board
            self.assertEqual(recovered.analysis_version, 2)
            self.assertIsNone(recovered.bullish[0].signal_quality.bars_since_pivot)
            self.assertTrue(store.claim(DAY, 'upgrade', NOW, 60, analysis_version=ANALYSIS_VERSION))


if __name__ == '__main__':
    unittest.main()
