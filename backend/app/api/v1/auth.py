from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from app.db.database import get_db
from app.schemas.auth import (
    LoginRequest,
    LoginResponse,
    SignUpRequest,
    SignUpResponse,
)
from app.services.auth_service import AuthException, login_service, signup_service

router = APIRouter(prefix="/api/v1/auth", tags=["auth"])


@router.post("/signup", response_model=SignUpResponse)
def signup(request: SignUpRequest, db: Session = Depends(get_db)):
    try:
        return signup_service(db, request)
    except AuthException as e:
        raise HTTPException(status_code=400, detail=e.message)


@router.post("/login", response_model=LoginResponse)
def login(request: LoginRequest, db: Session = Depends(get_db)):
    try:
        return login_service(db, request)
    except AuthException as e:
        raise HTTPException(status_code=401, detail=e.message)