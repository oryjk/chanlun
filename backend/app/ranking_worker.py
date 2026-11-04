import logging
from datetime import datetime, time, timedelta, timezone
from threading import Event, Thread
from zoneinfo import ZoneInfo

from ..application.commands.build_daily_rankings import BuildDailyRankingsCommand
from ..application.ports.readers import ClosedTradingDayReader, RankingReader


class RankingWorker:
    def __init__(self, calendar: ClosedTradingDayReader, command: BuildDailyRankingsCommand,
                 reader: RankingReader, run_at="15:30"):
        self._calendar, self._command, self._reader = calendar, command, reader
        self._run_at = time.fromisoformat(run_at)
        self._stop = Event()
        self._thread = None

    def tick(self, now):
        local = now.astimezone(ZoneInfo("Asia/Shanghai"))
        cutoff = local.date() if local.time() >= self._run_at else local.date() - timedelta(days=1)
        view = self._reader.read()
        if view.progress.state == "error" and view.progress.updated_at and now - view.progress.updated_at < timedelta(minutes=15):
            return
        trade_date = self._calendar.latest_on_or_before(cutoff)
        if view.board and view.board.trade_date and view.board.trade_date >= trade_date:
            return
        self._command.execute(trade_date, cancelled=self._stop.is_set)

    def _run(self):
        while not self._stop.is_set():
            try:
                self.tick(datetime.now(timezone.utc))
            except Exception:
                logging.getLogger(__name__).exception("收盘榜单后台任务暂时失败")
                self._command.report_unavailable()
            self._stop.wait(60)

    def start(self):
        if self._thread is None:
            self._thread = Thread(target=self._run, name="daily-ranking-scheduler", daemon=True)
            self._thread.start()

    def stop(self):
        self._stop.set()
        if self._thread:
            self._thread.join(timeout=60)
