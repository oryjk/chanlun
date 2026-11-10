import hashlib
import json
import time
import unittest

import jwt
from cryptography.hazmat.primitives.asymmetric import ec, rsa

from backend.domain.app_auth import AuthRejected
from backend.infrastructure.auth.apple_gateway import AppleIdentityGateway


class AppleGatewayTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.signer = rsa.generate_private_key(public_exponent=65537, key_size=2048)
        cls.client_key = ec.generate_private_key(ec.SECP256R1())
        cls.jwk = json.loads(jwt.algorithms.RSAAlgorithm.to_jwk(cls.signer.public_key()))
        cls.jwk.update(kid='test-key', alg='RS256', use='sig')

    def setUp(self):
        self.calls = []
        self.claims = dict(iss='https://appleid.apple.com', aud='cn.test.chanlun',
                           sub='subject', iat=int(time.time()), exp=int(time.time()) + 300,
                           nonce=hashlib.sha256(b'nonce').hexdigest())
        self.gateway = AppleIdentityGateway('cn.test.chanlun', 'TEAM', 'KEY', self.client_key, self.transport)

    def token(self, **changes):
        return jwt.encode(self.claims | changes, self.signer, algorithm='RS256', headers={'kid':'test-key'})

    def transport(self, method, url, fields=None):
        self.calls.append((method, url, fields))
        if url.endswith('/keys'):
            return {'keys':[self.jwk]}
        if url.endswith('/token'):
            return {'id_token':self.token(), 'refresh_token':'refresh'}
        return {}

    def test_valid_login_verifies_nonce_and_uses_es256_client_secret(self):
        result = self.gateway.verify_and_exchange(self.token(), 'code', 'nonce')
        self.assertEqual((result.subject, result.refresh_token), ('subject','refresh'))
        fields = self.calls[-1][2]
        secret = jwt.decode(fields['client_secret'], self.client_key.public_key(), algorithms=['ES256'], audience='https://appleid.apple.com')
        self.assertEqual(secret['sub'], 'cn.test.chanlun')
        self.assertEqual(fields['code'], 'code')

    def test_wrong_nonce_issuer_audience_expiry_or_signature_never_exchanges_code(self):
        for changes in ({'nonce':'wrong'}, {'iss':'https://evil.test'}, {'aud':'wrong'}, {'exp':0}):
            self.calls.clear()
            with self.assertRaises(AuthRejected):
                self.gateway.verify_and_exchange(self.token(**changes), 'code', 'nonce')
            self.assertFalse(any(call[1].endswith('/token') for call in self.calls))
        forged = jwt.encode(self.claims, 'x' * 32, algorithm='HS256', headers={'kid':'test-key'})
        with self.assertRaises(AuthRejected):
            self.gateway.verify_and_exchange(forged, 'code', 'nonce')

    def test_code_exchange_for_a_different_subject_is_rejected(self):
        def transport(method, url, fields=None):
            if url.endswith('/token'):
                return {'id_token':self.token(sub='different'), 'refresh_token':'refresh'}
            return self.transport(method,url,fields)
        self.gateway._transport = transport
        with self.assertRaises(AuthRejected):
            self.gateway.verify_and_exchange(self.token(), 'code', 'nonce')

    def test_notification_requires_verified_signature_and_valid_event(self):
        event_time = int(time.time())
        event = self.token(jti='event', events=json.dumps({'type':'consent-revoked','sub':'subject','event_time':event_time}))
        self.assertEqual(self.gateway.verify_notification(event), ('event','subject','consent-revoked',event_time))
        with self.assertRaises(AuthRejected):
            self.gateway.verify_notification('forged')
