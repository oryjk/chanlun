from collections.abc import Callable
from concurrent.futures import ThreadPoolExecutor
from dataclasses import replace
from datetime import date, datetime, timedelta
import logging
from time import perf_counter

from ...domain.errors import MarketDataNotFound, MarketDataUnavailable
from ...domain.overnight import (
    BENCHMARK, EXIT_END, EXIT_START, HISTORY_SESSIONS, MARKET_TZ, SCAN_END, SCAN_START,
    BoardQuote, MinuteEvidence, MinuteSeries, OvernightCandidate, closed_minutes,
    completed_through, evaluate_minutes, exit_exception, is_mainboard, quote_age_seconds,
    screen_quotes, within_gain,
)
from ..overnight import ExitObservation, OvernightScan
from ..ports.overnight import (
    OvernightHistoryReader, OvernightHistoryWriter, OvernightMinutesReader,
    OvernightQuoteReader, OvernightSealsReader, OvernightStateReader, OvernightStateWriter,
    OvernightUniverseReader,
)


LOGGER = logging.getLogger(__name__)
_DATA_ERRORS = (MarketDataNotFound, MarketDataUnavailable)


class SyncOvernightHistoryCommand:
    def __init__(self, source: OvernightSealsReader, reader: OvernightHistoryReader, writer: OvernightHistoryWriter):
        self._source = source
        self._reader = reader
        self._writer = writer

    def execute(self, sessions: tuple[date, ...], stop_requested: Callable[[], bool] = lambda: False) -> None:
        stored = set(self._reader.read_seal_coverage(sessions))
        for session in sessions:
            if stop_requested():
                return
            if session in stored:
                continue
            try:
                result = self._source.read_seals(session)
            except _DATA_ERRORS:
                LOGGER.warning('隔夜套利缺少 %s 收盘封板数据，将重试', session)
                continue
            if result.complete and result.trade_date == session:
                self._writer.save_seals(result)
            else:
                LOGGER.warning('隔夜套利 %s 收盘封板数据未完成', session)


def _read_minutes(reader: OvernightMinutesReader, secids: tuple[str, ...]) -> dict[str, MinuteSeries]:
    if not secids:
        return {}

    def read(secid: str) -> MinuteSeries | None:
        try:
            result = reader.read_minutes(secid)
        except _DATA_ERRORS:
            return None
        return result if result.secid == secid else None

    with ThreadPoolExecutor(max_workers=min(6, len(secids))) as executor:
        return {series.secid: series for series in executor.map(read, secids) if series is not None}


def _retain_minutes(series: MinuteSeries, cutoff: datetime) -> MinuteSeries:
    return replace(series, bars=closed_minutes(series, cutoff))


class ScanOvernightCommand:
    def __init__(self, universe: OvernightUniverseReader, quotes: OvernightQuoteReader,
                 minutes: OvernightMinutesReader, history: OvernightHistoryReader,
                 state: OvernightStateWriter):
        self._universe = universe
        self._quotes = quotes
        self._minutes = minutes
        self._history = history
        self._state = state

    def execute(self, now: datetime) -> OvernightScan | None:
        now = now.astimezone(MARKET_TZ)
        if not SCAN_START <= now.time() < SCAN_END:
            return None
        started = perf_counter()
        trade_date = now.date()
        sessions = self._history.read_sessions(trade_date)
        if not sessions or sessions[-1] != trade_date:
            return None
        history = sessions[:-1][-HISTORY_SESSIONS:]
        history_sessions = len(self._history.read_seal_coverage(history))
        seals = self._history.read_recent_seals(history)
        scan = OvernightScan(trade_date, now, history_sessions)

        def timestamp() -> datetime:
            return now + timedelta(seconds=perf_counter() - started)

        try:
            universe = self._universe.read_universe()
        except _DATA_ERRORS:
            universe = ()
        universe_at = timestamp()
        if universe_at.time() >= SCAN_END:
            return None
        universe = tuple(quote for quote in universe if is_mainboard(quote.secid))
        scan = replace(scan, universe_count=len(universe))
        if universe:
            scan, series = self._screen(scan, universe, universe_at, seals, timestamp)
        else:
            scan, series = replace(scan, status='unavailable'), ()
        finished = timestamp()
        # A round that failed to finish during the window cannot publish a new observation.
        if finished.time() >= SCAN_END:
            LOGGER.info('隔夜套利尾盘刷新超出窗口 date=%s elapsed_ms=%d', trade_date, (perf_counter() - started) * 1000)
            return None
        scan = replace(scan, observed_at=finished, elapsed_ms=round((perf_counter() - started) * 1000))
        self._state.save_scan(trade_date, scan, series)
        LOGGER.info('隔夜套利尾盘刷新 date=%s elapsed_ms=%s max_quote_age_s=%s universe=%s fresh=%s matched=%s checked=%s status=%s',
                    trade_date, scan.elapsed_ms, scan.max_quote_age_seconds, scan.universe_count,
                    scan.fresh_count, scan.matched_count, scan.minute_checked_count, scan.status)
        return scan

    def _screen(self, scan: OvernightScan, universe: tuple[BoardQuote, ...], universe_at: datetime,
                seals: dict[str, date], timestamp: Callable[[], datetime]) -> tuple[OvernightScan, tuple[MinuteSeries, ...]]:
        first_pass = []
        fresh_times: list[datetime] = []
        for quote in universe:
            age = quote_age_seconds(quote.as_of, universe_at)
            if age is None:
                continue
            fresh_times.append(quote.as_of)
            if within_gain(quote) and quote.secid in seals:
                first_pass.append(quote.secid)

        ids = tuple(first_pass)
        try:
            detail = self._quotes.read_quotes(ids) if ids else {}
        except _DATA_ERRORS:
            detail = {}
        quotes_at = timestamp()
        detailed_times: list[datetime] = []
        selected: list[OvernightCandidate] = []
        for secid in ids:
            quote = detail.get(secid)
            if quote is None or quote.secid != secid:
                continue
            age = quote_age_seconds(quote.as_of, quotes_at)
            if (age is None or quote.volume_ratio is None or quote.turnover_rate is None
                    or quote.total_market_cap is None):
                continue
            detailed_times.append(quote.as_of)
            if screen_quotes(quote):
                selected.append(OvernightCandidate(
                    quote.secid, quote.symbol, quote.name, quote.price, quote.change_percent,
                    quote.volume_ratio, quote.turnover_rate, quote.total_market_cap,
                    seals[quote.secid], quote.as_of, 'unavailable',
                ))

        series = _read_minutes(self._minutes, (BENCHMARK, *(item.secid for item in selected))) if selected else {}
        benchmark = series.get(BENCHMARK)
        cutoff = completed_through(quotes_at)
        candidates = []
        evidence_at = timestamp()
        for candidate in selected:
            stock = series.get(candidate.secid)
            if quote_age_seconds(candidate.as_of, evidence_at) is None:
                evidence = MinuteEvidence('unavailable', '分时返回时筛选报价已过期')
            elif candidate.as_of < cutoff:
                evidence = MinuteEvidence('unavailable', '筛选报价早于需核对的分时分钟')
            elif stock and benchmark:
                evidence = evaluate_minutes(stock, benchmark, quotes_at)
            else:
                evidence = MinuteEvidence('unavailable', '未取得个股或沪深300分时')
            candidates.append(replace(
                candidate, status=evidence.status, reason=evidence.reason, average_price=evidence.average_price,
                benchmark_change_percent=evidence.benchmark_change_percent, pullback_at=evidence.pullback_at,
                minute_at=evidence.minute_at, minute_change_percent=evidence.minute_change_percent,
            ))

        checked = sum(item.status != 'unavailable' for item in candidates)
        status = 'ready'
        if not fresh_times:
            status = 'unavailable'
        elif (scan.history_sessions < HISTORY_SESSIONS or len(fresh_times) < scan.universe_count
              or len(detailed_times) < len(ids) or checked < len(candidates)):
            status = 'partial'
        oldest = min((*fresh_times, *detailed_times), default=None)
        return replace(
            scan, status=status, fresh_count=len(fresh_times), matched_count=len(selected), minute_checked_count=checked,
            max_quote_age_seconds=round(max(0, (evidence_at - oldest).total_seconds()), 2) if oldest else None,
            candidates=tuple(sorted(candidates, key=lambda item: (-item.change_percent, item.secid))),
        ), tuple(_retain_minutes(item, cutoff) for item in series.values())


class ObserveOvernightExitCommand:
    def __init__(self, quotes: OvernightQuoteReader, minutes: OvernightMinutesReader,
                 history: OvernightHistoryReader, state_reader: OvernightStateReader, state_writer: OvernightStateWriter):
        self._quotes = quotes
        self._minutes = minutes
        self._history = history
        self._state_reader = state_reader
        self._state_writer = state_writer

    def execute(self, now: datetime) -> None:
        now = now.astimezone(MARKET_TZ)
        if not EXIT_START <= now.time() < EXIT_END:
            return
        days = self._history.read_sessions(now.date(), limit=2)
        if len(days) != 2 or days[-1] != now.date():
            return
        previous, today = days
        ids = tuple(item.secid for item in self._state_reader.read_signals(previous))
        if not ids:
            return
        try:
            quotes = self._quotes.read_quotes(ids)
        except _DATA_ERRORS:
            quotes = {}
        cutoff = completed_through(now)
        valid = {key: item for key, item in quotes.items()
                 if key == item.secid and item.as_of >= cutoff and quote_age_seconds(item.as_of, now) is not None}
        at_limit = tuple(key for key, item in valid.items()
                         if item.limit_up and item.price == item.session_low == item.limit_up)
        series = _read_minutes(self._minutes, at_limit)
        observations = []
        for secid in ids:
            quote = valid.get(secid)
            observed = None
            if quote is not None and quote.limit_up and (quote.price != quote.limit_up
                    or (quote.session_low is not None and quote.session_low != quote.limit_up)):
                observed = False
            elif secid in series and quote is not None:
                observed = exit_exception(series[secid], self._history.read_minutes(previous, secid),
                                          quote.limit_up, now, session_low=quote.session_low)
            observations.append(ExitObservation(secid, cutoff, observed))
        self._state_writer.save_exit_observations(today, tuple(observations),
                                                  tuple(_retain_minutes(item, cutoff) for item in series.values()))


class ArchiveOvernightMinutesCommand:
    def __init__(self, minutes: OvernightMinutesReader, reader: OvernightHistoryReader, writer: OvernightHistoryWriter):
        self._minutes = minutes
        self._reader = reader
        self._writer = writer

    def execute(self, trade_date: date) -> None:
        ids = self._reader.read_unfinished_minutes(trade_date)
        series = _read_minutes(self._minutes, ids)
        cutoff = datetime.combine(trade_date, SCAN_END, MARKET_TZ)
        self._writer.save_minutes(trade_date, tuple(_retain_minutes(item, cutoff) for item in series.values()))
