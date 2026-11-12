from __future__ import annotations

import re

from ...domain.security import Exchange, SecurityId

_EASTMONEY_RE = re.compile(r"^(0|1)\.([0-9A-Za-z]{1,10})$")


def to_eastmoney(secid: str) -> str:
    sid = SecurityId.parse(secid)
    prefix = "1" if sid.exchange is Exchange.SH else "0"
    return f"{prefix}.{sid.code}"


def from_eastmoney(quote_id: str) -> str | None:
    if not isinstance(quote_id, str):
        return None
    match = _EASTMONEY_RE.fullmatch(quote_id.strip())
    if match is None:
        return None
    market, raw_code = match.groups()
    code = raw_code.upper()
    if market == "1":
        exchange = Exchange.SH
    elif code.startswith(("4", "8", "9")):
        exchange = Exchange.BJ
    else:
        exchange = Exchange.SZ
    return str(SecurityId(code=code, exchange=exchange))
