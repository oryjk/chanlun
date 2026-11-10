"""Native App identities are independent of registration-system identities."""
from dataclasses import dataclass
from datetime import datetime


class AuthRejected(Exception):
    pass


class AuthUnavailable(Exception):
    pass


@dataclass(frozen=True)
class AppIdentity:
    user_id: str
    nickname: str


@dataclass(frozen=True)
class AppleIdentity:
    issuer: str
    subject: str
    refresh_token: str


@dataclass(frozen=True)
class LoginChallenge:
    challenge_id: str
    nonce: str
    state: str
    created_at: datetime
    expires_at: datetime


@dataclass(frozen=True)
class AppLoginResult:
    user: AppIdentity
    token: str
    expires_at: datetime


@dataclass(frozen=True)
class Revocation:
    job_id: str
    refresh_token: str
