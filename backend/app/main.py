from contextlib import asynccontextmanager
import asyncio

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from ..interfaces.api.auth_routes import router as auth_router
from ..interfaces.api.app_auth_routes import router as app_auth_router
from ..interfaces.api.error_handlers import register_error_handlers
from ..interfaces.api.routes import router
from .container import build_services, build_ranking_worker
from .settings import load_settings
from .app_auth import build_native_auth

settings = load_settings()


@asynccontextmanager
async def lifespan(application):
    application.state.native_auth.start()
    worker = build_ranking_worker(settings, application.state.commands) if settings.ranking_enabled else None
    if worker:
        worker.start()
    try:
        yield
    finally:
        await asyncio.to_thread(application.state.native_auth.stop)
        if worker:
            await asyncio.to_thread(worker.stop)


app = FastAPI(title="缠观 API", version="0.5.0", lifespan=lifespan)
app.state.settings = settings
app.state.native_auth = build_native_auth()
app.state.queries, app.state.commands = build_services(settings)
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_allow_origins,
    allow_methods=["GET", "POST"],
    allow_headers=["*"],
)
app.include_router(router)
app.include_router(auth_router)
app.include_router(app_auth_router)
register_error_handlers(app)
