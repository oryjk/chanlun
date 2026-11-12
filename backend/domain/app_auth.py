"""Native App identities are independent of registration-system identities."""
from dataclasses import dataclass
from datetime import datetime
import hashlib
import hmac
import re
import secrets

EMAIL_PATTERN = re.compile(r'^[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,}$')
CODE_PATTERN = re.compile(r'^\d{6}$')


class AuthRejected(Exception):
    pass


class AuthUnavailable(Exception):
    pass


class AuthValidationError(Exception):
    pass


class AuthConflict(Exception):
    pass


class AuthRateLimited(Exception):
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
class EmailVerificationChallenge:
    email_key: str
    code_digest: str
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


def normalize_email(raw: str) -> str:
    value = str(raw or '').strip().lower()
    if not value or len(value) > 254 or '\r' in value or '\n' in value or not EMAIL_PATTERN.match(value):
        raise AuthValidationError('邮箱格式不正确')
    return value


def validate_password(raw: str) -> str:
    value = str(raw or '')
    if len(value) < 8 or len(value) > 128:
        raise AuthValidationError('密码长度需为 8 到 128 位')
    return value


def validate_verification_code(raw: str) -> str:
    value = str(raw or '').strip()
    if not CODE_PATTERN.match(value):
        raise AuthRejected('验证码错误或已过期')
    return value


def default_email_nickname(normalized_email: str, nickname: str | None) -> str:
    cleaned = str(nickname or '').strip()[:60]
    if cleaned:
        return cleaned
    local_part = normalized_email.partition('@')[0].strip()[:60]
    return local_part or '缠观用户'


def hash_password(password: str) -> str:
    salt = secrets.token_bytes(16)
    derived = hashlib.scrypt(password.encode('utf-8'), salt=salt, n=16384, r=8, p=1, dklen=32)
    return f"scrypt$16384$8$1${salt.hex()}${derived.hex()}"


def verify_password(password_hash: str, password: str) -> bool:
    try:
        scheme, n_str, r_str, p_str, salt_hex, expected_hex = password_hash.split('$', 5)
        if scheme != 'scrypt':
            return False
        salt = bytes.fromhex(salt_hex)
        expected = bytes.fromhex(expected_hex)
        derived = hashlib.scrypt(
            password.encode('utf-8'),
            salt=salt,
            n=int(n_str),
            r=int(r_str),
            p=int(p_str),
            dklen=len(expected),
        )
        return hmac.compare_digest(derived, expected)
    except Exception:
        return False

