import time
from collections import OrderedDict
from threading import Lock

from ...application.ports.readers import MarketDataReader
from ...domain.models import MarketData, TimeFrame


class CachedMarketDataReader:
    def __init__(self, reader: MarketDataReader, ttl_seconds: int = 300, max_size: int = 128):
        self._reader = reader
        self._ttl = ttl_seconds
        self._max_size = max_size
        self._items: OrderedDict[tuple[str, TimeFrame], tuple[float, MarketData]] = OrderedDict()
        self._lock = Lock()

    def read(self, secid: str, timeframe: TimeFrame, force_refresh: bool = False) -> MarketData:
        key = (secid, timeframe)
        now = time.monotonic()
        if not force_refresh:
            with self._lock:
                cached = self._items.get(key)
                if cached and now - cached[0] < self._ttl:
                    self._items.move_to_end(key)
                    return cached[1]

        data = self._reader.read(secid, timeframe, force_refresh=force_refresh)
        with self._lock:
            self._items[key] = (now, data)
            self._items.move_to_end(key)
            if len(self._items) > self._max_size:
                self._items.popitem(last=False)
        return data
