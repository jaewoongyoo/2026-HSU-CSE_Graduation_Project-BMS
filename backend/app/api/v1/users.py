from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from app.db.database import get_db
from app.schemas.user import UserResponse, UserUpdate
from app.services.user_service import (
    UserException,
    get_user_service,
    get_users_service,
    update_user_service,
    delete_user_service,
)

router = APIRouter(prefix="/api/v1/users", tags=["users"])

# R - 목록 조회
@router.get("", response_model=list[UserResponse])
def read_users(skip: int = 0, limit: int = 10, db: Session = Depends(get_db)):
    return get_users_service(db, skip=skip, limit=limit)

# R - 단일 조회
@router.get("/{user_id}", response_model=UserResponse)
def read_user(user_id: int, db: Session = Depends(get_db)):
    try:
        return get_user_service(db, user_id)
    except UserException as e:
        raise HTTPException(status_code=404, detail=e.message)

# U - 수정
@router.patch("/{user_id}", response_model=UserResponse)
def update_user_info(user_id: int, request: UserUpdate, db: Session = Depends(get_db)):
    try:
        return update_user_service(db, user_id, request)
    except UserException as e:
        raise HTTPException(status_code=404, detail=e.message)

# D - 삭제
@router.delete("/{user_id}")
def delete_user_info(user_id: int, db: Session = Depends(get_db)):
    try:
        return delete_user_service(db, user_id)
    except UserException as e:
        raise HTTPException(status_code=404, detail=e.message)