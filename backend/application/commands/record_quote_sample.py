from datetime import datetime

from ...domain.models import QuoteSeries
from ..ports.writers import QuoteWriter
from ..security_ids import normalize_security_id


class RecordQuoteSampleCommand:
    def __init__(self, writer: QuoteWriter):
        self._writer = writer

    def execute(self, secid: str, time: datetime, price: float) -> QuoteSeries:
        return self._writer.append(normalize_security_id(secid), time, price)
