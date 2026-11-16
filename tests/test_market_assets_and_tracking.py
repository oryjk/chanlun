from datetime import date, datetime, timezone
import json
from pathlib import Path
import socket
import tempfile
import threading
import time
import unittest
from urllib.error import HTTPError
from urllib.request import Request, urlopen

from fastapi import FastAPI
import uvicorn

from backend.app.app_auth import NativeAuthServices
from backend.app.tracking import build_native_tracking
from backend.application.commands.app_login import AppAuthService
from backend.application.commands.services import CommandServices
from backend.application.queries.app_session import GetAppSession
from backend.application.queries.get_market_assets import GetMarketAssetsQuery
from backend.application.queries.services import QueryServices
from backend.application.ranking_jobs import RankingItem, RankingProgress, RankingView
from backend.domain.app_auth import AppleIdentity
from backend.domain.errors import MarketDataNotFound, MarketDataUnavailable
from backend.domain.models import (
    BuySellPoint,
    Candle,
    Instrument,
    MarketData,
    QuotePoint,
    RankingBoard,
    RankingEntry,
    RealtimeQuote,
    Stock,
    TimeFrame,
)
from backend.domain.tracking import (
    AssetCategory,
    AssetSortField,
    MarketAssetItem,
    SIMULATED_PRINCIPAL,
    TrackedAssetRecord,
    build_tracked_asset_summary,
)
from backend.infrastructure.market.fallback_market_data import FallbackQuoteReader
from backend.infrastructure.market.sina_quote import SinaQuoteReader
from backend.infrastructure.persistence.sqlite_app_auth import SqliteAppAuthStore
from backend.infrastructure.persistence.sqlite_app_tracking import SqliteAppTrackingStore
from backend.interfaces.api.app_auth_routes import router as app_auth_router
from backend.interfaces.api.app_tracking_routes import router as app_tracking_router
from backend.interfaces.api.error_handlers import register_error_handlers
from backend.interfaces.api.routes import router as market_router


class _FixedClock:
    def __init__(self, now: datetime):
        self._now = now

    def now(self) -> datetime:
        return self._now


class _FakeHttpClient:
    def __init__(self, responses: dict[str, str]):
        self._responses = responses

    def get_text(self, url: str, params=None, *, headers=None, encoding=None) -> str:
        for key, value in self._responses.items():
            if key in url:
                return value
        raise MarketDataUnavailable("mock missing")


class _StubPrimaryQuoteReader:
    def __init__(self, fail: bool = False):
        self._fail = fail
        self.calls = 0

    def read_quote(self, secid: str) -> RealtimeQuote:
        self.calls += 1
        if self._fail:
            raise MarketDataUnavailable("primary down")
        return RealtimeQuote(
            secid=secid,
            symbol=secid.split(".")[0],
            name="主源标的名称",
            time=datetime(2026, 11, 16, 7, 0, tzinfo=timezone.utc),
            open=10.0,
            high=10.5,
            low=9.9,
            previous_close=10.0,
            latest=10.2,
            change=0.2,
            change_percent=2.0,
            volume=1000,
            amount=10200.0,
        )


class _StubCatalogReader:
    def __init__(self, items=(), error=None):
        self._items = tuple(items)
        self._error = error

    def list_assets(self, category, *, sort_field, descending, limit):
        if self._error:
            raise self._error
        return self._items[:limit]


class _StubRankingReader:
    def __init__(self, view: RankingView, saved_items=()):
        self._view = view
        self._saved_items = tuple(saved_items)

    def read(self) -> RankingView:
        return self._view

    def read_items(self, trade_date: date, *, analysis_version: int):
        return self._saved_items


class _StubMarketDataReader:
    def read(self, secid: str, timeframe: TimeFrame, force_refresh: bool = False) -> MarketData:
        candles = (
            Candle(datetime(2026, 11, 12, tzinfo=timezone.utc), 100.0, 102.0, 103.0, 99.0, 1000),
            Candle(datetime(2026, 11, 13, tzinfo=timezone.utc), 102.0, 105.0, 106.0, 101.0, 1200),
        )
        return MarketData(
            instrument=Instrument(secid=secid, symbol=secid.split(".")[0], name="测试标的", decimal=2, data_source="stub"),
            candles=candles,
        )


class _StubStockSearchReader:
    def search(self, query: str) -> list[Stock]:
        return [Stock("600519.SH", "600519", "贵州茅台", "沪A")]


class TrackingDomainAndAdaptersTests(unittest.TestCase):
    def test_simulated_principal_summary_math(self):
        record = TrackedAssetRecord(
            user_id="u1",
            secid="600519.SH",
            symbol="600519",
            name="贵州茅台",
            market="沪A",
            start_date=date(2026, 11, 10),
            start_price=100.0,
            created_at=datetime(2026, 11, 10, 8, 0, tzinfo=timezone.utc),
        )
        summary = build_tracked_asset_summary(
            record,
            latest_price=110.0,
            previous_close=105.0,
            day_return_pct=4.76,
            sparkline=(100.0, 105.0, 110.0),
        )
        self.assertEqual(summary.current_value, round(SIMULATED_PRINCIPAL * 1.1, 2))
        self.assertEqual(summary.cumulative_profit, 1000.0)
        self.assertEqual(summary.cumulative_return_pct, 10.0)
        self.assertEqual(summary.day_return_pct, 4.76)
        self.assertEqual(summary.day_profit, 500.0)

    def test_sina_quote_reader_and_fallback_breaker(self):
        http = _FakeHttpClient(
            {
                "hq.sinajs.cn/list=": (
                    'var hq_str_sh600519="贵州茅台,1426.300,1435.260,1453.900,1454.930,1425.000,0,0,3996700,5767305714.000,'
                    + ",".join(["0"] * 20)
                    + ',2026-11-16,15:34:59,00";\n'
                    'var hq_str_sz159915="创业板,2.413,2.442,2.334,2.414,2.325,0,0,4724979365,11185140204.400,'
                    + ",".join(["0"] * 20)
                    + ',2026-11-16,15:35:45,00";\n'
                )
            }
        )
        sina = SinaQuoteReader(http)
        batch = sina.read_quotes(("600519.SH", "159915.SZ"))
        self.assertEqual(batch["600519.SH"].name, "贵州茅台")
        self.assertEqual(batch["600519.SH"].latest, 1453.9)
        self.assertEqual(batch["159915.SZ"].latest, 2.334)

        primary = _StubPrimaryQuoteReader(fail=True)
        fallback = FallbackQuoteReader(primary, sina, failure_threshold=2, cooldown_seconds=60)
        self.assertEqual(fallback.read_quote("600519.SH").name, "贵州茅台")
        self.assertEqual(fallback.read_quote("600519.SH").name, "贵州茅台")
        self.assertEqual(fallback.read_quote("600519.SH").name, "贵州茅台")
        self.assertEqual(primary.calls, 2)  # 3rd call bypassed tripped primary!

    def test_market_assets_query_enriches_and_falls_back_to_rankings(self):
        stock = Stock("600519.SH", "600519", "贵州茅台", "沪A")
        etf = Stock("159915.SZ", "159915", "创业板ETF", "ETF")
        entry_stock = RankingEntry(
            rank=1,
            stock=stock,
            bias="bullish",
            strength=3,
            latest=1453.9,
            change_percent=1.3,
            invalidation_price=1400.0,
            latest_point=BuySellPoint(2, "buy", 5, 10, datetime(2026, 11, 10, tzinfo=timezone.utc), 1400.0, True, "二买"),
            trend=(QuotePoint(datetime(2026, 11, 12, tzinfo=timezone.utc), 1410.0), QuotePoint(datetime(2026, 11, 16, tzinfo=timezone.utc), 1453.9)),
        )
        entry_etf = RankingEntry(
            rank=2,
            stock=etf,
            bias="bullish",
            strength=2,
            latest=2.334,
            change_percent=-1.2,
            invalidation_price=2.2,
            latest_point=BuySellPoint(1, "buy", 4, 8, datetime(2026, 11, 9, tzinfo=timezone.utc), 2.25, True, "一买"),
            trend=(QuotePoint(datetime(2026, 11, 12, tzinfo=timezone.utc), 2.30), QuotePoint(datetime(2026, 11, 16, tzinfo=timezone.utc), 2.334)),
        )
        board = RankingBoard(
            generated_at=datetime(2026, 11, 16, 8, 0, tzinfo=timezone.utc),
            timeframe=TimeFrame.ONE_DAY,
            bullish=(entry_stock, entry_etf),
            bearish=(),
            trade_date=date(2026, 11, 16),
        )
        view = RankingView(board, RankingProgress("ready", date(2026, 11, 16)))
        ranking_reader = _StubRankingReader(
            view,
            saved_items=(RankingItem(stock.secid, entry_stock, None), RankingItem(etf.secid, entry_etf, None)),
        )
        catalog_item = MarketAssetItem(stock, 1453.9, 18.64, 1.3, 1426.3, 1454.93, 1425.0, 1435.26, 3996700, 5767305714.0)
        query = GetMarketAssetsQuery(
            _StubCatalogReader(items=(catalog_item,)),
            ranking_reader,
            _FixedClock(datetime(2026, 11, 17, 2, 0, tzinfo=timezone.utc)),
        )
        result = query.execute("stock", "amount", "desc", 20)
        self.assertEqual(result.category, AssetCategory.STOCK)
        self.assertEqual(result.as_of_date, "2026-11-16")
        self.assertEqual(result.items[0].signal_label, "二类买点")
        self.assertEqual(result.items[0].sparkline, (1410.0, 1453.9))

        fallback_query = GetMarketAssetsQuery(
            _StubCatalogReader(error=MarketDataUnavailable("down")),
            ranking_reader,
            _FixedClock(datetime(2026, 11, 17, 2, 0, tzinfo=timezone.utc)),
        )
        etf_result = fallback_query.execute("etf", "change_percent", "desc", 20)
        self.assertEqual([item.stock.secid for item in etf_result.items], ["159915.SZ"])

    def test_sqlite_tracking_store_upsert_idempotent_and_readonly(self):
        with tempfile.TemporaryDirectory() as tmp:
            store = SqliteAppTrackingStore(Path(tmp) / "track.sqlite3")
            first = TrackedAssetRecord("u1", "600519.SH", "600519", "贵州茅台", "沪A", date(2026, 11, 16), 1453.9, datetime(2026, 11, 16, 7, 0, tzinfo=timezone.utc))
            duplicate = TrackedAssetRecord("u1", "600519.SH", "600519", "贵州茅台", "沪A", date(2026, 11, 17), 1500.0, datetime(2026, 11, 17, 7, 0, tzinfo=timezone.utc))
            saved = store.upsert_tracking(first)
            kept = store.upsert_tracking(duplicate)
            self.assertEqual(saved.start_price, 1453.9)
            self.assertEqual(kept.start_price, 1453.9)
            self.assertEqual(len(store.list_by_user("u1")), 1)
            self.assertEqual(store.remove_trackings("u1", ("600519.SH",)), 1)
            self.assertEqual(store.list_by_user("u1"), ())


class MarketAssetsAndTrackingHttpTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory()
        auth_store = SqliteAppAuthStore(Path(cls.temp.name) / "auth.db", "test" * 10)
        clock = _FixedClock(datetime(2026, 11, 17, 2, 0, tzinfo=timezone.utc))

        class _FakeApple:
            def verify_and_exchange(self, token, code, nonce):
                return AppleIdentity("https://appleid.apple.com", "sub", "refresh")
            def revoke(self, token):
                pass
            def verify_notification(self, payload):
                pass

        class _FakeEmail:
            def __init__(self):
                self.codes = {}
            def send_verification_code(self, email, code, expires_minutes):
                self.codes[email] = code

        cls.email_gateway = _FakeEmail()
        auth_service = AppAuthService(auth_store, auth_store, _FakeApple(), clock.now, cls.email_gateway)

        http = _FakeHttpClient(
            {
                "hq.sinajs.cn/list=": (
                    'var hq_str_sh600519="贵州茅台,1426.300,1435.260,1453.900,1454.930,1425.000,0,0,3996700,5767305714.000,'
                    + ",".join(["0"] * 20)
                    + ',2026-11-16,15:34:59,00";\n'
                )
            }
        )
        quote_reader = FallbackQuoteReader(_StubPrimaryQuoteReader(fail=True), SinaQuoteReader(http))
        stock = Stock("600519.SH", "600519", "贵州茅台", "沪A")
        board = RankingBoard(
            generated_at=datetime(2026, 11, 16, 8, 0, tzinfo=timezone.utc),
            timeframe=TimeFrame.ONE_DAY,
            bullish=(
                RankingEntry(
                    rank=1,
                    stock=stock,
                    bias="bullish",
                    strength=3,
                    latest=1453.9,
                    change_percent=1.3,
                    invalidation_price=1400.0,
                    trend=(QuotePoint(datetime(2026, 11, 15, tzinfo=timezone.utc), 1435.26), QuotePoint(datetime(2026, 11, 16, tzinfo=timezone.utc), 1453.9)),
                ),
            ),
            bearish=(),
            trade_date=date(2026, 11, 16),
        )
        ranking_reader = _StubRankingReader(RankingView(board, RankingProgress("ready", date(2026, 11, 16))))
        market_data = _StubMarketDataReader()
        search_reader = _StubStockSearchReader()

        app = FastAPI()
        app.state.native_auth = NativeAuthServices(auth_service, GetAppSession(auth_store, clock.now), None)
        app.state.native_tracking = build_native_tracking(
            quote_reader=quote_reader,
            batch_quotes=quote_reader,
            ranking_reader=ranking_reader,
            market_data=market_data,
            stock_search=search_reader,
            clock=clock,
            store_path=str(Path(cls.temp.name) / "track.db"),
        )
        catalog_item = MarketAssetItem(stock, 1453.9, 18.64, 1.3, 1426.3, 1454.93, 1425.0, 1435.26, 3996700, 5767305714.0)
        app.state.queries = QueryServices(
            search_stocks=None,
            get_market_snapshot=None,
            get_quote=None,
            get_quote_series=None,
            get_rankings=None,
            get_signal_confluence=None,
            get_current_user=None,
            get_mini_program_link=None,
            get_market_assets=GetMarketAssetsQuery(_StubCatalogReader(items=(catalog_item,)), ranking_reader, clock),
        )
        app.state.commands = CommandServices(None, None, None)
        app.include_router(market_router)
        app.include_router(app_auth_router)
        app.include_router(app_tracking_router)
        register_error_handlers(app)

        sock = socket.socket()
        sock.bind(("127.0.0.1", 0))
        cls.base = f"http://127.0.0.1:{sock.getsockname()[1]}"
        cls.server = uvicorn.Server(uvicorn.Config(app, log_level="error"))
        cls.thread = threading.Thread(target=lambda: cls.server.run(sockets=[sock]), daemon=True)
        cls.thread.start()
        deadline = time.monotonic() + 5
        while not cls.server.started and time.monotonic() < deadline:
            time.sleep(0.01)

    @classmethod
    def tearDownClass(cls):
        cls.server.should_exit = True
        cls.thread.join(5)
        cls.temp.cleanup()

    def request(self, path: str, body=None, token: str | None = None):
        headers = {"Content-Type": "application/json"}
        if token:
            headers["Authorization"] = f"Bearer {token}"
        req = Request(self.base + path, data=json.dumps(body).encode() if body is not None else None, headers=headers)
        try:
            with urlopen(req, timeout=5) as resp:
                return resp.status, json.loads(resp.read())
        except HTTPError as error:
            return error.code, json.loads(error.read())

    def test_assets_and_authenticated_tracking_lifecycle(self):
        status, assets = self.request("/api/market/assets?category=stock&sort=amount&order=desc&limit=10")
        self.assertEqual(status, 200)
        self.assertEqual(assets["category"], "stock")
        self.assertEqual(len(assets["items"]), 1)
        self.assertEqual(assets["items"][0]["stock"]["name"], "贵州茅台")

        self.assertEqual(self.request("/api/market/assets?category=invalid")[0], 400)
        self.assertEqual(self.request("/api/app-tracking")[0], 401)
        self.assertEqual(self.request("/api/app-tracking/start", {"secid": "600519.SH"})[0], 401)

        self.request("/api/app-auth/email/code", {"email": "track@example.com"})
        code = self.email_gateway.codes["track@example.com"]
        _, reg = self.request(
            "/api/app-auth/email/register",
            {"email": "track@example.com", "code": code, "password": "TrackPassword123"},
        )
        token = reg["token"]

        status, started = self.request("/api/app-tracking/start", {"secid": "600519.SH"}, token=token)
        self.assertEqual(status, 200)
        self.assertTrue(started["ok"])
        self.assertEqual(started["item"]["secid"], "600519.SH")

        status, overview = self.request("/api/app-tracking", token=token)
        self.assertEqual(status, 200)
        self.assertEqual(len(overview["items"]), 1)
        self.assertEqual(overview["items"][0]["current_value"], 10000.0)
        self.assertEqual(overview["items"][0]["sparkline"], [1435.26, 1453.9])

        status, canceled = self.request("/api/app-tracking/cancel", {"secids": ["600519.SH"]}, token=token)
        self.assertEqual(status, 200)
        self.assertEqual(canceled["removed"], 1)

        _, empty_overview = self.request("/api/app-tracking", token=token)
        self.assertEqual(empty_overview["items"], [])


if __name__ == "__main__":
    unittest.main()
