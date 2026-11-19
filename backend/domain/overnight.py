from dataclasses import dataclass, replace
from datetime import date, datetime, time, timedelta
from typing import Literal
from zoneinfo import ZoneInfo


MARKET_TZ = ZoneInfo('Asia/Shanghai')
BENCHMARK = '000300.SH'
HISTORY_SESSIONS = 20
SCAN_START = time(14, 30)
SCAN_END = time(15, 0)
EXIT_START = time(9, 30)
EXIT_END = time(11, 30)
QUOTE_MAX_AGE_SECONDS = 90
_MINUTE_PUBLICATION_DELAY = timedelta(seconds=5)
_PRICE_TICK = 0.01
_EPSILON = 1e-8

Phase = Literal['waiting', 'preparing', 'scanning', 'closed', 'exit']
CandidateStatus = Literal['watching', 'pullback', 'filtered', 'unavailable']


@dataclass(frozen=True)
class BoardQuote:
    secid: str
    symbol: str
    name: str
    price: float
    previous_close: float
    change_percent: float
    as_of: datetime


@dataclass(frozen=True)
class ScreeningQuote:
    secid: str
    symbol: str
    name: str
    price: float
    previous_close: float
    change_percent: float
    volume_ratio: float | None
    turnover_rate: float | None
    total_market_cap: float | None
    limit_up: float | None
    as_of: datetime
    session_low: float | None = None


@dataclass(frozen=True)
class MinuteBar:
    at: datetime
    close: float
    low: float
    average: float
    volume: int


@dataclass(frozen=True)
class MinuteSeries:
    secid: str
    previous_close: float
    bars: tuple[MinuteBar, ...]


@dataclass(frozen=True)
class DailySeal:
    secid: str
    close: float
    limit_up: float


@dataclass(frozen=True)
class HistoricalSeals:
    trade_date: date
    seals: tuple[DailySeal, ...]
    complete: bool


@dataclass(frozen=True)
class MinuteEvidence:
    status: CandidateStatus
    reason: str | None = None
    average_price: float | None = None
    benchmark_change_percent: float | None = None
    pullback_at: datetime | None = None
    minute_at: datetime | None = None
    minute_change_percent: float | None = None


@dataclass(frozen=True)
class OvernightCandidate:
    secid: str
    symbol: str
    name: str
    price: float
    change_percent: float
    volume_ratio: float
    turnover_rate: float
    total_market_cap: float
    limit_up_date: date
    as_of: datetime
    status: CandidateStatus
    reason: str | None = None
    average_price: float | None = None
    benchmark_change_percent: float | None = None
    pullback_at: datetime | None = None
    minute_at: datetime | None = None
    minute_change_percent: float | None = None


def is_mainboard(secid: str) -> bool:
    code, separator, exchange = secid.partition('.')
    return separator == '.' and len(code) == 6 and code.isascii() and code.isdigit() and (
        (exchange == 'SH' and code.startswith(('600', '601', '603', '605')))
        or (exchange == 'SZ' and code.startswith(('000', '001', '002', '003')))
    )


def within_gain(quote: BoardQuote | ScreeningQuote) -> bool:
    if quote.previous_close <= 0 or quote.price <= 0:
        return False
    return _in_gain_band((quote.price / quote.previous_close - 1) * 100)


def _in_gain_band(percent: float) -> bool:
    return 3 - _EPSILON <= percent <= 5 + _EPSILON


def screen_quotes(quote: ScreeningQuote) -> bool:
    return (
        is_mainboard(quote.secid)
        and within_gain(quote)
        and quote.volume_ratio is not None and quote.volume_ratio >= 1
        and quote.turnover_rate is not None and 5 <= quote.turnover_rate <= 10
        and quote.total_market_cap is not None and 50e8 <= quote.total_market_cap <= 200e8
    )


def quote_age_seconds(at: datetime, now: datetime) -> float | None:
    if at.astimezone(MARKET_TZ).date() != now.astimezone(MARKET_TZ).date():
        return None
    age = (now - at).total_seconds()
    return max(age, 0) if -5 <= age <= QUOTE_MAX_AGE_SECONDS else None


def is_session_minute(at: datetime) -> bool:
    return time(9, 30) <= at.time() <= time(11, 30) or time(13, 1) <= at.time() <= SCAN_END


def closed_minutes(series: MinuteSeries, cutoff: datetime) -> tuple[MinuteBar, ...]:
    return tuple(
        bar for bar in series.bars
        if bar.at.date() == cutoff.date() and bar.at <= cutoff and is_session_minute(bar.at)
    )


def _complete_minutes(series: MinuteSeries, cutoff: datetime) -> tuple[MinuteBar, ...]:
    bars = closed_minutes(series, cutoff)
    at = datetime.combine(cutoff.date(), time(9, 30), MARKET_TZ)
    expected = []
    while at <= cutoff:
        if is_session_minute(at):
            expected.append(at)
        at += timedelta(minutes=1)
    if tuple(expected) != tuple(bar.at for bar in bars):
        return ()
    return bars


def completed_through(now: datetime) -> datetime:
    return (now.astimezone(MARKET_TZ) - _MINUTE_PUBLICATION_DELAY).replace(second=0, microsecond=0)


def evaluate_minutes(stock: MinuteSeries, index: MinuteSeries, now: datetime) -> MinuteEvidence:
    cutoff = completed_through(now)
    bars = _complete_minutes(stock, cutoff)
    if len(bars) < 2 or stock.previous_close <= 0 or index.previous_close <= 0:
        return MinuteEvidence('unavailable', '个股分时缺少已结束分钟')
    last = bars[-1]
    reference = next((bar for bar in index.bars if bar.at == last.at), None)
    if reference is None:
        return MinuteEvidence('unavailable', '沪深300缺少同一分钟行情')
    benchmark = (reference.close / index.previous_close - 1) * 100
    gain = (last.close / stock.previous_close - 1) * 100
    evidence = MinuteEvidence('watching', average_price=last.average, benchmark_change_percent=benchmark,
                              minute_at=last.at, minute_change_percent=gain)
    if not _in_gain_band(gain):
        return replace(evidence, status='filtered', reason='核对分钟的个股涨幅已超出 3%–5%')
    if any(bar.close < bar.average - _EPSILON for bar in bars):
        return replace(evidence, status='filtered', reason='盘中有分钟收盘价低于分时均价')
    if gain <= benchmark + _EPSILON:
        return replace(evidence, status='filtered', reason='同一分钟涨幅未超过沪深300')

    prior = bars[-2]
    distance = last.low - last.average
    if (SCAN_START <= last.at.time() < SCAN_END and last.volume > 0
            and prior.close > prior.average and last.low < prior.close
            and -_EPSILON <= distance <= _PRICE_TICK + _EPSILON
            and last.close > last.average):
        return replace(evidence, status='pullback', reason='分钟低价触及均价附近且未跌破，收在均价上方', pullback_at=last.at)
    return replace(evidence, reason='等待回踩分时均价后收回')


def exit_exception(today: MinuteSeries, previous: MinuteSeries | None,
                   limit_up: float | None, now: datetime, *, session_low: float | None) -> bool | None:
    if limit_up is None or limit_up <= 0 or session_low is None or session_low <= 0:
        return None
    if abs(session_low - limit_up) > _EPSILON:
        return False
    if previous is None or not previous.bars:
        return None
    current_bars = _complete_minutes(today, completed_through(now))
    if len(current_bars) < 2:
        return None
    if any(abs(bar.close - limit_up) > _EPSILON or abs(bar.low - limit_up) > _EPSILON for bar in current_bars):
        return False
    cutoff = datetime.combine(previous.bars[0].at.date(), current_bars[-1].at.time(), MARKET_TZ)
    previous_bars = _complete_minutes(previous, cutoff)
    prior_volume = sum(bar.volume for bar in previous_bars)
    today_volume = sum(bar.volume for bar in current_bars)
    if prior_volume <= 0 or today_volume <= 0:
        return None
    return today_volume < prior_volume
