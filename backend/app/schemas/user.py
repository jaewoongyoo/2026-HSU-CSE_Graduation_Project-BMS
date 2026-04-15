from typing import Optional
from pydantic import BaseModel

class UserUpdate(BaseModel):
    password: Optional[str] = None

class UserResponse(BaseModel):
    id: int
    username: str

    class Config:
        from_attributes = True