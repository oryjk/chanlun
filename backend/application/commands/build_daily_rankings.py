from concurrent.futures import FIRST_COMPLETED, ThreadPoolExecutor, wait
import logging
from uuid import uuid4

from ...domain.analysis_version import ANALYSIS_VERSION
from ...domain.errors import ChanlunError
from ...domain.models import RankingBoard, TimeFrame
from ...domain.rankings import build_ranking_entry, rank_entries
from ...domain.services.market_analysis import MarketAnalysisPipeline, close_window
from ..ports.clock import Clock
from ..ports.readers import MarketDataReader, RankingReader, StockUniverseReader
from ..ports.writers import RankingCheckpointWriter, RankingLease, RankingPublisher
from ..ranking_jobs import RankingItem, RankingProgress


class BuildDailyRankingsCommand:
    analysis_version = ANALYSIS_VERSION

    def __init__(
        self,
        universe: StockUniverseReader,
        market_data: MarketDataReader,
        pipeline: MarketAnalysisPipeline,
        reader: RankingReader,
        lease: RankingLease,
        checkpoints: RankingCheckpointWriter,
        publisher: RankingPublisher,
        clock: Clock,
        *,
        workers=4,
        retries=2,
        lease_seconds=180,
    ):
        self._universe = universe
        self._market_data = market_data
        self._pipeline = pipeline
        self._reader = reader
        self._lease = lease
        self._checkpoints = checkpoints
        self._publisher = publisher
        self._clock = clock
        self._workers = workers
        self._retries = retries
        self._lease_seconds = lease_seconds

    def report_unavailable(self):
        self._checkpoints.note_error("暂时无法确认收盘数据，后台将稍后重试", self._clock.now())

    def _analyze(self, stock, trade_date, cancelled):
        for attempt in range(self._retries + 1):
            if cancelled():
                return None
            try:
                snapshot = self._pipeline.analyze(
                    close_window(
                        self._market_data.read(stock.secid, TimeFrame.ONE_DAY, force_refresh=True),
                        trade_date,
                    ),
                    TimeFrame.ONE_DAY,
                    as_of=self._clock.now(),
                )
                return RankingItem(stock.secid, build_ranking_entry(stock, snapshot))
            except ChanlunError:
                if attempt == self._retries:
                    return RankingItem(stock.secid, error="暂未获取到有效收盘行情")

    def execute(self, trade_date, cancelled=lambda: False):
        if cancelled():
            return False
        owner = uuid4().hex
        if not self._lease.claim(trade_date, owner, self._clock.now(), self._lease_seconds,
                                 analysis_version=self.analysis_version):
            return False
        executor = ThreadPoolExecutor(max_workers=self._workers, thread_name_prefix="close-ranking")
        pending = set()
        try:
            listing = executor.submit(self._universe.list_all, cancelled=cancelled)
            while not listing.done():
                if cancelled():
                    raise InterruptedError
                if not self._lease.heartbeat(owner, self._clock.now(), self._lease_seconds):
                    raise RuntimeError("榜单任务租约已失效")
                wait((listing,), timeout=5)
            stocks = listing.result()
            if not stocks or len({stock.secid for stock in stocks}) != len(stocks):
                raise ValueError("证券清单不完整")
            identities = {stock.secid for stock in stocks}
            items = {item.secid: item for item in self._reader.read_items(trade_date, analysis_version=self.analysis_version)
                     if item.secid in identities and item.error is None}
            total = len(stocks)

            def progress():
                failures = sum(item.error is not None for item in items.values())
                return RankingProgress("running", trade_date, total, len(items), len(items) - failures,
                                       failures, self._clock.now(), analysis_version=self.analysis_version)

            self._checkpoints.set_progress(progress(), owner)
            remaining = iter(stock for stock in stocks if stock.secid not in items)

            def fill():
                while len(pending) < self._workers * 2 and not cancelled():
                    stock = next(remaining, None)
                    if stock is None:
                        break
                    pending.add(executor.submit(self._analyze, stock, trade_date, cancelled))

            fill()
            while pending:
                if cancelled():
                    raise InterruptedError
                done, pending = wait(pending, timeout=5, return_when=FIRST_COMPLETED)
                if not self._lease.heartbeat(owner, self._clock.now(), self._lease_seconds):
                    raise RuntimeError("榜单任务租约已失效")
                for future in done:
                    item = future.result()
                    if item is None:
                        raise InterruptedError
                    items[item.secid] = item
                    self._checkpoints.record(trade_date, item, progress(), owner, self._clock.now(), self._lease_seconds)
                fill()
            if cancelled():
                raise InterruptedError
            state = progress()
            if state.analyzed_count == 0:
                raise ValueError("本次未取得有效收盘行情")
            entries = [item.entry for item in items.values() if item.entry is not None and item.error is None]
            board = RankingBoard(self._clock.now(), TimeFrame.ONE_DAY, rank_entries(entries, "bullish"),
                                 rank_entries(entries, "bearish"), trade_date, total, state.analyzed_count,
                                 state.failed_count, "degraded" if state.failed_count else "ready",
                                 analysis_version=self.analysis_version)
            self._publisher.publish(board, owner, self._clock.now())
            return True
        except InterruptedError:
            # 进程停止导致的中断不是失败：释放租约并保留检查点，下次启动立即续算，不进入失败退避。
            self._lease.release(owner, self._clock.now())
            return False
        except Exception:
            logging.getLogger(__name__).exception("收盘榜单构建失败，收盘日期 %s", trade_date)
            self._checkpoints.fail(owner, "收盘榜单暂时未能更新，稍后重试", self._clock.now())
            return False
        finally:
            for future in pending:
                future.cancel()
            executor.shutdown(wait=True, cancel_futures=True)
