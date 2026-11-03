import base64
import hashlib
import hmac
import json
import time
from collections.abc import Callable

from ...domain.models import UserIdentity

MIN_SECRET_LENGTH = 32


def _encode(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode("ascii")


def _decode(text: str) -> bytes:
    return base64.urlsafe_b64decode(text + "=" * (-len(text) % 4))


class SignedSessionCodec:
    """无状态会话：HMAC-SHA256 签名的用户身份与过期时间，不落库。"""

    def __init__(self, secret: str, max_age_seconds: int, clock: Callable[[], float] = time.time):
        if len(secret) < MIN_SECRET_LENGTH:
            raise ValueError(f"会话密钥至少 {MIN_SECRET_LENGTH} 个字符")
        self._secret = secret.encode("utf-8")
        self.max_age_seconds = max_age_seconds
        self._clock = clock

    def issue(self, identity: UserIdentity) -> str:
        payload = {
            "uid": identity.user_id,
            "nick": identity.nickname,
            "avatar": identity.avatar_url,
            "exp": int(self._clock()) + self.max_age_seconds,
        }
        body = _encode(json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8"))
        return f"{body}.{self._sign(body)}"

    def parse(self, token: str) -> UserIdentity | None:
        body, _, signature = token.partition(".")
        if not body or not hmac.compare_digest(signature, self._sign(body)):
            return None
        try:
            payload = json.loads(_decode(body))
            if int(payload["exp"]) <= self._clock():
                return None
            return UserIdentity(
                user_id=int(payload["uid"]),
                nickname=str(payload["nick"]),
                avatar_url=payload.get("avatar"),
            )
        except (ValueError, KeyError, TypeError):
            return None

    def _sign(self, body: str) -> str:
        return _encode(hmac.new(self._secret, body.encode("ascii"), hashlib.sha256).digest())
