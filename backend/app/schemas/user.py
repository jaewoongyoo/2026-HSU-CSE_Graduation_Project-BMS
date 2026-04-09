from typing import Optional
from pydantic import BaseModel

class UserUpdate(BaseModel):
    name: Optional[str] = None
    password: Optional[str] = None

class UserResponse(BaseModel):
    id: int
    name: str
    username: str

    class Config:
        from_attributes = True