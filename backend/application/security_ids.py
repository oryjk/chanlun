from __future__ import annotations

import re

from ..domain.errors import InvalidSecurityId
from ..domain.security import Exchange, SecurityId

_CANONICAL_RE = re.compile(r"^[0-9A-Z]{1,10}\.(SH|SZ|BJ)$")
_LEGACY_RE = re.compile(r"^(0|1)\.([0-9A-Za-z]{1,10})$")
_UNSUPPORTED_LEGACY_RE = re.compile(r"^(116|105|106|107)\.[0-9A-Za-z.-]{1,24}$")


def normalize_security_id(value: str) -> str:
    if not isinstance(value, str):
        raise InvalidSecurityId("证券标识格式不正确")
    cleaned = value.strip()
    upper = cleaned.upper()
    if _CANONICAL_RE.fullmatch(upper):
        return str(SecurityId.parse(upper))
    if _UNSUPPORTED_LEGACY_RE.fullmatch(cleaned):
        raise InvalidSecurityId("暂不支持该市场")
    match = _LEGACY_RE.fullmatch(cleaned)
    if match is not None:
        market, raw_code = match.groups()
        code = raw_code.upper()
        if market == "1":
            exchange = Exchange.SH
        elif code.startswith(("4", "8", "9")):
            exchange = Exchange.BJ
        else:
            exchange = Exchange.SZ
        return str(SecurityId(code=code, exchange=exchange))
    raise InvalidSecurityId("证券标识格式不正确")


def legacy_security_id(secid: str) -> str:
    sid = SecurityId.parse(normalize_security_id(secid))
    prefix = "1" if sid.exchange is Exchange.SH else "0"
    return f"{prefix}.{sid.code}"
