import logging
import time
from threading import Lock

from ...domain.errors import MarketDataUnavailable
from ...domain.models import MarketData, Stock, TimeFrame
from ...application.ports.readers import MarketDataReader, StockUniverseReader


class FallbackMarketDataReader:
    """主备切换。主源连续不可用达到阈值后，在冷却期内直接使用备用源，到期后再试探主源一次。"""

    def __init__(self, primary: MarketDataReader, fallback: MarketDataReader, *,
                 failure_threshold: int = 3, cooldown_seconds: float = 300, clock=time.monotonic):
        self._primary = primary
        self._fallback = fallback
        self._threshold = failure_threshold
        self._cooldown = cooldown_seconds
        self._clock = clock
        self._failures = 0
        self._skip_until = 0.0
        self._lock = Lock()

    def read(self, secid: str, timeframe: TimeFrame, force_refresh: bool = False) -> MarketData:
        if self._primary_suspended():
            return self._fallback.read(secid, timeframe, force_refresh=force_refresh)
        try:
            data = self._primary.read(secid, timeframe, force_refresh=force_refresh)
        except MarketDataUnavailable:
            self._record_failure()
            return self._fallback.read(secid, timeframe, force_refresh=force_refresh)
        with self._lock:
            self._failures = 0
        return data

    def _primary_suspended(self) -> bool:
        with self._lock:
            return self._clock() < self._skip_until

    def _record_failure(self):
        with self._lock:
            self._failures += 1
            if self._failures >= self._threshold:
                self._skip_until = self._clock() + self._cooldown
                self._failures = self._threshold - 1  # 冷却到期后的试探再失败一次即重新冷却
                logging.getLogger(__name__).warning("主行情源连续不可用，%s 秒内直接使用备用源", self._cooldown)


class FallbackStockUniverseReader:
    def __init__(self, primary: StockUniverseReader, fallback: StockUniverseReader):
        self._primary = primary
        self._fallback = fallback

    def list_all(self, *, cancelled=lambda: False) -> tuple[Stock, ...]:
        try:
            return self._primary.list_all(cancelled=cancelled)
        except MarketDataUnavailable:
            logging.getLogger(__name__).warning("主证券清单不可用，改用备用清单")
            return self._fallback.list_all(cancelled=cancelled)
