from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from app.db.database import get_db
from app.schemas.user import UserResponse, UserUpdate
from app.services.user_service import (
    UserException,
    delete_user_service,
    get_all_users_service,
    get_user_service,
    update_user_service,
)

router = APIRouter(prefix="/api/v1/users", tags=["users"])


@router.get("", response_model=list[UserResponse])
def read_users(db: Session = Depends(get_db)):
    return get_all_users_service(db)


@router.get("/{user_id}", response_model=UserResponse)
def read_user(user_id: int, db: Session = Depends(get_db)):
    try:
        return get_user_service(db, user_id)
    except UserException as e:
        raise HTTPException(status_code=404, detail=e.message)


@router.patch("/{user_id}", response_model=UserResponse)
def update_user_info(user_id: int, request: UserUpdate, db: Session = Depends(get_db)):
    try:
        return update_user_service(db, user_id, request)
    except UserException as e:
        raise HTTPException(status_code=404, detail=e.message)


@router.delete("/{user_id}")
def delete_user_info(user_id: int, db: Session = Depends(get_db)):
    try:
        return delete_user_service(db, user_id)
    except UserException as e:
        raise HTTPException(status_code=404, detail=e.message)
