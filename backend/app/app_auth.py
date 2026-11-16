"""Composition root for native identity; disabled unless explicitly configured."""
from dataclasses import dataclass, field
from datetime import datetime, timezone
import logging
import os
from pathlib import Path
from threading import Event, Thread

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import ec

from ..application.commands.app_login import AppAuthService
from ..application.queries.app_session import GetAppSession
from ..infrastructure.auth.apple_gateway import AppleIdentityGateway
from ..infrastructure.auth.smtp_gateway import SmtpEmailGateway
from ..infrastructure.persistence.sqlite_app_auth import SqliteAppAuthStore


@dataclass
class NativeAuthServices:
    commands: AppAuthService | None = None
    session: GetAppSession | None = None
    apple: AppleIdentityGateway | None = None
    _stop: Event = field(default_factory=Event)
    _thread: Thread | None = None

    def start(self):
        if not self.commands or not self.apple or self._thread:
            return
        def run():
            while not self._stop.is_set():
                try:
                    self.commands.retry_revocations()
                except Exception:
                    logging.getLogger(__name__).warning('Apple 授权撤销任务暂时不可用')
                self._stop.wait(60)
        self._thread = Thread(target=run, name='apple-revocations', daemon=True)
        self._thread.start()

    def stop(self):
        self._stop.set()
        if self._thread:
            self._thread.join(timeout=2)


def _build_apple_gateway() -> tuple[AppleIdentityGateway, str] | tuple[None, None]:
    if os.getenv('CHANLUN_APPLE_ENABLED', 'false').lower() != 'true':
        return None, None
    names = (
        'CHANLUN_APPLE_CLIENT_ID',
        'CHANLUN_APPLE_TEAM_ID',
        'CHANLUN_APPLE_KEY_ID',
        'CHANLUN_APPLE_KEY_PATH',
        'CHANLUN_APPLE_TOKEN_ENCRYPTION_KEY',
    )
    values = {name: os.getenv(name, '').strip() for name in names}
    if not all(values.values()) or len(values['CHANLUN_APPLE_TOKEN_ENCRYPTION_KEY']) < 32:
        raise ValueError('Apple 登录配置不完整，令牌加密密钥至少需要 32 字符')
    key = serialization.load_pem_private_key(Path(values[names[3]]).read_bytes(), password=None)
    if not isinstance(key, ec.EllipticCurvePrivateKey) or not isinstance(key.curve, ec.SECP256R1):
        raise ValueError('Apple 私钥必须为 P-256 椭圆曲线私钥')
    apple = AppleIdentityGateway(values[names[0]], values[names[1]], values[names[2]], key)
    return apple, values['CHANLUN_APPLE_TOKEN_ENCRYPTION_KEY']


def _first_env(*names: str) -> str:
    for name in names:
        value = os.getenv(name, '').strip()
        if value:
            return value
    return ''


def _build_email_gateway() -> SmtpEmailGateway | None:
    if os.getenv('CHANLUN_EMAIL_AUTH_ENABLED', '').strip().lower() == 'false':
        return None
    smtp_host = _first_env('CHANLUN_SMTP_HOST', 'SMTP_HOST')
    if not smtp_host:
        return None
    tls_mode = (
        _first_env('CHANLUN_SMTP_TLS_MODE', 'SMTP_TLS_MODE').lower()
        or 'tls'
    )
    default_port = '587' if tls_mode in ('starttls', 'start_tls') else '465'
    smtp_port = int(_first_env('CHANLUN_SMTP_PORT', 'SMTP_PORT') or default_port)
    username = _first_env('CHANLUN_SMTP_USERNAME', 'SMTP_USERNAME')
    password = os.getenv('CHANLUN_SMTP_PASSWORD') or os.getenv('SMTP_PASSWORD') or ''
    sender = _first_env(
        'CHANLUN_SMTP_FROM',
        'CHANLUN_SMTP_FROM_EMAIL',
        'SMTP_FROM_EMAIL',
    ) or username
    if not sender:
        raise ValueError(
            'SMTP 配置需提供发件人邮箱 CHANLUN_SMTP_FROM / SMTP_FROM_EMAIL 或用户名'
        )
    explicit_from_name = os.getenv('CHANLUN_SMTP_FROM_NAME', '').strip()
    fallback_from_name = os.getenv('SMTP_FROM_NAME', '').strip()
    from_name = (
        explicit_from_name
        or (fallback_from_name if fallback_from_name and fallback_from_name != '策略家' else '缠观')
    )
    return SmtpEmailGateway(
        host=smtp_host,
        port=smtp_port,
        username=username,
        password=password,
        sender=sender,
        tls_mode=tls_mode,
        from_name=from_name,
    )


def build_native_auth():
    apple, apple_secret = _build_apple_gateway()
    email_gateway = _build_email_gateway()
    email_flag = os.getenv('CHANLUN_EMAIL_AUTH_ENABLED', '').strip().lower()
    email_enabled = (email_flag != 'false') and (email_flag == 'true' or email_gateway is not None)
    if apple is None and not email_enabled:
        return NativeAuthServices()
    encryption_key = (
        apple_secret
        or os.getenv('CHANLUN_APP_AUTH_SECRET', '').strip()
        or os.getenv('CHANLUN_APPLE_TOKEN_ENCRYPTION_KEY', '').strip()
        or os.getenv('CHANLUN_SESSION_SECRET', '').strip()
    )
    if len(encryption_key) < 32:
        if apple is not None:
            raise ValueError('Apple 登录配置不完整，令牌加密密钥至少需要 32 字符')
        encryption_key = 'chanlun-email-auth-default-encryption-key'
    store = SqliteAppAuthStore(
        os.getenv('CHANLUN_APP_AUTH_STORE_PATH', 'var/accounts/accounts.sqlite3'),
        encryption_key,
    )
    code_pepper = _first_env('CHANLUN_EMAIL_CODE_PEPPER', 'EMAIL_CODE_PEPPER')
    now = lambda: datetime.now(timezone.utc)
    return NativeAuthServices(
        AppAuthService(
            store,
            store,
            apple,
            now,
            email_gateway=email_gateway,
            code_pepper=code_pepper,
        ),
        GetAppSession(store, now),
        apple,
    )
