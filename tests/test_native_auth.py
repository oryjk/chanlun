import hashlib
import tempfile
import unittest
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta, timezone
from pathlib import Path

from backend.application.commands.app_login import AppAuthService
from backend.domain.app_auth import AppleIdentity, AuthRejected
from backend.infrastructure.persistence.sqlite_app_auth import SqliteAppAuthStore


class FakeApple:
    def __init__(self):
        self.revoked = []
        self.available = True

    def verify_and_exchange(self, token, code, nonce):
        if token != 'valid' or not code:
            raise AuthRejected('凭证无效')
        return AppleIdentity('https://appleid.apple.com', 'apple-subject', 'refresh-secret')

    def revoke(self, token):
        if not self.available:
            raise ConnectionError('offline')
        self.revoked.append(token)


class NativeAuthTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.path = Path(self.temp.name) / 'accounts.sqlite3'
        self.store = SqliteAppAuthStore(self.path, 'test-encryption-key')
        self.apple = FakeApple()
        self.now = datetime(2026, 11, 10, 6, tzinfo=timezone.utc)
        self.service = AppAuthService(self.store, self.store, self.apple, lambda: self.now)

    def tearDown(self):
        self.temp.cleanup()

    def login(self, nickname=None):
        challenge = self.service.challenge()
        return self.service.login(challenge.challenge_id, 'valid', 'code', nickname)

    def test_challenge_is_random_and_expires_in_five_minutes(self):
        first, second = self.service.challenge(), self.service.challenge()
        self.assertNotEqual(first.nonce, second.nonce)
        self.assertEqual(first.expires_at - self.now, timedelta(minutes=5))
        self.assertNotEqual(first.state, second.state)

    def test_same_apple_identity_keeps_internal_uuid_without_h5_integer_id(self):
        first, second = self.login('甲'), self.login()
        self.assertEqual(first.user.user_id, second.user.user_id)
        self.assertIsInstance(first.user.user_id, str)
        self.assertEqual(second.user.nickname, '甲')
        self.assertEqual(first.expires_at - self.now, timedelta(days=30))

    def test_concurrent_challenge_replay_issues_only_one_session(self):
        c = self.service.challenge()
        def attempt(_):
            try:
                return self.service.login(c.challenge_id, 'valid', 'code').user
            except AuthRejected:
                return None
        with ThreadPoolExecutor(max_workers=4) as pool:
            results = list(pool.map(attempt, range(4)))
        self.assertEqual(sum(item is not None for item in results), 1)

    def test_expired_or_invalid_challenge_never_logs_in(self):
        c = self.service.challenge()
        self.now += timedelta(minutes=5)
        for value in (c.challenge_id, 'unknown'):
            with self.assertRaises(AuthRejected):
                self.service.login(value, 'valid', 'code')

    def test_bad_credentials_burn_challenge(self):
        c = self.service.challenge()
        with self.assertRaises(AuthRejected):
            self.service.login(c.challenge_id, 'bad', 'code')
        with self.assertRaises(AuthRejected):
            self.service.login(c.challenge_id, 'valid', 'code')

    def test_session_expiry_and_logout(self):
        result = self.login()
        self.assertEqual(self.service.session(result.token), result.user)
        self.service.logout(result.token)
        self.assertIsNone(self.service.session(result.token))
        result = self.login()
        self.now = result.expires_at
        self.assertIsNone(self.service.session(result.token))

    def test_session_and_refresh_secrets_are_not_plaintext_on_disk(self):
        result = self.login()
        raw = self.path.read_bytes()
        self.assertNotIn(result.token.encode(), raw)
        self.assertNotIn(b'refresh-secret', raw)
        self.assertIn(hashlib.sha256(result.token.encode()).hexdigest().encode(), raw)

    def test_delete_revokes_every_session_and_apple_token(self):
        first, second = self.login(), self.login()
        self.service.delete_account(first.token)
        self.assertIsNone(self.service.session(first.token))
        self.assertIsNone(self.service.session(second.token))
        self.assertEqual(self.apple.revoked, [])
        self.assertEqual(len(self.store.pending_revocations()), 1)
        self.service.retry_revocations()
        self.assertEqual(self.apple.revoked, ['refresh-secret'])

    def test_revocation_outage_does_not_restore_deleted_account(self):
        result = self.login()
        self.apple.available = False
        self.service.delete_account(result.token)
        self.assertIsNone(self.service.session(result.token))
        self.assertEqual(len(self.store.pending_revocations()), 1)
        self.apple.available = True
        self.service.retry_revocations()
        self.assertEqual(self.store.pending_revocations(), ())

    def test_challenge_created_before_deletion_cannot_recreate_account(self):
        result = self.login()
        stale = self.service.challenge()
        self.now += timedelta(seconds=1)
        self.service.delete_account(result.token)
        with self.assertRaises(AuthRejected):
            self.service.login(stale.challenge_id, 'valid', 'code')
        self.now += timedelta(seconds=1)
        self.assertNotEqual(self.login().user.user_id, result.user.user_id)

    def test_signed_notification_is_deduplicated_and_revokes_sessions(self):
        result = self.login()
        self.assertTrue(self.service.process_event('event-1', 'apple-subject', 'consent-revoked'))
        self.assertFalse(self.service.process_event('event-1', 'apple-subject', 'consent-revoked'))
        self.assertIsNone(self.service.session(result.token))

    def test_revocation_during_first_login_blocks_session_creation(self):
        challenge = self.service.challenge()
        self.now += timedelta(seconds=1)
        self.service.process_event('first-login-revoked', 'apple-subject', 'consent-revoked')
        with self.assertRaises(AuthRejected):
            self.service.login(challenge.challenge_id, 'valid', 'code')

    def test_old_delayed_notification_does_not_delete_reauthorized_account(self):
        self.login()
        old_event = self.now.timestamp() + 1
        self.now += timedelta(seconds=10)
        fresh = self.login()
        self.service.process_event('delayed-event', 'apple-subject', 'consent-revoked', old_event)
        self.assertEqual(self.service.session(fresh.token), fresh.user)

    def test_delete_can_be_retried_after_response_is_lost(self):
        result = self.login()
        self.service.delete_account(result.token)
        self.service.delete_account(result.token)
        self.assertIsNone(self.service.session(result.token))
        self.assertEqual(len(self.store.pending_revocations()), 1)

    def test_guest_and_default_nickname(self):
        self.assertIsNone(self.service.session(None))
        self.assertEqual(self.login().user.nickname, 'Apple 用户')
