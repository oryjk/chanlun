from datetime import date, datetime
import json
import unittest
from zoneinfo import ZoneInfo

from backend.domain.errors import MarketDataUnavailable
from backend.infrastructure.market.overnight import (
    EastmoneyOvernightMinuteReader,
    SinaOvernightUniverseReader,
    TencentOvernightQuoteReader,
    TonghuashunOvernightSealReader,
)

SHANGHAI = ZoneInfo("Asia/Shanghai")
TRADE_DATE = date(2026, 11, 19)


class SinaResponse:
    def __init__(self, shanghai_rows=None, shanghai_count=2):
        self.rows = {
            "sh_a": shanghai_rows if shanghai_rows is not None else [
                self.row("sh600519", "贵州茅台", "1460.21", "1442.66", "1.217"),
                self.row("sh688001", "华兴源创", "28.00", "28.00", "0"),
            ],
            "sz_a": [self.row("sz002717", "*ST岭南", "0", "0.45", "0")],
        }
        self.count = {"sh_a": shanghai_count, "sz_a": 1}

    def get_text(self, url, params=None, **kwargs):
        if url.endswith("getHQNodeStockCount"):
            return str(self.count[params["node"]])
        if url.endswith("getHQNodeData"):
            start = (params["page"] - 1) * params["num"]
            return json.dumps(self.rows[params["node"]][start:start + params["num"]])
        return 'var hq_str_sh000001="上证指数,3583.12,3587.83,3577.62,3584.28,3547.94,' + (
            '0,0,412912755,636909057689,' + '0,' * 20 + '2026-11-19,15:35:57,00";'
        )

    @staticmethod
    def row(symbol, name, price, previous, change):
        return {
            "symbol": symbol, "code": symbol[2:], "name": name, "trade": price,
            "settlement": previous, "changepercent": change, "ticktime": "15:35:30",
            "volume": 0 if price == "0" else 25000,
        }


class TencentResponse:
    @staticmethod
    def get_text(url, params, **kwargs):
        fields = [""] * 50
        for index, value in {
            0: "51", 1: "平安银行", 2: "000001", 3: "12.04", 4: "12.20",
            30: "20261119161500", 32: "-1.31", 38: "0.80", 45: "2336.47",
            47: "13.42", 49: "0.91",
        }.items():
            fields[index] = value
        return 'v_sz000001="' + "~".join(fields) + '";\nv_sh600519="";'


class EastmoneyResponse:
    @staticmethod
    def get_text(url, params, **kwargs):
        return json.dumps({
            "rc": 0, "full": 1, "data": {
                "code": "000001", "market": 0, "preClose": 12.20,
                "trendsTotal": 241,
                "trends": [
                    "2026-11-19 09:30,12.20,12.20,12.20,12.20,10980,13395600.00,12.200",
                    "2026-11-19 09:31,12.24,12.19,12.25,12.18,340,415000.00,12.201",
                ],
            },
        })


class TonghuashunResponse:
    def __init__(self, *, duplicate=False, stale=False, session_status="closed"):
        self.duplicate = duplicate
        self.stale = stale
        self.session_status = session_status

    def get_text(self, url, params, **kwargs):
        page = params["page"]
        rows = [
            {"market_id": 37, "code": "002731", "name": "萃华退", "latest": 7.33},
            {"market_id": 22, "code": "600745", "name": "*ST闻泰", "latest": 44.18},
        ]
        if params["filter"] != "HS,ST":
            rows[0] = {"market_id": 33, "code": "000656", "name": "金科股份", "latest": 1.02}
        row = rows[0 if self.duplicate else page - 1]
        return json.dumps({
            "status_code": 0, "data": {
                "date": "20261118" if self.stale else "20261119",
                "page": {"limit": 1, "total": 2, "count": 2, "page": page},
                "trade_status": {"id": self.session_status},
                "info": [row],
            },
        })


class OvernightMarketDataTest(unittest.TestCase):
    def test_universe_keeps_only_main_board_and_counts_suspended_st_as_unchanged(self):
        reader = SinaOvernightUniverseReader(SinaResponse())
        reader.PAGE_SIZE = 1
        rows = reader.read_universe()
        self.assertEqual([row.secid for row in rows], ["600519.SH", "002717.SZ"])
        self.assertEqual(rows[1].price, 0.45)
        self.assertEqual(rows[1].change_percent, 0.0)
        self.assertEqual(rows[1].as_of, datetime(2026, 11, 19, 15, 35, 30, tzinfo=SHANGHAI))

    def test_universe_rejects_incomplete_node(self):
        with self.assertRaises(MarketDataUnavailable):
            SinaOvernightUniverseReader(SinaResponse(shanghai_count=3)).read_universe()

    def test_universe_rejects_duplicated_page_even_when_total_row_count_matches(self):
        repeated = SinaResponse.row("sh600519", "贵州茅台", "1460.21", "1442.66", "1.217")
        reader = SinaOvernightUniverseReader(SinaResponse(shanghai_rows=[repeated, repeated]))
        reader.PAGE_SIZE = 1
        with self.assertRaises(MarketDataUnavailable):
            reader.read_universe()

    def test_screening_quote_converts_chinese_market_cap_units(self):
        results = TencentOvernightQuoteReader(TencentResponse()).read_quotes(
            ("000001.SZ", "600519.SH")
        )
        self.assertEqual(list(results), ["000001.SZ"])
        quote = results["000001.SZ"]
        self.assertEqual(quote.total_market_cap, 233_647_000_000)
        self.assertEqual((quote.volume_ratio, quote.turnover_rate, quote.limit_up), (0.91, 0.8, 13.42))
        self.assertEqual(quote.as_of, datetime(2026, 11, 19, 16, 15, tzinfo=SHANGHAI))

    def test_screening_quotes_keep_healthy_results_when_one_batch_fails(self):
        class OneFailedBatch(TencentResponse):
            def get_text(self, url, params, **kwargs):
                if "sz000001" not in url:
                    raise MarketDataUnavailable("暂未返回报价")
                return super().get_text(url, params, **kwargs)

        reader = TencentOvernightQuoteReader(OneFailedBatch())
        reader.BATCH_SIZE = 1
        quotes = reader.read_quotes(("000001.SZ", "600519.SH"))
        self.assertEqual(tuple(quotes), ("000001.SZ",))

    def test_enriched_minute_series_converts_volume_to_shares_and_uses_close(self):
        series = EastmoneyOvernightMinuteReader(EastmoneyResponse()).read_minutes("000001.SZ")
        self.assertEqual(series.previous_close, 12.20)
        bar = series.bars[1]
        self.assertEqual((bar.close, bar.low, bar.average, bar.volume), (12.19, 12.18, 12.201, 34_000))
        self.assertEqual(bar.at, datetime(2026, 11, 19, 9, 31, tzinfo=SHANGHAI))

    def test_seals_follow_source_pagination_and_keep_st(self):
        result = TonghuashunOvernightSealReader(TonghuashunResponse(), today=lambda: TRADE_DATE).read_seals(TRADE_DATE)
        self.assertTrue(result.complete)
        self.assertEqual([(seal.secid, seal.close, seal.limit_up) for seal in result.seals], [
            ("002731.SZ", 7.33, 7.33), ("600745.SH", 44.18, 44.18),
        ])

    def test_duplicate_wrong_date_and_intraday_seal_feeds_are_incomplete(self):
        for response in (
            TonghuashunResponse(duplicate=True), TonghuashunResponse(stale=True),
            TonghuashunResponse(session_status="trading"),
        ):
            with self.subTest(response=response):
                reader = TonghuashunOvernightSealReader(response, today=lambda: TRADE_DATE)
                self.assertFalse(reader.read_seals(TRADE_DATE).complete)
