from sqlalchemy import (
    Boolean,
    Column,
    DateTime,
    Float,
    ForeignKey,
    Integer,
    String,
    Text,
    UniqueConstraint,
)
from sqlalchemy.orm import relationship
from sqlalchemy.sql import func

from app.db.database import Base


class BatterySession(Base):
    __tablename__ = "battery_sessions"

    id = Column(Integer, primary_key=True, index=True)
    session_id = Column(String(50), unique=True, index=True, nullable=False)

    user_id = Column(String(100), nullable=False)
    device_model = Column(String(100), nullable=False)
    android_api_level = Column(Integer, nullable=False)

    powerbank_id = Column(String(100), nullable=True)
    cable_id = Column(String(100), nullable=True)

    phone_capacity_mah = Column(Integer, nullable=False, default=4000)
    powerbank_capacity_mah = Column(Integer, nullable=False, default=10000)

    session_start_ts = Column(DateTime(timezone=True), nullable=False)
    session_end_ts = Column(DateTime(timezone=True), nullable=True)

    capacity_ah = Column(Float, nullable=True)
    status = Column(String(30), nullable=False, default="in_progress")

    created_at = Column(DateTime(timezone=True), server_default=func.now(), nullable=False)
    updated_at = Column(
        DateTime(timezone=True),
        server_default=func.now(),
        onupdate=func.now(),
        nullable=False,
    )

    raw_points = relationship(
        "BatteryRawPoint",
        back_populates="session",
        cascade="all, delete-orphan",
    )
    ai_result = relationship(
        "BatteryAiResult",
        back_populates="session",
        uselist=False,
        cascade="all, delete-orphan",
    )


class BatteryRawPoint(Base):
    __tablename__ = "battery_raw_points"
    __table_args__ = (
        UniqueConstraint("session_id", "elapsed_ms", name="uq_raw_session_elapsed"),
    )

    id = Column(Integer, primary_key=True, index=True)
    session_id = Column(String(50), ForeignKey("battery_sessions.session_id"), nullable=False, index=True)

    timestamp = Column(DateTime(timezone=True), nullable=False)
    voltage_mv = Column(Float, nullable=False)
    current_ma = Column(Float, nullable=False)
    temperature_c = Column(Float, nullable=False)
    elapsed_ms = Column(Float, nullable=False)

    battery_level = Column(Integer, nullable=True)
    battery_status = Column(String(50), nullable=True)
    screen_state = Column(Boolean, nullable=True)

    created_at = Column(DateTime(timezone=True), server_default=func.now(), nullable=False)

    session = relationship("BatterySession", back_populates="raw_points")


class BatteryAiResult(Base):
    __tablename__ = "battery_ai_results"

    id = Column(Integer, primary_key=True, index=True)
    session_id = Column(
        String(50),
        ForeignKey("battery_sessions.session_id"),
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

    created_at = Column(DateTime(timezone=True), server_default=func.now(), nullable=False)
    updated_at = Column(
        DateTime(timezone=True),
        server_default=func.now(),
        onupdate=func.now(),
        nullable=False,
    )

    session = relationship("BatterySession", back_populates="ai_result")

    from sqlalchemy import (
    Boolean,
    Column,
    DateTime,
    Float,
    ForeignKey,
    Integer,
    String,
    Text,
    UniqueConstraint,
)
from sqlalchemy.orm import relationship
from sqlalchemy.sql import func

from app.db.database import Base


class User(Base):
    __tablename__ = "users"

    id = Column(Integer, primary_key=True, index=True)
    name = Column(String(50), nullable=False)
    username = Column(String(50), unique=True, index=True, nullable=False)
    password_hash = Column(String(255), nullable=False)

    created_at = Column(DateTime(timezone=True), server_default=func.now(), nullable=False)