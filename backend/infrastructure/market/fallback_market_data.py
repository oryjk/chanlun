from ...domain.errors import MarketDataUnavailable
from ...domain.models import MarketData, TimeFrame
from ...application.ports.readers import MarketDataReader


class FallbackMarketDataReader:
    def __init__(self, primary: MarketDataReader, fallback: MarketDataReader):
        self._primary = primary
        self._fallback = fallback

    def read(self, secid: str, timeframe: TimeFrame, force_refresh: bool = False) -> MarketData:
        try:
            return self._primary.read(secid, timeframe, force_refresh=force_refresh)
        except MarketDataUnavailable:
            return self._fallback.read(secid, timeframe, force_refresh=force_refresh)
