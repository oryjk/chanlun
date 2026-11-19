from dataclasses import dataclass
from hashlib import sha256
from secrets import token_urlsafe

from ..ports.auth import SessionCodec, SumlyTicketStore
from ...domain.models import UserIdentity


@dataclass(frozen=True)
class SumlyWebviewLogin:
    tickets: SumlyTicketStore
    sessions: SessionCodec

    def issue(self, user_id: int) -> str:
        code = token_urlsafe(32)
        self.tickets.save(_digest(code), user_id)
        return code

    def exchange(self, code: str) -> str | None:
        user_id = self.tickets.consume(_digest(code))
        if user_id is None:
            return None
        return self.sessions.issue(UserIdentity(user_id=user_id, nickname="记金用户", source="sumly"))


def _digest(code: str) -> str:
    return sha256(code.encode("ascii")).hexdigest()
