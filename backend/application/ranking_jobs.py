from dataclasses import dataclass
from datetime import date, datetime

from ..domain.analysis_version import ANALYSIS_VERSION
from ..domain.models import RankingBoard, RankingEntry


@dataclass(frozen=True)
class RankingProgress:
    state: str = "pending"
    trade_date: date | None = None
    total_count: int = 0
    processed_count: int = 0
    analyzed_count: int = 0
    failed_count: int = 0
    updated_at: datetime | None = None
    error: str | None = None
    analysis_version: int = ANALYSIS_VERSION


@dataclass(frozen=True)
class RankingView:
    board: RankingBoard | None
    progress: RankingProgress


@dataclass(frozen=True)
class RankingItem:
    secid: str
    entry: RankingEntry | None = None
    error: str | None = None
