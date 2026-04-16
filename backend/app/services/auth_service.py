from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.core.security import hash_password, verify_password
from app.repositories.auth_repo import create_user, get_user_by_username
from app.schemas.auth import LoginRequest, SignUpRequest


class AuthException(Exception):
    def __init__(self, message: str):
        self.message = message
        super().__init__(message)


def signup_service(db: Session, request: SignUpRequest) -> dict:
    existing_user = get_user_by_username(db, request.username)
    if existing_user:
        raise AuthException("username already exists")

    try:
        user = create_user(
            db=db,
            username=request.username,
            password_hash=hash_password(request.password),
            phone_model=request.phone_model,
            phone_uid=request.phone_uid,
        )
    except IntegrityError as exc:
        raise AuthException("username already exists") from exc

    return {
        "id": user.id,
        "username": user.username,
        "phone_model": user.phone_model,
        "phone_uid": user.phone_uid,
        "message": "signup successful",
    }


def login_service(db: Session, request: LoginRequest) -> dict:
    user = get_user_by_username(db, request.username)
    if not user:
        raise AuthException("invalid username or password")

    if not verify_password(request.password, user.password_hash):
        raise AuthException("invalid username or password")

    return {
        "id": user.id,
        "username": user.username,
        "phone_model": user.phone_model,
        "phone_uid": user.phone_uid,
        "message": "login successful",
    }
