#!/usr/bin/env python3
"""Exercise actual API HTTP on isolated storage; market upstreams remain live.

Registration identity exchange uses a local fake HTTP server, never a real user.
Exit 1 distinguishes live upstream failures in the printed report. No production
runtime files or existing ranking snapshots are read or written.
"""
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import importlib
import json
import os
from pathlib import Path
import socket
import sys
import tempfile
from threading import Thread
import time
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

import uvicorn

ROOT = Path(__file__).resolve().parents[1]


class Registration(BaseHTTPRequestHandler):
    used = False
    def log_message(self, *args):
        pass
    def send(self, status, data):
        self.send_response(status)
        self.send_header('Content-Type', 'application/json')
        self.end_headers()
        self.wfile.write(json.dumps({'code':0 if status == 200 else status, 'data':data}).encode())
    def do_POST(self):
        body = json.loads(self.rfile.read(int(self.headers.get('Content-Length', 0))) or b'{}')
        if self.path.endswith('/auth/webview-codes/exchange') and body.get('code') == 'smoke-code' and not type(self).used:
            type(self).used = True
            self.send(200, {'token':'smoke-registration-token'})
        else:
            self.send(401, None)
    def do_GET(self):
        if self.path.endswith('/users/me') and self.headers.get('Authorization') == 'Bearer smoke-registration-token':
            self.send(200, {'id':77, 'nickname':'HTTP 测试账号', 'avatar_url':None})
        else:
            self.send(401, None)


def main():
    with tempfile.TemporaryDirectory(prefix='chanlun-http-') as directory:
        registration = ThreadingHTTPServer(('127.0.0.1', 0), Registration)
        Thread(target=registration.serve_forever, daemon=True).start()
        os.environ.update(CHANLUN_RANKING_ENABLED='false', CHANLUN_APPLE_ENABLED='false',
                          CHANLUN_RANKING_STORE_PATH=str(Path(directory) / 'rankings.sqlite3'),
                          CHANLUN_SESSION_SECRET='isolated-http-smoke-session-secret-at-least-32',
                          CHANLUN_REGISTRATION_API_BASE=f'http://127.0.0.1:{registration.server_port}/api/v1/app')
        previous = Path.cwd()
        os.chdir(directory)
        sys.path.insert(0, str(ROOT))
        app = importlib.import_module('backend.app.main').app
        sock = socket.socket()
        sock.bind(('127.0.0.1', 0))
        base = f'http://127.0.0.1:{sock.getsockname()[1]}'
        server = uvicorn.Server(uvicorn.Config(app, log_level='error'))
        thread = Thread(target=lambda: server.run(sockets=[sock]), daemon=True)
        thread.start()
        deadline = time.monotonic() + 10
        while not server.started and time.monotonic() < deadline:
            time.sleep(.02)
        if not server.started:
            raise RuntimeError('API startup failed')
        results = []
        def request(name, path, expected=200, body=None, headers=None):
            req = Request(base + path, data=json.dumps(body).encode() if body is not None else None,
                          headers={'Content-Type':'application/json', **(headers or {})})
            try:
                with urlopen(req, timeout=75) as response:
                    data = json.loads(response.read())
                    row = {'name':name,'status':response.status,'ok':response.status == expected}
                    if name == 'CORS':
                        row['ok'] &= response.headers.get('Access-Control-Allow-Origin') == '*'
                    if name == 'H5 code login':
                        cookie = response.headers.get('Set-Cookie', '')
                        row['ok'] &= 'HttpOnly' in cookie and 'smoke-registration-token' not in json.dumps(data)
                        row['cookie'] = cookie.split(';')[0]
            except HTTPError as error:
                row = {'name':name,'status':error.code,'ok':error.code == expected}
            except (URLError, TimeoutError, OSError) as error:
                row = {'name':name,'status':'transport error','ok':False,'error':type(error).__name__}
            results.append(row)
            print(json.dumps({k:v for k,v in row.items() if k != 'cookie'}, ensure_ascii=False), flush=True)
            return row
        try:
            request('health', '/api/health')
            request('CORS', '/api/health', headers={'Origin':'https://smoke.example'})
            request('timeframes', '/api/timeframes')
            request('persisted ranking pending', '/api/rankings')
            request('invalid security', '/api/chan?secid=invalid', 400)
            request('guest H5 session', '/api/auth/session')
            login = request('H5 code login', '/api/auth/webview-login', body={'code':'smoke-code'})
            if login.get('cookie'):
                request('H5 authenticated session', '/api/auth/session', headers={'Cookie':login['cookie']})
            request('H5 code replay', '/api/auth/webview-login', 401, {'code':'smoke-code'})
            request('native guest', '/api/app-auth/session')
            request('native disabled', '/api/app-auth/challenge', 503, {})
            request('quote sample write', '/api/quote/samples', body={'secid':'1.600519','time':datetime.now(timezone.utc).isoformat(),'price':100})
            jobs = [('stock search','/api/stocks/search?q=600519'), ('ETF search','/api/stocks/search?q=510300')]
            jobs += [(f'K line {period}', f'/api/chan?secid=1.600519&timeframe={period}') for period in ('1d','1h','30m','5m')]
            jobs += [('confluence','/api/chan/confluence?secid=1.600519'), ('quote','/api/quote?secid=1.600519'), ('quote series','/api/quote/series?secid=1.600519')]
            with ThreadPoolExecutor(max_workers=3) as pool:
                list(pool.map(lambda item: request(*item), jobs))
        finally:
            server.should_exit = True
            thread.join(5)
            registration.shutdown()
            registration.server_close()
            os.chdir(previous)
        failures = [row['name'] for row in results if not row['ok']]
        print(json.dumps({'checked':len(results),'failed':failures,'storage':'temporary','market':'live upstream','registration':'local fake HTTP'},ensure_ascii=False))
        return bool(failures)


if __name__ == '__main__':
    sys.exit(main())
