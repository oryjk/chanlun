from ...domain.models import MarketData, MarketSnapshot, MarketSummary, TimeFrame, validate_security_id
from ...domain.services.chan_analyzer import ChanAnalyzer
from ...domain.services.structure_guidance import StructureGuidanceEvaluator
from ..ports.readers import MarketDataReader


class GetMarketSnapshotQuery:
    def __init__(
        self,
        reader: MarketDataReader,
        analyzer: ChanAnalyzer,
        guidance_evaluator: StructureGuidanceEvaluator,
    ):
        self._reader = reader
        self._analyzer = analyzer
        self._guidance_evaluator = guidance_evaluator

    def execute(self, secid: str, timeframe: str, refresh: bool = False) -> MarketSnapshot:
        normalized_secid = validate_security_id(secid)
        period = TimeFrame.parse(timeframe)
        market_data = self._reader.read(normalized_secid, period, force_refresh=refresh)
        return self._build_snapshot(market_data, period)

    def _build_snapshot(self, market_data: MarketData, timeframe: TimeFrame) -> MarketSnapshot:
        candles = market_data.candles
        latest = candles[-1]
        previous = candles[-2]
        change = latest.close - previous.close
        summary = MarketSummary(
            latest=latest.close,
            change=round(change, 4),
            change_percent=round(change / previous.close * 100, 2),
            high=max(candle.high for candle in candles),
            low=min(candle.low for candle in candles),
            volume=sum(candle.volume for candle in candles[-20:]),
        )
        analysis = self._analyzer.analyze(candles)
        guidance = self._guidance_evaluator.evaluate(analysis, latest.close)
        return MarketSnapshot(
            instrument=market_data.instrument,
            timeframe=timeframe,
            timeframe_label=timeframe.label,
            candles=candles,
            analysis=analysis,
            summary=summary,
            guidance=guidance,
        )
