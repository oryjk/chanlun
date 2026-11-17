from __future__ import annotations

from collections.abc import Iterable
from dataclasses import dataclass
from datetime import date, datetime
from enum import Enum

from .errors import InvalidAssetQuery
from .models import BuySellPoint, Stock

SIMULATED_PRINCIPAL = 10000.0


class AssetCategory(str, Enum):
    STOCK = "stock"
    ETF = "etf"

    @classmethod
    def parse(cls, value: str) -> AssetCategory:
        try:
            return cls(value.strip().lower())
        except (ValueError, AttributeError) as error:
            raise InvalidAssetQuery("不支持的资产分类，请选择 stock 或 etf") from error


class AssetSortField(str, Enum):
    AMOUNT = "amount"
    CHANGE_PERCENT = "change_percent"
    LATEST = "latest"

    @classmethod
    def parse(cls, value: str) -> AssetSortField:
        try:
            return cls(value.strip().lower())
        except (ValueError, AttributeError) as error:
            raise InvalidAssetQuery("不支持的排序字段") from error


@dataclass(frozen=True)
class MarketAssetItem:
    stock: Stock
    latest: float
    change: float
    change_percent: float
    open: float
    high: float
    low: float
    previous_close: float
    volume: int
    amount: float
    sparkline: tuple[float, ...] = ()
    signal_label: str | None = None


@dataclass(frozen=True)
class MarketAssetList:
    category: AssetCategory
    as_of_date: str | None
    items: tuple[MarketAssetItem, ...]
    total: int = 0
    offset: int = 0
    has_more: bool = False

    def __post_init__(self) -> None:
        if self.total <= 0 and self.items:
            object.__setattr__(self, "total", self.offset + len(self.items))


def is_active_market_asset(item: MarketAssetItem) -> bool:
    """判断标的在当前交易快照中是否存在有效成交。"""
    return item.latest > 0 and item.volume > 0 and item.amount > 0


def _asset_sort_metric(item: MarketAssetItem, sort_field: AssetSortField) -> float:
    if sort_field is AssetSortField.CHANGE_PERCENT:
        return item.change_percent
    if sort_field is AssetSortField.LATEST:
        return item.latest
    return item.amount


def sort_market_assets(
    items: Iterable[MarketAssetItem],
    sort_field: AssetSortField,
    *,
    descending: bool = True,
) -> list[MarketAssetItem]:
    """统一排序市场标的：活跃成交股在前，停牌/零成交股固定垫底，secid 升序作确定性次级键。"""
    return sorted(
        items,
        key=lambda entry: (
            0 if is_active_market_asset(entry) else 1,
            -_asset_sort_metric(entry, sort_field) if descending else _asset_sort_metric(entry, sort_field),
            entry.stock.secid,
        ),
    )


@dataclass(frozen=True)
class TrackedAssetRecord:
    user_id: str
    secid: str
    symbol: str
    name: str
    market: str
    start_date: date
    start_price: float
    created_at: datetime


@dataclass(frozen=True)
class TrackedAssetSummary:
    stock: Stock
    start_date: date
    start_price: float
    latest_price: float
    day_return_pct: float
    day_profit: float
    cumulative_return_pct: float
    cumulative_profit: float
    current_value: float
    sparkline: tuple[float, ...] = ()


@dataclass(frozen=True)
class TrackingOverview:
    as_of_date: date
    items: tuple[TrackedAssetSummary, ...]


def signal_point_label(point: BuySellPoint | None) -> str | None:
    if point is None:
        return None
    prefix = {1: "一", 2: "二", 3: "三"}.get(point.kind, str(point.kind))
    side = "买" if point.side == "buy" else "卖"
    return f"{prefix}类{side}点"


def build_tracked_asset_summary(
    record: TrackedAssetRecord,
    *,
    latest_price: float,
    previous_close: float | None,
    day_return_pct: float | None,
    sparkline: tuple[float, ...] = (),
) -> TrackedAssetSummary:
    """Compute ¥10,000 simulated tracking valuation & P/L from baseline price."""
    resolved_latest = latest_price if latest_price > 0 else record.start_price
    base = record.start_price if record.start_price > 0 else resolved_latest
    current_value = round(SIMULATED_PRINCIPAL * (resolved_latest / base), 2) if base > 0 else SIMULATED_PRINCIPAL
    cumulative_profit = round(current_value - SIMULATED_PRINCIPAL, 2)
    cumulative_return_pct = (
        round(((resolved_latest - base) / base) * 100.0, 2) if base > 0 else 0.0
    )

    if day_return_pct is not None:
        resolved_day_pct = round(day_return_pct, 2)
    elif previous_close and previous_close > 0:
        resolved_day_pct = round(((resolved_latest - previous_close) / previous_close) * 100.0, 2)
    else:
        resolved_day_pct = 0.0

    if previous_close and previous_close > 0 and base > 0:
        day_profit = round(SIMULATED_PRINCIPAL * ((resolved_latest - previous_close) / base), 2)
    elif 100.0 + resolved_day_pct > 0:
        day_profit = round(current_value * (resolved_day_pct / (100.0 + resolved_day_pct)), 2)
    else:
        day_profit = 0.0

    return TrackedAssetSummary(
        stock=Stock(
            secid=record.secid,
            symbol=record.symbol,
            name=record.name,
            market=record.market,
        ),
        start_date=record.start_date,
        start_price=record.start_price,
        latest_price=resolved_latest,
        day_return_pct=resolved_day_pct,
        day_profit=day_profit,
        cumulative_return_pct=cumulative_return_pct,
        cumulative_profit=cumulative_profit,
        current_value=current_value,
        sparkline=sparkline,
    )
