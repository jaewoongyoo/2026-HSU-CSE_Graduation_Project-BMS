from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    AWS_REGION: str = "ap-northeast-2"
    DDB_SESSION_TABLE: str = "battery_sessions"
    DDB_RAW_TABLE: str = "battery_session_raw"
    AI_SERVER_BASE_URL: str = "http://127.0.0.1:8000"
    APP_NAME: str = "BatteryInsight Backend"
    APP_ENV: str = "local"
    REQUEST_TIMEOUT_SECONDS: int = 10

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8")


settings = Settings()
