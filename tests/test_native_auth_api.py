import json
import socket
import tempfile
import threading
import time
import unittest
from unittest.mock import patch
from datetime import datetime, timezone
from pathlib import Path
from urllib.error import HTTPError
from urllib.request import Request, urlopen

from fastapi import FastAPI
import uvicorn

from backend.app.app_auth import NativeAuthServices, build_native_auth
from backend.application.commands.app_login import AppAuthService
from backend.application.queries.app_session import GetAppSession
from backend.domain.app_auth import AppleIdentity, AuthRejected
from backend.infrastructure.persistence.sqlite_app_auth import SqliteAppAuthStore
from backend.interfaces.api.app_auth_routes import router


class FakeApple:
    def verify_and_exchange(self, token, code, nonce):
        if token != 'valid':
            raise AuthRejected('凭证无效')
        return AppleIdentity('https://appleid.apple.com', 'subject', 'refresh')
    def revoke(self, token):
        pass
    def verify_notification(self, payload):
        raise AuthRejected('通知无效')


class NativeAuthHTTPTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory()
        store = SqliteAppAuthStore(Path(cls.temp.name) / 'auth.db', 'test' * 10)
        now = lambda: datetime.now(timezone.utc)
        apple = FakeApple()
        service = AppAuthService(store, store, apple, now)
        app = FastAPI()
        app.state.native_auth = NativeAuthServices(service, GetAppSession(store, now), apple)
        app.include_router(router)
        cls.app = app
        sock = socket.socket()
        sock.bind(('127.0.0.1', 0))
        cls.base = f'http://127.0.0.1:{sock.getsockname()[1]}/api/app-auth'
        cls.server = uvicorn.Server(uvicorn.Config(app, log_level='error'))
        cls.thread = threading.Thread(target=lambda: cls.server.run(sockets=[sock]), daemon=True)
        cls.thread.start()
        deadline = time.monotonic() + 5
        while not cls.server.started and time.monotonic() < deadline:
            time.sleep(.01)
        if not cls.server.started:
            raise RuntimeError('Test HTTP server did not start')

    @classmethod
    def tearDownClass(cls):
        cls.server.should_exit = True
        cls.thread.join(5)
        cls.temp.cleanup()

    def request(self, path, body=None, token=None):
        headers = {'Content-Type':'application/json'}
        if token:
            headers['Authorization'] = 'Bearer ' + token
        request = Request(self.base + path, data=json.dumps(body).encode() if body is not None else None, headers=headers)
        try:
            with urlopen(request, timeout=5) as response:
                return response.status, json.loads(response.read())
        except HTTPError as error:
            return error.code, json.loads(error.read())

    def login(self):
        status, challenge = self.request('/challenge', {})
        self.assertEqual(status, 200)
        return self.request('/apple', {'challenge_id':challenge['challenge_id'], 'identity_token':'valid', 'authorization_code':'code'})

    def test_login_session_logout_and_guest_over_real_http(self):
        self.assertEqual(self.request('/session'), (200, {'authenticated':False,'user':None}))
        status, login = self.login()
        self.assertEqual(status, 200)
        token = login['token']
        self.assertEqual(self.request('/session', token=token)[1]['user'], login['user'])
        self.assertEqual(self.request('/logout', {}, token)[0], 200)
        self.assertFalse(self.request('/session', token=token)[1]['authenticated'])

    def test_delete_account_invalidates_token_over_http(self):
        _, login = self.login()
        self.assertEqual(self.request('/delete-account', {}, login['token'])[0], 200)
        self.assertFalse(self.request('/session', token=login['token'])[1]['authenticated'])
        self.assertEqual(self.request('/delete-account', {}, login['token'])[0], 200)

    def test_invalid_credentials_and_signed_notification_are_rejected(self):
        self.assertEqual(self.request('/apple', {'challenge_id':'unknown','identity_token':'x','authorization_code':'x'})[0], 401)
        self.assertEqual(self.request('/apple', {})[0], 422)
        self.assertEqual(self.request('/apple-notifications', {'payload':'forged'})[0], 401)


class NativeAuthConfigurationTests(unittest.TestCase):
    def test_unconfigured_auth_is_disabled_without_creating_storage(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'not-created.db'
            with patch.dict('os.environ', {'CHANLUN_APPLE_ENABLED':'false', 'CHANLUN_APP_AUTH_STORE_PATH':str(path)}):
                services = build_native_auth()
            self.assertIsNone(services.commands)
            self.assertFalse(path.exists())

    def test_invalid_key_is_rejected_before_creating_account_database(self):
        with tempfile.TemporaryDirectory() as directory:
            key = Path(directory) / 'invalid.p8'
            key.write_text('not a private key')
            store = Path(directory) / 'accounts.db'
            values = {'CHANLUN_APPLE_ENABLED':'true', 'CHANLUN_APPLE_CLIENT_ID':'cn.test',
                      'CHANLUN_APPLE_TEAM_ID':'team', 'CHANLUN_APPLE_KEY_ID':'key',
                      'CHANLUN_APPLE_KEY_PATH':str(key), 'CHANLUN_APPLE_TOKEN_ENCRYPTION_KEY':'s' * 32,
                      'CHANLUN_APP_AUTH_STORE_PATH':str(store)}
            with patch.dict('os.environ', values):
                with self.assertRaises(ValueError):
                    build_native_auth()
            self.assertFalse(store.exists())
