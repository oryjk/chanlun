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
    return Settings(
        cors_allow_origins=_cors_allow_origins(),
        registration_api_base=os.getenv("CHANLUN_REGISTRATION_API_BASE", DEFAULT_REGISTRATION_API_BASE),
        session_secret=_session_secret(),
        session_max_age=int(os.getenv("CHANLUN_SESSION_MAX_AGE", DEFAULT_SESSION_MAX_AGE)),
        session_cookie_secure=os.getenv("CHANLUN_SESSION_COOKIE_SECURE", "false").lower() == "true",
    )
