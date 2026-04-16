from sqlalchemy.orm import Session

from app.core.security import hash_password
from app.repositories.user_repo import delete_user, get_all_users, get_user_by_id, update_user
from app.schemas.user import UserUpdate


class UserException(Exception):
    def __init__(self, message: str):
        self.message = message
        super().__init__(message)


def get_user_service(db: Session, user_id: int):
    user = get_user_by_id(db, user_id)
    if not user:
        raise UserException("사용자를 찾을 수 없습니다.")
    return user


def get_all_users_service(db: Session):
    return get_all_users(db)


def update_user_service(db: Session, user_id: int, request: UserUpdate):
    user = get_user_by_id(db, user_id)
    if not user:
        raise UserException("사용자를 찾을 수 없습니다.")

    update_data = request.model_dump(exclude_unset=True)
    if "password" in update_data:
        update_data["password_hash"] = hash_password(update_data.pop("password"))

    return update_user(db, user, update_data)


def delete_user_service(db: Session, user_id: int):
    user = get_user_by_id(db, user_id)
    if not user:
        raise UserException("사용자를 찾을 수 없습니다.")

    delete_user(db, user)
    return {"message": "사용자가 성공적으로 삭제되었습니다."}
