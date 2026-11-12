from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
import json
import socket
import threading
import time
from types import SimpleNamespace
import unittest
from urllib.error import HTTPError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

from fastapi import FastAPI
import uvicorn

from backend.application.commands.record_quote_sample import RecordQuoteSampleCommand
from backend.application.queries.get_market_snapshot import GetMarketSnapshotQuery
from backend.application.queries.get_quote import GetQuoteQuery
from backend.application.queries.get_quote_series import GetQuoteSeriesQuery
from backend.application.queries.get_signal_confluence import GetSignalConfluenceQuery
from backend.domain.models import (
    Candle,
    Instrument,
    MarketData,
    QuotePoint,
    QuoteSeries,
    RealtimeQuote,
)
from backend.domain.services.chan_analyzer import ChanAnalyzer
from backend.domain.services.chan_signals import ChanSignalAnalyzer
from backend.domain.services.market_analysis import MarketAnalysisPipeline
from backend.domain.services.signal_quality import SignalQualityEvaluator
from backend.domain.services.structure_guidance import StructureGuidanceEvaluator
from backend.interfaces.api.error_handlers import register_error_handlers
from backend.interfaces.api.routes import router


@dataclass(frozen=True)
class _HttpResponse:
    status_code: int
    body: dict

    def json(self) -> dict:
        return self.body


class _UvicornClient:
    def __init__(self, base_url: str):
        self._base_url = base_url

    def get(self, path: str, params: dict | None = None) -> _HttpResponse:
        url = f"{self._base_url}{path}"
        if params:
            url = f"{url}?{urlencode(params)}"
        return self._send(Request(url, method="GET"))

    def post(self, path: str, json: dict) -> _HttpResponse:
        payload = _encode_json(json)
        request = Request(
            f"{self._base_url}{path}",
            data=payload,
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        return self._send(request)

    def _send(self, request: Request) -> _HttpResponse:
        try:
            with urlopen(request, timeout=5) as response:
                return _HttpResponse(response.status, json.loads(response.read()))
        except HTTPError as error:
            return _HttpResponse(error.code, json.loads(error.read()))


def _encode_json(payload: dict) -> bytes:
    return json.dumps(payload).encode("utf-8")


class _FakeMarketReader:
    def __init__(self):
        self.seen: list[str] = []
        base = datetime(2026, 11, 1, tzinfo=timezone.utc)
        prices = (100, 102, 104, 103, 101, 105, 108, 107, 104, 109, 112, 110)
        self._candles = tuple(
            Candle(base + timedelta(days=index), price, price, price + 1, price - 1, 100)
            for index, price in enumerate(prices)
        )

    def read(self, secid: str, timeframe, force_refresh: bool = False) -> MarketData:
        self.seen.append(secid)
        code = secid.split(".")[0]
        return MarketData(Instrument(secid, code, "贵州茅台", 2, "test"), self._candles)

    def read_quote(self, secid: str) -> RealtimeQuote:
        self.seen.append(secid)
        code = secid.split(".")[0]
        return RealtimeQuote(
            secid=secid,
            symbol=code,
            name="贵州茅台",
            time=datetime(2026, 11, 10, 2, 0, tzinfo=timezone.utc),
            open=1500.0,
            high=1510.0,
            low=1490.0,
            previous_close=1495.0,
            latest=1505.0,
            change=10.0,
            change_percent=0.67,
            volume=1000,
            amount=1505000.0,
        )

    def read_series(self, secid: str) -> QuoteSeries:
        self.seen.append(secid)
        return QuoteSeries(secid=secid, points=())

    def read_intraday(self, secid: str) -> QuoteSeries:
        self.seen.append(secid)
        return QuoteSeries(
            secid=secid,
            points=(QuotePoint(datetime(2026, 11, 10, 2, 0, tzinfo=timezone.utc), 1500.0),),
        )


class _FakeQuoteWriter:
    def __init__(self):
        self.appended: list[tuple[str, datetime, float]] = []

    def append(self, secid: str, time: datetime, price: float) -> QuoteSeries:
        self.appended.append((secid, time, price))
        return QuoteSeries(secid=secid, points=(QuotePoint(time=time, price=price),))


class _FixedClock:
    def now(self) -> datetime:
        return datetime(2026, 11, 10, 8, 0, tzinfo=timezone.utc)


class _SpySnapshotQuery:
    def __init__(self, inner: GetMarketSnapshotQuery):
        self._inner = inner
        self.route_secids: list[str] = []

    def execute(self, secid: str, timeframe: str, refresh: bool = False):
        self.route_secids.append(secid)
        return self._inner.execute(secid, timeframe, refresh)


class SecurityIdApiTests(unittest.TestCase):
    def setUp(self):
        self.reader = _FakeMarketReader()
        self.writer = _FakeQuoteWriter()
        pipeline = MarketAnalysisPipeline(
            ChanAnalyzer(),
            StructureGuidanceEvaluator(),
            ChanSignalAnalyzer(),
            SignalQualityEvaluator(),
        )
        base_snapshot_query = GetMarketSnapshotQuery(self.reader, pipeline, _FixedClock())
        self.snapshot_query = _SpySnapshotQuery(base_snapshot_query)

        app = FastAPI()
        register_error_handlers(app)
        app.state.queries = SimpleNamespace(
            get_market_snapshot=self.snapshot_query,
            get_quote=GetQuoteQuery(self.reader),
            get_quote_series=GetQuoteSeriesQuery(self.reader, self.reader),
            get_signal_confluence=GetSignalConfluenceQuery(base_snapshot_query),
        )
        app.state.commands = SimpleNamespace(
            record_quote_sample=RecordQuoteSampleCommand(self.writer),
        )
        app.include_router(router)

        sock = socket.socket()
        sock.bind(("127.0.0.1", 0))
        self.client = _UvicornClient(f"http://127.0.0.1:{sock.getsockname()[1]}")
        self.server = uvicorn.Server(uvicorn.Config(app, log_level="error", lifespan="off"))
        self.thread = threading.Thread(target=lambda: self.server.run(sockets=[sock]), daemon=True)
        self.thread.start()
        for _ in range(100):
            if self.server.started:
                break
            time.sleep(0.01)
        self.assertTrue(self.server.started)

    def tearDown(self):
        self.server.should_exit = True
        self.thread.join(timeout=3)

    def test_legacy_and_canonical_inputs_reach_the_reader_as_canonical(self):
        client = self.client
        reader = self.reader
        for raw in ("1.600519", "600519.SH", "600519.sh"):
            response = client.get("/api/chan", params={"secid": raw, "timeframe": "1d"})
            self.assertEqual(response.status_code, 200)
            self.assertEqual(response.json()["instrument"]["secid"], "600519.SH")

            quote_response = client.get("/api/quote", params={"secid": raw})
            self.assertEqual(quote_response.status_code, 200)
            self.assertEqual(quote_response.json()["secid"], "600519.SH")

            series_response = client.get("/api/quote/series", params={"secid": raw})
            self.assertEqual(series_response.status_code, 200)
            self.assertEqual(series_response.json()["secid"], "600519.SH")

            confluence_response = client.get("/api/chan/confluence", params={"secid": raw})
            self.assertEqual(confluence_response.status_code, 200)
            self.assertEqual(confluence_response.json()["secid"], "600519.SH")

        default_response = client.get("/api/chan")
        self.assertEqual(default_response.status_code, 200)
        self.assertEqual(default_response.json()["instrument"]["secid"], "600519.SH")
        self.assertEqual(self.snapshot_query.route_secids[-1], "600519.SH")

        self.assertEqual(set(reader.seen), {"600519.SH"})

    def test_unsupported_and_malformed_ids_are_400_with_chinese_detail(self):
        client = self.client
        for raw, detail in (
            ("116.00700", "暂不支持该市场"),
            ("105.AAPL", "暂不支持该市场"),
            ("600519.HK", "证券标识格式不正确"),
            ("600519.XX", "证券标识格式不正确"),
            ("600519", "证券标识格式不正确"),
            ("abc", "证券标识格式不正确"),
            ("", "证券标识格式不正确"),
        ):
            for path in ("/api/chan", "/api/quote"):
                response = client.get(path, params={"secid": raw})
                self.assertEqual(response.status_code, 400, f"{path} {raw}")
                self.assertIn(detail, response.json()["detail"], f"{path} {raw}")

            sample_response = client.post(
                "/api/quote/samples",
                json={"secid": raw, "time": "2026-11-10T02:00:00Z", "price": 1500},
            )
            self.assertEqual(sample_response.status_code, 400, f"POST /api/quote/samples {raw}")
            self.assertIn(detail, sample_response.json()["detail"], f"POST /api/quote/samples {raw}")

    def test_quote_sample_is_written_under_canonical_id(self):
        client = self.client
        writer = self.writer
        response = client.post(
            "/api/quote/samples",
            json={"secid": "1.600519", "time": "2026-11-10T02:00:00Z", "price": 1500},
        )
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()["secid"], "600519.SH")
        self.assertEqual(writer.appended[0][0], "600519.SH")


if __name__ == "__main__":
    unittest.main()
