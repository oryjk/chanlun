from datetime import date
from typing import Protocol

from ...domain.overnight import BoardQuote, HistoricalSeals, MinuteSeries, OvernightCandidate, ScreeningQuote
from ..overnight import ExitObservation, OvernightScan


class OvernightUniverseReader(Protocol):
    def read_universe(self) -> tuple[BoardQuote, ...]: ...


class OvernightQuoteReader(Protocol):
    def read_quotes(self, secids: tuple[str, ...]) -> dict[str, ScreeningQuote]: ...


class OvernightMinutesReader(Protocol):
    def read_minutes(self, secid: str) -> MinuteSeries: ...


class OvernightSessionReader(Protocol):
    def read_sessions(self, through: date) -> tuple[date, ...]: ...


class OvernightSealsReader(Protocol):
    def read_seals(self, trade_date: date) -> HistoricalSeals: ...


class OvernightHistoryReader(Protocol):
    def read_sessions(self, through: date, limit: int = 21) -> tuple[date, ...]: ...

    def read_seal_coverage(self, sessions: tuple[date, ...]) -> tuple[date, ...]: ...

    def read_recent_seals(self, sessions: tuple[date, ...]) -> dict[str, date]: ...

    def read_minutes(self, trade_date: date, secid: str) -> MinuteSeries | None: ...

    def read_unfinished_minutes(self, trade_date: date) -> tuple[str, ...]: ...


class OvernightHistoryWriter(Protocol):
    def save_sessions(self, sessions: tuple[date, ...]) -> None: ...

    def save_seals(self, history: HistoricalSeals) -> None: ...

    def save_minutes(self, trade_date: date, series: tuple[MinuteSeries, ...]) -> None: ...


class OvernightStateReader(Protocol):
    def read_scan(self, trade_date: date | None = None) -> OvernightScan | None: ...

    def read_signals(self, trade_date: date) -> tuple[OvernightCandidate, ...]: ...

    def read_exit_observations(self, trade_date: date) -> dict[str, ExitObservation]: ...


class OvernightStateWriter(Protocol):
    def save_scan(self, trade_date: date, scan: OvernightScan, minutes: tuple[MinuteSeries, ...]) -> None: ...

    def save_exit_observations(self, trade_date: date, observations: tuple[ExitObservation, ...],
                               minutes: tuple[MinuteSeries, ...]) -> None: ...
