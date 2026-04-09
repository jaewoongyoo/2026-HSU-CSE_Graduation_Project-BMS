from fastapi import FastAPI

from app.core.logging import configure_logging
from app.db.database import Base, engine
from app.db import models  # noqa: F401

# 중복된 import를 하나로 합치고 users 라우터를 추가했습니다.
from app.api.v1 import auth, health, result, session, upload, users

configure_logging()

app = FastAPI(title="BatteryInsight Backend", version="1.0.0")

@app.on_event("startup")
def on_startup():
    Base.metadata.create_all(bind=engine)

app.include_router(session.router)
app.include_router(upload.router)
app.include_router(result.router)
app.include_router(health.router)
app.include_router(auth.router)

# 새로 만든 users 라우터 등록
app.include_router(users.router)