from ...domain.models import MarketSnapshot, TimeFrame
from ...domain.services.market_analysis import MarketAnalysisPipeline
from ..ports.clock import Clock
from ..ports.readers import MarketDataReader
from ..security_ids import normalize_security_id


class GetMarketSnapshotQuery:
    def __init__(self, reader: MarketDataReader, pipeline: MarketAnalysisPipeline, clock: Clock):
        self._reader = reader
        self._pipeline = pipeline
        self._clock = clock

    def execute(self, secid: str, timeframe: str, refresh: bool = False) -> MarketSnapshot:
        normalized_secid = normalize_security_id(secid)
        period = TimeFrame.parse(timeframe)
        market_data = self._reader.read(normalized_secid, period, force_refresh=refresh)
        return self._pipeline.analyze(market_data, period, as_of=self._clock.now())
