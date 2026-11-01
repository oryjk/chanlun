from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from ..interfaces.api.error_handlers import register_error_handlers
from ..interfaces.api.routes import router
from .container import build_queries

app = FastAPI(title="缠观 API", version="0.3.0")
app.state.queries = build_queries()
app.add_middleware(
    CORSMiddleware,
    allow_origins=[
        "http://localhost:5173",
        "http://127.0.0.1:5173",
        "http://192.168.3.52:5173",
    ],
    allow_methods=["GET"],
    allow_headers=["*"],
)
app.include_router(router)
register_error_handlers(app)
