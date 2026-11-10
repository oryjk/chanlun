"""Apple OAuth transport and JWT verification. No credential is logged."""
import hashlib
import hmac
import json
from math import isfinite
import time
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

import jwt

from ...domain.app_auth import AppleIdentity, AuthRejected, AuthUnavailable

APPLE = 'https://appleid.apple.com'


def apple_transport(method, url, fields=None):
    body = urlencode(fields).encode() if fields is not None else None
    request = Request(url, data=body, method=method,
                      headers={'Content-Type':'application/x-www-form-urlencoded', 'Accept':'application/json'})
    try:
        with urlopen(request, timeout=10) as response:
            return json.loads(response.read() or b'{}')
    except HTTPError as error:
        if error.code in (400, 401):
            raise AuthRejected('Apple 登录凭证已失效，请重试') from None
        raise AuthUnavailable('Apple 登录服务暂时不可用') from None
    except (URLError, TimeoutError, OSError, ValueError):
        raise AuthUnavailable('Apple 登录服务暂时不可用') from None


class AppleIdentityGateway:
    def __init__(self, client_id, team_id, key_id, private_key, transport=apple_transport):
        self._client_id, self._team_id, self._key_id = client_id, team_id, key_id
        self._private_key, self._transport = private_key, transport
        self._keys, self._keys_until = (), 0

    def _verify(self, token, notification=False):
        try:
            header = jwt.get_unverified_header(token)
            if header.get('alg') != 'RS256' or not isinstance(header.get('kid'), str):
                raise AuthRejected('Apple 登录凭证格式不正确')
            if time.monotonic() >= self._keys_until:
                self._reload_keys()
            key = next((item for item in self._keys if item.get('kid') == header['kid']), None)
            if key is None:
                self._reload_keys()
                key = next((item for item in self._keys if item.get('kid') == header['kid']), None)
            if key is None:
                raise AuthRejected('Apple 登录签名不正确')
            return jwt.decode(token, jwt.PyJWK.from_dict(key).key, algorithms=['RS256'],
                              audience=self._client_id, issuer=APPLE,
                              options={'require':['iss','aud','iat'] + ([] if notification else ['exp','sub'])})
        except (jwt.PyJWTError, ValueError, TypeError, KeyError):
            raise AuthRejected('Apple 登录凭证无效或已过期') from None

    def _reload_keys(self):
        payload = self._transport('GET', APPLE + '/auth/keys')
        if not isinstance(payload, dict) or not isinstance(payload.get('keys'), list):
            raise AuthUnavailable('Apple 签名服务返回异常')
        self._keys = tuple(payload['keys'])
        self._keys_until = time.monotonic() + 3600

    def _secret(self):
        now = int(time.time())
        return jwt.encode({'iss':self._team_id, 'iat':now, 'exp':now + 300,
                           'aud':APPLE, 'sub':self._client_id}, self._private_key,
                          algorithm='ES256', headers={'kid':self._key_id})

    def verify_and_exchange(self, token, code, nonce):
        claims = self._verify(token)
        expected = hashlib.sha256(nonce.encode()).hexdigest()
        if not hmac.compare_digest(str(claims.get('nonce', '')), expected):
            raise AuthRejected('登录请求不匹配，请重试')
        response = self._transport('POST', APPLE + '/auth/token', {
            'client_id':self._client_id, 'client_secret':self._secret(),
            'grant_type':'authorization_code', 'code':code})
        if not isinstance(response, dict) or not response.get('id_token') or not response.get('refresh_token'):
            raise AuthUnavailable('Apple 登录返回信息不完整')
        exchanged = self._verify(response['id_token'])
        if exchanged.get('sub') != claims.get('sub'):
            raise AuthRejected('Apple 登录身份不匹配')
        if not hmac.compare_digest(str(exchanged.get('nonce','')), expected):
            raise AuthRejected('Apple 授权请求不匹配')
        return AppleIdentity(APPLE, claims['sub'], response['refresh_token'])

    def revoke(self, token):
        self._transport('POST', APPLE + '/auth/revoke', {
            'client_id':self._client_id, 'client_secret':self._secret(),
            'token':token, 'token_type_hint':'refresh_token'})

    def verify_notification(self, payload):
        claims = self._verify(payload, notification=True)
        try:
            event = json.loads(claims['events']) if isinstance(claims['events'], str) else claims['events']
            values = claims['jti'], event['sub'], event['type']
            if not all(isinstance(value, str) and 0 < len(value) <= 256 for value in values):
                raise ValueError
            event_time = event['event_time']
            if isinstance(event_time, bool) or not isinstance(event_time, (int, float)) or not isfinite(event_time) or not 0 < event_time <= time.time() + 60:
                raise ValueError
            return (*values, float(event_time))
        except (KeyError, TypeError, ValueError):
            raise AuthRejected('Apple 通知格式不正确') from None
