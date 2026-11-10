from datetime import timedelta
import logging
import secrets

from ...domain.app_auth import AppLoginResult, AuthRejected, LoginChallenge
from ..ports.app_auth import AppAuthReader, AppAuthWriter, AppleGateway


class AppAuthService:
    """Command entry points; session inspection is delegated to a read-only port."""

    def __init__(self, reader: AppAuthReader, writer: AppAuthWriter, apple: AppleGateway, now):
        self._reader, self._writer, self._apple, self._now = reader, writer, apple, now

    def challenge(self) -> LoginChallenge:
        now = self._now()
        value = LoginChallenge(secrets.token_urlsafe(32), secrets.token_urlsafe(32),
                               secrets.token_urlsafe(32), now, now + timedelta(minutes=5))
        self._writer.add_challenge(value)
        return value

    def login(self, challenge_id, identity_token, authorization_code, nickname=None) -> AppLoginResult:
        if not identity_token or not authorization_code:
            raise AuthRejected('登录凭证不完整，请重新登录')
        challenge = self._writer.consume_challenge(challenge_id, self._now())
        identity = self._apple.verify_and_exchange(identity_token, authorization_code, challenge.nonce)
        now = self._now()
        if now >= challenge.expires_at:
            raise AuthRejected('登录已超时，请重试')
        token = secrets.token_urlsafe(48)
        expires = now + timedelta(days=30)
        user = self._writer.login(identity, nickname, token, expires, challenge, now)
        return AppLoginResult(user, token, expires)

    def session(self, token):
        return self._reader.session(token, self._now()) if token else None

    def logout(self, token):
        if token:
            self._writer.logout(token)

    def delete_account(self, token):
        self._writer.delete_account(token, self._now())

    def retry_revocations(self):
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
        return self._writer.process_event(event_id, subject, kind, now,
                                          now.timestamp() if event_time is None else event_time)
