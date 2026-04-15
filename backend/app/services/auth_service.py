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
        raise AuthException("이미 사용 중인 사용자명입니다.")

    try:
        user = create_user(
            db=db,
            username=request.username,
            password_hash=hash_password(request.password),
        )
    except IntegrityError as exc:
        raise AuthException("이미 사용 중인 사용자명입니다.") from exc

    return {
        "id": user.id,
        "username": user.username,
        "message": "회원가입이 완료되었습니다.",
    }


def login_service(db: Session, request: LoginRequest) -> dict:
    user = get_user_by_username(db, request.username)
    if not user:
        raise AuthException("아이디 또는 비밀번호가 올바르지 않습니다.")

    if not verify_password(request.password, user.password_hash):
        raise AuthException("아이디 또는 비밀번호가 올바르지 않습니다.")

    return {
        "id": user.id,
        "username": user.username,
        "message": "로그인에 성공했습니다.",
    }
