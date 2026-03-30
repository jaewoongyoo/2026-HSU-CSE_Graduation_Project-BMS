from fastapi import HTTPException, status


class SessionNotFoundException(HTTPException):
    def __init__(self, session_id: str):
        super().__init__(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Session not found: {session_id}",
        )


class InvalidRawDataException(HTTPException):
    def __init__(self, detail: str):
        super().__init__(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail=detail,
        )


class AIServiceException(HTTPException):
    def __init__(self, detail: str = "AI service request failed"):
        super().__init__(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail=detail,
        )
