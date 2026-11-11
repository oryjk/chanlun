import json
import socket
import tempfile
import threading
import time
import unittest
from datetime import date, datetime, timedelta, timezone
from pathlib import Path
from types import SimpleNamespace
from urllib.request import urlopen

import uvicorn
from fastapi import FastAPI

from backend.application.commands.build_daily_rankings import BuildDailyRankingsCommand
from backend.application.queries.get_rankings import GetRankingsQuery
from backend.domain.models import Candle, Instrument, MarketData, Stock
from backend.domain.services.chan_analyzer import ChanAnalyzer
from backend.domain.services.chan_signals import ChanSignalAnalyzer
from backend.domain.services.market_analysis import MarketAnalysisPipeline
from backend.domain.services.signal_quality import SignalQualityEvaluator
from backend.domain.services.structure_guidance import StructureGuidanceEvaluator
from backend.infrastructure.persistence.sqlite_ranking_store import SqliteRankingStore
from backend.interfaces.api.routes import router


class RankingApiTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.path = Path(self.temp.name) / "rankings.db"
        self.store = SqliteRankingStore(self.path)
        app = FastAPI()
        app.state.queries = SimpleNamespace(get_rankings=GetRankingsQuery(self.store))
        app.include_router(router)
        sock = socket.socket()
        sock.bind(("127.0.0.1", 0))
        self.url = f"http://127.0.0.1:{sock.getsockname()[1]}/api/rankings"
        self.server = uvicorn.Server(uvicorn.Config(app, log_level="error", lifespan="off"))
        self.thread = threading.Thread(target=lambda: self.server.run(sockets=[sock]), daemon=True)
        self.thread.start()
        for _ in range(100):
            if self.server.started:
                break
            time.sleep(.01)
        self.assertTrue(self.server.started)

    def tearDown(self):
        self.server.should_exit = True
        self.thread.join(timeout=3)
        self.temp.cleanup()

    def get(self):
        with urlopen(self.url, timeout=3) as response:
            self.assertEqual(response.status, 200)
            return json.load(response)

    def test_first_load_is_preparation_not_a_finished_empty_board(self):
        body = self.get()
        self.assertEqual(body["state"], "pending")
        self.assertIsNone(body["generated_at"])
        self.assertIsNone(body["trade_date"])
        self.assertEqual(body["bullish"], [])

    def test_http_reads_published_close_snapshot_without_recomputing(self):
        calls = []
        prices = (105, 100, 105, 110, 115, 120, 116, 112, 108, 105, 111, 117, 123, 130, 128, 126, 124, 122, 126)
        end = datetime(2026, 11, 3, tzinfo=timezone.utc)
        start = end - timedelta(days=len(prices) - 1)
        candles = tuple(
            Candle(start + timedelta(days=i), price, price, price + 1, price - 1, 100)
            for i, price in enumerate(prices)
        )
        def read_market(secid, timeframe, **kwargs):
            calls.append(secid)
            return MarketData(Instrument(secid, secid.split(".")[-1], "股票", 2, "test"), candles)
        stocks = (Stock("0.000001", "000001", "平安银行", "深A"), Stock("1.510300", "510300", "沪深300ETF", "ETF"))
        pipeline = MarketAnalysisPipeline(
            ChanAnalyzer(),
            StructureGuidanceEvaluator(),
            ChanSignalAnalyzer(),
            SignalQualityEvaluator(),
        )
        class FixedClock:
            def __init__(self, value):
                self.value = value
            def now(self):
                return self.value
        command = BuildDailyRankingsCommand(
            SimpleNamespace(list_all=lambda **kwargs: stocks),
            SimpleNamespace(read=read_market),
            pipeline,
            self.store,
            self.store,
            self.store,
            self.store,
            FixedClock(end),
        )
        self.assertTrue(command.execute(date(2026, 11, 3)))
        before = self.path.stat().st_mtime_ns
        for _ in range(2):
            body = self.get()
            self.assertEqual(body["trade_date"], "2026-11-03")
            self.assertEqual((body["total_count"], body["analyzed_count"]), (2, 2))
            self.assertEqual(len(body["bullish"]), 2)
        self.assertEqual(len(calls), 2)
        self.assertEqual(self.path.stat().st_mtime_ns, before)


if __name__ == "__main__":
    unittest.main()
