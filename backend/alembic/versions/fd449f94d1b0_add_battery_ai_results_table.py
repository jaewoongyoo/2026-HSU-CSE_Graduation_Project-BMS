"""add battery ai results table

Revision ID: fd449f94d1b0
Revises: 0c1d6d5f9e1a
Create Date: 2026-04-15 21:05:00

"""

from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = "fd449f94d1b0"
down_revision: Union[str, Sequence[str], None] = "0c1d6d5f9e1a"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_table(
        "battery_ai_results",
        sa.Column("id", sa.Integer(), nullable=False),
        sa.Column("session_id", sa.String(length=100), nullable=False),
        sa.Column("soh_percentage", sa.Float(), nullable=True),
        sa.Column("condition", sa.String(length=50), nullable=True),
        sa.Column("estimated_full_charges", sa.Float(), nullable=True),
        sa.Column("powerbank_usable_mah", sa.Float(), nullable=True),
        sa.Column("smartphone_received_mah", sa.Float(), nullable=True),
        sa.Column("mean_temperature_c", sa.Float(), nullable=True),
        sa.Column("raw_response_json", sa.Text(), nullable=True),
        sa.Column("created_at", sa.DateTime(), server_default=sa.text("now()"), nullable=True),
        sa.Column("updated_at", sa.DateTime(), server_default=sa.text("now()"), nullable=True),
        sa.ForeignKeyConstraint(["session_id"], ["battery_sessions.session_id"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index(op.f("ix_battery_ai_results_id"), "battery_ai_results", ["id"], unique=False)
    op.create_index(
        op.f("ix_battery_ai_results_session_id"),
        "battery_ai_results",
        ["session_id"],
        unique=False,
    )


def downgrade() -> None:
    op.drop_index(op.f("ix_battery_ai_results_session_id"), table_name="battery_ai_results")
    op.drop_index(op.f("ix_battery_ai_results_id"), table_name="battery_ai_results")
    op.drop_table("battery_ai_results")
