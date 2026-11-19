from contextlib import contextmanager
from dataclasses import asdict
from datetime import date, datetime
import fcntl
import json
from pathlib import Path
import sqlite3

from ...application.overnight import ExitObservation, OvernightScan
from ...domain.overnight import HistoricalSeals, MARKET_TZ, SCAN_END, MinuteBar, MinuteSeries, OvernightCandidate


_SCHEMA = '''
CREATE TABLE IF NOT EXISTS overnight_sessions (
    trade_date TEXT PRIMARY KEY
);
CREATE TABLE IF NOT EXISTS overnight_seal_days (
    trade_date TEXT PRIMARY KEY
);
CREATE TABLE IF NOT EXISTS overnight_seals (
    trade_date TEXT NOT NULL,
    secid TEXT NOT NULL,
    close REAL NOT NULL,
    limit_up REAL NOT NULL,
    PRIMARY KEY (trade_date, secid)
);
CREATE TABLE IF NOT EXISTS overnight_minutes (
    trade_date TEXT NOT NULL,
    secid TEXT NOT NULL,
    previous_close REAL NOT NULL,
    last_minute TEXT NOT NULL,
    bar_count INTEGER NOT NULL,
    bars TEXT NOT NULL,
    PRIMARY KEY (trade_date, secid)
);
CREATE TABLE IF NOT EXISTS overnight_scans (
    trade_date TEXT NOT NULL,
    observed_at TEXT NOT NULL,
    snapshot TEXT NOT NULL,
    PRIMARY KEY (trade_date, observed_at)
);
CREATE TABLE IF NOT EXISTS overnight_signals (
    trade_date TEXT NOT NULL,
    secid TEXT NOT NULL,
    signal_at TEXT NOT NULL,
    evidence TEXT NOT NULL,
    PRIMARY KEY (trade_date, secid)
);
CREATE TABLE IF NOT EXISTS overnight_exit_observations (
    trade_date TEXT NOT NULL,
    secid TEXT NOT NULL,
    observed_at TEXT NOT NULL,
    exception_observed INTEGER,
    PRIMARY KEY (trade_date, secid)
);
'''


def _json(value) -> str:
    return json.dumps(value, default=lambda item: item.isoformat(), ensure_ascii=False, separators=(',', ':'), allow_nan=False)


def _candidate(row: dict) -> OvernightCandidate:
    row['as_of'] = datetime.fromisoformat(row['as_of'])
    row['limit_up_date'] = date.fromisoformat(row['limit_up_date'])
    if row['pullback_at']:
        row['pullback_at'] = datetime.fromisoformat(row['pullback_at'])
    if row.get('minute_at'):
        row['minute_at'] = datetime.fromisoformat(row['minute_at'])
    return OvernightCandidate(**row)


class SqliteOvernightStore:
    def __init__(self, path: str | Path):
        self.path = Path(path).resolve()
        self.path.parent.mkdir(parents=True, exist_ok=True)
        with self._connect(readonly=False) as db:
            db.execute('PRAGMA journal_mode=WAL')
            db.executescript(_SCHEMA)

    @contextmanager
    def _connect(self, *, readonly: bool = True):
        mode = 'ro' if readonly else 'rwc'
        db = sqlite3.connect(f'{self.path.as_uri()}?mode={mode}', uri=True, timeout=5)
        try:
            with db:
                yield db
        finally:
            db.close()

    @contextmanager
    def worker_lock(self):
        with self.path.with_suffix('.worker.lock').open('a') as handle:
            try:
                fcntl.flock(handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
            except BlockingIOError:
                yield False
                return
            try:
                yield True
            finally:
                fcntl.flock(handle, fcntl.LOCK_UN)

    def save_sessions(self, sessions: tuple[date, ...]) -> None:
        with self._connect(readonly=False) as db:
            db.executemany('INSERT OR IGNORE INTO overnight_sessions VALUES (?)', ((item.isoformat(),) for item in sessions))

    def read_sessions(self, through: date, limit: int = 21) -> tuple[date, ...]:
        with self._connect() as db:
            rows = db.execute('SELECT trade_date FROM overnight_sessions WHERE trade_date<=? ORDER BY trade_date DESC LIMIT ?',
                              (through.isoformat(), limit)).fetchall()
        return tuple(date.fromisoformat(row[0]) for row in reversed(rows))

    def read_seal_coverage(self, sessions: tuple[date, ...]) -> tuple[date, ...]:
        if not sessions:
            return ()
        marks = ','.join('?' for _ in sessions)
        with self._connect() as db:
            rows = db.execute(f'SELECT trade_date FROM overnight_seal_days WHERE trade_date IN ({marks}) ORDER BY trade_date',
                              tuple(item.isoformat() for item in sessions)).fetchall()
        return tuple(date.fromisoformat(row[0]) for row in rows)

    def read_recent_seals(self, sessions: tuple[date, ...]) -> dict[str, date]:
        if not sessions:
            return {}
        marks = ','.join('?' for _ in sessions)
        with self._connect() as db:
            rows = db.execute(f'SELECT secid, MAX(trade_date) FROM overnight_seals WHERE trade_date IN ({marks}) GROUP BY secid',
                              tuple(item.isoformat() for item in sessions)).fetchall()
        return {secid: date.fromisoformat(day) for secid, day in rows}

    def save_seals(self, history: HistoricalSeals) -> None:
        if not history.complete:
            return
        day = history.trade_date.isoformat()
        with self._connect(readonly=False) as db:
            inserted = db.execute('INSERT OR IGNORE INTO overnight_seal_days VALUES (?)', (day,))
            if inserted.rowcount:
                db.executemany('INSERT INTO overnight_seals VALUES (?, ?, ?, ?)',
                               ((day, item.secid, item.close, item.limit_up) for item in history.seals))

    def read_minutes(self, trade_date: date, secid: str) -> MinuteSeries | None:
        with self._connect() as db:
            row = db.execute('SELECT previous_close, bars FROM overnight_minutes WHERE trade_date=? AND secid=?',
                             (trade_date.isoformat(), secid)).fetchone()
        if not row:
            return None
        bars = tuple(MinuteBar(datetime.fromisoformat(at), close, low, average, volume)
                     for at, close, low, average, volume in json.loads(row[1]))
        return MinuteSeries(secid, row[0], bars)

    def read_unfinished_minutes(self, trade_date: date) -> tuple[str, ...]:
        end = datetime.combine(trade_date, SCAN_END, MARKET_TZ).isoformat()
        with self._connect() as db:
            rows = db.execute('SELECT secid FROM overnight_minutes WHERE trade_date=? AND (last_minute<? OR bar_count<241)',
                              (trade_date.isoformat(), end)).fetchall()
        return tuple(row[0] for row in rows)

    def _save_minutes(self, db, trade_date: date, series: tuple[MinuteSeries, ...]) -> None:
        db.executemany('''INSERT INTO overnight_minutes VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(trade_date, secid) DO UPDATE SET previous_close=excluded.previous_close,
                last_minute=excluded.last_minute, bar_count=excluded.bar_count, bars=excluded.bars
            WHERE excluded.last_minute>=overnight_minutes.last_minute AND excluded.bar_count>=overnight_minutes.bar_count
              AND excluded.bars<>overnight_minutes.bars''',
            ((trade_date.isoformat(), item.secid, item.previous_close, item.bars[-1].at.isoformat(), len(item.bars),
              _json([(bar.at, bar.close, bar.low, bar.average, bar.volume) for bar in item.bars]))
             for item in series if item.bars))

    def save_minutes(self, trade_date: date, series: tuple[MinuteSeries, ...]) -> None:
        if series:
            with self._connect(readonly=False) as db:
                self._save_minutes(db, trade_date, series)

    def save_scan(self, trade_date: date, scan: OvernightScan, minutes: tuple[MinuteSeries, ...]) -> None:
        if scan.observed_at is None:
            return
        day = trade_date.isoformat()
        with self._connect(readonly=False) as db:
            self._save_minutes(db, trade_date, minutes)
            db.execute('INSERT OR IGNORE INTO overnight_scans VALUES (?, ?, ?)',
                       (day, scan.observed_at.isoformat(), _json(asdict(scan))))
            db.executemany('INSERT OR IGNORE INTO overnight_signals VALUES (?, ?, ?, ?)',
                           ((day, item.secid, item.pullback_at.isoformat(), _json(asdict(item)))
                            for item in scan.candidates if item.pullback_at is not None))

    def read_scan(self, trade_date: date | None = None) -> OvernightScan | None:
        with self._connect() as db:
            if trade_date:
                row = db.execute('SELECT snapshot FROM overnight_scans WHERE trade_date=? ORDER BY observed_at DESC LIMIT 1',
                                 (trade_date.isoformat(),)).fetchone()
            else:
                row = db.execute('SELECT snapshot FROM overnight_scans ORDER BY trade_date DESC, observed_at DESC LIMIT 1').fetchone()
        if not row:
            return None
        scan = json.loads(row[0])
        scan['trade_date'] = date.fromisoformat(scan['trade_date'])
        scan['observed_at'] = datetime.fromisoformat(scan['observed_at'])
        scan['candidates'] = tuple(_candidate(item) for item in scan['candidates'])
        return OvernightScan(**scan)

    def read_signals(self, trade_date: date) -> tuple[OvernightCandidate, ...]:
        with self._connect() as db:
            rows = db.execute('SELECT evidence FROM overnight_signals WHERE trade_date=? ORDER BY signal_at DESC, secid',
                              (trade_date.isoformat(),)).fetchall()
        return tuple(_candidate(json.loads(row[0])) for row in rows)

    def save_exit_observations(self, trade_date: date, observations: tuple[ExitObservation, ...],
                               minutes: tuple[MinuteSeries, ...]) -> None:
        with self._connect(readonly=False) as db:
            self._save_minutes(db, trade_date, minutes)
            db.executemany('''INSERT INTO overnight_exit_observations VALUES (?, ?, ?, ?)
                ON CONFLICT(trade_date, secid) DO UPDATE SET observed_at=excluded.observed_at,
                    exception_observed=excluded.exception_observed
                WHERE excluded.observed_at>=overnight_exit_observations.observed_at''',
                ((trade_date.isoformat(), item.secid, item.observed_at.isoformat(), item.exception_observed)
                 for item in observations))

    def read_exit_observations(self, trade_date: date) -> dict[str, ExitObservation]:
        with self._connect() as db:
            rows = db.execute('SELECT secid, observed_at, exception_observed FROM overnight_exit_observations WHERE trade_date=?',
                              (trade_date.isoformat(),)).fetchall()
        return {secid: ExitObservation(secid, datetime.fromisoformat(at), None if value is None else bool(value))
                for secid, at, value in rows}
