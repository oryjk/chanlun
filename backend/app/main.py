import os

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from ..interfaces.api.error_handlers import register_error_handlers
from ..interfaces.api.routes import router
from .container import build_services


def cors_allow_origins() -> list[str]:
    # 逗号分隔的来源列表；未配置时允许任意客户端访问
    value = os.getenv("CHANLUN_CORS_ORIGINS", "*")
    return [origin.strip() for origin in value.split(",") if origin.strip()] or ["*"]


app = FastAPI(title="缠观 API", version="0.3.0")
app.state.queries, app.state.commands = build_services()
app.add_middleware(
    CORSMiddleware,
    allow_origins=cors_allow_origins(),
    allow_methods=["GET", "POST"],
    allow_headers=["*"],
)
app.include_router(router)
register_error_handlers(app)
