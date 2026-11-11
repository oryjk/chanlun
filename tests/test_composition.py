from pathlib import Path
import tempfile
import unittest

from backend.app.container import build_application
from backend.app.settings import Settings


def settings(tmp, enabled):
    return Settings(
        ["*"],
        "http://127.0.0.1:9",
        "x" * 32,
        60,
        False,
        ranking_store_path=str(Path(tmp) / "r.sqlite3"),
        ranking_enabled=enabled,
    )


class CompositionTests(unittest.TestCase):
    def test_ranking_worker_shares_store_and_market_data_with_services(self):
        with tempfile.TemporaryDirectory() as tmp:
            built = build_application(settings(tmp, True))
            worker = built.ranking_worker
            self.assertIs(worker._reader, built.queries.get_rankings._reader)
            self.assertIs(worker._calendar._market_data, built.queries.get_market_snapshot._reader)
            self.assertIs(worker._command, built.commands.build_daily_rankings)

    def test_disabled_ranking_builds_no_worker(self):
        with tempfile.TemporaryDirectory() as tmp:
            self.assertIsNone(build_application(settings(tmp, False)).ranking_worker)


if __name__ == "__main__":
    unittest.main()
