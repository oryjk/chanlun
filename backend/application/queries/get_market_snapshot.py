from dataclasses import replace
from datetime import date, datetime, timezone

from ...domain.errors import MarketDataNotFound
from ...domain.models import MarketData, MarketSnapshot, MarketSummary, TimeFrame, validate_security_id
from ...domain.services.chan_analyzer import ChanAnalyzer
from ...domain.services.chan_signals import ChanSignalAnalyzer
from ...domain.services.macd import compute_macd
from ...domain.services.structure_guidance import StructureGuidanceEvaluator
from ...domain.services.signal_quality import SignalQualityEvaluator
from ..ports.readers import MarketDataReader


class GetMarketSnapshotQuery:
    def __init__(
        self,
        reader: MarketDataReader,
        analyzer: ChanAnalyzer,
        guidance_evaluator: StructureGuidanceEvaluator,
        signal_analyzer: ChanSignalAnalyzer,
        quality_evaluator: SignalQualityEvaluator | None = None,
    ):
        self._reader = reader
        self._analyzer = analyzer
        self._guidance_evaluator = guidance_evaluator
        self._signal_analyzer = signal_analyzer
        self._quality_evaluator = quality_evaluator

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
        previous_close = self._find_previous_close(candles)
        change = latest.close - previous_close
        summary = MarketSummary(
            latest=latest.close,
            change=round(change, 4),
            change_percent=round(change / previous_close * 100, 2) if previous_close else 0.0,
            high=max(candle.high for candle in candles),
            low=min(candle.low for candle in candles),
            volume=sum(candle.volume for candle in candles[-20:]),
        )
        analysis = self._analyzer.analyze(candles)
        guidance = self._guidance_evaluator.evaluate(analysis, latest.close)
        macd = compute_macd(candles)
        signals = self._signal_analyzer.analyze(analysis, macd)
        return MarketSnapshot(
            instrument=market_data.instrument,
            timeframe=timeframe,
            timeframe_label=timeframe.label,
            candles=candles,
            analysis=analysis,
            summary=summary,
            guidance=guidance,
            macd=macd,
            signals=signals,
            signal_quality=self._quality_evaluator.evaluate(
                candles, analysis, macd, signals, timeframe=timeframe, as_of=datetime.now(timezone.utc)
            ) if self._quality_evaluator else None,
        )

    @staticmethod
    def _find_previous_close(candles: tuple) -> float:
        latest_date = candles[-1].time.date()
        for candle in reversed(candles[:-1]):
            if candle.time.date() < latest_date:
                return candle.close
        return candles[-2].close
