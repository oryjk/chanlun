from datetime import datetime

from ...domain.models import QuoteSeries, validate_security_id
from ..ports.writers import QuoteWriter


class RecordQuoteSampleCommand:
    def __init__(self, writer: QuoteWriter):
        self._writer = writer

    def execute(self, secid: str, time: datetime, price: float) -> QuoteSeries:
        return self._writer.append(validate_security_id(secid), time, price)
