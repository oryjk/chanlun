from concurrent.futures import ThreadPoolExecutor
from dataclasses import replace
from datetime import datetime, timezone

from ...domain.errors import ChanlunError
from ...domain.models import RankingBoard, RankingEntry, Stock, TimeFrame
from ..ports.readers import StockPoolReader
from .get_market_snapshot import GetMarketSnapshotQuery

POOL_SIZE = 40
ALIGNED_SIDES = {"bullish": "buy", "bearish": "sell"}


class GetRankingsQuery:
    def __init__(self, pool_reader: StockPoolReader, snapshot_query: GetMarketSnapshotQuery):
        self._pool_reader = pool_reader
        self._snapshot_query = snapshot_query

    def execute(self, limit: int = 6) -> RankingBoard:
        stocks = self._pool_reader.list_liquid(POOL_SIZE)
        with ThreadPoolExecutor(max_workers=4) as executor:
            entries = [entry for entry in executor.map(self._read_entry, stocks) if entry is not None]

        return RankingBoard(
            generated_at=datetime.now(timezone.utc),
            timeframe=TimeFrame.ONE_DAY,
            bullish=self._rank(entries, "bullish", limit),
            bearish=self._rank(entries, "bearish", limit),
        )

    def _read_entry(self, stock: Stock) -> RankingEntry | None:
        try:
            snapshot = self._snapshot_query.execute(stock.secid, TimeFrame.ONE_DAY.value)
        except ChanlunError:
            return None
        guidance = snapshot.guidance
        if guidance.bias == "neutral":
            return None
        points = snapshot.signals.points
        return RankingEntry(
            rank=0,
            stock=stock,
            bias=guidance.bias,
            strength=guidance.strength,
            latest=snapshot.summary.latest,
            change_percent=snapshot.summary.change_percent,
            invalidation_price=guidance.invalidation_price,
            latest_point=points[-1] if points else None,
        )

    def _rank(self, entries: list[RankingEntry], bias: str, limit: int) -> tuple[RankingEntry, ...]:
        selected = [entry for entry in entries if entry.bias == bias]
        # 同等强度时，日线最近买卖点与方向一致者优先，再按当日涨跌幅
        sign = -1 if bias == "bullish" else 1
        selected.sort(
            key=lambda entry: (
                -entry.strength,
                not self._aligned(entry, bias),
                sign * entry.change_percent,
                entry.stock.symbol,
            )
        )
        return tuple(replace(entry, rank=index) for index, entry in enumerate(selected[:limit], 1))

    def _aligned(self, entry: RankingEntry, bias: str) -> bool:
        return entry.latest_point is not None and entry.latest_point.side == ALIGNED_SIDES[bias]
