import base64
from contextlib import contextmanager
from datetime import datetime
import hashlib
import hmac
from pathlib import Path
import secrets
import sqlite3
from uuid import uuid4

from cryptography.fernet import Fernet

from ...domain.app_auth import (
    AppIdentity,
    AuthConflict,
    AuthRateLimited,
    AuthRejected,
    EmailVerificationChallenge,
    LoginChallenge,
    Revocation,
    hash_password,
    verify_password,
)


def digest(value: str) -> str:
    return hashlib.sha256(value.encode()).hexdigest()


class SqliteAppAuthStore:
    def __init__(self, path, encryption_key):
        self.path = Path(path).resolve()
        self.path.parent.mkdir(parents=True, exist_ok=True)
        # Accept an application secret of >=32 chars; derive a Fernet key.
        self._cipher = Fernet(base64.urlsafe_b64encode(hashlib.sha256(encryption_key.encode()).digest()))
        with self._db() as db:
            db.executescript('''
                PRAGMA journal_mode=WAL;
                CREATE TABLE IF NOT EXISTS challenges (
                    id TEXT PRIMARY KEY, nonce TEXT NOT NULL, state TEXT NOT NULL,
                    created REAL NOT NULL, expires REAL NOT NULL);
                CREATE TABLE IF NOT EXISTS users (
                    id TEXT PRIMARY KEY, subject TEXT UNIQUE NOT NULL, nickname TEXT NOT NULL,
                    refresh BLOB NOT NULL, authorized REAL NOT NULL DEFAULT 0,
                    password_hash TEXT NOT NULL DEFAULT '');
                CREATE TABLE IF NOT EXISTS email_codes (
                    email_key TEXT PRIMARY KEY, code_digest TEXT NOT NULL,
                    attempts INTEGER NOT NULL DEFAULT 0, created REAL NOT NULL, expires REAL NOT NULL);
                CREATE TABLE IF NOT EXISTS sessions (
                    token TEXT PRIMARY KEY, user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    expires REAL NOT NULL);
                CREATE TABLE IF NOT EXISTS revocations (id TEXT PRIMARY KEY, token BLOB NOT NULL);
                CREATE TABLE IF NOT EXISTS tombstones (subject TEXT PRIMARY KEY, deleted REAL NOT NULL);
                CREATE TABLE IF NOT EXISTS events (id TEXT PRIMARY KEY, received REAL NOT NULL);
                CREATE TABLE IF NOT EXISTS deletion_receipts (token TEXT PRIMARY KEY, expires REAL NOT NULL);
            ''')
            columns = {row[1] for row in db.execute('PRAGMA table_info(users)')}
            if 'authorized' not in columns:
                db.execute('ALTER TABLE users ADD COLUMN authorized REAL NOT NULL DEFAULT 0')
            if 'password_hash' not in columns:
                db.execute("ALTER TABLE users ADD COLUMN password_hash TEXT NOT NULL DEFAULT ''")

    @contextmanager
    def _db(self, readonly=False):
        db = sqlite3.connect(self.path.as_uri() + '?mode=ro' if readonly else str(self.path),
                             uri=readonly, timeout=10)
        try:
            db.execute('PRAGMA foreign_keys=ON')
            with db:
                yield db
        finally:
            db.close()

    def add_challenge(self, challenge):
        with self._db() as db:
            db.execute('DELETE FROM challenges WHERE expires<=?', (challenge.created_at.timestamp(),))
            db.execute('INSERT INTO challenges VALUES(?,?,?,?,?)', (
                challenge.challenge_id, challenge.nonce, challenge.state,
                challenge.created_at.timestamp(), challenge.expires_at.timestamp()))

    def consume_challenge(self, challenge_id, now):
        with self._db() as db:
            db.execute('BEGIN IMMEDIATE')
            row = db.execute('SELECT nonce,state,created,expires FROM challenges WHERE id=?', (challenge_id,)).fetchone()
            if not row or row[3] <= now.timestamp():
                raise AuthRejected('登录请求已过期或已使用，请重新登录')
            db.execute('DELETE FROM challenges WHERE id=?', (challenge_id,))
        return LoginChallenge(challenge_id, row[0], row[1],
                              datetime.fromtimestamp(row[2], now.tzinfo), datetime.fromtimestamp(row[3], now.tzinfo))

    def login(self, identity, nickname, token, expires_at, challenge, now):
        key = digest(identity.issuer + ':' + identity.subject)
        with self._db() as db:
            db.execute('BEGIN IMMEDIATE')
            tomb = db.execute('SELECT deleted FROM tombstones WHERE subject=?', (key,)).fetchone()
            if tomb and tomb[0] >= challenge.created_at.timestamp():
                raise AuthRejected('账号状态已变化，请重新登录')
            row = db.execute('SELECT id,nickname FROM users WHERE subject=?', (key,)).fetchone()
            name = str(nickname or '').strip()[:60]
            user = AppIdentity(row[0] if row else str(uuid4()), name or (row[1] if row else 'Apple 用户'))
            db.execute('INSERT INTO users(id,subject,nickname,refresh,authorized) VALUES(?,?,?,?,?) ON CONFLICT(subject) DO UPDATE SET nickname=excluded.nickname,refresh=excluded.refresh,authorized=excluded.authorized',
                       (user.user_id, key, user.nickname, self._cipher.encrypt(identity.refresh_token.encode()), now.timestamp()))
            db.execute('DELETE FROM sessions WHERE expires<=?', (now.timestamp(),))
            db.execute('INSERT INTO sessions VALUES(?,?,?)', (digest(token), user.user_id, expires_at.timestamp()))
            db.execute('DELETE FROM tombstones WHERE deleted<?', (now.timestamp() - 600,))
        return user

    def save_email_code(self, challenge: EmailVerificationChallenge, cooldown_seconds: int = 60):
        now_ts = challenge.created_at.timestamp()
        with self._db() as db:
            db.execute('BEGIN IMMEDIATE')
            db.execute('DELETE FROM email_codes WHERE expires<=?', (now_ts,))
            if db.execute('SELECT 1 FROM users WHERE subject=?', (challenge.email_key,)).fetchone():
                raise AuthConflict('该邮箱已注册，请直接登录')
            existing = db.execute('SELECT created FROM email_codes WHERE email_key=?', (challenge.email_key,)).fetchone()
            if existing and (now_ts - existing[0]) < cooldown_seconds:
                raise AuthRateLimited('验证码发送过于频繁，请稍后再试')
            db.execute(
                'INSERT OR REPLACE INTO email_codes(email_key, code_digest, attempts, created, expires) VALUES(?,?,0,?,?)',
                (challenge.email_key, challenge.code_digest, now_ts, challenge.expires_at.timestamp()),
            )

    def register_email(self, email_key: str, code_digest: str, password_hash: str, nickname: str,
                       token: str, expires_at: datetime, now: datetime) -> AppIdentity:
        now_ts = now.timestamp()
        rejected = False
        with self._db() as db:
            db.execute('BEGIN IMMEDIATE')
            if db.execute('SELECT 1 FROM users WHERE subject=?', (email_key,)).fetchone():
                raise AuthConflict('该邮箱已注册，请直接登录')
            row = db.execute(
                'SELECT code_digest, attempts, expires FROM email_codes WHERE email_key=?',
                (email_key,),
            ).fetchone()
            if not row or row[2] <= now_ts or row[1] >= 5:
                db.execute('DELETE FROM email_codes WHERE email_key=?', (email_key,))
                rejected = True
            elif not hmac.compare_digest(row[0], code_digest):
                next_attempts = row[1] + 1
                if next_attempts >= 5:
                    db.execute('DELETE FROM email_codes WHERE email_key=?', (email_key,))
                else:
                    db.execute('UPDATE email_codes SET attempts=? WHERE email_key=?', (next_attempts, email_key))
                rejected = True
            else:
                db.execute('DELETE FROM email_codes WHERE email_key=?', (email_key,))
                user = AppIdentity(str(uuid4()), nickname)
                db.execute(
                    'INSERT INTO users(id,subject,nickname,refresh,authorized,password_hash) VALUES(?,?,?,?,?,?)',
                    (user.user_id, email_key, user.nickname, b'', now_ts, password_hash),
                )
                db.execute('DELETE FROM sessions WHERE expires<=?', (now_ts,))
                db.execute('INSERT INTO sessions VALUES(?,?,?)', (digest(token), user.user_id, expires_at.timestamp()))
        if rejected:
            raise AuthRejected('验证码错误或已过期')
        return user

    def find_email_credentials(self, email_key: str) -> tuple[AppIdentity, str] | None:
        with self._db(True) as db:
            row = db.execute(
                'SELECT id, nickname, password_hash FROM users WHERE subject=?',
                (email_key,),
            ).fetchone()
        if not row or not row[2]:
            return None
        return AppIdentity(row[0], row[1]), row[2]

    def create_session(self, user_id: str, token: str, expires_at: datetime, now: datetime) -> None:
        with self._db() as db:
            db.execute('DELETE FROM sessions WHERE expires<=?', (now.timestamp(),))
            db.execute('INSERT INTO sessions VALUES(?,?,?)', (digest(token), user_id, expires_at.timestamp()))

    def session(self, token, now):
        with self._db(True) as db:
            row = db.execute('SELECT u.id,u.nickname FROM sessions s JOIN users u ON u.id=s.user_id WHERE s.token=? AND s.expires>?',
                             (digest(token), now.timestamp())).fetchone()
        return AppIdentity(*row) if row else None

    def logout(self, token):
        with self._db() as db:
            db.execute('DELETE FROM sessions WHERE token=?', (digest(token),))

    def _delete(self, db, row, now, enqueue=True):
        user_id, subject, refresh = row
        if enqueue and refresh:
            db.execute('INSERT INTO revocations VALUES(?,?)', (str(uuid4()), refresh))
        db.execute('DELETE FROM deletion_receipts WHERE expires<=?', (now.timestamp(),))
        db.execute('INSERT OR REPLACE INTO deletion_receipts SELECT token,? FROM sessions WHERE user_id=?',
                   (now.timestamp() + 86400, user_id))
        db.execute('INSERT INTO tombstones VALUES(?,?) ON CONFLICT(subject) DO UPDATE SET deleted=excluded.deleted',
                   (subject, now.timestamp()))
        db.execute('DELETE FROM users WHERE id=?', (user_id,))

    def delete_account(self, token, now):
        with self._db() as db:
            db.execute('BEGIN IMMEDIATE')
            row = db.execute('SELECT u.id,u.subject,u.refresh FROM sessions s JOIN users u ON u.id=s.user_id WHERE s.token=? AND s.expires>?',
                             (digest(token), now.timestamp())).fetchone()
            if not row:
                if db.execute('SELECT 1 FROM deletion_receipts WHERE token=? AND expires>?',
                              (digest(token), now.timestamp())).fetchone():
                    return
                raise AuthRejected('登录已失效，请重新登录')
            self._delete(db, row, now)

    def pending_revocations(self):
        with self._db(True) as db:
            rows = db.execute('SELECT id,token FROM revocations LIMIT 20').fetchall()
        return tuple(Revocation(key, self._cipher.decrypt(value).decode()) for key, value in rows)

    def finish_revocation(self, job_id):
        with self._db() as db:
            db.execute('DELETE FROM revocations WHERE id=?', (job_id,))

    def process_event(self, event_id, subject, kind, now, event_time):
        if kind not in ('consent-revoked', 'account-deleted'):
            return False
        key = digest('https://appleid.apple.com:' + subject)
        with self._db() as db:
            db.execute('BEGIN IMMEDIATE')
            if db.execute('SELECT 1 FROM events WHERE id=?', (event_id,)).fetchone():
                return False
            db.execute('INSERT INTO events VALUES(?,?)', (event_id, now.timestamp()))
            row = db.execute('SELECT id,subject,refresh,authorized FROM users WHERE subject=?', (key,)).fetchone()
            if row and event_time < int(row[3]):
                return True  # Valid delayed event for an earlier authorization generation.
            if row:
                self._delete(db, row[:3], now, enqueue=False)
            # Also guard a first login whose identity exchange is still in flight.
            db.execute('INSERT INTO tombstones VALUES(?,?) ON CONFLICT(subject) DO UPDATE SET deleted=MAX(tombstones.deleted,excluded.deleted)',
                       (key, float(int(event_time) + 1)))
        return True
