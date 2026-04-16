from typing import Optional

from pydantic import BaseModel, Field, field_validator


class SignUpRequest(BaseModel):
    username: str = Field(..., min_length=1, max_length=50)
    password: str = Field(..., min_length=4, max_length=72)
    phone_model: Optional[str] = Field(default=None, max_length=100)
    phone_uid: Optional[str] = Field(default=None, max_length=100)

    @field_validator("username")
    @classmethod
    def validate_username(cls, value: str) -> str:
        value = value.strip()
        if not value:
            raise ValueError("username must not be blank")
        return value

    @field_validator("password")
    @classmethod
    def validate_password_bytes(cls, value: str) -> str:
        if len(value.encode("utf-8")) > 72:
            raise ValueError("password must be 72 bytes or fewer in UTF-8")
        return value

    @field_validator("phone_model", "phone_uid")
    @classmethod
    def normalize_optional_text(cls, value: Optional[str]) -> Optional[str]:
        if value is None:
            return None
        value = value.strip()
        return value or None


class SignUpResponse(BaseModel):
    id: int
    username: str
    phone_model: Optional[str] = None
    phone_uid: Optional[str] = None
    message: str


class LoginRequest(BaseModel):
    username: str = Field(..., min_length=1, max_length=50)
    password: str = Field(..., min_length=4, max_length=72)

    @field_validator("username")
    @classmethod
    def validate_username(cls, value: str) -> str:
        value = value.strip()
        if not value:
            raise ValueError("username must not be blank")
        return value

    @field_validator("password")
    @classmethod
    def validate_password_bytes(cls, value: str) -> str:
        if len(value.encode("utf-8")) > 72:
            raise ValueError("password must be 72 bytes or fewer in UTF-8")
        return value


class LoginResponse(BaseModel):
    id: int
    username: str
    phone_model: Optional[str] = None
    phone_uid: Optional[str] = None
    message: str
