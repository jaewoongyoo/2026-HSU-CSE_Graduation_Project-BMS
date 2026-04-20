"""simplify session keys and drop unused fields

Revision ID: e5f6a7b8c9d0
Revises: d4e5f6a7b8c9
Create Date: 2026-04-20 19:20:00

"""

from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = "e5f6a7b8c9d0"
down_revision: Union[str, Sequence[str], None] = "d4e5f6a7b8c9"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column("battery_telemetry", sa.Column("session_id_int", sa.Integer(), nullable=True))
    op.add_column("battery_ai_results", sa.Column("session_id_int", sa.Integer(), nullable=True))
    op.add_column("soh_analysis", sa.Column("session_id_int", sa.Integer(), nullable=True))

    op.execute(
        sa.text(
            """
            UPDATE battery_telemetry bt
            SET session_id_int = bs.id
            FROM battery_sessions bs
            WHERE bt.session_id = bs.session_id
            """
        )
    )
    op.execute(
        sa.text(
            """
            UPDATE battery_ai_results bar
            SET session_id_int = bs.id
            FROM battery_sessions bs
            WHERE bar.session_id = bs.session_id
            """
        )
    )
    op.execute(
        sa.text(
            """
            UPDATE soh_analysis sa2
            SET session_id_int = bs.id
            FROM battery_sessions bs
            WHERE sa2.session_id = bs.session_id
            """
        )
    )

    with op.batch_alter_table("battery_telemetry") as batch_op:
        batch_op.drop_constraint("battery_telemetry_session_id_fkey", type_="foreignkey")
        batch_op.drop_column("session_id")
        batch_op.drop_column("screen_state")
        batch_op.drop_column("power_w")
        batch_op.alter_column("session_id_int", existing_type=sa.Integer(), nullable=True, new_column_name="session_id")
        batch_op.create_foreign_key(
            "battery_telemetry_session_id_fkey",
            "battery_sessions",
            ["session_id"],
            ["id"],
            ondelete="CASCADE",
        )

    with op.batch_alter_table("battery_ai_results") as batch_op:
        batch_op.drop_constraint("battery_ai_results_session_id_fkey", type_="foreignkey")
        batch_op.drop_column("session_id")
        batch_op.alter_column("session_id_int", existing_type=sa.Integer(), nullable=False, new_column_name="session_id")
        batch_op.create_foreign_key(
            "battery_ai_results_session_id_fkey",
            "battery_sessions",
            ["session_id"],
            ["id"],
            ondelete="CASCADE",
        )

    with op.batch_alter_table("soh_analysis") as batch_op:
        batch_op.drop_constraint("soh_analysis_session_id_fkey", type_="foreignkey")
        batch_op.drop_column("session_id")
        batch_op.alter_column("session_id_int", existing_type=sa.Integer(), nullable=True, new_column_name="session_id")
        batch_op.create_foreign_key(
            "soh_analysis_session_id_fkey",
            "battery_sessions",
            ["session_id"],
            ["id"],
            ondelete="CASCADE",
        )

    with op.batch_alter_table("battery_sessions") as batch_op:
        batch_op.drop_column("session_id")
        batch_op.drop_column("powerbank_id")

    op.create_index(
        op.f("ix_battery_ai_results_session_id"),
        "battery_ai_results",
        ["session_id"],
        unique=True,
    )


def downgrade() -> None:
    with op.batch_alter_table("battery_sessions") as batch_op:
        batch_op.add_column(sa.Column("powerbank_id", sa.String(length=100), nullable=True))
        batch_op.add_column(sa.Column("session_id", sa.String(length=100), nullable=True))

    op.execute(
        sa.text(
            """
            UPDATE battery_sessions
            SET session_id = 'sess_' || id::text
            WHERE session_id IS NULL
            """
        )
    )

    with op.batch_alter_table("battery_sessions") as batch_op:
        batch_op.alter_column("session_id", existing_type=sa.String(length=100), nullable=False)

    op.create_index(
        op.f("ix_battery_sessions_session_id"),
        "battery_sessions",
        ["session_id"],
        unique=True,
    )

    op.add_column("battery_telemetry", sa.Column("session_id_str", sa.String(length=100), nullable=True))
    op.add_column("battery_ai_results", sa.Column("session_id_str", sa.String(length=100), nullable=True))
    op.add_column("soh_analysis", sa.Column("session_id_str", sa.String(length=100), nullable=True))

    op.execute(
        sa.text(
            """
            UPDATE battery_telemetry bt
            SET session_id_str = bs.session_id
            FROM battery_sessions bs
            WHERE bt.session_id = bs.id
            """
        )
    )
    op.execute(
        sa.text(
            """
            UPDATE battery_ai_results bar
            SET session_id_str = bs.session_id
            FROM battery_sessions bs
            WHERE bar.session_id = bs.id
            """
        )
    )
    op.execute(
        sa.text(
            """
            UPDATE soh_analysis sa2
            SET session_id_str = bs.session_id
            FROM battery_sessions bs
            WHERE sa2.session_id = bs.id
            """
        )
    )

    with op.batch_alter_table("battery_telemetry") as batch_op:
        batch_op.drop_constraint("battery_telemetry_session_id_fkey", type_="foreignkey")
        batch_op.drop_column("session_id")
        batch_op.alter_column("session_id_str", existing_type=sa.String(length=100), nullable=True, new_column_name="session_id")
        batch_op.add_column(sa.Column("screen_state", sa.Boolean(), nullable=True))
        batch_op.add_column(sa.Column("power_w", sa.Float(), nullable=True))
        batch_op.create_foreign_key(
            "battery_telemetry_session_id_fkey",
            "battery_sessions",
            ["session_id"],
            ["session_id"],
            ondelete="CASCADE",
        )

    with op.batch_alter_table("battery_ai_results") as batch_op:
        batch_op.drop_constraint("battery_ai_results_session_id_fkey", type_="foreignkey")
        batch_op.drop_column("session_id")
        batch_op.alter_column("session_id_str", existing_type=sa.String(length=100), nullable=False, new_column_name="session_id")
        batch_op.create_foreign_key(
            "battery_ai_results_session_id_fkey",
            "battery_sessions",
            ["session_id"],
            ["session_id"],
            ondelete="CASCADE",
        )

    op.create_index(
        op.f("ix_battery_ai_results_session_id"),
        "battery_ai_results",
        ["session_id"],
        unique=True,
    )

    with op.batch_alter_table("soh_analysis") as batch_op:
        batch_op.drop_constraint("soh_analysis_session_id_fkey", type_="foreignkey")
        batch_op.drop_column("session_id")
        batch_op.alter_column("session_id_str", existing_type=sa.String(length=100), nullable=True, new_column_name="session_id")
        batch_op.create_foreign_key(
            "soh_analysis_session_id_fkey",
            "battery_sessions",
            ["session_id"],
            ["session_id"],
            ondelete="CASCADE",
        )
