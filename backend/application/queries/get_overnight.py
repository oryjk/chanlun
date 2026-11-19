from dataclasses import fields, replace
from datetime import date, datetime, timedelta

from ...domain.overnight import EXIT_END, EXIT_START, HISTORY_SESSIONS, MARKET_TZ, SCAN_END, SCAN_START, OvernightCandidate
from ..overnight import ExitObservation, OvernightReminder, OvernightScan, OvernightView
from ..ports.clock import Clock
from ..ports.overnight import OvernightHistoryReader, OvernightStateReader


_EXIT_EVIDENCE_LIFETIME = timedelta(minutes=2)


def _to_reminder(item: OvernightCandidate, signal_date: date, signal_at: datetime,
                 observation: ExitObservation | None, now: datetime) -> OvernightReminder:
    reminder = OvernightReminder(item.secid, item.symbol, item.name, signal_date, signal_at)
    if observation is None or observation.exception_observed is None:
        return reminder
    until = observation.observed_at + _EXIT_EVIDENCE_LIFETIME
    if not observation.observed_at <= now < until:
        return reminder
    return replace(reminder, exception_observed=observation.exception_observed,
                   exception_as_of=observation.observed_at, exception_valid_until=until)


class GetOvernightQuery:
    def __init__(self, history: OvernightHistoryReader, state: OvernightStateReader, clock: Clock):
        self._history = history
        self._state = state
        self._clock = clock

    def execute(self) -> OvernightView:
        now = self._clock.now().astimezone(MARKET_TZ)
        today = now.date()
        days = self._history.read_sessions(today)
        is_session = bool(days and days[-1] == today)
        previous = (days[:-1] if is_session else days)[-HISTORY_SESSIONS:]
        history_count = len(self._history.read_seal_coverage(previous))
        active_window = is_session and now.time() >= SCAN_START
        scan = self._state.read_scan(today) if active_window else self._state.read_scan()
        scan = scan or OvernightScan(trade_date=today if active_window else None, history_sessions=history_count)
        phase = 'waiting'
        reminders = ()
        reminders_until = None
        if is_session and EXIT_START <= now.time() < EXIT_END:
            phase = 'exit'
            reminders_until = datetime.combine(today, EXIT_END, MARKET_TZ)
            if previous:
                observations = self._state.read_exit_observations(today)
                reminders = tuple(
                    _to_reminder(item, previous[-1], item.pullback_at, observations.get(item.secid), now)
                    for item in self._state.read_signals(previous[-1]) if item.pullback_at is not None
                )
        elif active_window:
            phase = 'closed' if now.time() >= SCAN_END else 'scanning' if scan.observed_at else 'preparing'

        if scan.trade_date and phase != 'exit':
            signals = {item.secid: item for item in self._state.read_signals(scan.trade_date)}
            current = tuple(item for item in scan.candidates if item.secid not in signals)
            scan = replace(scan, candidates=(*signals.values(), *current))
        values = {field.name: getattr(scan, field.name) for field in fields(OvernightScan)}
        return OvernightView(**values, phase=phase, reminders=reminders, reminders_until=reminders_until)
