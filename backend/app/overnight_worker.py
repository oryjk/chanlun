from collections.abc import Callable
from concurrent.futures import Future, ThreadPoolExecutor
from datetime import datetime, time
import logging
from threading import Event, Thread
from time import monotonic
from typing import ContextManager

from ..application.commands.overnight import (
    ArchiveOvernightMinutesCommand, ObserveOvernightExitCommand, ScanOvernightCommand, SyncOvernightHistoryCommand,
)
from ..application.ports.clock import Clock
from ..application.ports.overnight import OvernightHistoryReader, OvernightHistoryWriter, OvernightSessionReader
from ..domain.overnight import EXIT_END, EXIT_START, HISTORY_SESSIONS, MARKET_TZ, SCAN_END, SCAN_START


LOGGER = logging.getLogger(__name__)
_SCAN_INTERVAL = 15
_CLOSE_ARCHIVE_AT = time(15, 5)
_CLOSE_SEAL_SYNC_AT = time(15, 35)


class OvernightWorker:
    def __init__(self, clock: Clock, calendar: OvernightSessionReader, reader: OvernightHistoryReader,
                 writer: OvernightHistoryWriter, guard: Callable[[], ContextManager[bool]],
                 sync: SyncOvernightHistoryCommand, scan: ScanOvernightCommand,
                 exit_observations: ObserveOvernightExitCommand, archive: ArchiveOvernightMinutesCommand):
        self._clock = clock
        self._calendar = calendar
        self._reader = reader
        self._writer = writer
        self._guard = guard
        self._sync = sync
        self._scan = scan
        self._exit = exit_observations
        self._archive = archive
        self._stop = Event()
        self._thread: Thread | None = None
        self._calendar_retry_at = 0.0
        self._history_retry_at = 0.0
        self._archive_retry_at = 0.0
        self._exit_retry_at = 0.0

    def start(self) -> None:
        if self._thread and self._thread.is_alive():
            return
        self._stop.clear()
        self._thread = Thread(target=self._run, name='overnight-method', daemon=True)
        self._thread.start()

    def stop(self) -> None:
        self._stop.set()
        if self._thread:
            self._thread.join(timeout=2)

    def _run(self) -> None:
        while not self._stop.is_set():
            try:
                with self._guard() as active:
                    if active:
                        self._run_owned()
            except OSError:
                LOGGER.exception('隔夜套利任务无法获得运行锁')
            self._stop.wait(_SCAN_INTERVAL)

    def _run_owned(self) -> None:
        with ThreadPoolExecutor(max_workers=1, thread_name_prefix='overnight-history') as history_pool:
            history_job: Future | None = None
            while not self._stop.is_set():
                started = monotonic()
                try:
                    if history_job is not None and history_job.done():
                        previous_job, history_job = history_job, None
                        previous_job.result()
                    history_job = self._tick(self._clock.now().astimezone(MARKET_TZ), started, history_pool, history_job)
                except Exception:
                    LOGGER.exception('隔夜套利后台任务将重试')
                self._stop.wait(max(1, _SCAN_INTERVAL - (monotonic() - started)))

    def _tick(self, now: datetime, started: float, history_pool: ThreadPoolExecutor,
              history_job: Future | None) -> Future | None:
        today = now.date()
        at = now.time()
        dates = self._reader.read_sessions(today)
        is_today = bool(dates and dates[-1] == today)
        if (not dates or (not is_today and at >= EXIT_START)) and started >= self._calendar_retry_at:
            self._calendar_retry_at = started + (45 if now.weekday() < 5 and at < SCAN_END else 900)
            self._writer.save_sessions(self._calendar.read_sessions(today))
            dates = self._reader.read_sessions(today)
            is_today = bool(dates and dates[-1] == today)
        if not dates:
            return history_job
        closed = dates if not is_today or at >= _CLOSE_SEAL_SYNC_AT else dates[:-1]
        required = closed[-(HISTORY_SESSIONS + int(is_today and at >= _CLOSE_SEAL_SYNC_AT)):]
        if history_job is None and required and started >= self._history_retry_at:
            self._history_retry_at = started + 180
            if len(self._reader.read_seal_coverage(required)) < len(required):
                history_job = history_pool.submit(self._sync.execute, required, stop_requested=self._stop.is_set)
        if is_today and SCAN_START <= at < SCAN_END:
            self._scan.execute(now)
            return history_job
        if is_today and EXIT_START <= at < EXIT_END and started >= self._exit_retry_at:
            self._exit_retry_at = started + 45
            self._exit.execute(now)

        latest_closed = dates[-1] if not is_today or at >= _CLOSE_ARCHIVE_AT else dates[-2] if len(dates) > 1 else None
        if latest_closed and started >= self._archive_retry_at:
            self._archive_retry_at = started + 180
            self._archive.execute(latest_closed)
        return history_job
