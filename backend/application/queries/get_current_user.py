from ...domain.models import UserIdentity
from ..ports.auth import SessionCodec


class GetCurrentUserQuery:
    def __init__(self, codec: SessionCodec):
        self._codec = codec

    def execute(self, session_token: str | None) -> UserIdentity | None:
        if not session_token:
            return None
        return self._codec.parse(session_token)
