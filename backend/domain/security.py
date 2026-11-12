from __future__ import annotations

import re
from dataclasses import dataclass
from enum import Enum

from .errors import InvalidSecurityId

_CANONICAL_RE = re.compile(r"^([0-9A-Z]{1,10})\.(SH|SZ|BJ)$")


class Exchange(str, Enum):
    SH = "SH"
    SZ = "SZ"
    BJ = "BJ"


@dataclass(frozen=True)
class SecurityId:
    code: str
    exchange: Exchange

    def __str__(self) -> str:
        return f"{self.code}.{self.exchange.value}"

    @classmethod
    def parse(cls, value: str) -> SecurityId:
        if not isinstance(value, str):
            raise InvalidSecurityId("证券标识格式不正确")
        match = _CANONICAL_RE.fullmatch(value)
        if match is None:
            raise InvalidSecurityId("证券标识格式不正确")
        code, exchange = match.groups()
        return cls(code=code, exchange=Exchange(exchange))
