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
    op.add_column("battery_telemetry", sa.Column("temperature_c", sa.Float(), nullable=True))
    op.add_column("battery_telemetry", sa.Column("elapsed_ms", sa.Float(), nullable=True))
    op.add_column("battery_telemetry", sa.Column("battery_status", sa.String(length=50), nullable=True))
    op.add_column("battery_telemetry", sa.Column("screen_state", sa.Boolean(), nullable=True))


def downgrade() -> None:
    op.drop_column("battery_telemetry", "screen_state")
    op.drop_column("battery_telemetry", "battery_status")
    op.drop_column("battery_telemetry", "elapsed_ms")
    op.drop_column("battery_telemetry", "temperature_c")
    op.drop_column("devices", "powerbank_capacity_mah")
