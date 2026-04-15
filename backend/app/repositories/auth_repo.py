from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.db.models import User


def get_user_by_username(db: Session, username: str) -> User | None:
    return db.query(User).filter(User.username == username).first()


def create_user(
    db: Session,
    username: str,
    password_hash: str,
    phone_model: str | None = None,
    phone_uid: str | None = None,
) -> User:
    user = User(
        username=username,
        password_hash=password_hash,
        phone_model=phone_model,
        phone_uid=phone_uid,
    )
    db.add(user)
    try:
        db.commit()
    except IntegrityError:
        db.rollback()
        raise
    db.refresh(user)
    return user
