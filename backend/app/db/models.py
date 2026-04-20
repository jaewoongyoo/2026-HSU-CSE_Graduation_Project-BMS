from sqlalchemy import (
    Boolean,
    Column,
    DateTime,
    Float,
    ForeignKey,
    Integer,
    String,
    Text,
)
from sqlalchemy.orm import relationship
from sqlalchemy.sql import func

from app.db.database import Base


class User(Base):
    __tablename__ = "users"

    id = Column(Integer, primary_key=True, index=True)
    username = Column(String(100), unique=True, nullable=False)
    password_hash = Column(String(255), nullable=False)
    phone_model = Column(String(100), nullable=True)
    phone_uid = Column(String(100), nullable=True)
    created_at = Column(DateTime, server_default=func.now(), nullable=True)

    devices = relationship("Device", back_populates="user", cascade="all, delete-orphan")


class Device(Base):
    __tablename__ = "devices"

    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=True)
    manufacturer = Column(String(100), nullable=True)
    model_name = Column(String(100), nullable=False)
    powerbank_capacity_mah = Column(Integer, nullable=True)
    manufacture_date = Column(String(20), nullable=True)
    created_at = Column(DateTime, server_default=func.now(), nullable=True)

    user = relationship("User", back_populates="devices")
    sessions = relationship("BatterySession", back_populates="device", cascade="all, delete-orphan")
    telemetry_points = relationship(
        "BatteryTelemetry",
        back_populates="device",
        cascade="all, delete-orphan",
    )
    soh_analyses = relationship("SohAnalysis", back_populates="device", cascade="all, delete-orphan")
    shared_reports = relationship(
        "SharedReport",
        back_populates="device",
        cascade="all, delete-orphan",
    )


class BatterySession(Base):
    __tablename__ = "battery_sessions"

    id = Column(Integer, primary_key=True, index=True)
    session_id = Column(String(100), unique=True, nullable=False, index=True)
    device_id = Column(Integer, ForeignKey("devices.id", ondelete="CASCADE"), nullable=False)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False)
    android_api_level = Column(Integer, nullable=False)
    powerbank_id = Column(String(100), nullable=True)
    session_start_ts = Column(DateTime, nullable=False)
    session_end_ts = Column(DateTime, nullable=True)
    capacity_ah = Column(Float, nullable=True)
    powerbank_capacity_start_mah = Column(Float, nullable=True)
    powerbank_capacity_end_mah = Column(Float, nullable=True)
    status = Column(String(30), nullable=False)
    label_capacity_ah = Column(Float, nullable=True)
    created_at = Column(DateTime, server_default=func.now(), nullable=True)
    updated_at = Column(DateTime, server_default=func.now(), onupdate=func.now(), nullable=True)

    device = relationship("Device", back_populates="sessions")
    telemetry_points = relationship(
        "BatteryTelemetry",
        back_populates="session",
        cascade="all, delete-orphan",
    )
    ai_result = relationship(
        "BatteryAiResult",
        back_populates="session",
        cascade="all, delete-orphan",
        uselist=False,
    )
    soh_analyses = relationship("SohAnalysis", back_populates="session", cascade="all, delete-orphan")


class BatteryTelemetry(Base):
    __tablename__ = "battery_telemetry"

    id = Column(Integer, primary_key=True, index=True)
    device_id = Column(Integer, ForeignKey("devices.id", ondelete="CASCADE"), nullable=True)
    session_id = Column(String(100), ForeignKey("battery_sessions.session_id", ondelete="CASCADE"), nullable=True)
    timestamp = Column(DateTime, nullable=False)
    soc = Column(Float, nullable=True)
    voltage = Column(Float, nullable=True)
    current_ma = Column(Float, nullable=True)
    temperature_c = Column(Float, nullable=True)
    elapsed_ms = Column(Float, nullable=True)
    battery_status = Column(String(50), nullable=True)
    screen_state = Column(Boolean, nullable=True)
    power_w = Column(Float, nullable=True)
    created_at = Column(DateTime, server_default=func.now(), nullable=True)

    device = relationship("Device", back_populates="telemetry_points")
    session = relationship("BatterySession", back_populates="telemetry_points")


class BatteryAiResult(Base):
    __tablename__ = "battery_ai_results"

    id = Column(Integer, primary_key=True, index=True)
    session_id = Column(
        String(100),
        ForeignKey("battery_sessions.session_id", ondelete="CASCADE"),
        unique=True,
        nullable=False,
        index=True,
    )
    soh_percentage = Column(Float, nullable=True)
    condition = Column(String(50), nullable=True)
    estimated_full_charges = Column(Float, nullable=True)
    powerbank_usable_mah = Column(Float, nullable=True)
    smartphone_received_mah = Column(Float, nullable=True)
    mean_temperature_c = Column(Float, nullable=True)
    raw_response_json = Column(Text, nullable=True)
    created_at = Column(DateTime, server_default=func.now(), nullable=True)
    updated_at = Column(DateTime, server_default=func.now(), onupdate=func.now(), nullable=True)

    session = relationship("BatterySession", back_populates="ai_result")


class SohAnalysis(Base):
    __tablename__ = "soh_analysis"

    id = Column(Integer, primary_key=True, index=True)
    device_id = Column(Integer, ForeignKey("devices.id", ondelete="CASCADE"), nullable=True)
    session_id = Column(String(100), ForeignKey("battery_sessions.session_id", ondelete="CASCADE"), nullable=True)
    analyzed_at = Column(DateTime, server_default=func.now(), nullable=True)
    current_soh = Column(Float, nullable=True)
    grade = Column(String(5), nullable=True)
    predicted_soh_1m = Column(Float, nullable=True)
    predicted_soh_3m = Column(Float, nullable=True)
    predicted_soh_6m = Column(Float, nullable=True)
    cable_loss_pct = Column(Float, nullable=True)
    recommendation = Column(Text, nullable=True)

    device = relationship("Device", back_populates="soh_analyses")
    session = relationship("BatterySession", back_populates="soh_analyses")


class SharedReport(Base):
    __tablename__ = "shared_reports"

    id = Column(Integer, primary_key=True, index=True)
    device_id = Column(Integer, ForeignKey("devices.id", ondelete="CASCADE"), nullable=True)
    share_token = Column(String(100), unique=True, nullable=False)
    phone_model = Column(String(100), nullable=True)
    is_public = Column(Boolean, server_default="true", nullable=True)
    expires_at = Column(DateTime, nullable=True)
    created_at = Column(DateTime, server_default=func.now(), nullable=True)

    device = relationship("Device", back_populates="shared_reports")
