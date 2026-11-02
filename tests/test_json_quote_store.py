import json
import tempfile
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path

from backend.infrastructure.persistence.json_quote_store import JsonQuoteStore


class JsonQuoteStoreTests(unittest.TestCase):
    def test_append_read_and_deduplicate_timestamp(self):
        with tempfile.TemporaryDirectory() as directory:
            store = JsonQuoteStore(directory)
            timestamp = datetime(2026, 11, 2, 2, 34, 45, tzinfo=timezone.utc)

            store.append("0.301162", timestamp, 33.21)
            store.append("0.301162", timestamp + timedelta(seconds=3), 33.28)
            store.append("0.301162", timestamp + timedelta(seconds=3), 33.29)
            series = store.read_series("0.301162")

            self.assertEqual(len(series.points), 2)
            self.assertEqual([point.price for point in series.points], [33.21, 33.29])
            files = list(Path(directory).glob("*.json"))
            self.assertEqual(len(files), 1)
            payload = json.loads(files[0].read_text(encoding="utf-8"))
            self.assertEqual(payload["secid"], "0.301162")
            self.assertEqual(payload["points"][0]["time"], "2026-11-02T02:34:45Z")

    def test_append_cleans_previous_day_files(self):
        with tempfile.TemporaryDirectory() as directory:
            yesterday = datetime.now().astimezone().date() - timedelta(days=1)
            old_file = Path(directory) / f"0.301162-{yesterday:%Y%m%d}.json"
            old_file.write_text('{"secid":"0.301162","points":[]}', encoding="utf-8")
            store = JsonQuoteStore(directory)

            store.append("0.301162", datetime.now(timezone.utc), 33.29)

            self.assertFalse(old_file.exists())
            self.assertEqual(len(store.read_series("0.301162").points), 1)


if __name__ == "__main__":
    unittest.main()
