from datetime import timedelta
import hashlib
import logging
import secrets

from ...domain.app_auth import (
    AppLoginResult,
    AuthRejected,
    AuthUnavailable,
    EmailVerificationChallenge,
    LoginChallenge,
    default_email_nickname,
    hash_password,
    normalize_email,
    validate_password,
    validate_verification_code,
    verify_password,
)
from ..ports.app_auth import AppAuthReader, AppAuthWriter, AppleGateway, EmailCodeGateway


def _email_subject_key(normalized_email: str) -> str:
    return hashlib.sha256(('email:' + normalized_email).encode('utf-8')).hexdigest()


def _email_code_digest(email_key: str, code: str) -> str:
    return hashlib.sha256((email_key + ':' + code).encode('utf-8')).hexdigest()


class AppAuthService:
    """Command entry points; session inspection is delegated to a read-only port."""

    def __init__(
        self,
        reader: AppAuthReader,
        writer: AppAuthWriter,
        apple: AppleGateway | None,
        now,
        email_gateway: EmailCodeGateway | None = None,
    ):
        self._reader = reader
        self._writer = writer
        self._apple = apple
        self._now = now
        self._email = email_gateway
        self._dummy_password_hash = hash_password('dummy-password-for-timing')

    def challenge(self) -> LoginChallenge:
        now = self._now()
        value = LoginChallenge(
            secrets.token_urlsafe(32),
            secrets.token_urlsafe(32),
            secrets.token_urlsafe(32),
            now,
            now + timedelta(minutes=5),
        )
        self._writer.add_challenge(value)
        return value

    def login(self, challenge_id, identity_token, authorization_code, nickname=None) -> AppLoginResult:
        if not identity_token or not authorization_code:
            raise AuthRejected('登录凭证不完整，请重新登录')
        if self._apple is None:
            raise AuthUnavailable('Apple 登录尚未配置，请使用邮箱账号登录')
        challenge = self._writer.consume_challenge(challenge_id, self._now())
        identity = self._apple.verify_and_exchange(identity_token, authorization_code, challenge.nonce)
        now = self._now()
        if now >= challenge.expires_at:
            raise AuthRejected('登录已超时，请重试')
        token = secrets.token_urlsafe(48)
        expires = now + timedelta(days=30)
        user = self._writer.login(identity, nickname, token, expires, challenge, now)
        return AppLoginResult(user, token, expires)

    def send_email_code(self, email: str) -> None:
        normalized = normalize_email(email)
        if self._email is None:
            raise AuthUnavailable('邮箱验证码服务尚未配置，行情仍可正常使用')
        now = self._now()
        email_key = _email_subject_key(normalized)
        code = f"{secrets.randbelow(1_000_000):06d}"
        challenge = EmailVerificationChallenge(
            email_key=email_key,
            code_digest=_email_code_digest(email_key, code),
            created_at=now,
            expires_at=now + timedelta(minutes=10),
        )
        self._writer.save_email_code(challenge, cooldown_seconds=60)
        self._email.send_verification_code(normalized, code, 10)

    def register_email(
        self,
        email: str,
        code: str,
        password: str,
        nickname: str | None = None,
    ) -> AppLoginResult:
        normalized = normalize_email(email)
        clean_password = validate_password(password)
        clean_code = validate_verification_code(code)
        email_key = _email_subject_key(normalized)
        code_digest = _email_code_digest(email_key, clean_code)
        now = self._now()
        token = secrets.token_urlsafe(48)
        expires = now + timedelta(days=30)
        user = self._writer.register_email(
            email_key=email_key,
            code_digest=code_digest,
            password_hash=hash_password(clean_password),
            nickname=default_email_nickname(normalized, nickname),
            token=token,
            expires_at=expires,
            now=now,
        )
        return AppLoginResult(user, token, expires)

    def login_email(self, email: str, password: str) -> AppLoginResult:
        normalized = normalize_email(email)
        raw_password = str(password or '')
        if not raw_password or len(raw_password) > 128:
            raise AuthRejected('邮箱或密码错误')
        email_key = _email_subject_key(normalized)
        found = self._reader.find_email_credentials(email_key)
        target_hash = found[1] if found else self._dummy_password_hash
        valid = verify_password(target_hash, raw_password)
        if not found or not valid:
            raise AuthRejected('邮箱或密码错误')
        user = found[0]
        now = self._now()
        token = secrets.token_urlsafe(48)
        expires = now + timedelta(days=30)
        self._writer.create_session(user.user_id, token, expires, now)
        return AppLoginResult(user, token, expires)

    def session(self, token):
        return self._reader.session(token, self._now()) if token else None

    def logout(self, token):
        if token:
            self._writer.logout(token)

    def delete_account(self, token):
        self._writer.delete_account(token, self._now())

    def retry_revocations(self):
        if self._apple is None:
            return
        for job in self._reader.pending_revocations():
            try:
                self._apple.revoke(job.refresh_token)
            except Exception:
                # No exception detail: HTTP libraries may include a credential in it.
                logging.getLogger(__name__).warning('Apple 授权撤销暂未完成，将稍后重试')
            else:
                self._writer.finish_revocation(job.job_id)

    def process_event(self, event_id, subject, kind, event_time=None):
        now = self._now()
        return self._writer.process_event(
            event_id,
            subject,
            kind,
            now,
            now.timestamp() if event_time is None else event_time,
        )
