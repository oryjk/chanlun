import unittest

from backend.application.commands.login_with_webview_code import LoginWithWebviewCodeCommand
from backend.application.queries.get_current_user import GetCurrentUserQuery
from backend.application.queries.get_mini_program_link import GetMiniProgramLinkQuery
from backend.domain.errors import AuthenticationFailed, IdentityServiceUnavailable
from backend.domain.models import UserIdentity
from backend.infrastructure.auth.registration_gateway import RegistrationSystemGateway
from backend.infrastructure.auth.signed_session_codec import SignedSessionCodec

SECRET = "s" * 32
BASE = "http://registration.test/api/v1/app"


class FakeClock:
    def __init__(self, now=1_000_000.0):
        self.now = now

    def __call__(self):
        return self.now


class RecordingTransport:
    def __init__(self, responses):
        self.responses = list(responses)
        self.calls = []

    def __call__(self, method, url, body, headers):
        self.calls.append((method, url, body, headers))
        return self.responses.pop(0)


def ok(data):
    return 200, {"code": 0, "message": "ok", "data": data}


class SessionCodecTests(unittest.TestCase):
    def test_round_trip_until_expiry(self):
        clock = FakeClock()
        codec = SignedSessionCodec(SECRET, 60, clock)
        token = codec.issue(UserIdentity(7, "球员小王", "/uploads/a.png"))

        self.assertEqual(codec.parse(token), UserIdentity(7, "球员小王", "/uploads/a.png"))
        clock.now += 61
        self.assertIsNone(codec.parse(token))

    def test_tampered_or_foreign_token_is_rejected(self):
        codec = SignedSessionCodec(SECRET, 60)
        token = codec.issue(UserIdentity(7, "a"))
        body, signature = token.split(".")

        self.assertIsNone(codec.parse(f"{body}x.{signature}"))
        self.assertIsNone(SignedSessionCodec("t" * 32, 60).parse(token))
        self.assertIsNone(codec.parse("garbage"))

    def test_short_secret_is_refused(self):
        with self.assertRaises(ValueError):
            SignedSessionCodec("short", 60)


class RegistrationGatewayTests(unittest.TestCase):
    def test_exchange_reads_profile_with_registration_token(self):
        transport = RecordingTransport([ok({"token": "jwt"}), ok({"id": 7, "nickname": "小王", "avatar_url": None})])

        identity = RegistrationSystemGateway(BASE + "/", transport).exchange_webview_code("code-1")

        self.assertEqual(identity, UserIdentity(7, "小王", None))
        self.assertEqual(transport.calls[0][:3], ("POST", f"{BASE}/auth/webview-codes/exchange", {"code": "code-1"}))
        self.assertEqual(transport.calls[1][1], f"{BASE}/users/me")
        self.assertEqual(transport.calls[1][3], {"Authorization": "Bearer jwt"})

    def test_rejected_code_is_authentication_failure(self):
        transport = RecordingTransport([(401, {"code": 401, "message": "code 无效或已过期", "data": None})])
        with self.assertRaises(AuthenticationFailed):
            RegistrationSystemGateway(BASE, transport).exchange_webview_code("used")

    def test_server_error_or_bad_payload_is_unavailable(self):
        for responses in ([(502, None)], [ok({})], [ok({"token": "jwt"}), ok({"nickname": "no id"})]):
            with self.assertRaises(IdentityServiceUnavailable):
                RegistrationSystemGateway(BASE, RecordingTransport(responses)).exchange_webview_code("c")

    def test_chanlun_link(self):
        transport = RecordingTransport([ok({"url": "https://wxaurl.cn/abc"})])
        link = RegistrationSystemGateway(BASE, transport).chanlun_link("secid=1.600519")

        self.assertEqual(link, "https://wxaurl.cn/abc")
        self.assertEqual(transport.calls[0][2], {"return_query": "secid=1.600519"})


class StubGateway:
    def __init__(self):
        self.codes = []

    def exchange_webview_code(self, code):
        self.codes.append(code)
        return UserIdentity(7, "小王")


class StubLinkReader:
    def __init__(self):
        self.queries = []

    def chanlun_link(self, return_query):
        self.queries.append(return_query)
        return "https://wxaurl.cn/abc"


class AuthUseCaseTests(unittest.TestCase):
    def test_login_issues_session_for_exchanged_identity(self):
        codec = SignedSessionCodec(SECRET, 3600)
        gateway = StubGateway()

        result = LoginWithWebviewCodeCommand(gateway, codec).execute("  code-1 ")

        self.assertEqual(gateway.codes, ["code-1"])
        self.assertEqual(result.max_age_seconds, 3600)
        self.assertEqual(GetCurrentUserQuery(codec).execute(result.session_token), UserIdentity(7, "小王"))

    def test_blank_code_never_reaches_gateway(self):
        gateway = StubGateway()
        with self.assertRaises(AuthenticationFailed):
            LoginWithWebviewCodeCommand(gateway, SignedSessionCodec(SECRET, 60)).execute("  ")
        self.assertEqual(gateway.codes, [])

    def test_missing_session_is_guest(self):
        self.assertIsNone(GetCurrentUserQuery(SignedSessionCodec(SECRET, 60)).execute(None))

    def test_link_query_drops_unsafe_return_query(self):
        reader = StubLinkReader()
        query = GetMiniProgramLinkQuery(reader)

        query.execute("?secid=1.600519&timeframe=30m")
        query.execute("https://evil.test/?a=1")

        self.assertEqual(reader.queries, ["secid=1.600519&timeframe=30m", ""])


if __name__ == "__main__":
    unittest.main()
