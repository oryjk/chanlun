from ...domain.errors import MarketDataUnavailable
from ...domain.models import TimeFrame
from ...application.ports.readers import MarketDataReader


class IndexClosedTradingDayReader:
    def __init__(self, market_data: MarketDataReader):
        self._market_data = market_data

    def latest_on_or_before(self, cutoff):
        data = self._market_data.read("1.000001", TimeFrame.ONE_DAY)
        dates = [candle.time.date() for candle in data.candles if candle.time.date() <= cutoff]
        if not dates:
            raise MarketDataUnavailable("暂时无法确认最近收盘日期，请稍后重试")
        return max(dates)
