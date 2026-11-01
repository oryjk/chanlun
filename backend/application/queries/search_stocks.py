from ...domain.errors import EmptySearchQuery
from ...domain.models import Stock
from ..ports.readers import StockSearchReader


class SearchStocksQuery:
    def __init__(self, reader: StockSearchReader):
        self._reader = reader

    def execute(self, query: str) -> list[Stock]:
        normalized = query.strip()
        if not normalized:
            raise EmptySearchQuery("请输入证券代码或名称")
        return self._reader.search(normalized)
