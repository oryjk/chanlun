import logging
import os
import secrets
from dataclasses import dataclass

DEFAULT_REGISTRATION_API_BASE = "http://127.0.0.1:18081/api/v1/app"
DEFAULT_SESSION_MAX_AGE = 30 * 24 * 3600


@dataclass(frozen=True)
class Settings:
    cors_allow_origins: list[str]
    registration_api_base: str
    session_secret: str
    session_max_age: int
    session_cookie_secure: bool
    ranking_store_path: str = "var/rankings/rankings.sqlite3"
    ranking_run_at: str = "15:30"
    ranking_workers: int = 4
    ranking_enabled: bool = True


def _cors_allow_origins() -> list[str]:
    # 逗号分隔的来源列表；未配置时允许任意客户端访问
    value = os.getenv("CHANLUN_CORS_ORIGINS", "*")
    return [origin.strip() for origin in value.split(",") if origin.strip()] or ["*"]


def _session_secret() -> str:
    secret = os.getenv("CHANLUN_SESSION_SECRET", "").strip()
    if secret:
        return secret
    # 未配置时使用进程内随机密钥：开发可用，但重启后所有登录态失效
    logging.getLogger(__name__).warning("CHANLUN_SESSION_SECRET 未配置，使用临时会话密钥")
    return secrets.token_urlsafe(48)


def load_settings() -> Settings:
    from datetime import time
    run_at = os.getenv("CHANLUN_RANKING_RUN_AT", "15:30")
    parsed_time = time.fromisoformat(run_at)
    if parsed_time.tzinfo is not None or parsed_time < time(15, 0):
        raise ValueError("CHANLUN_RANKING_RUN_AT 必须是收盘后的北京时间（15:00 或之后）")
    workers = int(os.getenv("CHANLUN_RANKING_WORKERS", "4"))
    if not 1 <= workers <= 16:
        raise ValueError("CHANLUN_RANKING_WORKERS 必须在 1 到 16 之间")
    return Settings(
        cors_allow_origins=_cors_allow_origins(),
        registration_api_base=os.getenv("CHANLUN_REGISTRATION_API_BASE", DEFAULT_REGISTRATION_API_BASE),
        session_secret=_session_secret(),
        session_max_age=int(os.getenv("CHANLUN_SESSION_MAX_AGE", DEFAULT_SESSION_MAX_AGE)),
        session_cookie_secure=os.getenv("CHANLUN_SESSION_COOKIE_SECURE", "false").lower() == "true",
        ranking_store_path=os.getenv("CHANLUN_RANKING_STORE_PATH", "var/rankings/rankings.sqlite3"),
        ranking_run_at=run_at,
        ranking_workers=workers,
        ranking_enabled=os.getenv("CHANLUN_RANKING_ENABLED", "true").lower() == "true",
    )
