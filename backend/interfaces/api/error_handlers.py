from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

from ...domain.errors import (
    AuthenticationFailed,
    ChanlunError,
    EmptySearchQuery,
    IdentityServiceUnavailable,
    InvalidSecurityId,
    InvalidTimeframe,
    MarketDataNotFound,
    MarketDataUnavailable,
)


def register_error_handlers(app: FastAPI) -> None:
    @app.exception_handler(ChanlunError)
    async def handle_domain_error(request: Request, error: ChanlunError) -> JSONResponse:
        status_code = 500
        if isinstance(error, (InvalidSecurityId, InvalidTimeframe, EmptySearchQuery)):
            status_code = 400
        elif isinstance(error, MarketDataNotFound):
            status_code = 404
        elif isinstance(error, AuthenticationFailed):
            status_code = 401
        elif isinstance(error, (MarketDataUnavailable, IdentityServiceUnavailable)):
            status_code = 502
        return JSONResponse(status_code=status_code, content={"detail": str(error)})
