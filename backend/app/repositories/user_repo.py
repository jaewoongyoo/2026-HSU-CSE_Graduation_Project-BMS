from sqlalchemy.orm import Session
from app.db.models import User

# R - 단일 사용자 조회
def get_user_by_id(db: Session, user_id: int) -> User | None:
    return db.query(User).filter(User.id == user_id).first()

# R - 전체 사용자 목록 조회 (페이징 포함)
def get_users(db: Session, skip: int = 0, limit: int = 100) -> list[User]:
    return db.query(User).offset(skip).limit(limit).all()

# U - 사용자 정보 수정
def update_user(db: Session, user: User, update_data: dict) -> User:
    for key, value in update_data.items():
        setattr(user, key, value)
    db.commit()
    db.refresh(user)
    return user

# D - 사용자 삭제
def delete_user(db: Session, user: User) -> None:
    db.delete(user)
    db.commit()