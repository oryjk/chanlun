from dataclasses import replace

from ...domain.models import RankingBoard, TimeFrame
from ...domain.rankings import rank_entries
from ..ports.readers import RankingReader


class GetRankingsQuery:
    def __init__(self, reader: RankingReader):
        self._reader = reader

    def execute(self, limit: int = 500) -> RankingBoard:
        view = self._reader.read()
        board = view.board or RankingBoard(None, TimeFrame.ONE_DAY, (), (), state="pending")
        progress = view.progress
        return replace(board, bullish=rank_entries(board.bullish, "bullish", limit),
                       bearish=rank_entries(board.bearish, "bearish", limit),
                       state=progress.state, progress_total=progress.total_count,
                       progress_processed=progress.processed_count, update_error=progress.error)
