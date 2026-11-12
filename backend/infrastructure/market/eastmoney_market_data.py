from datetime import datetime
from zoneinfo import ZoneInfo

from ...domain.errors import MarketDataNotFound
from ...domain.models import Candle, Instrument, MarketData, TimeFrame
from ...domain.security import SecurityId
from ..http.json_client import JsonHttpClient
from .eastmoney_codes import to_eastmoney

SHANGHAI_TZ = ZoneInfo("Asia/Shanghai")


class EastmoneyMarketDataReader:
    URL = "http://push2his.eastmoney.com/api/qt/stock/kline/get"
    BAR_COUNT = 240
    PERIODS = {
        TimeFrame.FIVE_MINUTES: 5,
        TimeFrame.THIRTY_MINUTES: 30,
        TimeFrame.ONE_HOUR: 60,
        TimeFrame.ONE_DAY: 101,
    }

    def __init__(self, http_client: JsonHttpClient):
        self._http = http_client

    def read(self, secid: str, timeframe: TimeFrame, force_refresh: bool = False) -> MarketData:
        payload = self._http.get(
            self.URL,
            {
                "secid": to_eastmoney(secid),
                "klt": self.PERIODS[timeframe],
                "fqt": 1,
                "lmt": self.BAR_COUNT,
                "end": "20500101",
                "fields1": "f1,f2,f3,f4,f5,f6",
                "fields2": "f51,f52,f53,f54,f55,f56",
            },
        )
        data = payload.get("data")
        if not data:
            raise MarketDataNotFound("未找到该证券的行情数据")
        return self._to_market_data(secid, data)

    def _to_market_data(self, secid: str, data: dict) -> MarketData:
        sid = SecurityId.parse(secid)
        canonical_secid = str(sid)
        candles = []
        for kline in data.get("klines") or []:
            candle = self._to_candle(kline, SHANGHAI_TZ)
            if candle is not None:
                candles.append(candle)

        candles = candles[-self.BAR_COUNT :]
        if len(candles) < 2:
            raise MarketDataNotFound("该证券的有效行情不足，无法进行分析")

        instrument = Instrument(
            secid=canonical_secid,
            symbol=data.get("code") or sid.code,
            name=data.get("name") or data.get("code") or canonical_secid,
            decimal=data.get("decimal") or 2,
            currency="CNY",
            exchange=None,
            data_source="东方财富 · 公开行情",
        )
        return MarketData(instrument=instrument, candles=tuple(candles))

    def _to_candle(self, kline: str, timezone: ZoneInfo) -> Candle | None:
        try:
            values = kline.split(",")
            time_value = values[0]
            time_format = "%Y-%m-%d" if len(time_value) == 10 else "%Y-%m-%d %H:%M"
            return Candle(
                time=datetime.strptime(time_value, time_format).replace(tzinfo=timezone),
                open=round(float(values[1]), 4),
                close=round(float(values[2]), 4),
                high=round(float(values[3]), 4),
                low=round(float(values[4]), 4),
                volume=round(float(values[5])),
            )
        except (AttributeError, IndexError, TypeError, ValueError):
            return None
