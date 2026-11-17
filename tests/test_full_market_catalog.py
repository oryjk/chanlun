from datetime import datetime
import json
from pathlib import Path
import tempfile
import unittest
from zoneinfo import ZoneInfo

from backend.domain.errors import MarketDataUnavailable
from backend.domain.tracking import AssetCategory, AssetSortField
from backend.infrastructure.cache.asset_catalog_cache import SqliteAssetCatalogCache
from backend.infrastructure.market.asset_catalog import MarketAssetCatalogAdapter
from backend.infrastructure.market.eastmoney_stock_search import EastmoneyStockSearchReader

_SHANGHAI_TZ = ZoneInfo("Asia/Shanghai")


class _MockHttpClient:
    def __init__(self, handler):
        self._handler = handler

    def get(self, url: str, params=None) -> dict:
        return json.loads(self.get_text(url, params))

    def get_text(self, url: str, params=None, *, headers=None, encoding=None) -> str:
        result = self._handler(url, params or {})
        if result is None:
            raise MarketDataUnavailable("mock unavailable")
        return result


def _sina_kline_jsonp(bars: list[tuple[str, float]]) -> str:
    payload = [
        {
            "day": day,
            "open": f"{close:.3f}",
            "high": f"{close:.3f}",
            "low": f"{close:.3f}",
            "close": f"{close:.3f}",
            "volume": "100000",
        }
        for day, close in bars
    ]
    return f"var _data=({json.dumps(payload)});"


class FullMarketCatalogTests(unittest.TestCase):
    def test_eastmoney_search_supports_bse_and_excludes_plain_neeq(self):
        client = _MockHttpClient(
            lambda url, params: json.dumps(
                {
                    "QuotationCodeTable": {
                        "Data": [
                            {
                                "QuoteID": "0.920045",
                                "Code": "920045",
                                "Name": "蘅东光",
                                "Classify": "NEEQ",
                                "SecurityTypeName": "京A",
                            },
                            {
                                "QuoteID": "0.830001",
                                "Code": "830001",
                                "Name": "普通新三板",
                                "Classify": "NEEQ",
                                "SecurityTypeName": "三板",
                            },
                            {
                                "QuoteID": "1.510300",
                                "Code": "510300",
                                "Name": "沪深300ETF",
                                "Classify": "Fund",
                                "SecurityTypeName": "基金",
                            },
                        ]
                    }
                },
                ensure_ascii=False,
            )
        )
        results = EastmoneyStockSearchReader(client).search("920")
        self.assertEqual([s.secid for s in results], ["920045.BJ", "510300.SH"])
        self.assertEqual(results[0].market, "京A")
        self.assertEqual(results[1].market, "ETF")

    def test_full_market_pagination_includes_bse_and_keeps_halted_at_bottom(self):
        counts = {"sh_a": 102, "sz_a": 2, "hs_bjs": 2, "etf_hq_fund": 2}
        sh_page1 = [
            {
                "symbol": f"sh600{100 + idx:03d}",
                "name": f"沪A测试{idx}",
                "trade": f"{10.0 + idx * 0.1:.2f}",
                "settlement": "10.00",
                "pricechange": "0.10",
                "changepercent": f"{0.5 + idx * 0.01:.2f}",
                "volume": 100000,
                "amount": 1_000_000 + idx * 1000,
            }
            for idx in range(100)
        ]
        sh_page2 = [
            {
                "symbol": "sh600998",
                "name": "停牌沪股",
                "trade": "0.00",
                "settlement": "8.50",
                "pricechange": "0.00",
                "changepercent": "0.00",
                "volume": 0,
                "amount": 0,
            },
            {
                "symbol": "sh600999",
                "name": "沪A末页",
                "trade": "25.00",
                "settlement": "24.00",
                "pricechange": "1.00",
                "changepercent": "4.17",
                "volume": 500000,
                "amount": 50_000_000,
            },
        ]
        sz_page1 = [
            {
                "symbol": "sz000001",
                "name": "平安银行",
                "trade": "12.30",
                "settlement": "12.20",
                "pricechange": "0.10",
                "changepercent": "0.82",
                "volume": 2500000,
                "amount": 30_750_000,
            },
            {
                "symbol": "sz300750",
                "name": "宁德时代",
                "trade": "264.80",
                "settlement": "258.60",
                "pricechange": "6.20",
                "changepercent": "2.40",
                "volume": 18500000,
                "amount": 4_890_000_000,
            },
        ]
        bj_page1 = [
            {
                "symbol": "bj920045",
                "name": "蘅东光",
                "trade": "350.99",
                "settlement": "339.60",
                "pricechange": "11.39",
                "changepercent": "3.35",
                "volume": 1575972,
                "amount": 541_736_881,
            },
            {
                "symbol": "bj920999",
                "name": "停牌北交",
                "trade": "0.00",
                "settlement": "15.00",
                "pricechange": "0.00",
                "changepercent": "0.00",
                "volume": 0,
                "amount": 0,
            },
        ]
        etf_page1 = [
            {
                "symbol": "sh510300",
                "name": "沪深300ETF",
                "trade": "4.054",
                "settlement": "4.041",
                "pricechange": "0.013",
                "changepercent": "0.32",
                "volume": 18931200,
                "amount": 76_466_833,
            },
            {
                "symbol": "sz159915",
                "name": "创业板ETF",
                "trade": "2.334",
                "settlement": "2.350",
                "pricechange": "-0.016",
                "changepercent": "-0.68",
                "volume": 47249793,
                "amount": 111_851_402,
            },
        ]

        def handler(url: str, params: dict) -> str | None:
            if "hq.sinajs.cn/list=" in url:
                return (
                    'var hq_str_sh000001="上证指数,3600,3602,3610,3615,3595,0,0,100,1000,'
                    + ",".join(["0"] * 20)
                    + ',2026-11-17,10:30:00,00";\n'
                )
            if "getHQNodeStockCount" in url:
                return f'"{counts[params["node"]]}"'
            if "getHQNodeData" in url:
                node = params["node"]
                page = int(params["page"])
                if node == "sh_a":
                    return json.dumps(sh_page1 if page == 1 else sh_page2, ensure_ascii=False)
                if node == "sz_a":
                    return json.dumps(sz_page1, ensure_ascii=False)
                if node == "hs_bjs":
                    return json.dumps(bj_page1, ensure_ascii=False)
                if node == "etf_hq_fund":
                    return json.dumps(etf_page1, ensure_ascii=False)
            if "getKLineData" in url:
                return _sina_kline_jsonp([("2026-11-13", 10.0), ("2026-11-16", 10.1)])
            return None

        with tempfile.TemporaryDirectory() as tmp:
            cache = SqliteAssetCatalogCache(Path(tmp) / "catalog.sqlite3")
            adapter = MarketAssetCatalogAdapter(
                _MockHttpClient(handler),
                cache_store=cache,
                ttl_seconds=60.0,
                now_fn=lambda: datetime(2026, 11, 17, 10, 30, tzinfo=_SHANGHAI_TZ),
            )
            page0 = adapter.list_assets(
                AssetCategory.STOCK,
                sort_field=AssetSortField.AMOUNT,
                descending=True,
                limit=50,
                offset=0,
            )
            self.assertEqual(page0.total, 106)
            self.assertEqual(page0.offset, 0)
            self.assertTrue(page0.has_more)
            self.assertEqual(len(page0.items), 50)
            self.assertEqual(page0.items[0].stock.secid, "300750.SZ")
            self.assertEqual(page0.items[1].stock.secid, "920045.BJ")
            self.assertEqual(page0.items[1].stock.market, "京A")

            # Fetch remaining pages and ensure halted stocks appear only at the tail, even on ascending sort
            page1 = adapter.list_assets(AssetCategory.STOCK, sort_field=AssetSortField.AMOUNT, descending=True, limit=50, offset=50)
            page2 = adapter.list_assets(AssetCategory.STOCK, sort_field=AssetSortField.AMOUNT, descending=True, limit=50, offset=100)
            self.assertFalse(page2.has_more)
            self.assertEqual(len(page2.items), 6)
            all_secids = [item.stock.secid for p in (page0, page1, page2) for item in p.items]
            self.assertEqual(len(all_secids), 106)
            self.assertEqual(len(set(all_secids)), 106)
            self.assertEqual({item.stock.secid for item in page2.items[-2:]}, {"600998.SH", "920999.BJ"})

            asc_tail = adapter.list_assets(AssetCategory.STOCK, sort_field=AssetSortField.AMOUNT, descending=False, limit=10, offset=100)
            self.assertEqual({item.stock.secid for item in asc_tail.items[-2:]}, {"600998.SH", "920999.BJ"})

            etf_list = adapter.list_assets(AssetCategory.ETF, sort_field=AssetSortField.AMOUNT, descending=True, limit=100, offset=0)
            self.assertEqual(etf_list.total, 2)
            self.assertFalse(etf_list.has_more)
            self.assertEqual([item.stock.secid for item in etf_list.items], ["159915.SZ", "510300.SH"])

    def test_sqlite_backfill_incremental_and_ex_dividend_sync(self):
        clock_now = [datetime(2026, 11, 17, 10, 30, tzinfo=_SHANGHAI_TZ)]
        kline_calls: list[tuple[str, int]] = []
        mode = ["initial"]  # "initial" -> "post_close" -> "gap_2d" -> "ex_div"
        tencent_qfq_calls: list[str] = []

        def handler(url: str, params: dict) -> str | None:
            if "hq.sinajs.cn/list=" in url:
                date_str = {
                    "initial": "2026-11-17",
                    "post_close": "2026-11-17",
                    "gap_2d": "2026-11-19",
                    "ex_div": "2026-11-20",
                }[mode[0]]
                return (
                    f'var hq_str_sh000001="上证指数,3600,3602,3610,3615,3595,0,0,100,1000,'
                    + ",".join(["0"] * 20)
                    + f',{date_str},15:05:00,00";\n'
                )
            if "getHQNodeStockCount" in url:
                return {"sh_a": '"2"', "sz_a": '"1"', "hs_bjs": '"1"', "etf_hq_fund": '"1"'}[params["node"]]
            if "getHQNodeData" in url:
                node = params["node"]
                if node == "sh_a":
                    if mode[0] == "initial":
                        return json.dumps([
                            {"symbol": "sh600519", "name": "贵州茅台", "trade": "1460.00", "settlement": "1453.90", "pricechange": "6.10", "changepercent": "0.42", "volume": 10000, "amount": 14_600_000},
                            {"symbol": "sh688999", "name": "新上市股", "trade": "52.00", "settlement": "50.00", "pricechange": "2.00", "changepercent": "4.00", "volume": 5000, "amount": 260_000},
                        ], ensure_ascii=False)
                    if mode[0] == "post_close":
                        return json.dumps([
                            {"symbol": "sh600519", "name": "贵州茅台", "trade": "1470.00", "settlement": "1453.90", "pricechange": "16.10", "changepercent": "1.11", "volume": 20000, "amount": 29_400_000},
                            {"symbol": "sh688999", "name": "新上市股", "trade": "53.00", "settlement": "50.00", "pricechange": "3.00", "changepercent": "6.00", "volume": 6000, "amount": 318_000},
                        ], ensure_ascii=False)
                    if mode[0] == "gap_2d":
                        return json.dumps([
                            {"symbol": "sh600519", "name": "贵州茅台", "trade": "1490.00", "settlement": "1480.00", "pricechange": "10.00", "changepercent": "0.68", "volume": 20000, "amount": 29_800_000},
                            {"symbol": "sh688999", "name": "新上市股", "trade": "55.00", "settlement": "54.00", "pricechange": "1.00", "changepercent": "1.85", "volume": 6000, "amount": 330_000},
                        ], ensure_ascii=False)
                    return json.dumps([
                        {"symbol": "sh600519", "name": "贵州茅台", "trade": "750.00", "settlement": "745.00", "pricechange": "5.00", "changepercent": "0.67", "volume": 40000, "amount": 30_000_000},
                        {"symbol": "sh688999", "name": "新上市股", "trade": "55.50", "settlement": "55.00", "pricechange": "0.50", "changepercent": "0.91", "volume": 6000, "amount": 333_000},
                    ], ensure_ascii=False)
                if node == "sz_a":
                    return json.dumps([
                        {"symbol": "sz000001", "name": "平安银行", "trade": "12.30", "settlement": "12.20", "pricechange": "0.10", "changepercent": "0.82", "volume": 5000, "amount": 61_500},
                    ], ensure_ascii=False)
                if node == "hs_bjs":
                    return json.dumps([
                        {"symbol": "bj920045", "name": "蘅东光", "trade": "340.00", "settlement": "339.60", "pricechange": "0.40", "changepercent": "0.12", "volume": 2000, "amount": 680_000},
                    ], ensure_ascii=False)
                if node == "etf_hq_fund":
                    return json.dumps([
                        {"symbol": "sh510300", "name": "沪深300ETF", "trade": "4.054", "settlement": "4.041", "pricechange": "0.013", "changepercent": "0.32", "volume": 5000, "amount": 20_270},
                    ], ensure_ascii=False)
            if "getKLineData" in url:
                sym = params["symbol"]
                datalen = int(params["datalen"])
                kline_calls.append((sym, datalen))
                if sym == "sh000001":
                    cal = [("2026-11-12", 3655.0), ("2026-11-13", 3634.0), ("2026-11-16", 3602.0)]
                    if mode[0] == "gap_2d":
                        cal.extend([("2026-11-17", 3610.0), ("2026-11-18", 3620.0), ("2026-11-19", 3630.0)])
                    elif mode[0] == "ex_div":
                        cal.extend([("2026-11-17", 3610.0), ("2026-11-18", 3620.0), ("2026-11-19", 3630.0), ("2026-11-20", 3640.0)])
                    return _sina_kline_jsonp(cal)
                if sym == "sh688999":
                    if mode[0] == "initial":
                        return _sina_kline_jsonp([("2026-11-17", 52.0)])
                    if mode[0] == "gap_2d":
                        return _sina_kline_jsonp([("2026-11-17", 53.0), ("2026-11-18", 54.0), ("2026-11-19", 55.0)])
                    return _sina_kline_jsonp([("2026-11-17", 53.0), ("2026-11-18", 54.0), ("2026-11-19", 55.0), ("2026-11-20", 55.5)])
                if sym == "sh600519":
                    if mode[0] == "initial":
                        return _sina_kline_jsonp([("2026-11-13", 1435.26), ("2026-11-16", 1453.90)])
                    if mode[0] == "gap_2d":
                        return _sina_kline_jsonp([("2026-11-16", 1453.90), ("2026-11-17", 1470.0), ("2026-11-18", 1480.0), ("2026-11-19", 1490.0)][-datalen:])
                    # Ex-dividend day: unadjusted sina still reports pre-split prices on 11-16..11-18
                    return _sina_kline_jsonp([("2026-11-17", 1470.0), ("2026-11-18", 1480.0), ("2026-11-19", 1490.0), ("2026-11-20", 750.0)])
                if mode[0] == "gap_2d":
                    return _sina_kline_jsonp([("2026-11-17", 12.1), ("2026-11-18", 12.2), ("2026-11-19", 12.3)][-datalen:])
                if mode[0] == "ex_div":
                    return _sina_kline_jsonp([("2026-11-18", 12.2), ("2026-11-19", 12.3), ("2026-11-20", 12.4)][-datalen:])
                return _sina_kline_jsonp([("2026-11-13", 12.0), ("2026-11-16", 12.2)])
            if "web.ifzq.gtimg.cn" in url:
                sym = params["param"].split(",")[0]
                tencent_qfq_calls.append(sym)
                if sym == "sh600519" and mode[0] == "ex_div":
                    return json.dumps({
                        "data": {
                            "sh600519": {
                                "qfqday": [
                                    ["2026-11-13", "717", "717.63", "720", "710", "1000"],
                                    ["2026-11-16", "726", "726.95", "730", "720", "1000"],
                                    ["2026-11-17", "735", "735.00", "740", "730", "1000"],
                                    ["2026-11-18", "740", "740.00", "745", "735", "1000"],
                                    ["2026-11-19", "745", "745.00", "750", "740", "1000"],
                                    ["2026-11-20", "748", "750.00", "752", "745", "1000"],
                                ]
                            }
                        }
                    })
                return json.dumps({"data": {}})
            return None

        with tempfile.TemporaryDirectory() as tmp:
            db_path = Path(tmp) / "catalog.sqlite3"
            cache = SqliteAssetCatalogCache(db_path)
            adapter = MarketAssetCatalogAdapter(
                _MockHttpClient(handler),
                cache_store=cache,
                ttl_seconds=0.0,
                now_fn=lambda: clock_now[0],
            )

            # 1) Initial full backfill during intraday
            stats1 = adapter.sync_all_sparklines_once()
            self.assertEqual(stats1["backfilled"], 5)
            self.assertEqual(stats1["failed"], 0)
            self.assertEqual(len(cache.load_sync_dates()), 5)
            self.assertEqual(cache.load_sync_dates()["600519.SH"], "2026-11-16")
            self.assertEqual(cache.load_sync_dates()["688999.SH"], "2026-11-16")

            # 2) Second run same intraday window: zero per-stock K-line fetches (even for 1-bar IPO 688999.SH)
            kline_calls.clear()
            stats2 = adapter.sync_all_sparklines_once()
            self.assertEqual(stats2["synced"], 5)
            self.assertEqual(stats2["backfilled"], 0)
            self.assertEqual([sym for sym, _ in kline_calls if sym != "sh000001"], [])

            # 3) Post-close (>= 15:05) same day: zero per-stock K-line fetches, updates directly from snapshot
            mode[0] = "post_close"
            clock_now[0] = datetime(2026, 11, 17, 15, 10, tzinfo=_SHANGHAI_TZ)
            kline_calls.clear()
            stats3 = adapter.sync_all_sparklines_once()
            self.assertEqual(stats3["incremental"], 5)
            self.assertEqual(stats3["backfilled"], 0)
            self.assertEqual([sym for sym, _ in kline_calls if sym != "sh000001"], [])
            bars_after_close = cache.load_daily_bars_map()["600519.SH"]
            self.assertIn(("2026-11-17", 1470.0, True), bars_after_close)

            # 4) Restart adapter from SQLite after 2 missing days: incremental fetch uses datalen = missing_days + 2 (4)
            mode[0] = "gap_2d"
            clock_now[0] = datetime(2026, 11, 19, 15, 20, tzinfo=_SHANGHAI_TZ)
            kline_calls.clear()
            reloaded_cache = SqliteAssetCatalogCache(db_path)
            reloaded_adapter = MarketAssetCatalogAdapter(
                _MockHttpClient(handler),
                cache_store=reloaded_cache,
                ttl_seconds=0.0,
                now_fn=lambda: clock_now[0],
            )
            stats4 = reloaded_adapter.sync_all_sparklines_once()
            self.assertEqual(stats4["backfilled"], 5)
            per_stock_lengths = [datalen for sym, datalen in kline_calls if sym != "sh000001"]
            self.assertTrue(all(length == 4 for length in per_stock_lengths), per_stock_lengths)
            self.assertEqual(reloaded_cache.load_sync_dates()["600519.SH"], "2026-11-19")

            # 5) Ex-dividend jump detected on 600519.SH -> immediately scaled in read path and replaced with qfqday in sync
            mode[0] = "ex_div"
            clock_now[0] = datetime(2026, 11, 20, 10, 30, tzinfo=_SHANGHAI_TZ)
            tencent_qfq_calls.clear()
            live_list = reloaded_adapter.list_assets(
                AssetCategory.STOCK,
                sort_field=AssetSortField.AMOUNT,
                descending=True,
                limit=10,
                offset=0,
            )
            moutai = next(item for item in live_list.items if item.stock.secid == "600519.SH")
            self.assertIn("sh600519", tencent_qfq_calls)
            self.assertEqual(moutai.sparkline[-2], 745.0)
            self.assertEqual(moutai.sparkline[-1], 750.0)
            persisted_moutai = reloaded_cache.load_daily_bars_map()["600519.SH"]
            self.assertEqual(persisted_moutai[0], ("2026-11-13", 717.63, True))


if __name__ == "__main__":
    unittest.main()
