from pydantic import BaseModel, Field, field_validator


class SignUpRequest(BaseModel):
    name: str = Field(..., min_length=1, max_length=50)
    username: str = Field(..., min_length=4, max_length=50)
    password: str = Field(..., min_length=4, max_length=72)

    @field_validator("password")
    @classmethod
    def validate_password_bytes(cls, value: str) -> str:
        if len(value.encode("utf-8")) > 72:
            raise ValueError("비밀번호는 UTF-8 기준 72바이트 이하여야 합니다.")
        return value

class SignUpResponse(BaseModel):
    id: int
    name: str
    username: str
    message: str


class LoginRequest(BaseModel):
    username: str = Field(..., min_length=4, max_length=50)
    password: str = Field(..., min_length=4, max_length=72)

    @field_validator("password")
    @classmethod
    def validate_password_bytes(cls, value: str) -> str:
        if len(value.encode("utf-8")) > 72:
            raise ValueError("비밀번호는 UTF-8 기준 72바이트 이하여야 합니다.")
        return value


class LoginResponse(BaseModel):
    id: int
    name: str
    username: str
    message: str