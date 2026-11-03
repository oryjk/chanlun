from dataclasses import dataclass

from ...domain.errors import AuthenticationFailed
from ...domain.models import UserIdentity
from ..ports.auth import IdentityGateway, SessionCodec


@dataclass(frozen=True)
class LoginResult:
    identity: UserIdentity
    session_token: str
    max_age_seconds: int


class LoginWithWebviewCodeCommand:
    def __init__(self, gateway: IdentityGateway, codec: SessionCodec):
        self._gateway = gateway
        self._codec = codec

    def execute(self, code: str) -> LoginResult:
        normalized = code.strip()
        if not normalized:
            raise AuthenticationFailed("登录凭证无效或已过期")
        identity = self._gateway.exchange_webview_code(normalized)
        return LoginResult(
            identity=identity,
            session_token=self._codec.issue(identity),
            max_age_seconds=self._codec.max_age_seconds,
        )
