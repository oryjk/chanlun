from datetime import datetime, timezone
from urllib.parse import quote

from ...domain.errors import MarketDataNotFound
from ...domain.models import Candle, Instrument, MarketData, TimeFrame
from ..http.json_client import JsonHttpClient


class YahooMarketDataReader:
    URL = "https://query1.finance.yahoo.com/v8/finance/chart"
    BAR_COUNT = 240
    PERIODS = {
        TimeFrame.FIVE_MINUTES: ("5m", "5d"),
        TimeFrame.THIRTY_MINUTES: ("30m", "1mo"),
        TimeFrame.ONE_HOUR: ("60m", "3mo"),
        TimeFrame.ONE_DAY: ("1d", "2y"),
    }

    def __init__(self, http_client: JsonHttpClient):
        self._http = http_client

    def read(self, secid: str, timeframe: TimeFrame, force_refresh: bool = False) -> MarketData:
        symbol = self._yahoo_symbol(secid)
        interval, date_range = self.PERIODS[timeframe]
        payload = self._http.get(
            f"{self.URL}/{quote(symbol)}",
            {
                "interval": interval,
                "range": date_range,
                "includePrePost": "false",
                "events": "div,splits",
            },
        )
        results = payload.get("chart", {}).get("result") or []
        if not results:
            raise MarketDataNotFound("未找到该证券的行情数据")
        return self._to_market_data(secid, symbol, results[0])

    def _to_market_data(self, secid: str, symbol: str, data: dict) -> MarketData:
        timestamps = data.get("timestamp") or []
        quotes = (data.get("indicators", {}).get("quote") or [{}])[0]
        candles = []
        for index, timestamp in enumerate(timestamps):
            try:
                values = (
                    quotes["open"][index],
                    quotes["close"][index],
                    quotes["high"][index],
                    quotes["low"][index],
                    quotes["volume"][index],
                )
                if any(value is None for value in values):
                    continue
                open_price, close, high, low, volume = values
                candles.append(
                    Candle(
                        time=datetime.fromtimestamp(timestamp, timezone.utc),
                        open=round(open_price, 4),
                        close=round(close, 4),
                        high=round(high, 4),
                        low=round(low, 4),
                        volume=round(volume),
                    )
                )
            except (KeyError, IndexError, TypeError):
                continue

        candles = candles[-self.BAR_COUNT :]
        if len(candles) < 2:
            raise MarketDataNotFound("该证券的有效行情不足，无法进行分析")

        meta = data.get("meta", {})
        instrument = Instrument(
            secid=secid,
            symbol=meta.get("symbol") or symbol,
            name=meta.get("longName") or meta.get("shortName") or symbol,
            decimal=meta.get("priceHint") or 2,
            currency=meta.get("currency"),
            exchange=meta.get("fullExchangeName") or meta.get("exchangeName"),
            data_source="Yahoo Finance · 公开行情",
        )
        return MarketData(instrument=instrument, candles=tuple(candles))

    def _yahoo_symbol(self, secid: str) -> str:
        market, code = secid.split(".", 1)
        if market == "1":
            return f"{code}.SS"
        if market == "0":
            suffix = "BJ" if code.startswith(("4", "8", "9")) else "SZ"
            return f"{code}.{suffix}"
        if market == "116":
            return f"{code[-4:]}.HK"
        return code
