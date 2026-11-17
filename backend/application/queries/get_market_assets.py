from dataclasses import replace
from zoneinfo import ZoneInfo

from ...domain.errors import ChanlunError
from ...domain.tracking import (
    AssetCategory,
    AssetSortField,
    MarketAssetItem,
    MarketAssetList,
    signal_point_label,
)
from ..ports.clock import Clock
from ..ports.readers import MarketAssetCatalogReader, RankingReader

_SHANGHAI_TZ = ZoneInfo("Asia/Shanghai")


def _sort_key(item: MarketAssetItem, sort_field: AssetSortField) -> float:
    if sort_field is AssetSortField.CHANGE_PERCENT:
        return item.change_percent
    if sort_field is AssetSortField.LATEST:
        return item.latest
    return item.amount


class GetMarketAssetsQuery:
    def __init__(
        self,
        catalog_reader: MarketAssetCatalogReader,
        ranking_reader: RankingReader,
        clock: Clock,
    ):
        self._catalog_reader = catalog_reader
        self._ranking_reader = ranking_reader
        self._clock = clock

    def execute(
        self,
        category: str = "stock",
        sort: str = "amount",
        order: str = "desc",
        limit: int = 60,
    ) -> MarketAssetList:
        parsed_category = AssetCategory.parse(category)
        parsed_sort = AssetSortField.parse(sort)
        descending = order.strip().lower() != "asc"
        clamped_limit = max(1, min(limit, 200))

        ranking_view = self._ranking_reader.read()
        board = ranking_view.board
        ranking_by_secid = {}
        if board is not None:
            for entry in (*board.bullish, *board.bearish):
                ranking_by_secid.setdefault(entry.stock.secid, entry)

        fallback_as_of_date = (
            board.trade_date.isoformat()
            if board is not None and board.trade_date is not None
            else self._clock.now().astimezone(_SHANGHAI_TZ).date().isoformat()
        )
        as_of_date = fallback_as_of_date

        items: tuple[MarketAssetItem, ...] = ()
        live_error: ChanlunError | None = None
        try:
            catalog = self._catalog_reader.list_assets(
                parsed_category,
                sort_field=parsed_sort,
                descending=descending,
                limit=clamped_limit,
            )
            if isinstance(catalog, MarketAssetList):
                items = catalog.items
                if items and catalog.as_of_date:
                    as_of_date = catalog.as_of_date
            else:
                items = tuple(catalog)
        except ChanlunError as error:
            live_error = error

        if not items and board is not None and board.trade_date is not None:
            items = self._fallback_from_rankings(
                board.trade_date,
                analysis_version=board.analysis_version,
                category=parsed_category,
                sort_field=parsed_sort,
                descending=descending,
                limit=clamped_limit,
            )
            if items:
                as_of_date = board.trade_date.isoformat()
                live_error = None

        if live_error is not None and not items:
            raise live_error

        enriched = tuple(self._enrich_from_ranking(item, ranking_by_secid.get(item.stock.secid)) for item in items)
        return MarketAssetList(category=parsed_category, as_of_date=as_of_date, items=enriched)

    def _fallback_from_rankings(
        self,
        trade_date,
        *,
        analysis_version: int,
        category: AssetCategory,
        sort_field: AssetSortField,
        descending: bool,
        limit: int,
    ) -> tuple[MarketAssetItem, ...]:
        saved_items = self._ranking_reader.read_items(trade_date, analysis_version=analysis_version)
        candidates: list[MarketAssetItem] = []
        for item in saved_items:
            entry = item.entry
            if entry is None:
                continue
            is_etf = entry.stock.market == "ETF"
            if category is AssetCategory.ETF and not is_etf:
                continue
            if category is AssetCategory.STOCK and is_etf:
                continue
            sparkline = tuple(round(point.price, 4) for point in entry.trend)
            prev_close = sparkline[-2] if len(sparkline) >= 2 else entry.latest
            candidates.append(
                MarketAssetItem(
                    stock=entry.stock,
                    latest=entry.latest,
                    change=round(entry.latest - prev_close, 4),
                    change_percent=entry.change_percent,
                    open=entry.latest,
                    high=entry.latest,
                    low=entry.latest,
                    previous_close=prev_close,
                    volume=0,
                    amount=0.0,
                    sparkline=sparkline,
                    signal_label=signal_point_label(entry.latest_point),
                )
            )
        candidates.sort(key=lambda entry: _sort_key(entry, sort_field), reverse=descending)
        return tuple(candidates[:limit])

    @staticmethod
    def _enrich_from_ranking(item: MarketAssetItem, ranking_entry) -> MarketAssetItem:
        if ranking_entry is None:
            return item
        sparkline = item.sparkline or tuple(round(point.price, 4) for point in ranking_entry.trend)
        label = item.signal_label or signal_point_label(ranking_entry.latest_point)
        if sparkline == item.sparkline and label == item.signal_label:
            return item
        return replace(item, sparkline=sparkline, signal_label=label)
