from dataclasses import replace
from datetime import date

from ...domain.errors import MarketDataNotFound
from ...domain.models import MarketData, MarketSnapshot, MarketSummary, TimeFrame, validate_security_id
from ...domain.services.chan_analyzer import ChanAnalyzer
from ...domain.services.chan_signals import ChanSignalAnalyzer
from ...domain.services.macd import compute_macd
from ...domain.services.structure_guidance import StructureGuidanceEvaluator
from ..ports.readers import MarketDataReader


class GetMarketSnapshotQuery:
    def __init__(
        self,
        reader: MarketDataReader,
        analyzer: ChanAnalyzer,
        guidance_evaluator: StructureGuidanceEvaluator,
        signal_analyzer: ChanSignalAnalyzer,
    ):
        self._reader = reader
        self._analyzer = analyzer
        self._guidance_evaluator = guidance_evaluator
        self._signal_analyzer = signal_analyzer

    def execute(self, secid: str, timeframe: str, refresh: bool = False, *, through_date: date | None = None) -> MarketSnapshot:
        normalized_secid = validate_security_id(secid)
        period = TimeFrame.parse(timeframe)
        market_data = self._reader.read(normalized_secid, period, force_refresh=refresh)
        if through_date is not None:
            candles = tuple(candle for candle in market_data.candles if candle.time.date() <= through_date)
            if len(candles) < 2 or candles[-1].time.date() != through_date:
                raise MarketDataNotFound("该证券的有效收盘行情不足，无法进行分析")
            market_data = replace(market_data, candles=candles)
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
        macd = compute_macd(candles)
        return MarketSnapshot(
            instrument=market_data.instrument,
            timeframe=timeframe,
            timeframe_label=timeframe.label,
            candles=candles,
            analysis=analysis,
            summary=summary,
            guidance=guidance,
            macd=macd,
            signals=self._signal_analyzer.analyze(analysis, macd),
        )
