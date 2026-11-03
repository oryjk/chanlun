from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from ..interfaces.api.auth_routes import router as auth_router
from ..interfaces.api.error_handlers import register_error_handlers
from ..interfaces.api.routes import router
from .container import build_services
from .settings import load_settings

settings = load_settings()
app = FastAPI(title="缠观 API", version="0.4.0")
app.state.settings = settings
app.state.queries, app.state.commands = build_services(settings)
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_allow_origins,
    allow_methods=["GET", "POST"],
    allow_headers=["*"],
)
app.include_router(router)
app.include_router(auth_router)
register_error_handlers(app)
