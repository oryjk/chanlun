import base64
from concurrent.futures import ThreadPoolExecutor
from dataclasses import replace
import hashlib
import hmac
import json
from pathlib import Path
import socket
from tempfile import TemporaryDirectory
from threading import Thread
import time
import unittest
from unittest.mock import patch
from urllib.error import HTTPError
from urllib.request import Request, urlopen

import uvicorn

from backend.application.queries.get_current_user import GetCurrentUserQuery
from backend.infrastructure.auth.signed_session_codec import SignedSessionCodec

BRIDGE_SECRET = "bridge-test-" * 3
SESSION_SECRET = "session-test-" * 3


class SumlyWebviewHTTPTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        temp = cls.enterClassContext(TemporaryDirectory())
        env = {
            "CHANLUN_SUMLY_BRIDGE_SECRET": BRIDGE_SECRET,
            "CHANLUN_SUMLY_BRIDGE_ISSUER_IP": "127.0.0.1",
            "CHANLUN_SUMLY_TICKET_STORE_PATH": str(Path(temp) / "tickets.sqlite3"),
            "CHANLUN_SESSION_SECRET": SESSION_SECRET,
            "CHANLUN_SESSION_COOKIE_SECURE": "true",
            "CHANLUN_RANKING_ENABLED": "false",
            "CHANLUN_RANKING_STORE_PATH": str(Path(temp) / "rankings.sqlite3"),
            "CHANLUN_APPLE_ENABLED": "false",
        }
        with patch.dict("os.environ", env):
            from backend.app.main import app
        cls.app = app
        sock = socket.socket()
        cls.addClassCleanup(sock.close)
        sock.bind(("127.0.0.1", 0))
        cls.base = f"http://127.0.0.1:{sock.getsockname()[1]}/api/auth"
        server = uvicorn.Server(uvicorn.Config(app, log_level="error"))
        thread = Thread(target=server.run, kwargs={"sockets": [sock]}, daemon=True)
        thread.start()

        def stop():
            server.should_exit = True
            thread.join(5)

        cls.addClassCleanup(stop)
        deadline = time.monotonic() + 5
        while not server.started and time.monotonic() < deadline:
            time.sleep(.01)
        if not server.started:
            raise RuntimeError("Test HTTP server did not start")

    def request(self, path, body=None, headers=None):
        data = json.dumps(body).encode() if body is not None else None
        request = Request(self.base + path, data=data, headers={"Content-Type": "application/json", **(headers or {})})
        try:
            response = urlopen(request, timeout=5)
        except HTTPError as error:
            response = error
        with response:
            raw = response.read()
            return response.status, response.headers, json.loads(raw) if raw else None

    def issue(self, user_id=42, **headers):
        return self.request("/sumly/tickets", {"user_id": user_id}, {
            "Authorization": f"Bearer {BRIDGE_SECRET}", **headers,
        })

    def test_ticket_requires_server_secret(self):
        self.assertEqual(self.request("/sumly/tickets", {"user_id": 42})[0], 401)
        self.assertEqual(self.issue(42, Authorization="Bearer rejected")[0], 401)

    def test_ticket_rejects_proxy_headers_even_from_the_allowed_ip(self):
        self.assertEqual(self.issue(42, **{"X-Forwarded-For": "127.0.0.1"})[0], 403)
        self.assertEqual(self.issue(42, Forwarded="for=127.0.0.1")[0], 403)

    def test_ticket_rejects_a_different_direct_source_ip(self):
        state = self.app.state
        bridge = replace(state.sumly_bridge, issuer_ip="192.0.2.42")
        with patch.object(state, "sumly_bridge", bridge):
            self.assertEqual(self.issue()[0], 403)

    def test_ticket_rejects_invalid_ids(self):
        for user_id in (0, -1, str(42), True, 2**63):
            with self.subTest(user_id=user_id):
                self.assertEqual(self.issue(user_id)[0], 422)

    def test_concurrent_exchange_uses_ticket_once_and_restores_scoped_cookie(self):
        status, headers, body = self.issue()
        self.assertEqual(status, 201)
        self.assertEqual(headers["Cache-Control"], "no-store")
        code = body["code"]
        with ThreadPoolExecutor(max_workers=2) as workers:
            results = list(workers.map(
                lambda _: self.request("/sumly/session", {"code": code}, {"X-Forwarded-For": "192.0.2.42"}),
                range(2),
            ))
        self.assertEqual(sorted(result[0] for result in results), [204, 401])
        session_headers = next(result[1] for result in results if result[0] == 204)
        self.assertEqual(session_headers["Cache-Control"], "no-store")
        cookie = session_headers["Set-Cookie"]
        for attribute in ("HttpOnly", "Secure", "SameSite=lax", "Max-Age=900"):
            self.assertIn(attribute, cookie)
        cookie_header = cookie.split(";", 1)[0]
        self.assertEqual(self.request("/session", headers={"Cookie": cookie_header})[2], {
            "authenticated": True, "user": {"user_id": "sumly:42", "nickname": "记金用户", "avatar_url": None},
        })
        identity = GetCurrentUserQuery(SignedSessionCodec(SESSION_SECRET, 3600)).execute(cookie_header.split("=", 1)[1])
        self.assertEqual(identity.tracking_key, "sumly:42")
        self.assertEqual(self.request("/session")[2], {"authenticated": False, "user": None})

    def test_pre_bridge_registration_cookie_keeps_its_numeric_identity(self):
        payload = {"uid": 42, "nick": "registration user", "avatar": None, "exp": int(time.time()) + 60}
        body = base64.urlsafe_b64encode(json.dumps(payload).encode()).rstrip(b"=")
        digest = hmac.digest(SESSION_SECRET.encode(), body, hashlib.sha256)
        tag = base64.urlsafe_b64encode(digest).rstrip(b"=")
        token = b".".join((body, tag)).decode()
        self.assertEqual(self.request("/session", headers={"Cookie": f"chanlun_session={token}"})[2], {
            "authenticated": True, "user": {"user_id": 42, "nickname": "registration user", "avatar_url": None},
        })
        identity = GetCurrentUserQuery(SignedSessionCodec(SESSION_SECRET, 3600)).execute(token)
        self.assertEqual(identity.tracking_key, "h5:42")


class SumlyTicketStoreTests(unittest.TestCase):
    def test_ticket_expires_after_sixty_seconds(self):
        from backend.infrastructure.persistence.sqlite_sumly_ticket import SqliteSumlyTicketStore

        with TemporaryDirectory() as directory:
            now = 100.0
            store = SqliteSumlyTicketStore(str(Path(directory) / "tickets.sqlite3"), clock=lambda: now)
            store.save("expired", 42)
            now += 60
            self.assertIsNone(store.consume("expired"))
