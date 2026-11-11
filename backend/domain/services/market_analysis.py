from dataclasses import replace
from datetime import date, datetime

from ..errors import MarketDataNotFound
from ..models import Candle, MarketData, MarketSnapshot, MarketSummary, TimeFrame
from .chan_analyzer import ChanAnalyzer
from .chan_signals import ChanSignalAnalyzer
from .macd import compute_macd
from .signal_quality import SignalQualityEvaluator
from .structure_guidance import StructureGuidanceEvaluator


def close_window(market_data: MarketData, through_date: date) -> MarketData:
    candles = tuple(candle for candle in market_data.candles if candle.time.date() <= through_date)
    if len(candles) < 2 or candles[-1].time.date() != through_date:
        raise MarketDataNotFound("该证券的有效收盘行情不足，无法进行分析")
    return replace(market_data, candles=candles)


class MarketAnalysisPipeline:
    def __init__(
        self,
        analyzer: ChanAnalyzer,
        guidance: StructureGuidanceEvaluator,
        signals: ChanSignalAnalyzer,
        quality: SignalQualityEvaluator,
    ):
        self._analyzer = analyzer
        self._guidance = guidance
        self._signals = signals
        self._quality = quality

    def analyze(self, market_data: MarketData, timeframe: TimeFrame, *, as_of: datetime) -> MarketSnapshot:
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
        guidance = self._guidance.evaluate(analysis, latest.close)
        macd = compute_macd(candles)
        signals = self._signals.analyze(analysis, macd)
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
            signal_quality=self._quality.evaluate(
                candles, analysis, macd, signals, timeframe=timeframe, as_of=as_of
            ),
        )

    @staticmethod
    def _find_previous_close(candles: tuple[Candle, ...]) -> float:
        latest_date = candles[-1].time.date()
        for candle in reversed(candles[:-1]):
            if candle.time.date() < latest_date:
                return candle.close
        return candles[-2].close
