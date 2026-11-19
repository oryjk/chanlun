from dataclasses import dataclass
from datetime import date, datetime
from typing import Literal

from ..domain.overnight import OvernightCandidate, Phase


ScanStatus = Literal['pending', 'ready', 'partial', 'unavailable']


@dataclass(frozen=True)
class OvernightScan:
    trade_date: date | None = None
    observed_at: datetime | None = None
    history_sessions: int = 0
    universe_count: int = 0
    fresh_count: int = 0
    matched_count: int = 0
    minute_checked_count: int = 0
    elapsed_ms: int | None = None
    max_quote_age_seconds: float | None = None
    status: ScanStatus = 'pending'
    candidates: tuple[OvernightCandidate, ...] = ()


@dataclass(frozen=True)
class ExitObservation:
    secid: str
    observed_at: datetime
    exception_observed: bool | None


@dataclass(frozen=True)
class OvernightReminder:
    secid: str
    symbol: str
    name: str
    signal_date: date
    signal_at: datetime
    exception_observed: bool | None = None
    exception_as_of: datetime | None = None
    exception_valid_until: datetime | None = None


@dataclass(frozen=True)
class OvernightView(OvernightScan):
    phase: Phase = 'waiting'
    reminders: tuple[OvernightReminder, ...] = ()
    reminders_until: datetime | None = None
