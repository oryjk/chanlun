from concurrent.futures import ThreadPoolExecutor

from fastapi import FastAPI
from fastapi.testclient import TestClient

from backend.app.sumly_bridge import build_sumly_bridge
from backend.interfaces.api.auth_routes import router as auth_router
from backend.interfaces.api.sumly_webview_routes import router
from backend.infrastructure.auth.signed_session_codec import SignedSessionCodec
from backend.application.queries.get_current_user import GetCurrentUserQuery
from types import SimpleNamespace


SECRET = 'x' * 42
COOKIE_SIGNING = 'y' * 42


def test_ticket_requires_server_credential_and_restores_scoped_cookie(tmp_path, monkeypatch):
    monkeypatch.setenv('CHANLUN_SUMLY_BRIDGE_SECRET', SECRET)
    monkeypatch.setenv('CHANLUN_SUMLY_TICKET_STORE_PATH', str(tmp_path / 'tickets.db'))
    settings = SimpleNamespace(session_cookie_secure=True, session_secret=COOKIE_SIGNING)
    app = FastAPI()
    app.state.settings = settings
    app.state.sumly_bridge = build_sumly_bridge(settings)
    app.state.queries = SimpleNamespace(get_current_user=GetCurrentUserQuery(SignedSessionCodec(COOKIE_SIGNING, 3600)))
    app.include_router(router)
    app.include_router(auth_router)
    client = TestClient(app, base_url='https://chanlun.test')
    issue = '/api/auth/sumly/tickets'
    exchange = '/api/auth/sumly/session'
    assert client.post(issue, json={'user_id': 42}).status_code == 401
    response = client.post(issue, json={'user_id': 42}, headers={'Authorization': f'Bearer {SECRET}'})
    assert response.status_code == 201
    assert response.headers['cache-control'] == 'no-store'
    code = response.json()['code']
    with ThreadPoolExecutor(max_workers=2) as workers:
        replies = list(workers.map(lambda _: TestClient(app, base_url='https://chanlun.test').post(exchange, json={'code': code}), range(2)))
    assert sorted(reply.status_code for reply in replies) == [204, 401]
    cookie = next(reply.headers['set-cookie'] for reply in replies if reply.status_code == 204)
    assert 'HttpOnly' in cookie and 'Secure' in cookie and 'SameSite=lax' in cookie
    restored = client.get('/api/auth/session', headers={'Cookie': cookie.split(';')[0]})
    assert restored.json()['user']['user_id'] == 'sumly:42'


def test_ticket_expires_after_sixty_seconds(tmp_path):
    from backend.infrastructure.persistence.sqlite_sumly_ticket import SqliteSumlyTicketStore

    now = 100.0
    tickets = SqliteSumlyTicketStore(str(tmp_path / 'tickets.db'), clock=lambda: now)
    tickets.save('expired', 42)
    now += 60
    assert tickets.consume('expired') is None
