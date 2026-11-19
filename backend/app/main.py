from contextlib import asynccontextmanager
import asyncio

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from ..interfaces.api.auth_routes import router as auth_router
from ..interfaces.api.app_auth_routes import router as app_auth_router
from ..interfaces.api.app_tracking_routes import router as app_tracking_router
from ..interfaces.api.error_handlers import register_error_handlers
from ..interfaces.api.routes import router
from ..interfaces.api.tracking_routes import router as tracking_router
from ..interfaces.api.sumly_webview_routes import router as sumly_router
from .container import build_application
from .env import load_dotenv_if_present
from .settings import load_settings
from .sumly_bridge import build_sumly_bridge
from .app_auth import build_native_auth

load_dotenv_if_present()
settings = load_settings()
services = build_application(settings)


@asynccontextmanager
async def lifespan(application):
    application.state.native_auth.start()
    worker = services.ranking_worker
    if worker:
        worker.start()
    catalog = services.asset_catalog
    if catalog:
        catalog.start_background_sync()
    try:
        yield
    finally:
        await asyncio.to_thread(application.state.native_auth.stop)
        if worker:
            await asyncio.to_thread(worker.stop)
        if catalog:
            await asyncio.to_thread(catalog.stop_background_sync)


app = FastAPI(title="缠观 API", version="0.5.0", lifespan=lifespan)
app.state.settings = settings
app.state.sumly_bridge = build_sumly_bridge(settings)
app.state.native_auth = build_native_auth()
app.state.native_tracking = services.native_tracking
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
app.include_router(sumly_router)
app.include_router(tracking_router)
app.include_router(app_auth_router)
app.include_router(app_tracking_router)
register_error_handlers(app)
