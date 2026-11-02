from dataclasses import dataclass

from .record_quote_sample import RecordQuoteSampleCommand


@dataclass(frozen=True)
class CommandServices:
    record_quote_sample: RecordQuoteSampleCommand
