import unittest
from datetime import date, datetime, timezone
from types import SimpleNamespace

from backend.app.ranking_worker import RankingWorker
from backend.application.ranking_jobs import RankingProgress, RankingView
from backend.domain.analysis_version import ANALYSIS_VERSION


class WorkerTests(unittest.TestCase):
    def test_current_day_old_version_is_rebuilt_but_current_version_is_not(self):
        from backend.domain.models import RankingBoard, TimeFrame
        runs = []
        day = date(2026, 11, 9)
        board = RankingBoard(datetime(2026, 11, 9, tzinfo=timezone.utc), TimeFrame.ONE_DAY,
                             (), (), trade_date=day, total_count=1, analyzed_count=1)
        for version in (1, 2, 3, ANALYSIS_VERSION):
            from dataclasses import replace
            worker = RankingWorker(SimpleNamespace(latest_on_or_before=lambda cutoff: day),
                SimpleNamespace(analysis_version=ANALYSIS_VERSION, execute=lambda target, **kw: runs.append(target)),
                SimpleNamespace(read=lambda: RankingView(replace(board, analysis_version=version), RankingProgress())))
            worker.tick(datetime(2026, 11, 10, 2, tzinfo=timezone.utc))
        self.assertEqual(runs, [day, day, day])

    def test_before_and_after_cutoff_use_only_closed_trading_dates(self):
        cutoffs, runs = [], []
        def day(cutoff):
            cutoffs.append(cutoff)
            return cutoff
        worker = RankingWorker(SimpleNamespace(latest_on_or_before=day),
                               SimpleNamespace(execute=lambda target, **kwargs: runs.append(target)),
                               SimpleNamespace(read=lambda: RankingView(None, RankingProgress())), "15:30")
        worker.tick(datetime(2026, 11, 3, 7, 29, tzinfo=timezone.utc))
        worker.tick(datetime(2026, 11, 3, 7, 30, tzinfo=timezone.utc))
        self.assertEqual(cutoffs, [date(2026, 11, 2), date(2026, 11, 3)])
        self.assertEqual(runs, cutoffs)

    def test_holiday_uses_last_actual_session_instead_of_weekday_guess(self):
        runs = []
        worker = RankingWorker(SimpleNamespace(latest_on_or_before=lambda cutoff: date(2026, 9, 30)),
                               SimpleNamespace(execute=lambda target, **kwargs: runs.append(target)),
                               SimpleNamespace(read=lambda: RankingView(None, RankingProgress())), "15:30")
        worker.tick(datetime(2026, 10, 1, 8, tzinfo=timezone.utc))
        self.assertEqual(runs, [date(2026, 9, 30)])


if __name__ == "__main__":
    unittest.main()
