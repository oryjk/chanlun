import logging
import time
from threading import Lock

from ...domain.errors import MarketDataUnavailable
from ...domain.models import MarketData, RealtimeQuote, Stock, TimeFrame
from ...application.ports.readers import MarketDataReader, QuoteReader, StockUniverseReader
from .sina_quote import SinaQuoteReader


class _PrimaryCircuitBreaker:
    """Shared short-circuit breaker when the primary upstream source fails consecutively."""

    def __init__(self, *, failure_threshold: int = 3, cooldown_seconds: float = 300, clock=time.monotonic, label: str = "主数据源"):
        self._threshold = failure_threshold
        self._cooldown = cooldown_seconds
        self._clock = clock
        self._label = label
        self._failures = 0
        self._skip_until = 0.0
        self._lock = Lock()

    def suspended(self) -> bool:
        with self._lock:
            return self._clock() < self._skip_until

    def record_success(self) -> None:
        with self._lock:
            self._failures = 0

    def record_failure(self) -> None:
        with self._lock:
            self._failures += 1
            if self._failures >= self._threshold:
                self._skip_until = self._clock() + self._cooldown
                self._failures = self._threshold - 1  # 冷却到期后的试探再失败一次即重新冷却
                logging.getLogger(__name__).warning("%s连续不可用，%s 秒内直接使用备用源", self._label, self._cooldown)


class FallbackMarketDataReader:
    """主备切换。主源连续不可用达到阈值后，在冷却期内直接使用备用源，到期后再试探主源一次。"""

    def __init__(
        self,
        primary: MarketDataReader,
        fallback: MarketDataReader,
        *,
        failure_threshold: int = 3,
        cooldown_seconds: float = 300,
        clock=time.monotonic,
    ):
        self._primary = primary
        self._fallback = fallback
        self._breaker = _PrimaryCircuitBreaker(
            failure_threshold=failure_threshold,
            cooldown_seconds=cooldown_seconds,
            clock=clock,
            label="主行情源",
        )

    def read(self, secid: str, timeframe: TimeFrame, force_refresh: bool = False) -> MarketData:
        if self._breaker.suspended():
            return self._fallback.read(secid, timeframe, force_refresh=force_refresh)
        try:
            data = self._primary.read(secid, timeframe, force_refresh=force_refresh)
        except MarketDataUnavailable:
            self._breaker.record_failure()
            return self._fallback.read(secid, timeframe, force_refresh=force_refresh)
        self._breaker.record_success()
        return data


class FallbackQuoteReader:
    """实时报价主备切换，并支持基于新浪批量接口的高效多标的报价读取。"""

    def __init__(
        self,
        primary: QuoteReader,
        fallback: SinaQuoteReader,
        *,
        failure_threshold: int = 3,
        cooldown_seconds: float = 300,
        clock=time.monotonic,
    ):
        self._primary = primary
        self._fallback = fallback
        self._breaker = _PrimaryCircuitBreaker(
            failure_threshold=failure_threshold,
            cooldown_seconds=cooldown_seconds,
            clock=clock,
            label="主实时报价源",
        )

    def read_quote(self, secid: str) -> RealtimeQuote:
        if self._breaker.suspended():
            return self._fallback.read_quote(secid)
        try:
            quote = self._primary.read_quote(secid)
        except MarketDataUnavailable:
            self._breaker.record_failure()
            return self._fallback.read_quote(secid)
        self._breaker.record_success()
        return quote

    def read_quotes(self, secids: tuple[str, ...]) -> dict[str, RealtimeQuote]:
        return self._fallback.read_quotes(secids)


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
