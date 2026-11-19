import os
from dataclasses import dataclass

from ..application.commands.sumly_webview import SumlyWebviewLogin
from ..infrastructure.auth.signed_session_codec import SignedSessionCodec
from ..infrastructure.persistence.sqlite_sumly_ticket import SqliteSumlyTicketStore
from .settings import Settings

SUMLY_SESSION_SECONDS = 15 * 60


@dataclass(frozen=True)
class SumlyBridge:
    secret: str
    login: SumlyWebviewLogin


def build_sumly_bridge(settings: Settings) -> SumlyBridge | None:
    secret = os.getenv("CHANLUN_SUMLY_BRIDGE_SECRET", "")
    if not secret:
        return None
    if len(secret) < 32:
        raise ValueError("CHANLUN_SUMLY_BRIDGE_SECRET 至少 32 个字符")
    path = os.getenv("CHANLUN_SUMLY_TICKET_STORE_PATH", "var/accounts/sumly-webview.sqlite3")
    session_codec = SignedSessionCodec(settings.session_secret, SUMLY_SESSION_SECONDS)
    return SumlyBridge(secret=secret, login=SumlyWebviewLogin(SqliteSumlyTicketStore(path), session_codec))
