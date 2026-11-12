import json
from datetime import datetime
from zoneinfo import ZoneInfo

from ...application.ports.readers import StockSearchReader
from ...domain.errors import ChanlunError, MarketDataNotFound, MarketDataUnavailable
from ...domain.models import Candle, Instrument, MarketData, TimeFrame
from ...domain.security import SecurityId
from ..http.json_client import JsonHttpClient
from .sina_adjustment import SinaForwardAdjustment


class SinaMarketDataReader:
    URL = "https://quotes.sina.cn/cn/api/jsonp_v2.php/var%20_data=/CN_MarketDataService.getKLineData"
    BAR_COUNT = 240
    PERIODS = {
        TimeFrame.FIVE_MINUTES: 5,
        TimeFrame.THIRTY_MINUTES: 30,
        TimeFrame.ONE_HOUR: 60,
        TimeFrame.ONE_DAY: 240,
    }

    def __init__(self, http_client: JsonHttpClient, stock_search: StockSearchReader | None = None,
                 adjustment: SinaForwardAdjustment | None = None):
        self._http = http_client
        self._stock_search = stock_search
        # 提供复权因子时输出前复权价格，与东方财富主数据源（fqt=1）口径一致；未提供时为不复权价格。
        self._adjustment = adjustment

    def read(self, secid: str, timeframe: TimeFrame, force_refresh: bool = False) -> MarketData:
        sid = SecurityId.parse(secid)
        canonical_secid = str(sid)
        symbol = self._sina_symbol(canonical_secid)
        text = self._http.get_text(
            self.URL,
            {"symbol": symbol, "scale": self.PERIODS[timeframe], "ma": "no", "datalen": self.BAR_COUNT},
        )
        start = text.find("([")
        end = text.rfind(")")
        if start < 0 or end <= start:
            raise MarketDataUnavailable("行情服务返回格式异常")
        try:
            items = json.loads(text[start + 1 : end])
            candles = tuple(self._to_candle(item) for item in items[-self.BAR_COUNT :])
        except (json.JSONDecodeError, KeyError, TypeError, ValueError) as error:
            raise MarketDataUnavailable("行情服务返回格式异常") from error
        if len(candles) < 2:
            raise MarketDataNotFound("该证券的有效行情不足，无法进行分析")
        if self._adjustment is not None:
            candles = self._forward_adjust(candles, self._adjustment.factor_lookup(symbol))
        code = sid.code
        name, is_etf = self._describe(canonical_secid, code)
        instrument = Instrument(
            secid=canonical_secid,
            symbol=code,
            name=name,
            decimal=3 if is_etf else 2,
            currency="CNY",
            exchange=sid.exchange.value,
            data_source="新浪财经 · 前复权（备用）" if self._adjustment else "新浪财经 · 不复权（备用）",
        )
        return MarketData(instrument=instrument, candles=candles)

    def _describe(self, secid: str, code: str) -> tuple[str, bool]:
        is_etf = code.startswith(("5", "15", "16"))
        if self._stock_search is None:
            return code, is_etf
        try:
            match = next((stock for stock in self._stock_search.search(code) if stock.secid == secid), None)
        except ChanlunError:
            return code, is_etf
        if match is None:
            return code, is_etf
        return match.name, is_etf or match.market == "ETF"

    def _sina_symbol(self, secid: str) -> str:
        sid = SecurityId.parse(secid)
        return f"{sid.exchange.value.lower()}{sid.code}"

    def _to_candle(self, item: dict) -> Candle:
        time_value = item["day"]
        time_format = "%Y-%m-%d" if len(time_value) == 10 else "%Y-%m-%d %H:%M:%S"
        return Candle(
            time=datetime.strptime(time_value, time_format).replace(tzinfo=ZoneInfo("Asia/Shanghai")),
            open=round(float(item["open"]), 4),
            close=round(float(item["close"]), 4),
            high=round(float(item["high"]), 4),
            low=round(float(item["low"]), 4),
            volume=round(float(item["volume"])),
        )

    @staticmethod
    def _forward_adjust(candles, factor):
        adjusted = []
        for candle in candles:
            f = factor(candle.time.date())
            adjusted.append(Candle(
                time=candle.time,
                open=round(candle.open / f, 4),
                close=round(candle.close / f, 4),
                high=round(candle.high / f, 4),
                low=round(candle.low / f, 4),
                volume=candle.volume,
            ))
        return tuple(adjusted)
