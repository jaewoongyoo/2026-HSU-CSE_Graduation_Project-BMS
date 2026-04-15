"""add ai input columns to existing schema

Revision ID: 0c1d6d5f9e1a
Revises:
Create Date: 2026-04-13 16:20:00

"""

from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = "0c1d6d5f9e1a"
down_revision: Union[str, Sequence[str], None] = None
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column("devices", sa.Column("powerbank_capacity_mah", sa.Integer(), nullable=True))
    op.create_table(
        "battery_sessions",
        sa.Column("id", sa.Integer(), nullable=False),
        sa.Column("session_id", sa.String(length=100), nullable=False),
        sa.Column("device_id", sa.String(length=100), nullable=False),
        sa.Column("user_id", sa.Integer(), nullable=False),
        sa.Column("android_api_level", sa.Integer(), nullable=False),
        sa.Column("powerbank_id", sa.String(length=100), nullable=True),
        sa.Column("cable_id", sa.String(length=100), nullable=True),
        sa.Column("phone_capacity_mah", sa.Integer(), nullable=False),
        sa.Column("powerbank_capacity_mah", sa.Integer(), nullable=False),
        sa.Column("session_start_ts", sa.DateTime(), nullable=False),
        sa.Column("session_end_ts", sa.DateTime(), nullable=True),
        sa.Column("capacity_ah", sa.Float(), nullable=True),
        sa.Column("status", sa.String(length=30), nullable=False),
        sa.Column("created_at", sa.DateTime(), server_default=sa.text("now()"), nullable=True),
        sa.Column("updated_at", sa.DateTime(), server_default=sa.text("now()"), nullable=True),
        sa.ForeignKeyConstraint(["device_id"], ["devices.device_id"], ondelete="CASCADE"),
        sa.ForeignKeyConstraint(["user_id"], ["users.id"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index(op.f("ix_battery_sessions_id"), "battery_sessions", ["id"], unique=False)
    op.create_index(op.f("ix_battery_sessions_session_id"), "battery_sessions", ["session_id"], unique=True)
    op.add_column("battery_telemetry", sa.Column("session_id", sa.String(length=100), nullable=True))
    op.add_column("battery_telemetry", sa.Column("temperature_c", sa.Float(), nullable=True))
    op.add_column("battery_telemetry", sa.Column("elapsed_ms", sa.Float(), nullable=True))
    op.add_column("battery_telemetry", sa.Column("battery_status", sa.String(length=50), nullable=True))
    op.add_column("battery_telemetry", sa.Column("screen_state", sa.Boolean(), nullable=True))
    op.create_foreign_key(
        "battery_telemetry_session_id_fkey",
        "battery_telemetry",
        "battery_sessions",
        ["session_id"],
        ["session_id"],
        ondelete="CASCADE",
    )
    op.add_column("soh_analysis", sa.Column("session_id", sa.String(length=100), nullable=True))
    op.create_foreign_key(
        "soh_analysis_session_id_fkey",
        "soh_analysis",
        "battery_sessions",
        ["session_id"],
        ["session_id"],
        ondelete="CASCADE",
    )


def downgrade() -> None:
    op.drop_constraint("soh_analysis_session_id_fkey", "soh_analysis", type_="foreignkey")
    op.drop_column("soh_analysis", "session_id")
    op.drop_constraint("battery_telemetry_session_id_fkey", "battery_telemetry", type_="foreignkey")
    op.drop_column("battery_telemetry", "screen_state")
    op.drop_column("battery_telemetry", "battery_status")
    op.drop_column("battery_telemetry", "elapsed_ms")
    op.drop_column("battery_telemetry", "temperature_c")
    op.drop_column("battery_telemetry", "session_id")
    op.drop_index(op.f("ix_battery_sessions_session_id"), table_name="battery_sessions")
    op.drop_index(op.f("ix_battery_sessions_id"), table_name="battery_sessions")
    op.drop_table("battery_sessions")
    op.drop_column("devices", "powerbank_capacity_mah")
