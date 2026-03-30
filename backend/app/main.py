from fastapi import FastAPI
from app.api.v1 import session, upload, result, health
from app.core.logging import configure_logging

configure_logging()

app = FastAPI(title="BatteryInsight Backend", version="1.0.0")

app.include_router(session.router)
app.include_router(upload.router)
app.include_router(result.router)
app.include_router(health.router)
