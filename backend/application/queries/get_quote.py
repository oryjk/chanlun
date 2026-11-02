from ...domain.models import RealtimeQuote, validate_security_id
from ..ports.readers import QuoteReader


class GetQuoteQuery:
    def __init__(self, reader: QuoteReader):
        self._reader = reader

    def execute(self, secid: str) -> RealtimeQuote:
        return self._reader.read_quote(validate_security_id(secid))
