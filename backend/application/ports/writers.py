from datetime import datetime
from typing import Protocol

from ...domain.models import QuoteSeries


class QuoteWriter(Protocol):
    def append(self, secid: str, time: datetime, price: float) -> QuoteSeries: ...
