from contextlib import asynccontextmanager
import asyncio

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from ..interfaces.api.auth_routes import router as auth_router
from ..interfaces.api.app_auth_routes import router as app_auth_router
from ..interfaces.api.error_handlers import register_error_handlers
from ..interfaces.api.routes import router
from .container import build_application
from .settings import load_settings
from .app_auth import build_native_auth

settings = load_settings()
services = build_application(settings)


@asynccontextmanager
async def lifespan(application):
    application.state.native_auth.start()
    worker = services.ranking_worker
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
app.state.queries = services.queries
app.state.commands = services.commands
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
