import logging

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app.api.v1 import auth, community, devices, health, result, session, upload, users
from app.core.logging import configure_logging
from app.db import models  # noqa: F401

configure_logging()
logger = logging.getLogger(__name__)

app = FastAPI(title="BatteryInsight Backend", version="1.0.0")


@app.exception_handler(RequestValidationError)
async def validation_exception_handler(_: Request, exc: RequestValidationError):
    return JSONResponse(
        status_code=422,
        content={"detail": exc.errors()},
    )


@app.exception_handler(Exception)
async def unhandled_exception_handler(_: Request, exc: Exception):
    logger.exception("Unhandled server error", exc_info=exc)
    return JSONResponse(
        status_code=500,
        content={"detail": "Internal Server Error"},
    )


app.include_router(session.router)
app.include_router(upload.router)
app.include_router(result.router)
app.include_router(health.router)
app.include_router(auth.router)
app.include_router(users.router)
app.include_router(devices.router)
app.include_router(community.router)
