from ...domain.models import RealtimeQuote
from ..ports.readers import QuoteReader
from ..security_ids import normalize_security_id


class GetQuoteQuery:
    def __init__(self, reader: QuoteReader):
        self._reader = reader

    def execute(self, secid: str) -> RealtimeQuote:
        return self._reader.read_quote(normalize_security_id(secid))
