from ...domain.errors import MarketDataNotFound, MarketDataUnavailable
from ...domain.models import QuoteSeries
from ...domain.services.quote_series import merge_quote_series
from ..ports.readers import IntradayQuoteReader, QuoteSeriesReader
from ..security_ids import normalize_security_id


class GetQuoteSeriesQuery:
    def __init__(self, reader: QuoteSeriesReader, intraday_reader: IntradayQuoteReader):
        self._reader = reader
        self._intraday_reader = intraday_reader

    def execute(self, secid: str) -> QuoteSeries:
        normalized_secid = normalize_security_id(secid)
        try:
            history = self._intraday_reader.read_intraday(normalized_secid)
        except (MarketDataNotFound, MarketDataUnavailable):
            history = QuoteSeries(secid=normalized_secid, points=())
        local = self._reader.read_series(normalized_secid)
        return merge_quote_series(history, local)
