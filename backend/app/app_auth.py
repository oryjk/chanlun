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
from ..infrastructure.persistence.sqlite_app_auth import SqliteAppAuthStore


@dataclass
class NativeAuthServices:
    commands: AppAuthService | None = None
    session: GetAppSession | None = None
    apple: AppleIdentityGateway | None = None
    _stop: Event = field(default_factory=Event)
    _thread: Thread | None = None

    def start(self):
        if not self.commands or self._thread:
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


def build_native_auth():
    if os.getenv('CHANLUN_APPLE_ENABLED', 'false').lower() != 'true':
        return NativeAuthServices()
    names = ('CHANLUN_APPLE_CLIENT_ID', 'CHANLUN_APPLE_TEAM_ID', 'CHANLUN_APPLE_KEY_ID',
             'CHANLUN_APPLE_KEY_PATH', 'CHANLUN_APPLE_TOKEN_ENCRYPTION_KEY')
    values = {name:os.getenv(name, '').strip() for name in names}
    if not all(values.values()) or len(values['CHANLUN_APPLE_TOKEN_ENCRYPTION_KEY']) < 32:
        raise ValueError('Apple 登录配置不完整，令牌加密密钥至少需要 32 字符')
    key = serialization.load_pem_private_key(Path(values[names[3]]).read_bytes(), password=None)
    if not isinstance(key, ec.EllipticCurvePrivateKey) or not isinstance(key.curve, ec.SECP256R1):
        raise ValueError('Apple 私钥必须为 P-256 椭圆曲线私钥')
    apple = AppleIdentityGateway(values[names[0]], values[names[1]], values[names[2]], key)
    store = SqliteAppAuthStore(os.getenv('CHANLUN_APP_AUTH_STORE_PATH', 'var/accounts/accounts.sqlite3'), values[names[4]])
    now = lambda: datetime.now(timezone.utc)
    return NativeAuthServices(AppAuthService(store, store, apple, now), GetAppSession(store, now), apple)
